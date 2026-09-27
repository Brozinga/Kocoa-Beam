package ru.ytkab0bp.beamklipper

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

// How the bundled Klipper/Moonraker/companion trees are unpacked and patched,
// free of Android so it can be unit-tested against the real vendored files.
object BundlePatches {
    // Placeholders the vendored sources carry, replaced with app paths at install.
    const val DEST_LIB = "\${DEST_LIB}"
    const val TEMP_PATH = "\${TEMP_PATH}"
    const val TTY_PATH = "\${TTY_PATH}"
    const val WEB_PORT_FILE = "\${WEB_PORT_FILE}"

    const val SYSFS_TTY_ORIGINAL = "TTY_PATH = \"/sys/class/tty\""

    fun sysfsTty(serialDir: String) = "TTY_PATH = \"$serialDir\""

    // moonraker_obico.janus_config_builder calls board_id() at MODULE import
    // time, and board_id() only guards the devicetree read with isfile(). On
    // Android that file exists but is denied by SELinux, so open() throws
    // PermissionError and crashes app.py's whole import chain before main().
    const val OBICO_BOARD_ID_ORIGINAL =
        "def board_id():\n    model_file = \"/sys/firmware/devicetree/base/model\"\n    if os.path.isfile(model_file):\n        with open(model_file, 'r') as file:\n            data = file.read()\n            if \"raspberry\" in data.lower():\n                return \"rpi\"\n            elif \"makerbase\" in data.lower() or \"roc-rk3328-cc\" in data:\n                return \"mks\"\n    return \"NA\""
    const val OBICO_BOARD_ID_PATCHED =
        "def board_id():\n    model_file = \"/sys/firmware/devicetree/base/model\"\n    try:\n        if os.path.isfile(model_file):\n            with open(model_file, 'r') as file:\n                data = file.read()\n                if \"raspberry\" in data.lower():\n                    return \"rpi\"\n                elif \"makerbase\" in data.lower() or \"roc-rk3328-cc\" in data:\n                    return \"mks\"\n    except OSError:\n        pass\n    return \"NA\""

    // printer_discovery only surfaces the one-time passcode through Klipper
    // gcode_macro variables; this also writes a plain JSON status file next
    // to moonraker-obico.cfg, which the app reads for Settings -> Link printer.
    const val OBICO_LINK_STATUS_ORIGINAL =
        "    def set_obico_link_status(self, is_linked, one_time_passcode, one_time_passlink):\n        self.moonrakerconn.set_macro_variables('OBICO_LINK_STATUS',"
    const val OBICO_LINK_STATUS_PATCHED =
        "    def set_obico_link_status(self, is_linked, one_time_passcode, one_time_passlink):\n        try:\n            import json as _json\n            _status_path = os.path.join(os.path.dirname(os.path.abspath(self.config._config_path)), 'obico_link_status.json')\n            with open(_status_path, 'w') as _f:\n                _json.dump({'is_linked': is_linked, 'one_time_passcode': one_time_passcode, 'one_time_passlink': one_time_passlink}, _f)\n        except Exception:\n            pass\n        self.moonrakerconn.set_macro_variables('OBICO_LINK_STATUS',"

    fun patchObicoBoardId(source: String): String = source.replace(OBICO_BOARD_ID_ORIGINAL, OBICO_BOARD_ID_PATCHED)

    fun patchObicoLinkStatus(source: String): String = source.replace(OBICO_LINK_STATUS_ORIGINAL, OBICO_LINK_STATUS_PATCHED)
}

object BundleFiles {
    // Other processes may be importing these files right now, so never leave
    // one truncated or half-written: skip identical content, otherwise write a
    // temp file and rename it over the target (atomic on the same directory).
    fun writeIfChanged(target: File, bytes: ByteArray) {
        try {
            if (target.exists() && target.readBytes().contentEquals(bytes)) return
        } catch (_: Exception) {}
        val tmp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(tmp).use { it.write(bytes) }
        if (!tmp.renameTo(target)) {
            tmp.delete()
            FileOutputStream(target).use { it.write(bytes) }
        }
    }

    // Replaces <root>/<key> with the files the bundle index lists for [key].
    // A key missing from the index leaves the tree removed (nothing to copy).
    fun unpack(open: (String) -> InputStream, files: List<String>?, root: File, key: String) {
        val dir = File(root, key)
        dir.deleteRecursively()
        if (files == null) return
        for (file in files) {
            val into = File(dir, file)
            into.parentFile?.mkdirs()
            open("$key/$file").use { inp ->
                FileOutputStream(into).use { fos -> inp.copyTo(fos) }
            }
        }
    }
}
