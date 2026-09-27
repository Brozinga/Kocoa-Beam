"""Unit tests for the Klipper extras the app ships in beam_ext.

Run from the repository root:
    python3 -m unittest discover -s app/src/test/python -v
"""
import filecmp
import importlib.util
import logging
import os
import tempfile
import unittest
from unittest import mock

ASSETS = os.path.join(os.path.dirname(__file__), '..', '..', 'main', 'assets')


def load(engine, name):
    path = os.path.join(ASSETS, engine, 'beam_ext', name + '.py')
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class FakeGcode:
    def __init__(self):
        self.commands = {}

    def register_command(self, name, func, desc=None):
        self.commands[name] = (func, desc)


class FakePrinter:
    def __init__(self):
        self.gcode = FakeGcode()

    def lookup_object(self, name):
        return self.gcode


class FakeConfig:
    def __init__(self):
        self.printer = FakePrinter()

    def get_printer(self):
        return self.printer


class Gcmd:
    def __init__(self, **params):
        self.params = params

    def get(self, name, default=None):
        return self.params[name] if name in self.params else default

    def get_int(self, name):
        return int(self.params[name])

    def get_float(self, name):
        return float(self.params[name])


class FakeResponse:
    def read(self):
        return b'{"ok": true}'

    def close(self):
        pass


class Extras(unittest.TestCase):
    engines = ('klipper', 'kalico')

    def urls(self, engine, module_name, command, port_file=None, **params):
        module = load(engine, module_name)
        if port_file is not None:
            module.WEB_PORT_FILE = port_file
        extra = module.load_config(FakeConfig())
        func = extra.gcode.commands[command][0]
        with mock.patch.object(module.urllib.request, 'urlopen',
                               return_value=FakeResponse()) as urlopen:
            func(Gcmd(**params))
        return [c.args[0] for c in urlopen.call_args_list]

    @staticmethod
    def port_of(url):
        return int(url.split('//')[1].split('/')[0].split(':')[1])


class Beeper(Extras):
    def test_registers_play_tone(self):
        for engine in self.engines:
            extra = load(engine, 'beam_beeper').load_config(FakeConfig())
            self.assertEqual(list(extra.gcode.commands), ['PLAY_TONE'])

    def test_play_tone_sends_duration_and_frequency(self):
        for engine in self.engines:
            urls = self.urls(engine, 'beam_beeper', 'PLAY_TONE',
                             DURATION='250', FREQUENCY='440')
            self.assertEqual(len(urls), 1)
            self.assertIn('/beam/play_tone?', urls[0])
            self.assertIn('duration=250', urls[0])
            self.assertIn('frequency=440', urls[0])

    def test_a_failed_request_is_logged_not_raised(self):
        module = load('klipper', 'beam_beeper')
        extra = module.load_config(FakeConfig())
        func = extra.gcode.commands['PLAY_TONE'][0]
        with mock.patch.object(module.urllib.request, 'urlopen',
                               side_effect=OSError('refused')):
            with self.assertLogs(level=logging.ERROR):
                func(Gcmd(DURATION='1', FREQUENCY='1'))


class Camera(Extras):
    def test_registers_flashlight_and_focus(self):
        for engine in self.engines:
            extra = load(engine, 'beam_camera').load_config(FakeConfig())
            self.assertEqual(sorted(extra.gcode.commands),
                             ['SET_CAMERA_FLASHLIGHT', 'SET_CAMERA_FOCUS'])

    def test_flashlight_passes_the_state_through(self):
        for engine in self.engines:
            urls = self.urls(engine, 'beam_camera', 'SET_CAMERA_FLASHLIGHT',
                             ENABLED='true')
            self.assertEqual(len(urls), 1)
            self.assertIn('/beam/set_camera_flashlight?enabled=true', urls[0])

    def test_autofocus_ignores_the_distance(self):
        for engine in self.engines:
            urls = self.urls(engine, 'beam_camera', 'SET_CAMERA_FOCUS',
                             AUTOFOCUS='True')
            self.assertIn('autofocus=true', urls[0])
            self.assertIn('focus=0', urls[0])

    def test_manual_focus_sends_the_distance(self):
        for engine in self.engines:
            urls = self.urls(engine, 'beam_camera', 'SET_CAMERA_FOCUS',
                             AUTOFOCUS='false', FOCUS_DISTANCE='2.5')
            self.assertIn('autofocus=false', urls[0])
            self.assertIn('focus=2.5', urls[0])

    def test_a_failed_request_is_logged_not_raised(self):
        module = load('klipper', 'beam_camera')
        extra = module.load_config(FakeConfig())
        func = extra.gcode.commands['SET_CAMERA_FLASHLIGHT'][0]
        with mock.patch.object(module.urllib.request, 'urlopen',
                               side_effect=OSError('refused')):
            with self.assertLogs(level=logging.ERROR):
                func(Gcmd(ENABLED='true'))


