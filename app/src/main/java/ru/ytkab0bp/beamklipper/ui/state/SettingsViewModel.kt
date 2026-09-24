package ru.ytkab0bp.beamklipper.ui.state

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.KlipperInstance
import ru.ytkab0bp.beamklipper.utils.CameraZoom
import ru.ytkab0bp.beamklipper.utils.Prefs
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
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
    val cameraResolution: StateFlow<Int> = AppState.cameraResolution
    val cameraZoom: StateFlow<Float> = AppState.cameraZoom
    val octoEverywhereEnabled: StateFlow<Boolean> = AppState.octoEverywhereEnabled
    val obicoEnabled: StateFlow<Boolean> = AppState.obicoEnabled
    val obicoServerUrl: StateFlow<String> = AppState.obicoServerUrl
    val obicoLinked: StateFlow<Boolean> = AppState.obicoLinked
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

    fun setObicoEnabled(enabled: Boolean) {
        Prefs.isObicoEnabled = enabled
        KlipperInstance.onObicoConfigChanged(enabled)
    }

    data class ObicoDiscoveryStatus(val isLinked: Boolean, val passcode: String, val passlink: String)

    // moonraker_obico's own PrinterDiscovery (running inside ObicoService
    // once Obico is enabled and not yet linked) polls the configured server
    // every ~2s and gets back a fresh one-time passcode to show the user —
    // the same code Obico's "Klipper (self-installed)" onboarding expects
    // you to enter manually when it can't auto-detect the printer on the
    // LAN. Upstream only exposes it via a Klipper gcode_macro variable
    // (meant for a printer.cfg macro + KlipperScreen panel this app doesn't
    // require); BundleInstaller patches it to also write a small JSON
    // status file next to moonraker-obico.cfg, read here instead.
    fun obicoDiscoveryStatus(): ObicoDiscoveryStatus? {
        for (inst in KlipperInstance.getInstances()) {
            val statusFile = File(inst.directory, "obico/obico_link_status.json")
            if (!statusFile.exists()) continue
            return try {
                val json = org.json.JSONObject(statusFile.readText())
                ObicoDiscoveryStatus(
                    isLinked = json.optBoolean("is_linked", false),
                    passcode = json.optString("one_time_passcode", ""),
                    passlink = json.optString("one_time_passlink", "")
                )
            } catch (_: Throwable) { null }
        }
        return null
    }

    // Discovery linking a printer completes entirely inside the Python
    // process — it writes the auth_token straight into moonraker-obico.cfg
    // itself, with no way to notify the Android side directly. Called while
    // polling obicoDiscoveryStatus(): the moment that file reports
    // is_linked, pull the token it already wrote back into Prefs so the
    // rest of the app (the "Linked ✓" row, future service restarts) agrees.
    fun syncObicoLinkStatus(): ObicoDiscoveryStatus? {
        val status = obicoDiscoveryStatus() ?: return null
        if (status.isLinked && Prefs.obicoAuthToken.isNullOrBlank()) {
            for (inst in KlipperInstance.getInstances()) {
                val cfgFile = File(inst.directory, "obico/moonraker-obico.cfg")
                if (!cfgFile.exists()) continue
                val token = try {
                    Regex("(?m)^\\s*auth_token\\s*=\\s*(\\S+)\\s*$").find(cfgFile.readText())?.groupValues?.get(1)
                } catch (_: Throwable) { null }
                if (!token.isNullOrBlank()) {
                    Prefs.obicoAuthToken = token
                    break
                }
            }
        }
        return status
    }

    fun isObicoCloud(url: String): Boolean = url == Prefs.OBICO_CLOUD_URL

    fun obicoServerLabel(url: String): String =
        if (isObicoCloud(url)) localizedContext().getString(ru.ytkab0bp.beamklipper.R.string.ObicoServerCloud)
        else url

    fun setObicoServer(cloud: Boolean, selfHostedUrl: String) {
        Prefs.obicoServerUrl = if (cloud) Prefs.OBICO_CLOUD_URL else selfHostedUrl
    }

    fun unlinkObico() {
        Prefs.obicoAuthToken = null
    }

    sealed class ObicoLinkResult {
        object Success : ObicoLinkResult()
        object InvalidCode : ObicoLinkResult()
        data class NetworkError(val message: String?) : ObicoLinkResult()
    }

    // Obico's own linking flow (moonraker_obico.link) is an interactive
    // terminal script: it either waits for a UDP-discovered "Link Now" tap
    // in the Obico app, or falls back to reading a 6-digit code from stdin.
    // Neither fits a Service with no terminal, so this replicates just the
    // one HTTP call that flow makes underneath (moonraker_obico.utils.
    // verify_link_code: POST {server}/api/v1/octo/verify/?code=XXXXXX ->
    // {"printer": {"auth_token": "..."}}) directly, from a code the user
    // gets from the Obico app/website and types into our own dialog.
    suspend fun linkObico(code: String): ObicoLinkResult = withContext(Dispatchers.IO) {
        try {
            val trimmed = code.trim()
            if (trimmed.isEmpty()) return@withContext ObicoLinkResult.InvalidCode
            val serverUrl = Prefs.obicoServerUrl.trimEnd('/')
            val url = URL("$serverUrl/api/v1/octo/verify/?code=" + URLEncoder.encode(trimmed, "UTF-8"))
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val responseCode = try { conn.responseCode } finally {}
            when {
                responseCode in 200..299 -> {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val authToken = JSONObject(body).getJSONObject("printer").getString("auth_token")
                    Prefs.obicoAuthToken = authToken
                    ObicoLinkResult.Success
                }
                responseCode in 400..499 -> ObicoLinkResult.InvalidCode
                else -> ObicoLinkResult.NetworkError("HTTP $responseCode")
            }
        } catch (e: Exception) {
            ObicoLinkResult.NetworkError(e.message)
        }
    }

    data class CameraSourceOption(val id: String?, val label: String)

    fun setCameraSource(id: String?) {
        Prefs.cameraId = id
    }

    fun cycleCameraRotation() {
        Prefs.cameraRotation = (Prefs.cameraRotation + 90) % 360
    }

    fun cycleCameraResolution() {
        Prefs.cameraResolution = (Prefs.cameraResolution + 1) % 3
    }

    // Mirrors CameraService.resolveCameraId() so the zoom steps offered here
    // match the camera the service will really open (a pinned id, else a USB
    // webcam if one is plugged in, else the first camera).
    private fun zoomOptionsForSelectedCamera(): List<Float> {
        return try {
            val manager = KlipperApp.INSTANCE.getSystemService(Context.CAMERA_SERVICE) as CameraManager
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
            } ?: return listOf(1f)
            CameraZoom.options(CameraZoom.maxZoom(manager.getCameraCharacteristics(id)))
        } catch (_: Throwable) {
            listOf(1f)
        }
    }

    fun cameraZoomOptions(): List<Float> = zoomOptionsForSelectedCamera()

    // Saved zoom may exceed what a newly selected camera supports.
    fun effectiveCameraZoom(zoom: Float): Float = CameraZoom.clamp(zoom, zoomOptionsForSelectedCamera())

    fun cycleCameraZoom() {
        val options = zoomOptionsForSelectedCamera()
        if (options.size <= 1) return
        val current = CameraZoom.clamp(Prefs.cameraZoom, options)
        Prefs.cameraZoom = options[(options.indexOf(current) + 1) % options.size]
    }

    fun cameraResolutionTitle(resolution: Int): String = localizedContext().getString(
        when (resolution) {
            Prefs.CAMERA_RESOLUTION_MEDIUM -> ru.ytkab0bp.beamklipper.R.string.CameraResolutionMedium
            Prefs.CAMERA_RESOLUTION_HIGH -> ru.ytkab0bp.beamklipper.R.string.CameraResolutionHigh
            else -> ru.ytkab0bp.beamklipper.R.string.CameraResolutionLow
        }
    )

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
