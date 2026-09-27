package ru.ytkab0bp.beamklipper

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException

// The patches are only worth anything while the vendored sources still carry
// the exact text they replace, so these read the real files under src/main.
class BundlePatchesTest {
    private fun source(path: String) = File("src/main/$path").readText()

    @Test
    fun `klipper and kalico chelper still carry the library placeholder`() {
        assertTrue(source("klipper/klippy/chelper/__init__.py").contains(BundlePatches.DEST_LIB))
        assertTrue(source("kalico/klippy/chelper/__init__.py").contains(BundlePatches.DEST_LIB))
    }

    @Test
    fun `klipper's mcu still carries the serial directory placeholder`() {
        // Kalico's mcu.py has no such placeholder: the installer's TTY_PATH
        // patch of it changes nothing.
        assertTrue(source("klipper/klippy/mcu.py").contains(BundlePatches.TTY_PATH))
    }

    @Test
    fun `klipper's resonance tester still carries the temp directory placeholder`() {
        assertTrue(source("klipper/klippy/extras/resonance_tester.py").contains(BundlePatches.TEMP_PATH))
    }

    @Test
    fun `moonraker's sysfs helper still has the tty path the app redirects`() {
        assertTrue(source("moonraker/moonraker/utils/sysfs_devs.py").contains(BundlePatches.SYSFS_TTY_ORIGINAL))
    }

    @Test
    fun `the tty path is redirected into the app's serial folder`() {
        val patched = source("moonraker/moonraker/utils/sysfs_devs.py")
            .replace(BundlePatches.SYSFS_TTY_ORIGINAL, BundlePatches.sysfsTty("/data/files/serial"))
        assertTrue(patched.contains("TTY_PATH = \"/data/files/serial\""))
        assertFalse(patched.contains("/sys/class/tty"))
    }

    @Test
    fun `the obico board_id function is still what the patch expects`() {
        assertTrue(source("obico/moonraker_obico/utils.py").contains(BundlePatches.OBICO_BOARD_ID_ORIGINAL))
    }

    @Test
    fun `the board_id patch guards the devicetree read`() {
        val patched = BundlePatches.patchObicoBoardId(source("obico/moonraker_obico/utils.py"))
        assertFalse(patched.contains(BundlePatches.OBICO_BOARD_ID_ORIGINAL))
        assertTrue(patched.contains(BundlePatches.OBICO_BOARD_ID_PATCHED))
        assertTrue(patched.contains("except OSError:"))
    }

    @Test
    fun `the obico discovery function is still what the patch expects`() {
        assertTrue(source("obico/moonraker_obico/printer_discovery.py").contains(BundlePatches.OBICO_LINK_STATUS_ORIGINAL))
    }

    @Test
    fun `the link status patch writes the json file the app reads`() {
        val patched = BundlePatches.patchObicoLinkStatus(source("obico/moonraker_obico/printer_discovery.py"))
        assertTrue(patched.contains("obico_link_status.json"))
        assertTrue(patched.contains("one_time_passcode"))
        assertEquals(1, patched.split("obico_link_status.json").size - 1)
    }

    @Test
    fun `beam_beeper and beam_camera still carry the web port file placeholder`() {
        assertTrue(source("assets/klipper/beam_ext/beam_beeper.py").contains(BundlePatches.WEB_PORT_FILE))
        assertTrue(source("assets/klipper/beam_ext/beam_camera.py").contains(BundlePatches.WEB_PORT_FILE))
        // kalico's copy is generated from the same source by the build.
        assertTrue(source("assets/kalico/beam_ext/beam_beeper.py").contains(BundlePatches.WEB_PORT_FILE))
        assertTrue(source("assets/kalico/beam_ext/beam_camera.py").contains(BundlePatches.WEB_PORT_FILE))
    }

