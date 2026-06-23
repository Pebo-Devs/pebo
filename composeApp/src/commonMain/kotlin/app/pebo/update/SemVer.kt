package app.pebo.update

/**
 * Minimal semantic-version value for release gating. Parses up to three dot-separated numeric
 * components (`1`, `1.2`, `1.2.3`), ignoring a leading `v`/`V` and any `-prerelease` / `+build`
 * suffix. Missing components are treated as 0, so `1.0` == `1.0.0`.
 */
internal data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int =
        compareValuesBy(this, other, SemVer::major, SemVer::minor, SemVer::patch)

    companion object {
        fun parse(raw: String): SemVer? {
            val core = raw.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore('-')
                .substringBefore('+')
                .trim()
            if (core.isEmpty()) return null
            val nums = core.split('.').map { it.toIntOrNull() ?: return null }
            if (nums.isEmpty()) return null
            return SemVer(
                major = nums.getOrElse(0) { 0 },
                minor = nums.getOrElse(1) { 0 },
                patch = nums.getOrElse(2) { 0 },
            )
        }
    }
}

/** True when [latest] is a strictly newer version than [current]; false if either can't be parsed. */
internal fun isNewerVersion(current: String, latest: String): Boolean {
    val currentVer = SemVer.parse(current) ?: return false
    val latestVer = SemVer.parse(latest) ?: return false
    return latestVer > currentVer
}
