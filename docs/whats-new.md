# Update v2

**Languages: [English](whats-new.md) · [Português (BR)](pt-br/whats-new.md) · [简体中文](zh-Hans/whats-new.md)**

This page summarises what this project changes on top of the base application, for
both end users and developers. For firmware, see
[build-firmware.md](build-firmware.md); for the optional Klipper add-ons, see
[mods/klipper-addons.md](mods/klipper-addons.md).

<p align="center"><img src="images/principal-screen.png" alt="Kocoa Beam main screen" width="280"></p>

## Bundled software

The print stack is updated to recent upstream releases. The exact versions are
pinned in the build (`gradle.properties` and `app/build.gradle`):

| Component | Version bundled |
|---|---|
| Klipper host | current upstream (MCU firmware target: **0.13**) |
| Kalico host | current upstream |
| Moonraker | **0.11.0** (Web API 1.5.0) |
| Fluidd | **1.37.5** |
| Mainsail | **2.19.0** |
| Happy Hare (MMU) | **v4.0.0** |
| Moonraker-timelapse | bundled |

Static assets for Fluidd and Mainsail are served with correct MIME types, so both
front ends load fully styled and saving files or configs from the web UI works.

<p align="center">
  <img src="images/moonraker-version.png" alt="Moonraker welcome page" width="420">
  <img src="images/fluidd-screen-klipper-version.png" alt="Fluidd system page" width="420">
</p>

## Web interface ports

Each front end has its own port instead of a shared one:

| Front end | URL |
|---|---|
| Fluidd | `http://<device-ip>:4408/` |
| Mainsail | `http://<device-ip>:4409/` |

The port follows the front-end toggle on the main screen, which also shows the
active URL. Camera endpoints remain on `:8889`.

## USB webcam support

The camera server (**Settings → Camera → Enable camera server**) can now also
stream from a generic USB UVC webcam, not just the device's own camera:

- With **Camera source** left on **Automatic**, a plugged-in USB webcam is
  preferred over the built-in camera, and hot-plugging it in or out is picked
  up live.
- **Settings → Camera → Camera source** lets you pin a specific camera
  (built-in front/back, or a specific USB webcam) instead of relying on
  auto-detection.

This relies on the device exposing the USB webcam through Android's standard
Camera2 API as an external camera (`LENS_FACING_EXTERNAL`), which most
AOSP-based devices support since Android 9 but which some OEM camera stacks
may not expose. It has not been hardware-tested against a real UVC webcam in
this project.

## OctoEverywhere remote access

**Settings → Remote access → Enable OctoEverywhere** runs the real
[OctoEverywhere](https://octoeverywhere.com) Klipper companion, vendored from
its upstream source and adapted to run as its own background process on
Android instead of the systemd service/venv it normally installs as. It talks
to whichever printer profile is currently running over the same local
Moonraker connection Fluidd/Mainsail use — no extra setup on the Moonraker
side.

- Turning it on starts the companion; **Link printer** then shows a QR code
  (once the companion has generated its printer ID, usually within a few
  seconds) to finish linking your OctoEverywhere account, the same one-time
  step as any other install.
- Its own crash telemetry (Sentry) is disabled; the actual remote-access
  connection to octoeverywhere.com is unaffected.
- If you also enable the camera server above, OctoEverywhere can pick up that
  same USB/built-in webcam feed automatically once it's added as a webcam in
  Fluidd or Mainsail — it does not need a separate camera setup.
- Only one printer profile can be linked to OctoEverywhere at a time, even if
  you run multiple profiles concurrently.

## In-app log viewer

A **Logs** tab exposes the Klipper, Moonraker and application logs. Each can be
viewed, copied, downloaded to the device's `Downloads/` folder, or shared — no
PC or `adb` required.

<p align="center"><img src="images/log-screen.png" alt="Logs tab" width="300"></p>

## G-code metadata and thumbnails

This did not work before and has been fixed. Uploaded jobs now display their
preview image, print time, filament usage and object list in Fluidd/Mainsail.
Moonraker normally extracts this by launching a separate helper process, which is
not possible inside an Android application; the extraction was changed to run
in-process.

<p align="center"><img src="images/thumbnail-metadata.png" alt="Fluidd job list with thumbnail and metadata" width="760"></p>

## Starting printer.cfg template

A `printer.cfg` template is provided with a macro pack covering `PRINT_START` /
`PRINT_END`, adaptive bed mesh, pressure-advance and speed calibration,
babystepping, filament load/unload and preheats.

## Bundled Klipper add-ons (opt-in)

Vendored but inactive until the matching section is added to `printer.cfg`:
KAMP, LED Effect, Z Calibration, Auto Speed, TMC Autotune. See
[mods/klipper-addons.md](mods/klipper-addons.md). A procedure for tuning input
shaper without an accelerometer is in
[mods/input-shaper-manual.md](mods/input-shaper-manual.md).

## Crash log

On an unexpected exit the application writes `last_crash.txt` for later
inspection.

## MCU firmware

Build tooling for any supported board is documented in
[build-firmware.md](build-firmware.md).

## Not tested

The camera extension (`[beam_camera]` — flashlight and autofocus control) is
carried over unchanged and has not been tested in this project.
