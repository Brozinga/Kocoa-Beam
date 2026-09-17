package ru.ytkab0bp.beamklipper

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

object BundleInstaller {
    @JvmStatic
    fun init(ctx: Context) {
        val prefs = ctx.getSharedPreferences("installation", 0)
        val assets = ctx.assets
        try {
            val pm = ctx.packageManager
            val info = pm.getPackageInfo(ctx.packageName, 0)
            var ver = readString(assets, "bundle_version") + "_beam-" + info.versionName

            val root = ctx.filesDir
            if (prefs.getString("version", "") != ver) {
                val index = JSONObject(readString(assets, "index.json"))
                unpack(assets, index, root, "klipper")
                unpack(assets, index, root, "kalico")
                unpack(assets, index, root, "moonraker")
                unpack(assets, index, root, "octoeverywhere")
                unpack(assets, index, root, "obico")
                prefs.edit().putString("version", ver).apply()
            }

            // Moonraker's file_manager registers "<klipper_path>/docs" as the
            // "docs" root and adds a startup warning if it does not exist. We
            // don't bundle Klipper's docs tree, so create the (empty) folder to
            // silence "Supplied path (…/klipper/docs) for (docs) is invalid".
            File(root, "klipper/docs").mkdirs()
            File(root, "kalico/docs").mkdirs()

            val nativeDir = File(info.applicationInfo!!.nativeLibraryDir)

            patchBundledFile(root, assets, "klipper", "klippy/chelper/__init__.py") {
                it.replace("\${DEST_LIB}", File(nativeDir, "libklippy_chelper.so").absolutePath)
            }
            patchBundledFile(root, assets, "kalico", "klippy/chelper/__init__.py") {
                it.replace("\${DEST_LIB}", File(nativeDir, "libkalico_chelper.so").absolutePath)
            }

            var str = readString(assets, "moonraker/moonraker/utils/sysfs_devs.py")
            str = str.replace("TTY_PATH = \"/sys/class/tty\"",
                "TTY_PATH = \"" + File(KlipperApp.INSTANCE.filesDir, "serial").absolutePath + "\"")
            FileOutputStream(File(root, "moonraker/moonraker/utils/sysfs_devs.py")).use {
                it.write(str.toByteArray(StandardCharsets.UTF_8))
            }

            val tempPath = File(KlipperApp.INSTANCE.cacheDir, "resonances").absolutePath
            patchBundledFile(root, assets, "klipper", "klippy/extras/resonance_tester.py") {
                it.replace("\${TEMP_PATH}", tempPath)
            }
            patchBundledFile(root, assets, "kalico", "klippy/extras/resonance_tester.py") {
                it.replace("\${TEMP_PATH}", tempPath)
            }

            val ttyPath = "'" + File(KlipperApp.INSTANCE.filesDir, "serial").absolutePath + "'"
            patchBundledFile(root, assets, "klipper", "klippy/mcu.py") {
                it.replace("\${TTY_PATH}", ttyPath)
            }
            patchBundledFile(root, assets, "kalico", "klippy/mcu.py") {
                it.replace("\${TTY_PATH}", ttyPath)
            }

            // moonraker_obico.janus_config_builder calls board_id() at MODULE
            // IMPORT time (not deferred to actual Janus startup, which never
            // happens here — see docs/obico.md), and board_id() only guards
            // the devicetree read with os.path.isfile(), not a try/except
            // around open(). On Android that file exists but reading it is
            // denied by SELinux (untrusted_app), so the open() throws
            // PermissionError, which crashes app.py's entire import chain
            // before it even reaches main() — no log file gets written, the
            // service just silently never connects. pi_version() one
            // function above already wraps the identical read in a bare
            // except; this mirrors that.
            patchBundledFile(root, assets, "obico", "moonraker_obico/utils.py") {
                it.replace(
                    "def board_id():\n    model_file = \"/sys/firmware/devicetree/base/model\"\n    if os.path.isfile(model_file):\n        with open(model_file, 'r') as file:\n            data = file.read()\n            if \"raspberry\" in data.lower():\n                return \"rpi\"\n            elif \"makerbase\" in data.lower() or \"roc-rk3328-cc\" in data:\n                return \"mks\"\n    return \"NA\"",
                    "def board_id():\n    model_file = \"/sys/firmware/devicetree/base/model\"\n    try:\n        if os.path.isfile(model_file):\n            with open(model_file, 'r') as file:\n                data = file.read()\n                if \"raspberry\" in data.lower():\n                    return \"rpi\"\n                elif \"makerbase\" in data.lower() or \"roc-rk3328-cc\" in data:\n                    return \"mks\"\n    except OSError:\n        pass\n    return \"NA\""
                )
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private fun unpack(assets: android.content.res.AssetManager, index: JSONObject, root: File, key: String) {
        val dir = File(root, key)
        dir.deleteRecursively()

        val arr = index.optJSONArray(key)
        if (arr == null) {
            return
        }
        for (i in 0 until arr.length()) {
            val file = arr.optString(i)
            val into = File(dir, file)
            into.parentFile?.mkdirs()
            assets.open("$key/$file").use { inp ->
                FileOutputStream(into).use { fos ->
                    inp.copyTo(fos)
                }
            }
        }
    }

    private fun patchBundledFile(
        root: File,
        assets: android.content.res.AssetManager,
        bundleKey: String,
        relativePath: String,
        transform: (String) -> String,
    ) {
        val target = File(root, "$bundleKey/$relativePath")
        if (!target.exists()) {
            return
        }
        val updated = transform(readString(assets, "$bundleKey/$relativePath"))
        FileOutputStream(target).use {
            it.write(updated.toByteArray(StandardCharsets.UTF_8))
        }
    }

    @JvmStatic
    fun readString(assets: android.content.res.AssetManager, key: String): String {
        return assets.open(key).use { inp ->
            inp.readBytes().toString(StandardCharsets.UTF_8)
        }
    }
}
