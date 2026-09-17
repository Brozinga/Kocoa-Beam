package ru.ytkab0bp.beamklipper.ui.state

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.KlipperInstance
import ru.ytkab0bp.beamklipper.utils.Prefs
import java.io.File
import java.util.Locale

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    // KlipperApp.INSTANCE.getString() reads the raw Application resources,
    // which AppCompatDelegate.setApplicationLocales() (Prefs.applyAppLanguage)
    // does NOT keep in sync on API < 33 — that compat path only wraps
    // Activity contexts via attachBaseContext, so it falls back to the
    // device's system locale instead of the user's in-app language choice.
    // Compose's stringResource() (Activity-scoped) gets this right on its
    // own; anywhere in this ViewModel that needs a string outside Compose
    // must build its own locale-correct context instead.
    private fun localizedContext(): Context {
        val language = Prefs.appLanguage
        val base = KlipperApp.INSTANCE
        if (language == Prefs.LANGUAGE_SYSTEM) return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language))
        return base.createConfigurationContext(config)
    }

    val engine: StateFlow<String> = AppState.engine
    val webFrontend: StateFlow<String> = AppState.webFrontend
    val usbNaming: StateFlow<Int> = AppState.usbNaming
    val cameraEnabled: StateFlow<Boolean> = AppState.cameraEnabled
    val cameraSourceId: StateFlow<String?> = AppState.cameraSourceId
    val cameraRotation: StateFlow<Int> = AppState.cameraRotation
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

    fun cycleCameraRotation() {
        Prefs.cameraRotation = (Prefs.cameraRotation + 90) % 360
    }

    fun cameraSourceOptions(): List<CameraSourceOption> {
        val options = mutableListOf(
            CameraSourceOption(null, localizedContext().getString(ru.ytkab0bp.beamklipper.R.string.CameraSourceAuto))
        )
        try {
            val manager = KlipperApp.INSTANCE.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val ids = manager.cameraIdList.toList()
            for (id in ids) {
                options.add(CameraSourceOption(id, cameraLabel(manager, id, ids)))
            }
        } catch (_: Throwable) {}
        return options
    }

    fun cameraSourceTitle(id: String?): String {
        if (id == null) return localizedContext().getString(ru.ytkab0bp.beamklipper.R.string.CameraSourceAuto)
        return try {
            val manager = KlipperApp.INSTANCE.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val ids = manager.cameraIdList.toList()
            if (ids.contains(id)) cameraLabel(manager, id, ids)
            else localizedContext().getString(ru.ytkab0bp.beamklipper.R.string.CameraSourceAuto)
        } catch (_: Throwable) {
            localizedContext().getString(ru.ytkab0bp.beamklipper.R.string.CameraSourceAuto)
        }
    }

    // Devices with multiple lenses per facing (main/ultra-wide/telephoto on the
    // back, sometimes two on the front) expose each as its own top-level
    // Camera2 id — cameraIdList already lists all of them, nothing special
    // needed there. What's missing is telling them apart: the raw id (e.g.
    // "0"/"2"/"3") means nothing to a user. Since there's no cross-OEM API for
    // "this is the ultra-wide", the generic, accurate option is the 35mm-
    // equivalent focal length (the same number phones are marketed with,
    // e.g. ~13mm/26mm/52mm), computed from LENS_INFO_AVAILABLE_FOCAL_LENGTHS
    // + SENSOR_INFO_PHYSICAL_SIZE — falls back to just numbering them if that
    // metadata isn't available.
    private fun cameraLabel(manager: CameraManager, id: String, allIds: List<String>): String {
        val ctx = localizedContext()
        val chars = try { manager.getCameraCharacteristics(id) } catch (_: Throwable) { null }
        val facing = chars?.get(CameraCharacteristics.LENS_FACING)
        if (facing == CameraCharacteristics.LENS_FACING_EXTERNAL) {
            return ctx.getString(ru.ytkab0bp.beamklipper.R.string.CameraSourceUsbWebcam, id)
        }
        val sameFacingIds = allIds.filter { other ->
            try { manager.getCameraCharacteristics(other).get(CameraCharacteristics.LENS_FACING) == facing } catch (_: Throwable) { false }
        }.sorted()
        val baseRes = when (facing) {
            CameraCharacteristics.LENS_FACING_BACK -> ru.ytkab0bp.beamklipper.R.string.CameraSourceBuiltInBack
            CameraCharacteristics.LENS_FACING_FRONT -> ru.ytkab0bp.beamklipper.R.string.CameraSourceBuiltInFront
            else -> ru.ytkab0bp.beamklipper.R.string.CameraSourceOther
        }
        if (sameFacingIds.size <= 1) return ctx.getString(baseRes, id)

        val multiRes = when (facing) {
            CameraCharacteristics.LENS_FACING_BACK -> ru.ytkab0bp.beamklipper.R.string.CameraSourceBackN
            CameraCharacteristics.LENS_FACING_FRONT -> ru.ytkab0bp.beamklipper.R.string.CameraSourceFrontN
            else -> baseRes
        }
        val index = sameFacingIds.indexOf(id) + 1
        val label = ctx.getString(multiRes, index)
        val focalHint = chars?.let { focalLengthHint(it) }
        return if (focalHint != null) "$label ($focalHint)" else label
    }

    private fun focalLengthHint(chars: CameraCharacteristics): String? {
        val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: return null
        val sensor = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
        val diagMm = kotlin.math.sqrt(sensor.width * sensor.width + sensor.height * sensor.height)
        if (diagMm <= 0f) return null
        // 43.27mm is the diagonal of a 36x24mm full-frame sensor — the
        // standard "35mm equivalent" reference used for this conversion.
        val equiv35 = focal * (43.27f / diagMm)
        return "${Math.round(equiv35)}mm"
    }

    fun engineTitle(engine: String): String = localizedContext().getString(
        if (engine == Prefs.ENGINE_KALICO) ru.ytkab0bp.beamklipper.R.string.Kalico
        else ru.ytkab0bp.beamklipper.R.string.Klipper
    )

    fun frontendTitle(frontend: String): String = localizedContext().getString(
        if (frontend == Prefs.FRONTEND_FLUIDD) ru.ytkab0bp.beamklipper.R.string.Fluidd
        else ru.ytkab0bp.beamklipper.R.string.Mainsail
    )

    fun usbNamingTitle(naming: Int): String = localizedContext().getString(
        if (naming == Prefs.USB_DEVICE_NAMING_BY_PATH) ru.ytkab0bp.beamklipper.R.string.USBDeviceNamingByPath
        else ru.ytkab0bp.beamklipper.R.string.USBDeviceNamingByVidPid
    )

    fun languageTitle(language: String): String = localizedContext().getString(
        when (language) {
            Prefs.LANGUAGE_ENGLISH -> ru.ytkab0bp.beamklipper.R.string.LanguageEnglish
            Prefs.LANGUAGE_PORTUGUESE_BRAZIL -> ru.ytkab0bp.beamklipper.R.string.LanguagePortuguese
            Prefs.LANGUAGE_RUSSIAN -> ru.ytkab0bp.beamklipper.R.string.LanguageRussian
            Prefs.LANGUAGE_CHINESE_SIMPLIFIED -> ru.ytkab0bp.beamklipper.R.string.LanguageChineseSimplified
            Prefs.LANGUAGE_CHINESE_TRADITIONAL -> ru.ytkab0bp.beamklipper.R.string.LanguageChineseTraditional
            else -> ru.ytkab0bp.beamklipper.R.string.LanguageSystem
        }
    )
}
