# Using the camera / USB webcam

**Languages: [English](webcam.md) · [Português (BR)](pt-br/webcam.md) · [简体中文](zh-Hans/webcam.md)**

Kocoa Beam can stream a live camera feed for print monitoring — either the
device's own built-in camera, or a USB UVC webcam plugged in via OTG/a hub.

<p align="center"><img src="images/camera-octoeverywhere-settings.png" alt="Settings screen showing the Camera section" width="280"></p>

## Enabling it

1. **Settings → Camera → Enable camera server.**
2. **Camera source** lets you pick which camera to use:
   - **Automatic** (default) — prefers a plugged-in USB webcam over the
     built-in camera, and switches live if you plug/unplug one while the
     server is running.
   - Or pin a specific camera. On devices with multiple lenses per facing
     (main/ultra-wide/telephoto on the back, sometimes two on the front),
     each one is listed separately with its 35mm-equivalent focal length —
     e.g. "Back camera 1 (26mm)" vs "Back camera 2 (14mm)" — the same
     number your phone is marketed with, so you can actually tell them
     apart.
3. **Camera rotation** cycles 0°/90°/180°/270°, for a phone mounted
   sideways or upside-down.

The feed is served at `http://<device-ip>:8889/` (stream) and
`http://<device-ip>:8889/snapshot` (single JPEG), independent of whichever
port Fluidd/Mainsail themselves are running on.

### Live preview tab

While the camera server is enabled, a **camera tab** appears in the top bar
next to Logs. It shows the live stream — exactly what Fluidd/Mainsail
receive, including rotation, resolution and zoom — so you can check the
framing without opening a browser. The first time, the app asks for the
**camera permission** (it is also requested when you enable the server).
Leaving the tab disconnects the preview, so it costs nothing when unused.

<p align="center"><img src="images/camera-preview-tab.png" alt="Live preview tab (zoom 1×) and the same view at 2× zoom" width="240"> <img src="images/camera-preview-zoom.png" alt="Live preview tab (zoom 1×) and the same view at 2× zoom" width="240"></p>

### Zoom

**Settings → Camera → Camera zoom** cycles through the zoom levels
(1×, 1.5×, 2×, 3× … up to 10×). Only levels the **selected camera really
supports** are offered — a phone's ultra-wide, tele lens and a USB webcam
each report different limits — and the list updates when you change the
camera source. If a camera has no zoom, the row shows "Not supported".
Changing zoom briefly restarts the camera server.

<p align="center"><img src="images/camera-settings-zoom.png" alt="Camera settings with the new Camera zoom row" width="240"></p>

### USB webcam support

This relies on the device exposing the USB webcam through Android's
standard Camera2 API as an external camera (`LENS_FACING_EXTERNAL`), which
most AOSP-based devices support since Android 9 but which some OEM camera
stacks do not. **Confirmed on real hardware:** a Samsung Galaxy S10+ (One
UI, Android 12) correctly detects a USB UVC webcam at the OS/USB level but
does **not** expose it through Camera2 at all — Samsung's camera HAL
doesn't implement the external-camera provider. The app falls back to the
built-in camera cleanly in that case; more AOSP-stock devices (Pixels,
some Android TV boxes/tablets) are expected to actually expose the webcam.

## Adding it to Fluidd or Mainsail

Fluidd and Mainsail both read their webcam list from the same place —
Moonraker's own webcam config for that printer instance — so you only need
to add it **once**, in either front end, and it shows up in both.

**In Fluidd:** gear icon (Settings) → **Cameras** → **+ Add Camera**:

<p align="center"><img src="images/fluidd-cameras-settings.png" alt="Fluidd's Cameras settings section" width="640"></p>

| Field | Value |
|---|---|
| Name | anything, e.g. "USB Cam" |
| Service | `MJPEG-Streamer` |
| Stream URL | `http://<device-ip>:8889/` |
| Snapshot URL | `http://<device-ip>:8889/snapshot` |

Use `127.0.0.1` only if you're viewing Fluidd in a browser on the phone
itself; from another device, use the phone's LAN IP (the same one already
in your Fluidd/Mainsail URL).

<p align="center"><img src="images/fluidd-dashboard-webcam.png" alt="Fluidd dashboard with the live webcam" width="640"></p>

**In Mainsail:** the equivalent add-camera form is under **Machine →
Webcams**. Use service type `UV4L-MJPEG` with the same stream/snapshot
URLs above. Since the config is shared, adding it in Fluidd is enough —
here's the same webcam already showing live on Mainsail's dashboard after
being added once in Fluidd:

<p align="center"><img src="images/mainsail-dashboard-webcam.png" alt="Mainsail dashboard with the live webcam" width="640"></p>

OctoEverywhere picks up this same webcam automatically too, once it's
configured here — see [`octoeverywhere.md`](octoeverywhere.md).

## Troubleshooting

- **Fluidd/Mainsail rejects the webcam / shows an error, but the stream
  URL works fine in a plain browser tab or `curl`:** this was a real bug
  (fixed) where `/snapshot` sent the wrong `Content-Type`. Make sure
  you're on a build that includes it — the response should be
  `Content-Type: image/jpeg`, not `multipart/x-mixed-replace`.
- **Feed is choppy or slow when viewed through OctoEverywhere specifically
  (but fine locally):** the default resolution/quality/framerate are
  already tuned for this (measured ~117KB/s at 640×480/~14fps, vs.
  ~1.25MB/s at the old 720p default) — a cloud-relayed connection is much
  more bandwidth-constrained than your LAN. If it's still too slow for
  your uplink, or you want more, the FPS ceiling and resolution can be
  adjusted (`CameraService`/`Prefs.cameraWidth`/`cameraHeight` in source) —
  no UI control for this yet.
