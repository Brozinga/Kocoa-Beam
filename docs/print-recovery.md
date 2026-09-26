# Print recovery — resume a print after a power loss

**Languages: [English](print-recovery.md) · [Português (BR)](pt-br/print-recovery.md) · [简体中文](zh-Hans/print-recovery.md)**

If the printer loses power, the USB cable comes loose or Klipper shuts down in
the middle of a print, Kocoa Beam can offer to **continue that print from where
it stopped**. It is a best-effort feature: it works best on parts that are still
firmly stuck to the bed.

The phone keeps running (it has a battery), so the print position is safe even
when the printer is off.

## How it works

1. While printing, the position in the G-code file, the nozzle position, the
   temperatures, fan, speed/flow, Z offset and bed mesh are saved to a small
   file every **2 seconds** (configurable).
2. After the printer is back and Klipper is ready, Fluidd and Mainsail show a
   **"Print interrupted"** window asking whether to resume.
3. If you tap **Resume print**, the printer:
   1. heats the bed;
   2. lifts the nozzle, homes **X and Y only**, and moves to the **X stop**
      (away from the part) — optional, see `park_enable_x` / `park_enable_y`;
   3. heats the nozzle there, so nothing drips on the print;
   4. optionally purges a little filament — see `purge`;
   5. goes back to the saved position and keeps printing from the saved byte
      of the file.

**Discard** forgets the print, then lifts the nozzle away from the abandoned
part (**50 mm** by default) and homes X and Y — both configurable, see
`discard_lift_z`, `discard_home_x` and `discard_home_y`. If you closed the window, run
`PRINT_RECOVERY_STATUS` in the console to see it again (it also reappears
every minute while a print is waiting).

## Turn it on

Add this to `printer.cfg` (any place outside another section) and restart:

```ini
[print_recovery]
```

Everything below is optional; the values shown are the defaults.

```ini
[print_recovery]
snapshot_interval: 2      # seconds between saves (0.5 - 300)
park_enable_x: True       # True: home X and heat the nozzle at the X stop
park_enable_y: True       # True: also home Y (False for either: not moved)
park_x:                   # X to move to after homing X (default: X minimum)
park_y:                   # Y to move to after homing Y (default: stay at home)
park_speed: 100           # mm/s travel speed
lift_z: 10                # mm the nozzle is lifted before homing X/Y
discard_lift_z: 50        # mm the nozzle rises when you press Discard (0 = no)
discard_home_x: True      # home X after Discard
discard_home_y: True      # home Y after Discard
purge: True               # True/False: purge before continuing (only when
                          # X or Y is parked)
purge_length: 20          # mm of filament to purge
purge_speed: 5            # mm/s
purge_retract: 2          # mm retracted after purging
min_extruded: 5           # mm extruded before the first save
language: auto            # window/console language (see below)
prompt: True              # show the Fluidd/Mainsail window
prompt_repeat: 60         # seconds between reminders (0 = once)
```

You can also override some values when resuming by hand:
`PRINT_RECOVERY_RESUME PARK_ENABLE_X=1 PARK_ENABLE_Y=0 PARK_X=-6 PURGE=0
PURGE_LENGTH=10 LIFT_Z=5` (the `*_ENABLE_*`, `PURGE` and Discard's `HOME_X` /
`HOME_Y` accept `1`/`0` or `True`/`False`; Discard also takes `LIFT_Z`). Anything you leave out
uses the value from `printer.cfg`, or the default above.

### What `park_x` and `park_y` do

After a power loss the printer no longer knows where X and Y are, so resuming
homes them first. When an axis is homed (`park_enable_x` / `park_enable_y`),
the nozzle is then moved to `park_x` / `park_y` and heats (and purges) there,
away from the print:

- `park_x` — the X position used to heat and purge. Empty means the machine's
  X minimum, i.e. the X stop (on the Neptune 3 Pro, `-6`, left of the bed).
- `park_y` — the same for Y. Empty means "stay where Y homing left the
  nozzle" (usually the front of the bed).

Set them only if the default corner is not a good place to drip filament.

### Not moving an axis (`park_enable_x: False` / `park_enable_y: False`)

An axis set to `False` is **not** homed or moved: it is assumed to be exactly
where it was. With both set to `False` the nozzle just lifts by `lift_z` and
heats in place above the part, and the purge is skipped (it would drop
filament on the print). Use it only if you are sure nothing moved; the nozzle
may drip a little while it heats.

### Language

The window and console messages follow the language chosen in Fluidd or
Mainsail (English, Portuguese, Russian, Chinese simplified/traditional; any
other language shows English). Set `language: pt` (or `en`, `ru`, `zh`,
`zh-TW`) to force one.

## Commands

| Command | What it does |
|---|---|
| `PRINT_RECOVERY_STATUS` | Shows the interrupted print and the window again |
| `PRINT_RECOVERY_RESUME` | Resumes it |
| `PRINT_RECOVERY_DISCARD` | Forgets it |

## After the printer comes back

- If Klipper shows an error such as *"Lost communication with MCU"*, press
  **Firmware Restart**. The window appears once Klipper is ready.
- Do **not** move the axes by hand: the resume assumes Z did not change.

## Limits — please read

- **Z is assumed unchanged.** Without power the motors let go. If the gantry
  dropped or was moved, the nozzle can touch the part. That is why the nozzle
  is lifted (`lift_z`) before anything moves.
- **The part cools down.** It can lift or warp, and a visible line usually
  remains at the resume height. Resume soon and only if the part is still stuck.
- A few seconds of printing before the interruption are printed again (the
  G-code is read ahead of the motion); this leaves a tiny bump at most.
- There is no power-loss detector on the printer board, so the nozzle cannot
  park or retract at the moment of the power cut.
- If the G-code file is deleted or changed, resuming is refused.
- **Delta printers are not supported:** they cannot home X and Y on their own.
- **Custom `G28`:** if your config overrides homing (for example a
  `homing_override` that also homes Z), the X/Y homing step of the resume can
  misbehave. Check that `G28 X Y` only homes X and Y.
- **Multi-filament and multi-tool setups** (MMU/Happy Hare, IDEX, tool
  changers) are not handled: only the active extruder is saved and restored.
- **Fans:** only the part-cooling fan (`[fan]`) is restored. Other fans
  (`fan_generic`, controller or auxiliary fans) go back to what your config or
  macros set.
- **Chamber:** chamber heaters and their temperatures are not saved or
  restored.
- The timelapse keeps the frames taken before the interruption. Moonraker
  records the resume as a new job in the history.
