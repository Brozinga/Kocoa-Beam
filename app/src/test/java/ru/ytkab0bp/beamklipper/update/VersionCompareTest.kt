package ru.ytkab0bp.beamklipper.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {
    @Test
    fun `tagDiffers is false for identical tags`() {
        assertFalse(VersionCompare.tagDiffers("v1.37.5", "v1.37.5"))
    }

    @Test
    fun `tagDiffers is true for different tags`() {
        assertTrue(VersionCompare.tagDiffers("v1.37.5", "v1.38.0"))
    }

    @Test
    fun `tagDiffers is false when the remote check failed`() {
        // A failed check (latest == null) must never claim an update exists.
        assertFalse(VersionCompare.tagDiffers("v1.37.5", null))
    }

    @Test
    fun `commitDiffers is false when the bundled marker is unknown`() {
        assertFalse(VersionCompare.commitDiffers(null, "abcdef0123456789"))
        assertFalse(VersionCompare.commitDiffers("", "abcdef0123456789"))
    }

    @Test
    fun `commitDiffers is false when the remote check failed`() {
        assertFalse(VersionCompare.commitDiffers("abcdef0", null))
    }

    @Test
    fun `commitDiffers is false when the full sha starts with the bundled short sha`() {
        assertFalse(VersionCompare.commitDiffers("3a1f884d", "3a1f884dc83c44714bfc93238525cb57f4474588"))
    }

    @Test
    fun `commitDiffers is true when the shas disagree`() {
        assertTrue(VersionCompare.commitDiffers("3a1f884d", "1cfb0c41e468645951a371621f06d32777b6107c"))
    }
}
