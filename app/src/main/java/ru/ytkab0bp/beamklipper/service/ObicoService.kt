package ru.ytkab0bp.beamklipper.service

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.utils.Prefs
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

// Runs the real Obico Klipper/Moonraker companion (vendored at
// app/src/main/obico, from github.com/TheSpaghettiDetective/moonraker-obico)
// as its own Chaquopy process, the same way OctoEverywhereService runs its
// own vendored companion. Single app-wide instance bound to whichever
// KlipperInstance first reaches RUNNING — see KlipperInstance.onObicoConfigChanged.
//
// Started as soon as Obico is enabled, linked or not: without an auth_token,
// moonraker_obico.app runs its own PrinterDiscovery — NOT local UDP/mDNS
// broadcast (that was a wrong assumption in an earlier version of this
// file), but polling POSTs to the configured server's /api/v1/octo/unlinked/
// every couple seconds, reporting this printer's device id/local IP/a
// one-time passcode, plus a small local Flask server (port 46793) the Obico
// app can hit directly if it's on the same LAN. This is what actually
// backs the "Link Obico" flow Obico's own touchscreen/LCD docs describe for
// a self-installed Klipper setup, and it's what makes the printer
// discoverable in the Obico app/website at all — a token obtained only via
// the manual 6-digit-code dialog below (SettingsViewModel.linkObico) was
// not enough on its own for the server to ever mark it online. The
// discovery loop itself also re-reads this config file every iteration and
// stops cleanly as soon as either path produces an auth_token, so both
// coexist without conflict.
class ObicoService : BasePythonService() {
    companion object {
        private const val TAG = "beam_obico"
        private const val ID = 700000
    }

    private var wifiLock: WifiManager.WifiLock? = null
    private val stoppedByUser = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? {
        val b = super.onBind(intent) ?: return null
        acquireLocks()
        val inst = instance
        if (inst != null) {
            val not = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                Notification.Builder(this, KlipperApp.SERVICES_CHANNEL)
            else
                Notification.Builder(this)
            not.setContentTitle(getString(R.string.ObicoTitle))
                .setContentText(getString(R.string.ObicoDescription))
                .setSmallIcon(R.drawable.icon_adaptive_foreground)
                .setOngoing(true)
            notificationManager.notify(ID, not.build())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(ID, not.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(ID, not.build())
            }
        }
        return b
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (stoppedByUser.get()) return
    }

    private fun acquireLocks() {
        try {
            if (wifiLock == null) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wifiLock = wm.createWifiLock(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL,
                    "BeamKlipper::ObicoWiFiLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to acquire wifilock", t)
        }
    }

    private fun releaseLocks() {
        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Throwable) {}
        wifiLock = null
    }

    override fun onDestroy() {
        stoppedByUser.set(true)
        releaseLocks()
        super.onDestroy()
        stopForeground(true)
        notificationManager.cancel(ID)
    }

    override fun onStartPython() {
        val inst = instance ?: return
        try {
            val authToken = Prefs.obicoAuthToken

            val obicoDir = File(KlipperApp.INSTANCE.filesDir, "obico")
            if (!File(obicoDir, "moonraker_obico/app.py").exists()) {
                Log.e(TAG, "Obico bundle not unpacked yet, aborting")
                return
            }

            val localStorage = File(inst.directory, "obico")
            localStorage.mkdirs()

            val configFolder = File(inst.publicDirectory, "config")
            val logFolder = File(inst.publicDirectory, "logs")
            logFolder.mkdirs()
            val moonrakerCfg = File(configFolder, "moonraker.conf")
            // Same race as OctoEverywhereService: "Moonraker connected" only
            // means MoonrakerService finished binding, not that moonraker.conf
            // (written asynchronously) exists yet. Poll instead of aborting.
            val deadline = System.currentTimeMillis() + 15_000L
            while (!moonrakerCfg.exists() && System.currentTimeMillis() < deadline) {
                try { Thread.sleep(300) } catch (_: InterruptedException) { break }
            }
            if (!moonrakerCfg.exists()) {
                Log.e(TAG, "moonraker.conf missing for instance ${inst.id} after waiting, aborting")
                return
            }
            val moonrakerPort = Regex("^\\s*port\\s*[:=]\\s*(\\d+)", RegexOption.MULTILINE)
                .find(moonrakerCfg.readText())?.groupValues?.get(1)?.toIntOrNull() ?: 7125

            val cfgFile = File(localStorage, "moonraker-obico.cfg")
            val logFile = File(logFolder, "obico.log")
            cfgFile.writeText(
                buildString {
                    append("[server]\n")
                    append("url = ").append(Prefs.obicoServerUrl).append('\n')
                    // Left unset when not yet linked: moonraker_obico.app
                    // reacts to that itself by running its own linking flow
                    // (PrinterDiscovery — see the class doc below), rather
                    // than us needing to gate starting it at all.
                    if (!authToken.isNullOrBlank()) {
                        append("auth_token = ").append(authToken).append('\n')
                    }
                    append('\n')
                    append("[moonraker]\n")
                    append("host = 127.0.0.1\n")
                    append("port = ").append(moonrakerPort).append('\n')
                    append('\n')
                    // The real-time WebRTC preview needs a native janus-gateway
                    // process plus ffmpeg (precompiled Linux/glibc binaries in
                    // the upstream repo, for desktop/RPi targets) — neither is
                    // bundled here (see docs/obico.md). This documented,
                    // supported config flag skips that pipeline entirely;
                    // Obico still gets periodic JPEG snapshots (JpegPoster),
                    // independent of this flag, from the same camera server
                    // used by Fluidd/Mainsail/OctoEverywhere.
                    append("[webcam]\n")
                    append("disable_video_streaming = True\n")
                    append('\n')
                    append("[logging]\n")
                    append("path = ").append(logFile.absolutePath).append('\n')
                    append("level = INFO\n")
                    append('\n')
                    // Opt out by default, matching OctoEverywhereService — the
                    // actual server connection this app makes is unaffected.
                    append("[misc]\n")
                    append("sentry_opt = out\n")
                }
            )

            // Mirrors OctoEverywhereService: write the bootstrap fresh into
            // the unpacked bundle dir. app.py's own entrypoint is an
            // `if __name__ == '__main__':` block, not a main() function, so
            // runPython() (which calls callAttr("main")) needs this wrapper.
            val bsFile = File(obicoDir, "obico_bs.py")
            try {
                bsFile.writeText(
                    "import os\nimport sys\nimport runpy\n\ndef main():\n    here = os.path.dirname(os.path.abspath(__file__))\n    if here not in sys.path:\n        sys.path.insert(0, here)\n    runpy.run_module(\"moonraker_obico.app\", run_name=\"__main__\", alter_sys=True)\n",
                    StandardCharsets.UTF_8
                )
            } catch (e: Throwable) {
                Log.w(TAG, "Bootstrap write failed", e)
            }

            runPython(obicoDir, "obico_bs", "app.py", "-c", cfgFile.absolutePath, "-l", logFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Obico", e)
        }
    }
}
