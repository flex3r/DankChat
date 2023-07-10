package com.flxrs.dankchat.changelog

import com.flxrs.dankchat.BuildConfig

data class DankChatVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<DankChatVersion> {

    override fun compareTo(other: DankChatVersion): Int = COMPARATOR.compare(this, other)

    fun formattedString(): String = "$major.$minor.$patch"

    companion object {
        private val CURRENT = fromString(BuildConfig.VERSION_NAME)!!
        private val COMPARATOR = Comparator
            .comparingInt(DankChatVersion::major)
            .thenComparingInt(DankChatVersion::minor)
            .thenComparingInt(DankChatVersion::patch)

        fun fromString(version: String): DankChatVersion? {
            return version.split(".")
                .mapNotNull(String::toIntOrNull)
                .takeIf { it.size == 3 }
                ?.let { (major, minor, patch) -> DankChatVersion(major, minor, patch) }
        }

        val LATEST_CHANGELOG = DankChatChangelog.entries.findLast { CURRENT >= it.version }
        val HAS_CHANGELOG = LATEST_CHANGELOG != null
    }
}
