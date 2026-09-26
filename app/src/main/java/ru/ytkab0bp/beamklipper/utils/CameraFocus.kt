package ru.ytkab0bp.beamklipper.utils

import android.content.Context
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.MeteringRectangle

// Tap-to-focus is optional hardware: many UVC webcams (and LEGACY-level phone
// cameras) report no AF regions or no triggerable AF mode, so every entry
// point checks the characteristics of the camera that will really be opened.
// Everything used here exists since API 21, so no version guards are needed.
object CameraFocus {
    // Side of the metering square, as a fraction of the active array's short side.
    private const val REGION_FRACTION = 0.12f

    fun isSupported(chars: CameraCharacteristics): Boolean {
        val regions = chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0
        val modes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: return false
        return regions > 0 && modes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) &&
            chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) != null
    }

    // Same choice CameraService makes: a pinned id, else a USB webcam if one is
    // plugged in, else the first camera.
    fun isSupportedForSelectedCamera(context: Context): Boolean = try {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val ids = manager.cameraIdList
        val pinned = Prefs.cameraId
        val id = when {
            pinned != null && ids.contains(pinned) -> pinned
            pinned == null -> ids.firstOrNull {
                try {
                    manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_EXTERNAL
                } catch (_: Throwable) { false }
            } ?: ids.firstOrNull()
            else -> ids.firstOrNull()
        }
        id != null && isSupported(manager.getCameraCharacteristics(id))
    } catch (_: Throwable) {
        false
    }

    // (nx, ny) is a point in 0..1 over the frame as served (already rotated
    // clockwise by [rotation] and zoomed); returns the AF region in sensor
    // active-array coordinates.
    fun region(chars: CameraCharacteristics, zoom: Float, rotation: Int, nx: Float, ny: Float): MeteringRectangle? {
        val active: Rect = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return null
        // Undo the clockwise rotation applied to the served frame.
        val (sx, sy) = when (rotation) {
            90 -> ny to 1f - nx
            180 -> 1f - nx to 1f - ny
            270 -> 1f - ny to nx
            else -> nx to ny
        }
        // The visible area is the centred crop that zoom leaves.
        val z = zoom.coerceAtLeast(1f)
        val viewW = active.width() / z
        val viewH = active.height() / z
        val cx = active.left + (active.width() - viewW) / 2f + sx.coerceIn(0f, 1f) * viewW
        val cy = active.top + (active.height() - viewH) / 2f + sy.coerceIn(0f, 1f) * viewH
        val side = (minOf(active.width(), active.height()) * REGION_FRACTION).toInt().coerceAtLeast(8)
        val left = (cx - side / 2f).toInt().coerceIn(active.left, active.right - side)
        val top = (cy - side / 2f).toInt().coerceIn(active.top, active.bottom - side)
        return MeteringRectangle(left, top, side, side, MeteringRectangle.METERING_WEIGHT_MAX)
    }
}
