package com.flxrs.dankchat.changelog

import com.flxrs.dankchat.BuildConfig

class DankChatVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<DankChatVersion> {

    override fun compareTo(other: DankChatVersion): Int = COMPARATOR.compare(this, other)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DankChatVersion) return false

        if (major != other.major) return false
        if (minor != other.minor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = major
        result = 31 * result + minor
        return result
    }

    fun formattedString(): String = "$major.$minor.$patch"

    companion object {
        private val COMPARATOR = Comparator
            .comparingInt(DankChatVersion::major)
            .thenComparingInt(DankChatVersion::minor)

        fun fromString(version: String): DankChatVersion? {
            return version.split(".")
                .mapNotNull(String::toIntOrNull)
                .takeIf { it.size == 3 }
                ?.let { (major, minor, patch) -> DankChatVersion(major, minor, patch) }
        }

        val CURRENT = fromString(BuildConfig.VERSION_NAME)

    }
}
