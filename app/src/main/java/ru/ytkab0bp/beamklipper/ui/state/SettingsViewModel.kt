package ru.ytkab0bp.beamklipper.ui.state

import android.app.Application
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.KlipperInstance
import ru.ytkab0bp.beamklipper.utils.Prefs
import java.io.File

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    val engine: StateFlow<String> = AppState.engine
    val webFrontend: StateFlow<String> = AppState.webFrontend
    val usbNaming: StateFlow<Int> = AppState.usbNaming
    val cameraEnabled: StateFlow<Boolean> = AppState.cameraEnabled
    val cameraSourceId: StateFlow<String?> = AppState.cameraSourceId
    val octoEverywhereEnabled: StateFlow<Boolean> = AppState.octoEverywhereEnabled
    val appLanguage: StateFlow<String> = AppState.appLanguage

    fun cycleEngine() {
        val next = if (Prefs.engine == Prefs.ENGINE_KLIPPER) Prefs.ENGINE_KALICO else Prefs.ENGINE_KLIPPER
        if (next == Prefs.ENGINE_KALICO &&
            !File(KlipperApp.INSTANCE.filesDir, "kalico/klippy/klippy.py").exists()
        ) {
            return
        }
        Prefs.engine = next
    }

    fun setEngine(engine: String) {
        if (engine == Prefs.ENGINE_KALICO &&
            !File(KlipperApp.INSTANCE.filesDir, "kalico/klippy/klippy.py").exists()
        ) {
            return
        }
        Prefs.engine = engine
    }

    fun cycleFrontend() {
        Prefs.webFrontend =
            if (Prefs.webFrontend == Prefs.FRONTEND_FLUIDD) Prefs.FRONTEND_MAINSAIL else Prefs.FRONTEND_FLUIDD
    }

    fun setFrontend(frontend: String) {
        Prefs.webFrontend = frontend
    }

    fun cycleUsbNaming() {
        Prefs.usbDeviceNaming =
            if (Prefs.usbDeviceNaming == Prefs.USB_DEVICE_NAMING_BY_PATH) Prefs.USB_DEVICE_NAMING_BY_VID_PID
            else Prefs.USB_DEVICE_NAMING_BY_PATH
    }

    fun setCameraEnabled(enabled: Boolean) {
        Prefs.isCameraEnabled = enabled
        KlipperInstance.onCameraConfigChanged(enabled)
    }

    fun refreshCameraSwitch(granted: Boolean) {
        if (granted) {
            Prefs.isCameraEnabled = true
            KlipperInstance.onCameraConfigChanged(true)
        }
    }

    fun setOctoEverywhereEnabled(enabled: Boolean) {
        Prefs.isOctoEverywhereEnabled = enabled
        KlipperInstance.onOctoEverywhereConfigChanged(enabled)
    }

    // OctoEverywhere writes its generated printer id to an INI-style secrets
    // file under the instance's own storage dir once it first starts (see
    // linux_host/secrets.py). Only one instance ever runs the companion at a
    // time, so scanning all of them for whichever has it is simplest.
    fun octoEverywhereLinkUrl(): String? {
        for (inst in KlipperInstance.getInstances()) {
            val secrets = File(inst.directory, "octoeverywhere/octoeverywhere.secrets")
            if (!secrets.exists()) continue
            val printerId = try {
                Regex("(?m)^\\s*printer_id\\s*=\\s*(.+?)\\s*$").find(secrets.readText())?.groupValues?.get(1)
            } catch (_: Throwable) { null }
            if (!printerId.isNullOrBlank()) {
                return "https://octoeverywhere.com/getstarted?printerid=$printerId"
            }
        }
        return null
    }

    data class CameraSourceOption(val id: String?, val label: String)

    fun setCameraSource(id: String?) {
        Prefs.cameraId = id
    }

    fun cameraSourceOptions(): List<CameraSourceOption> {
        val options = mutableListOf(
            CameraSourceOption(null, KlipperApp.INSTANCE.getString(ru.ytkab0bp.beamklipper.R.string.CameraSourceAuto))
        )
        try {
            val manager = KlipperApp.INSTANCE.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            for (id in manager.cameraIdList) {
                options.add(CameraSourceOption(id, cameraLabel(manager, id)))
            }
        } catch (_: Throwable) {}
        return options
    }

    fun cameraSourceTitle(id: String?): String {
        if (id == null) return KlipperApp.INSTANCE.getString(ru.ytkab0bp.beamklipper.R.string.CameraSourceAuto)
        return try {
            val manager = KlipperApp.INSTANCE.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            if (manager.cameraIdList.contains(id)) cameraLabel(manager, id)
            else KlipperApp.INSTANCE.getString(ru.ytkab0bp.beamklipper.R.string.CameraSourceAuto)
        } catch (_: Throwable) {
            KlipperApp.INSTANCE.getString(ru.ytkab0bp.beamklipper.R.string.CameraSourceAuto)
        }
    }

    private fun cameraLabel(manager: CameraManager, id: String): String {
        val ctx = KlipperApp.INSTANCE
        val facing = try {
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
        } catch (_: Throwable) { null }
        return when (facing) {
            CameraCharacteristics.LENS_FACING_EXTERNAL -> ctx.getString(ru.ytkab0bp.beamklipper.R.string.CameraSourceUsbWebcam, id)
            CameraCharacteristics.LENS_FACING_BACK -> ctx.getString(ru.ytkab0bp.beamklipper.R.string.CameraSourceBuiltInBack, id)
            CameraCharacteristics.LENS_FACING_FRONT -> ctx.getString(ru.ytkab0bp.beamklipper.R.string.CameraSourceBuiltInFront, id)
            else -> ctx.getString(ru.ytkab0bp.beamklipper.R.string.CameraSourceOther, id)
        }
    }

    fun engineTitle(engine: String): String = KlipperApp.INSTANCE.getString(
        if (engine == Prefs.ENGINE_KALICO) ru.ytkab0bp.beamklipper.R.string.Kalico
        else ru.ytkab0bp.beamklipper.R.string.Klipper
    )

    fun frontendTitle(frontend: String): String = KlipperApp.INSTANCE.getString(
        if (frontend == Prefs.FRONTEND_FLUIDD) ru.ytkab0bp.beamklipper.R.string.Fluidd
        else ru.ytkab0bp.beamklipper.R.string.Mainsail
    )

    fun usbNamingTitle(naming: Int): String = KlipperApp.INSTANCE.getString(
        if (naming == Prefs.USB_DEVICE_NAMING_BY_PATH) ru.ytkab0bp.beamklipper.R.string.USBDeviceNamingByPath
        else ru.ytkab0bp.beamklipper.R.string.USBDeviceNamingByVidPid
    )

    fun languageTitle(language: String): String = KlipperApp.INSTANCE.getString(
        when (language) {
            Prefs.LANGUAGE_ENGLISH -> ru.ytkab0bp.beamklipper.R.string.LanguageEnglish
            Prefs.LANGUAGE_RUSSIAN -> ru.ytkab0bp.beamklipper.R.string.LanguageRussian
            Prefs.LANGUAGE_CHINESE_SIMPLIFIED -> ru.ytkab0bp.beamklipper.R.string.LanguageChineseSimplified
            Prefs.LANGUAGE_CHINESE_TRADITIONAL -> ru.ytkab0bp.beamklipper.R.string.LanguageChineseTraditional
            else -> ru.ytkab0bp.beamklipper.R.string.LanguageSystem
        }
    )
}
