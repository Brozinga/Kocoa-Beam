# Print recovery: resume an interrupted print after a power loss, a lost
# printer connection or a firmware shutdown.
#
# While a print runs the position in the G-code file, the toolhead position
# and the printing state (temperatures, fan, speed/flow factors, offsets, bed
# mesh, ...) are saved to a small JSON file every few seconds. After the
# printer is back, Fluidd/Mainsail are asked (action:prompt) whether the print
# should be resumed. Resuming heats the bed, homes X/Y only, parks the nozzle
# at the X stop, heats the nozzle there (so it cannot ooze onto the part),
# optionally purges, then goes back to the saved position and continues the
# file from the saved byte offset.
#
# Dormant unless a [print_recovery] section is present in printer.cfg.
#
# This file may be distributed under the terms of the GNU GPLv3 license.
import collections, io, json, logging, os, time

MIN_SAMPLE_PERIOD = 0.2
MAX_SAMPLE_PERIOD = 0.5
HISTORY_SECONDS = 20.
# Extra time a queued move is assumed to still need after its print_time
EXECUTED_MARGIN = 0.3
OBJECT_SCAN_LIMIT = 4 * 1024 * 1024

class PrintRecovery:
    def __init__(self, config):
        self.printer = config.get_printer()
        self.reactor = self.printer.get_reactor()
        self.gcode = self.printer.lookup_object('gcode')
        # RESPOND is what Fluidd/Mainsail send when a prompt is closed
        self.printer.load_object(config, 'respond')
        self.interval = config.getfloat('snapshot_interval', 2.,
                                        minval=0.5, maxval=300.)
        self.park_x = config.getfloat('park_x', None)
        self.park_y = config.getfloat('park_y', None)
        self.park_speed = config.getfloat('park_speed', 100., above=0.)
        self.lift_z = config.getfloat('lift_z', 10., minval=0.)
        self.purge = config.getboolean('purge', True)
        self.purge_length = config.getfloat('purge_length', 20., minval=0.)
        self.purge_speed = config.getfloat('purge_speed', 5., above=0.)
        self.purge_retract = config.getfloat('purge_retract', 2., minval=0.)
        self.min_extruded = config.getfloat('min_extruded', 5., minval=0.)
        self.prompt = config.getboolean('prompt', True)
        self.prompt_repeat = config.getfloat('prompt_repeat', 60., minval=0.)
        self.period = min(MAX_SAMPLE_PERIOD,
                          max(MIN_SAMPLE_PERIOD, self.interval / 2.))
        state_file = config.get('state_file', None)
        if state_file is None:
            cfg_dir = os.path.dirname(
                self.printer.get_start_args()['config_file'])
            state_file = os.path.join(cfg_dir, '.print_recovery.json')
        self.state_file = os.path.expanduser(state_file)
        self.history = collections.deque()
        self.mesh_cache = (None, None)
        self.pending = None
        self.recording = False
        self.resuming = False
        self.shutdown = False
        self.last_write = 0.
        self.write_failed = False
        self.sample_timer = self.prompt_timer = None
        self.printer.register_event_handler("klippy:ready", self._handle_ready)
        self.printer.register_event_handler("klippy:shutdown",
                                            self._handle_shutdown)
        self.gcode.register_command(
            'PRINT_RECOVERY_RESUME', self.cmd_PRINT_RECOVERY_RESUME,
            desc=self.cmd_PRINT_RECOVERY_RESUME_help)
        self.gcode.register_command(
            'PRINT_RECOVERY_DISCARD', self.cmd_PRINT_RECOVERY_DISCARD,
            desc=self.cmd_PRINT_RECOVERY_DISCARD_help)
        self.gcode.register_command(
            'PRINT_RECOVERY_STATUS', self.cmd_PRINT_RECOVERY_STATUS,
            desc=self.cmd_PRINT_RECOVERY_STATUS_help)

    # ---- lifecycle -------------------------------------------------------
    def _handle_ready(self):
        self.shutdown = False
        self.pending = self._read_state()
        if self.pending is not None:
            logging.info("print_recovery: interrupted print found: %s at "
                         "byte %d", self.pending.get('filename'),
                         self.pending.get('file_position', 0))
            if self.prompt:
                self.prompt_timer = self.reactor.register_timer(
                    self._prompt_handler, self.reactor.monotonic() + 5.)
        self.sample_timer = self.reactor.register_timer(
            self._sample_handler, self.reactor.monotonic() + 1.)

    def _handle_shutdown(self):
        # Keep whatever was saved last: that is what a resume will use
        self.shutdown = True

    def get_status(self, eventtime=None):
        p = self.pending
        return {
            'pending': p is not None,
            'filename': p.get('filename') if p else None,
            'progress': p.get('progress') if p else None,
            'saved_at': p.get('saved_at') if p else None,
            'snapshot_interval': self.interval,
        }

    # ---- state file ------------------------------------------------------
    def _read_state(self):
        try:
            with io.open(self.state_file, 'r', encoding='utf-8') as f:
                data = json.load(f)
        except (IOError, OSError):
            return None
        except ValueError:
            logging.exception("print_recovery: unreadable state file")
            return None
        if not isinstance(data, dict) or not data.get('active') \
           or not data.get('filename'):
            return None
        return data

    def _write_state(self, data):
        tmp = self.state_file + '.tmp'
        try:
            with io.open(tmp, 'w', encoding='utf-8') as f:
                f.write(json.dumps(data, sort_keys=True))
                f.flush()
                os.fsync(f.fileno())
            os.replace(tmp, self.state_file)
            self.write_failed = False
        except (IOError, OSError):
            if not self.write_failed:
                logging.exception("print_recovery: unable to save state")
            self.write_failed = True

    def _clear_state(self):
        self.history.clear()
        self.recording = False
        try:
            os.remove(self.state_file)
        except (IOError, OSError):
            pass

    # ---- sampling --------------------------------------------------------
    def _status(self, name, eventtime):
        obj = self.printer.lookup_object(name, None)
        if obj is None or not hasattr(obj, 'get_status'):
            return {}
        try:
            return obj.get_status(eventtime) or {}
        except Exception:
            return {}

    def _mesh_state(self):
        bm = self.printer.lookup_object('bed_mesh', None)
        zmesh = bm.get_mesh() if bm is not None else None
        if zmesh is None:
            return None
        if self.mesh_cache[0] is zmesh:
            return self.mesh_cache[1]
        try:
            mesh = {
                'name': zmesh.get_profile_name(),
                'params': dict(zmesh.get_mesh_params()),
                'probed_matrix': zmesh.get_probed_matrix(),
                'offsets': list(getattr(zmesh, 'mesh_offsets', [0., 0.])),
            }
            json.dumps(mesh)
        except Exception:
            logging.exception("print_recovery: bed mesh not saved")
            mesh = None
        self.mesh_cache = (zmesh, mesh)
        return mesh

    def _take_sample(self, eventtime, sd, stats):
        toolhead = self.printer.lookup_object('toolhead')
        th = toolhead.get_status(eventtime)
        gm = self.printer.lookup_object('gcode_move').get_status(eventtime)
        ext = self._status(th.get('extruder', 'extruder'), eventtime)
        bed = self._status('heater_bed', eventtime)
        fan = self._status('fan', eventtime)
        disp = self._status('display_status', eventtime)
        excl = self._status('exclude_object', eventtime)
        sample = {
            'print_time': th['print_time'],
            'file_position': sd.file_position,
            'file_size': sd.file_size,
            'raw_position': list(toolhead.get_position()),
            'gcode_position': list(gm['gcode_position']),
            'homing_origin': list(gm['homing_origin']),
            'speed': gm['speed'],
            'speed_factor': gm['speed_factor'],
            'extrude_factor': gm['extrude_factor'],
            'absolute_coordinates': gm['absolute_coordinates'],
            'absolute_extrude': gm.get('absolute_extrude',
                                       gm.get('absolute_extrusion', True)),
            'extruder': th.get('extruder', 'extruder'),
            'extruder_target': ext.get('target', 0.),
            'bed_target': bed.get('target', 0.),
            'fan_speed': fan.get('speed', 0.),
            'pressure_advance': ext.get('pressure_advance'),
            'smooth_time': ext.get('smooth_time'),
            'max_velocity': th.get('max_velocity'),
            'max_accel': th.get('max_accel'),
            'minimum_cruise_ratio': th.get('minimum_cruise_ratio'),
            'max_accel_to_decel': th.get('max_accel_to_decel'),
            'square_corner_velocity': th.get('square_corner_velocity'),
            'progress': disp.get('progress'),
            'layer': stats.info_current_layer,
            'total_layer': stats.info_total_layer,
            'excluded_objects': list(excl.get('excluded_objects', [])),
            'current_object': excl.get('current_object'),
        }
        return sample

    def _sample_handler(self, eventtime):
        if self.shutdown:
            return self.reactor.NEVER
        try:
            self._sample(eventtime)
        except Exception:
            logging.exception("print_recovery: sampling failed")
        return eventtime + self.period

    def _sample(self, eventtime):
        sd = self.printer.lookup_object('virtual_sdcard', None)
        stats = self.printer.lookup_object('print_stats', None)
        if sd is None or stats is None or self.resuming:
            return
        state = stats.state
        if state in ('complete', 'cancelled'):
            if self.recording:
                logging.info("print_recovery: print %s, state cleared", state)
                self._clear_state()
            return
        if state != 'printing' or not sd.is_active():
            return
        if stats.get_status(eventtime)['filament_used'] < self.min_extruded:
            return
        sample = self._take_sample(eventtime, sd, stats)
        sample['filename'] = stats.filename
        self.history.append(sample)
        while self.history and \
                sample['print_time'] - self.history[0]['print_time'] \
                > HISTORY_SECONDS:
            self.history.popleft()
        if eventtime - self.last_write < self.interval:
            return
        # Save the newest sample whose moves the MCU has already executed:
        # G-code is read ahead of the motion, and what is still queued is
        # lost when the power drops.
        th = self.printer.lookup_object('toolhead').get_status(eventtime)
        done = th['estimated_print_time'] - EXECUTED_MARGIN
        chosen = None
        for s in self.history:
            if s['print_time'] <= done:
                chosen = s
            else:
                break
        if chosen is None:
            return
        data = dict(chosen)
        data['active'] = True
        data['saved_at'] = time.time()
        data['file_size'] = sd.file_size
        if chosen['file_size']:
            data['progress'] = float(chosen['file_position']) \
                / chosen['file_size']
        data['mesh'] = self._mesh_state()
        self._write_state(data)
        self.last_write = eventtime
        self.recording = True
        if self.pending is not None:
            self.pending = None

    # ---- prompt ----------------------------------------------------------
    def _describe(self, p):
        name = p.get('filename', '?')
        pct = int(round((p.get('progress') or 0.) * 100))
        where = "Z %.2f mm" % (p['gcode_position'][2],)
        if p.get('layer') is not None and p.get('total_layer'):
            where = "layer %d/%d, %s" % (p['layer'], p['total_layer'], where)
        mins = int((time.time() - p.get('saved_at', time.time())) / 60.)
        ago = "%d min ago" % mins if mins < 120 else "%.1f h ago" % (mins/60.)
        return name, pct, where, ago

    def _show_prompt(self):
        p = self.pending
        if p is None:
            return
        name, pct, where, ago = self._describe(p)
        info = lambda m: self.gcode.respond_info("action:" + m, log=False)
        info("prompt_begin Print interrupted")
        info("prompt_text %s stopped at %d%% (%s), saved %s."
             % (name, pct, where, ago))
        info("prompt_text Resume it? The bed heats first, then the nozzle "
             "heats parked at the X stop so it cannot drip on the part.")
        info("prompt_text Only resume if the part is still stuck to the "
             "bed and the axes were not moved by hand.")
        info("prompt_button_group_start")
        info("prompt_button Resume print|PRINT_RECOVERY_RESUME|primary")
        info("prompt_button Discard|PRINT_RECOVERY_DISCARD|error")
        info("prompt_button_group_end")
        info("prompt_show")

    def _end_prompt(self):
        self.gcode.respond_info("action:prompt_end", log=False)

    def _prompt_handler(self, eventtime):
        if self.pending is None or self.shutdown or self.resuming:
            return self.reactor.NEVER
        stats = self.printer.lookup_object('print_stats', None)
        if stats is not None and stats.state == 'printing':
            return self.reactor.NEVER
        self._show_prompt()
        if self.prompt_repeat <= 0.:
            return self.reactor.NEVER
        return eventtime + self.prompt_repeat

    # ---- commands --------------------------------------------------------
    cmd_PRINT_RECOVERY_STATUS_help = \
        "Show the interrupted print that can be resumed, if any"
    def cmd_PRINT_RECOVERY_STATUS(self, gcmd):
        if self.pending is None:
            gcmd.respond_info("print_recovery: nothing to resume")
            return
        name, pct, where, ago = self._describe(self.pending)
        gcmd.respond_info("print_recovery: %s stopped at %d%% (%s), saved %s"
                          % (name, pct, where, ago))
        self._show_prompt()

    cmd_PRINT_RECOVERY_DISCARD_help = \
        "Forget the interrupted print without resuming it"
    def cmd_PRINT_RECOVERY_DISCARD(self, gcmd):
        self.pending = None
        self._clear_state()
        self._end_prompt()
        gcmd.respond_info("print_recovery: interrupted print discarded")

    cmd_PRINT_RECOVERY_RESUME_help = \
        "Resume the interrupted print (PARK_X= PARK_Y= PURGE= " \
        "PURGE_LENGTH= LIFT_Z=)"
    def cmd_PRINT_RECOVERY_RESUME(self, gcmd):
        p = self.pending
        if p is None:
            raise gcmd.error("print_recovery: no interrupted print to resume")
        sd = self.printer.lookup_object('virtual_sdcard')
        stats = self.printer.lookup_object('print_stats')
        if sd.is_active() or stats.state == 'printing':
            raise gcmd.error("print_recovery: a print is already running")
        toolhead = self.printer.lookup_object('toolhead')
        th = toolhead.get_status(self.reactor.monotonic())
        amin, amax = th['axis_minimum'], th['axis_maximum']
        park_x = gcmd.get_float('PARK_X', self.park_x
                                if self.park_x is not None else amin[0])
        park_y = gcmd.get_float('PARK_Y', self.park_y)
        purge = gcmd.get_int('PURGE', 1 if self.purge else 0) != 0
        purge_len = gcmd.get_float('PURGE_LENGTH', self.purge_length,
                                   minval=0.)
        lift = gcmd.get_float('LIFT_Z', self.lift_z, minval=0.)
        path = os.path.join(sd.sdcard_dirname, p['filename'])
        try:
            size = os.path.getsize(path)
        except OSError:
            raise gcmd.error("print_recovery: %s no longer exists"
                             % (p['filename'],))
        if p.get('file_size') and size != p['file_size']:
            raise gcmd.error("print_recovery: %s changed since it was "
                             "interrupted" % (p['filename'],))
        self.resuming = True
        try:
            self._end_prompt()
            self._resume(gcmd, p, sd, amax, park_x, park_y, purge,
                         purge_len, lift)
        finally:
            self.resuming = False

    def _run(self, script):
        self.gcode.run_script_from_command(script)

    def _resume(self, gcmd, p, sd, amax, park_x, park_y, purge, purge_len,
                lift):
        info = gcmd.respond_info
        raw = p['raw_position']
        gpos = p['gcode_position']
        org = p['homing_origin']
        bed = p.get('bed_target') or 0.
        ext = p.get('extruder_target') or 0.
        fast = self.park_speed * 60.
        room = amax[2] - raw[2]
        if lift > 0. and room < min(lift, 2.):
            raise gcmd.error("print_recovery: not enough Z room (%.1f mm) to "
                             "lift the nozzle before homing X/Y" % (room,))
        lift = max(0., min(lift, room))
        info("print_recovery: heating the bed to %.0f C" % (bed,))
        script = ["G90", "BED_MESH_CLEAR",
                  "SET_GCODE_OFFSET X=0 Y=0 Z=0"]
        if bed:
            script.append("M140 S%.1f" % (bed,))
        self._run("\n".join(script))
        # Z is assumed unchanged: set it, lift, then find X/Y again
        toolhead = self.printer.lookup_object('toolhead')
        pos = list(toolhead.get_position())
        pos[2] = raw[2]
        toolhead.set_position(pos, homing_axes="z")
        self._run("G91\nG1 Z%.3f F600\nG90" % (lift,))
        info("print_recovery: homing X/Y")
        self._run("G28 X Y")
        move = "G1 X%.3f" % (park_x,)
        if park_y is not None:
            move += " Y%.3f" % (park_y,)
        self._run("G90\n%s F%.0f" % (move, fast))
        info("print_recovery: heating the nozzle to %.0f C at the X stop"
             % (ext,))
        script = ["M83"]
        if ext:
            script.append("M104 S%.1f" % (ext,))
        if bed:
            script.append("M190 S%.1f" % (bed,))
        if ext:
            script.append("M109 S%.1f" % (ext,))
        self._run("\n".join(script))
        retract = p_retract = 0.
        if purge and purge_len > 0.:
            info("print_recovery: purging %.1f mm" % (purge_len,))
            retract = p_retract = self.purge_retract
            script = ["G1 E%.3f F%.0f" % (purge_len, self.purge_speed * 60.)]
            if retract:
                script.append("G1 E-%.3f F1800" % (retract,))
            script.append("G4 P800")
            self._run("\n".join(script))
        self._restore_mesh(p)
        info("print_recovery: returning to the print")
        script = ["SET_GCODE_OFFSET X=%.4f Y=%.4f Z=%.4f"
                  % (org[0], org[1], org[2]),
                  "G90",
                  "G1 Z%.3f F600" % (gpos[2] + lift,),
                  "G1 X%.3f Y%.3f F%.0f" % (gpos[0], gpos[1], fast),
                  "G1 Z%.3f F300" % (gpos[2],)]
        if retract:
            script += ["M83", "G1 E%.3f F1200" % (retract,)]
        self._run("\n".join(script))
        self._restore_state(p)
        self._reopen_file(gcmd, p, sd)
        self.pending = None
        self.recording = True
        info("print_recovery: resumed %s from byte %d"
             % (p['filename'], p['file_position']))

    def _restore_mesh(self, p):
        mesh = p.get('mesh')
        if not mesh:
            return
        try:
            module = __import__('extras.bed_mesh', fromlist=['ZMesh'])
            bm = self.printer.lookup_object('bed_mesh')
            zmesh = module.ZMesh(mesh['params'], mesh.get('name'))
            zmesh.build_mesh(mesh['probed_matrix'])
            zmesh.set_mesh_offsets(mesh.get('offsets', [0., 0.]))
            bm.set_mesh(zmesh)
        except Exception:
            logging.exception("print_recovery: bed mesh not restored")
            self.gcode.respond_info("print_recovery: WARNING the bed mesh "
                                    "could not be restored")

    def _restore_state(self, p):
        script = []
        script.append("M82" if p['absolute_extrude'] else "M83")
        script.append("G92 E%.5f" % (p['gcode_position'][3],))
        script.append("M220 S%.2f" % (p['speed_factor'] * 100.,))
        script.append("M221 S%.2f" % (p['extrude_factor'] * 100.,))
        script.append("G1 F%.1f" % (p['speed'] * 60.,))
        vel = []
        if p.get('max_velocity'):
            vel.append("VELOCITY=%.3f" % (p['max_velocity'],))
        if p.get('max_accel'):
            vel.append("ACCEL=%.3f" % (p['max_accel'],))
        if p.get('square_corner_velocity') is not None:
            vel.append("SQUARE_CORNER_VELOCITY=%.3f"
                       % (p['square_corner_velocity'],))
        if p.get('minimum_cruise_ratio') is not None:
            vel.append("MINIMUM_CRUISE_RATIO=%.4f"
                       % (p['minimum_cruise_ratio'],))
        elif p.get('max_accel_to_decel'):
            vel.append("ACCEL_TO_DECEL=%.3f" % (p['max_accel_to_decel'],))
        if vel:
            script.append("SET_VELOCITY_LIMIT " + " ".join(vel))
        if p.get('pressure_advance') is not None:
            script.append("SET_PRESSURE_ADVANCE ADVANCE=%.5f SMOOTH_TIME=%.4f"
                          % (p['pressure_advance'],
                             p.get('smooth_time') or 0.04))
        if 'fan' in self.printer.objects:
            fan = p.get('fan_speed') or 0.
            script.append("M106 S%d" % int(round(fan * 255.))
                          if fan > 0. else "M107")
        if self.printer.lookup_object('display_status', None) is not None \
           and p.get('progress') is not None:
            script.append("M73 P%d" % int(round(p['progress'] * 100.)))
        script.append("G90" if p['absolute_coordinates'] else "G91")
        self._run("\n".join(script))

    def _object_definitions(self, path, limit):
        lines = []
        try:
            with io.open(path, 'r', encoding='utf-8', errors='replace',
                         newline='') as f:
                read = 0
                for line in f:
                    read += len(line)
                    if read > OBJECT_SCAN_LIMIT or read > limit:
                        break
                    if line.startswith('EXCLUDE_OBJECT_DEFINE'):
                        lines.append(line.strip())
        except (IOError, OSError):
            logging.exception("print_recovery: object scan failed")
        return lines

    def _reopen_file(self, gcmd, p, sd):
        path = os.path.join(sd.sdcard_dirname, p['filename'])
        # Lets add-ons (timelapse) tell this apart from a new print
        self.gcode.respond_raw("Print recovery: resuming")
        self._run("M23 %s" % (p['filename'],))
        self._run("M26 S%d" % (p['file_position'],))
        script = []
        if p.get('total_layer'):
            script.append("SET_PRINT_STATS_INFO TOTAL_LAYER=%d "
                          "CURRENT_LAYER=%d"
                          % (p['total_layer'], p.get('layer') or 0))
        if self.printer.lookup_object('exclude_object', None) is not None:
            script += self._object_definitions(path, p['file_position'])
            for name in p.get('excluded_objects', []):
                script.append("EXCLUDE_OBJECT NAME=%s" % (name,))
            if p.get('current_object'):
                script.append("EXCLUDE_OBJECT_START NAME=%s"
                              % (p['current_object'],))
        script.append("M24")
        self._run("\n".join(script))

def load_config(config):
    return PrintRecovery(config)
