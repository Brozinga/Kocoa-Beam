package ru.ytkab0bp.beamklipper.utils

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

import ru.ytkab0bp.beamklipper.BuildConfig
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.events.CameraSourceChangedEvent
import ru.ytkab0bp.beamklipper.events.EngineChangedEvent
import ru.ytkab0bp.beamklipper.events.WebFrontendChangedEvent
import ru.ytkab0bp.beamklipper.serial.UsbSerialManager
import ru.ytkab0bp.beamklipper.ui.state.AppState

object Prefs {
    const val USB_DEVICE_NAMING_BY_PATH = 0
    const val USB_DEVICE_NAMING_BY_VID_PID = 1
    const val ENGINE_KLIPPER = "klipper"
    const val ENGINE_KALICO = "kalico"
    const val FRONTEND_FLUIDD = "fluidd"
    const val FRONTEND_MAINSAIL = "mainsail"
    @Deprecated("Kalico was never a frontend; duplicates Mainsail assets. Migrate to FRONTEND_MAINSAIL.")
    const val FRONTEND_KALICO = "kalico_frontend"
    const val LANGUAGE_SYSTEM = "system"
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_PORTUGUESE_BRAZIL = "pt-BR"
    const val LANGUAGE_RUSSIAN = "ru"
    const val LANGUAGE_CHINESE_SIMPLIFIED = "zh-CN"
    const val LANGUAGE_CHINESE_TRADITIONAL = "zh-TW"

    private lateinit var mPrefs: SharedPreferences

    private fun getSafeString(key: String, default: String): String {
        return try {
            mPrefs.getString(key, default) ?: default
        } catch (_: ClassCastException) {
            val raw = mPrefs.all[key]
            if (raw != null) {
                val migrated = raw.toString()
                try { mPrefs.edit().putString(key, migrated).apply() } catch (_: Throwable) {}
                migrated
            } else default
        } catch (_: Throwable) {
            default
        }
    }

