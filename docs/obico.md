# Using Obico

**Languages: [English](obico.md) · [Português (BR)](pt-br/obico.md) · [简体中文](zh-Hans/obico.md)**

[Obico](https://www.obico.io) is a community-built, open-source smart 3D
printing platform: remote monitoring, print failure detection and remote
control, from Obico Cloud or your own self-hosted Obico Server. Kocoa Beam
runs the real `moonraker-obico` companion (vendored from
[TheSpaghettiDetective/moonraker-obico](https://github.com/TheSpaghettiDetective/moonraker-obico)),
adapted to run on-device instead of the systemd service it normally installs
as.

## Enabling it

1. Start a printer profile (it has to be **running** — Obico connects to
   whichever profile is currently active).
2. Go to **Settings → Remote access → Obico** and turn on **Enable Obico**.
3. Tap **Obico server** to pick where it connects:
   - **Obico Cloud** (`app.obico.io`) — the default, no account of your own
     to host.
   - **Self-hosted** — enter the URL of your own
     [Obico Server](https://www.obico.io/docs/server-guides/) instance.
   - Switching servers clears any existing link, since a link is only valid
     for the server that issued it.
4. Tap **Link printer**. In the Obico app or website, add a printer manually
   to get a 6-digit verification code, then enter it in the dialog and tap
   **Link**.
   - This is a different (but equivalent) linking path than Obico's own
     install script offers: that one either waits for you to tap "Link Now"
     on a phone on the same local network, or falls back to the same 6-digit
     code prompt. Kocoa Beam always uses the code, since there's no terminal
     to run the interactive tool in.
5. Once linked, your printer shows up in the Obico app/website. To unlink,
   open **Link printer** again — the dialog shows an **Unlink** option once
   linked.

## Webcam

Obico gets a snapshot of the webcam feed the same way Fluidd/Mainsail do:
through Moonraker's own webcam list. Enable the camera server and add it to
Fluidd or Mainsail once (config is shared between them) and Obico picks it up
automatically — full walkthrough with screenshots: [`webcam.md`](webcam.md).

Obico's real-time WebRTC preview (via a bundled `janus-gateway` process and
`ffmpeg`) is **not** available here — both are native binaries built for
desktop Linux/Raspberry Pi targets in the upstream project, not for Android,
and aren't bundled. This is a documented, supported config flag
(`disable_video_streaming`), not a patch to the companion's code. Obico still
gets a fresh snapshot periodically (every ~10s) for print monitoring and
failure detection, independent of that flag.

## Notes and limitations

- Only **one** printer profile can be linked at a time, even if you run
  several concurrently — Obico follows whichever profile reached "running"
  first.
- Obico's own crash telemetry (Sentry) is disabled by default; the actual
  connection to your chosen server is unaffected. It's also automatically
  disabled by the companion itself for any self-hosted server, regardless of
  this setting.
- This has not been hardware-tested end-to-end against a real account — the
  companion starts, connects to Moonraker correctly, and the linking API
  call has been verified against the real Obico Cloud servers (a
  deliberately wrong code correctly comes back "invalid"), but please report
  any issues you hit while linking or using it with a real account.

## Troubleshooting

- **Logs**: `obico.log` lives alongside `klippy.log`/`moonraker.log` in that
  profile's `logs` folder — browse to it with a file manager app through
  Kocoa Beam's own storage entry (Android's Files app → "Kocoa Beam"), the
  same place you'd already look for Klipper/Moonraker/OctoEverywhere logs.
- **"Not linked" never changes after entering a code**: double check the
  code hasn't expired (Obico's codes are short-lived) and that you picked
  the right server (Cloud vs. self-hosted) — a code from one doesn't work
  against the other.
- **Self-hosted server unreachable**: the error message shown in the Link
  dialog is the actual network error (e.g. "unable to resolve host") — check
  the URL first, including `http://` vs `https://`.