    @Test
    fun `patching twice does not duplicate the change`() {
        val once = BundlePatches.patchObicoLinkStatus(source("obico/moonraker_obico/printer_discovery.py"))
        // the patched text no longer contains the original, so a second pass is a no-op
        assertEquals(once, BundlePatches.patchObicoLinkStatus(once))
        val board = BundlePatches.patchObicoBoardId(source("obico/moonraker_obico/utils.py"))
        assertEquals(board, BundlePatches.patchObicoBoardId(board))
    }

    @Test
    fun `the patched python is still valid`() {
        val python = listOf("python3", "python").firstOrNull {
            runCatching { ProcessBuilder(it, "--version").redirectErrorStream(true).start().waitFor() == 0 }.getOrDefault(false)
        } ?: return
        val dir = createTempDir()
        try {
            listOf(
                "utils.py" to BundlePatches.patchObicoBoardId(source("obico/moonraker_obico/utils.py")),
                "printer_discovery.py" to BundlePatches.patchObicoLinkStatus(source("obico/moonraker_obico/printer_discovery.py"))
            ).forEach { (name, src) ->
                val f = File(dir, name).apply { writeText(src) }
                val p = ProcessBuilder(python, "-m", "py_compile", f.path).redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText()
                assertEquals("$name: $out", 0, p.waitFor())
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}

class BundleFilesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `a new file is written`() {
        val f = File(tmp.root, "a.txt")
        BundleFiles.writeIfChanged(f, "hello".toByteArray())
        assertEquals("hello", f.readText())
    }

    @Test
    fun `identical content is not rewritten`() {
        val f = tmp.newFile("a.txt").apply { writeText("same"); setLastModified(1000) }
        BundleFiles.writeIfChanged(f, "same".toByteArray())
        assertEquals(1000L, f.lastModified())
    }

    @Test
    fun `changed content replaces the file and leaves no temp file`() {
        val f = tmp.newFile("a.txt").apply { writeText("old") }
        BundleFiles.writeIfChanged(f, "new".toByteArray())
        assertEquals("new", f.readText())
        assertFalse(File(tmp.root, "a.txt.tmp").exists())
    }

    private fun assets(vararg entries: Pair<String, String>): (String) -> java.io.InputStream {
        val map = mapOf(*entries)
        return { name -> ByteArrayInputStream((map[name] ?: throw FileNotFoundException(name)).toByteArray()) }
    }

    @Test
    fun `unpack copies every listed file into its folder`() {
        BundleFiles.unpack(
            assets("klipper/a.py" to "A", "klipper/sub/b.py" to "B"),
            listOf("a.py", "sub/b.py"), tmp.root, "klipper"
        )
        assertEquals("A", File(tmp.root, "klipper/a.py").readText())
        assertEquals("B", File(tmp.root, "klipper/sub/b.py").readText())
    }

    @Test
    fun `unpack replaces what was there before`() {
        File(tmp.root, "klipper").mkdirs()
        File(tmp.root, "klipper/stale.py").writeText("old")
        BundleFiles.unpack(assets("klipper/a.py" to "A"), listOf("a.py"), tmp.root, "klipper")
        assertFalse(File(tmp.root, "klipper/stale.py").exists())
        assertTrue(File(tmp.root, "klipper/a.py").exists())
    }

    @Test
    fun `a key missing from the index is removed`() {
        File(tmp.root, "obico").mkdirs()
        File(tmp.root, "obico/x.py").writeText("old")
        BundleFiles.unpack(assets(), null, tmp.root, "obico")
        assertFalse(File(tmp.root, "obico").exists())
    }

    @Test
    fun `binary files are copied byte for byte`() {
        val bytes = byteArrayOf(0, 1, 2, -1, -128, 127)
        BundleFiles.unpack({ ByteArrayInputStream(bytes) }, listOf("lib.so"), tmp.root, "klipper")
        assertArrayEquals(bytes, File(tmp.root, "klipper/lib.so").readBytes())
    }
}
