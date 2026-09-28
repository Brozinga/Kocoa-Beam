package ru.ytkab0bp.beamklipper.update

// Klipper/Moonraker: informational only, no update button ever possible (see
// docs/klipper-vendor-update-procedure — native chelper needs recompiling,
// and the Android source patches are literal String.replace() calls that
// silently no-op if upstream text drifts). "bundled" is a short commit SHA,
// or null if the local marker is missing/unreadable.
data class VersionStatus(
    val bundled: String?,
    val latest: String?,
    val checkedAtMs: Long,
    val error: Boolean = false,
)

// Fluidd/Mainsail/Voyager-UI: "active" is whichever version is actually being
// served right now (an in-app override if one was ever installed, else the
// version baked into the APK).
data class FrontendVersionStatus(
    val active: String,
    val latest: String?,
    val checkedAtMs: Long,
    val error: Boolean = false,
)

object VersionCompare {
    // GitHub's releases/latest is trusted as "the newest" outright — no
    // semver parsing to get wrong, just "is it different from what we have."
    // A failed/unknown check (latest == null) never claims an update exists.
    fun tagDiffers(active: String, latest: String?): Boolean =
        latest != null && latest != active

    // Klipper/Moonraker: bundled is a short SHA, latest is the full SHA the
    // commits API returns. A missing/unknown bundled marker never claims the
    // component is behind — there is nothing to compare against.
    fun commitDiffers(bundledShortSha: String?, latestFullSha: String?): Boolean =
        !bundledShortSha.isNullOrBlank() &&
            !latestFullSha.isNullOrBlank() &&
            !latestFullSha.startsWith(bundledShortSha)
}
