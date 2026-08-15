package com.eatbefore.core.update

/**
 * A released version, compared the way people read it rather than the way strings sort.
 *
 * The whole point is `1.10.0` being newer than `1.9.0`: as text the tenth release sorts
 * *before* the ninth, and an update check that got that wrong would go quiet exactly when
 * the version numbers finally grew a second digit.
 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int = compareValuesBy(
        this,
        other,
        AppVersion::major,
        AppVersion::minor,
        AppVersion::patch,
    )

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        /**
         * Reads `1.9.0`, `v1.9.0` and `1.9` alike; anything else is null rather than a
         * guess — a version we cannot read must not be announced as an update.
         */
        fun parse(raw: String?): AppVersion? {
            val text = raw?.trim()?.removePrefix("v")?.takeIf { it.isNotEmpty() } ?: return null
            val parts = text.split('.', limit = PART_LIMIT)
            val numbers = parts.map { part ->
                part.takeWhile { it.isDigit() }.toIntOrNull() ?: return null
            }
            if (numbers.isEmpty()) return null
            return AppVersion(
                major = numbers[0],
                minor = numbers.getOrElse(1) { 0 },
                patch = numbers.getOrElse(2) { 0 },
            )
        }

        private const val PART_LIMIT = 3
    }
}