class Engines(unittest.TestCase):
    def test_klipper_and_kalico_ship_the_same_extras(self):
        for name in ('beam_beeper.py', 'beam_camera.py'):
            self.assertTrue(filecmp.cmp(
                os.path.join(ASSETS, 'klipper', 'beam_ext', name),
                os.path.join(ASSETS, 'kalico', 'beam_ext', name),
                shallow=False), name)


class WebPort(Extras):
    """The bug this covers: the extras used to hardcode port 8888, while the
    app's web server actually listens on 4408/4409/4410 depending on the
    selected front end (see WebService/Frontends), so PLAY_TONE and
    SET_CAMERA_* silently did nothing. The extras now read the port from a
    file (WEB_PORT_FILE) WebService keeps updated with the real port.
    """
    served_ports = {4408, 4409, 4410}

    def test_placeholder_is_not_hardcoded_to_the_old_wrong_port(self):
        for name in ('beam_beeper', 'beam_camera'):
            source = load('klipper', name)
            self.assertNotIn('8888', open(source.__file__).read())

    def test_the_placeholder_is_still_present_for_bundleinstaller_to_patch(self):
        for name in ('beam_beeper', 'beam_camera'):
            text = open(load('klipper', name).__file__).read()
            self.assertIn('${WEB_PORT_FILE}', text)

    def test_without_a_real_port_file_it_falls_back_to_mainsail_s_port(self):
        # Loaded straight from source, WEB_PORT_FILE is still the literal,
        # unpatched placeholder text (BundleInstaller only substitutes it in
        # the app's own unpacked copy) — opening it fails, so the default
        # applies, same as a fresh install before the app has served anything.
        for module_name, command, params in (
                ('beam_beeper', 'PLAY_TONE', dict(DURATION='1', FREQUENCY='1')),
                ('beam_camera', 'SET_CAMERA_FLASHLIGHT', dict(ENABLED='true'))):
            url = self.urls('klipper', module_name, command, **params)[0]
            self.assertEqual(4409, self.port_of(url))

    def test_it_reads_whichever_port_the_app_is_really_serving_on(self):
        with tempfile.TemporaryDirectory() as tmp:
            port_file = os.path.join(tmp, 'web_port')
            for served_port in (4408, 4409, 4410):
                with open(port_file, 'w') as f:
                    f.write(str(served_port))
                for module_name, command, params in (
                        ('beam_beeper', 'PLAY_TONE', dict(DURATION='1', FREQUENCY='1')),
                        ('beam_camera', 'SET_CAMERA_FOCUS', dict(AUTOFOCUS='true'))):
                    url = self.urls('klipper', module_name, command,
                                    port_file=port_file, **params)[0]
                    self.assertEqual(served_port, self.port_of(url))

    def test_surrounding_whitespace_in_the_port_file_is_tolerated(self):
        with tempfile.TemporaryDirectory() as tmp:
            port_file = os.path.join(tmp, 'web_port')
            with open(port_file, 'w') as f:
                f.write('  4410\n')
            url = self.urls('klipper', 'beam_beeper', 'PLAY_TONE', port_file=port_file,
                            DURATION='1', FREQUENCY='1')[0]
            self.assertEqual(4410, self.port_of(url))

    def test_a_garbage_port_file_falls_back_to_mainsail_s_port(self):
        with tempfile.TemporaryDirectory() as tmp:
            port_file = os.path.join(tmp, 'web_port')
            with open(port_file, 'w') as f:
                f.write('not-a-port')
            url = self.urls('klipper', 'beam_beeper', 'PLAY_TONE', port_file=port_file,
                            DURATION='1', FREQUENCY='1')[0]
            self.assertEqual(4409, self.port_of(url))

    def test_kalico_s_copy_resolves_the_port_the_same_way(self):
        with tempfile.TemporaryDirectory() as tmp:
            port_file = os.path.join(tmp, 'web_port')
            with open(port_file, 'w') as f:
                f.write('4408')
            url = self.urls('kalico', 'beam_camera', 'SET_CAMERA_FLASHLIGHT',
                            port_file=port_file, ENABLED='true')[0]
            self.assertEqual(4408, self.port_of(url))

    def test_the_resolved_port_is_always_one_the_app_actually_serves(self):
        # End-to-end sanity check standing in for the earlier known failure:
        # whatever the extras resolve to (default or from a real port file)
        # must be a port the app's web server can actually be listening on.
        for served_port in (None,) + tuple(self.served_ports):
            with tempfile.TemporaryDirectory() as tmp:
                port_file = os.path.join(tmp, 'web_port')
                kwargs = {}
                if served_port is not None:
                    with open(port_file, 'w') as f:
                        f.write(str(served_port))
                    kwargs['port_file'] = port_file
                url = self.urls('klipper', 'beam_beeper', 'PLAY_TONE',
                                DURATION='1', FREQUENCY='1', **kwargs)[0]
                self.assertIn(self.port_of(url), self.served_ports)


if __name__ == '__main__':
    unittest.main()