    private fun getSafeStringNullable(key: String): String? {
        return try {
            mPrefs.getString(key, null)
        } catch (_: ClassCastException) {
            val raw = mPrefs.all[key]
            if (raw != null) {
                val migrated = raw.toString()
                try { mPrefs.edit().putString(key, migrated).apply() } catch (_: Throwable) {}
                migrated
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun getSafeInt(key: String, default: Int): Int {
        return try {
            mPrefs.getInt(key, default)
        } catch (_: ClassCastException) {
            val raw = mPrefs.all[key]
            when (raw) {
                is Number -> {
                    val migrated = raw.toInt()
                    try { mPrefs.edit().putInt(key, migrated).apply() } catch (_: Throwable) {}
                    migrated
                }
                is String -> raw.toIntOrNull() ?: default
                else -> default
            }
        } catch (_: Throwable) {
            default
        }
    }

    private fun getSafeBoolean(key: String, default: Boolean): Boolean {
        return try {
            mPrefs.getBoolean(key, default)
        } catch (_: ClassCastException) {
            val raw = mPrefs.all[key]
            when (raw) {
                is Boolean -> raw
                is Number -> raw.toInt() != 0
                is String -> raw.toBooleanStrictOrNull() ?: default
                else -> default
            }
        } catch (_: Throwable) {
            default
        }
    }

    private fun getSafeFloat(key: String, default: Float): Float {
        return try {
            mPrefs.getFloat(key, default)
        } catch (_: ClassCastException) {
            val raw = mPrefs.all[key]
            when (raw) {
                is Number -> raw.toFloat()
                is String -> raw.toFloatOrNull() ?: default
                else -> default
            }
        } catch (_: Throwable) {
            default
        }
    }

    fun init(ctx: Context) {
        mPrefs = ctx.getSharedPreferences("${ctx.packageName}_preferences", Context.MODE_PRIVATE)
    }

    var webFrontend: String
        get() {
            val legacyMainsail = mPrefs.contains("mainsail")
            val raw = if (legacyMainsail) {
                val migrated = if (getSafeBoolean("mainsail", true)) FRONTEND_MAINSAIL else FRONTEND_FLUIDD
                try {
                    mPrefs.edit().putString("web_frontend", migrated).remove("mainsail").apply()
                } catch (_: Throwable) {}
                migrated
            } else {
                getSafeString("web_frontend", FRONTEND_MAINSAIL)
            }
            @Suppress("DEPRECATION")
            if (raw == FRONTEND_KALICO) {
                try { mPrefs.edit().putString("web_frontend", FRONTEND_MAINSAIL).apply() } catch (_: Throwable) {}
                return FRONTEND_MAINSAIL
            }
            return raw
        }
        set(value) {
            mPrefs.edit().putString("web_frontend", value).remove("mainsail").apply()
            KlipperApp.EVENT_BUS.fireEvent(WebFrontendChangedEvent())
        }

    var engine: String
        get() = getSafeString("engine", ENGINE_KLIPPER)
        set(value) {
            mPrefs.edit().putString("engine", value).apply()
            KlipperApp.EVENT_BUS.fireEvent(EngineChangedEvent())
        }

    val engineKey: String
        get() = engine

    var appLanguage: String
        get() = getSafeString("app_language", LANGUAGE_SYSTEM)
        set(value) {
            mPrefs.edit().putString("app_language", value).apply()
            AppState.updateAppLanguage()
        }

    // 640x480 rather than 720p: this feed gets relayed through OctoEverywhere's
    // cloud connection (shared with command/API traffic) when remote access is
    // on, not just served over LAN. Measured on real hardware: 1280x720 @ ~15fps
    // JPEG q85 pushed ~1.25MB/s continuously, which saturates a typical uplink
    // and stalls command execution over the same connection, not just the
    // webcam feed. 640x480 cuts pixel count (and roughly the bitrate) to ~1/3.
    val cameraWidth: Int
        get() = getSafeInt("camera_width", 640)

    val cameraHeight: Int
        get() = getSafeInt("camera_height", 480)

    // null means "auto": prefer a LENS_FACING_EXTERNAL (USB UVC webcam) camera
    // when one is plugged in, falling back to the first camera otherwise. See
    // CameraService.resolveCameraId(). CameraService runs in its own ":camera"
    // process, so it never observes this write directly — KlipperInstance
    // (main process, same as this setter) restarts it on CameraSourceChangedEvent
    // so the new pick is read fresh on the next process start.
    var cameraId: String?
        get() = getSafeStringNullable("camera_id")
        set(value) {
            mPrefs.edit().apply {
                if (value == null) remove("camera_id") else putString("camera_id", value)
            }.apply()
            AppState.updateCameraSourceId()
            KlipperApp.EVENT_BUS.fireEvent(CameraSourceChangedEvent())
        }

    // Clockwise degrees applied to every frame before it's served, for a
    // camera mounted sideways/upside-down. Only 0/90/180/270 are valid;
    // anything else is normalized. Same cross-process restart story as
    // cameraId above.
    var cameraRotation: Int
        get() = ((getSafeInt("camera_rotation", 0) % 360) + 360) % 360
        set(value) {
            val normalized = ((value % 360) + 360) % 360
            mPrefs.edit().putInt("camera_rotation", normalized).apply()
            AppState.updateCameraSourceId()
            KlipperApp.EVENT_BUS.fireEvent(CameraSourceChangedEvent())
        }

    var isCameraEnabled: Boolean
        get() = (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || KlipperApp.INSTANCE.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) &&
                getSafeBoolean("camera_enabled", false)
        set(value) {
            mPrefs.edit().putBoolean("camera_enabled", value).apply()
            AppState.updateCameraEnabled()
        }

    var isOctoEverywhereEnabled: Boolean
        get() = getSafeBoolean("octoeverywhere_enabled", false)
        set(value) {
            mPrefs.edit().putBoolean("octoeverywhere_enabled", value).apply()
            AppState.updateOctoEverywhereEnabled()
        }

    var usbDeviceNaming: Int
        get() = getSafeInt("usb_device_naming", USB_DEVICE_NAMING_BY_PATH)
        set(value) {
            UsbSerialManager.disconnectAll()
            mPrefs.edit().putInt("usb_device_naming", value).apply()
            UsbSerialManager.connectAll()
            AppState.updateUsbNaming()
        }

    var isFlashlightEnabled: Boolean
        get() = getSafeBoolean("flashlight", false)
        set(value) { mPrefs.edit().putBoolean("flashlight", value).apply() }

    var isAutofocusEnabled: Boolean
        get() = getSafeBoolean("autofocus", false)
        set(value) { mPrefs.edit().putBoolean("autofocus", value).apply() }

    var focusDistance: Float
        get() = getSafeFloat("focus", 0f)
        set(value) { mPrefs.edit().putFloat("focus", value).apply() }

    fun getLastCommit(): String? = getSafeStringNullable("last_commit")

    fun setLastCommit() {
        mPrefs.edit().putString("last_commit", BuildConfig.COMMIT).apply()
    }

    fun applyAppLanguage() {
        val locales = if (appLanguage == LANGUAGE_SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(appLanguage)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
