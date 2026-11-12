package com.flxrs.dankchat.preferences.notifications

import com.flxrs.dankchat.preferences.notifications.highlights.BlacklistedUserItem
import com.flxrs.dankchat.preferences.notifications.highlights.MessageHighlightItem
import com.flxrs.dankchat.preferences.notifications.ignores.MessageIgnoreItem
import com.flxrs.dankchat.preferences.notifications.ignores.UserIgnoreItem
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class InvalidRegexTest {
    @Test
    fun `message highlight reports a regex that does not compile`() {
        assertTrue(messageHighlight(pattern = INVALID, isRegex = true).hasInvalidRegex)
        assertFalse(messageHighlight(pattern = VALID, isRegex = true).hasInvalidRegex)
        assertFalse(messageHighlight(pattern = INVALID, isRegex = false).hasInvalidRegex)
    }

    @Test
    fun `blacklisted user reports a regex that does not compile`() {
        assertTrue(BlacklistedUserItem(id = 1, enabled = true, username = INVALID, isRegex = true).hasInvalidRegex)
        assertFalse(BlacklistedUserItem(id = 1, enabled = true, username = VALID, isRegex = true).hasInvalidRegex)
        assertFalse(BlacklistedUserItem(id = 1, enabled = true, username = INVALID, isRegex = false).hasInvalidRegex)
    }

    @Test
    fun `message ignore reports a regex that does not compile`() {
        assertTrue(messageIgnore(pattern = INVALID, isRegex = true).hasInvalidRegex)
        assertFalse(messageIgnore(pattern = VALID, isRegex = true).hasInvalidRegex)
        assertFalse(messageIgnore(pattern = INVALID, isRegex = false).hasInvalidRegex)
    }

    @Test
    fun `user ignore reports a regex that does not compile`() {
        assertTrue(UserIgnoreItem(id = 1, enabled = true, username = INVALID, isRegex = true, isCaseSensitive = false).hasInvalidRegex)
        assertFalse(UserIgnoreItem(id = 1, enabled = true, username = VALID, isRegex = true, isCaseSensitive = false).hasInvalidRegex)
        assertFalse(UserIgnoreItem(id = 1, enabled = true, username = INVALID, isRegex = false, isCaseSensitive = false).hasInvalidRegex)
    }

    private fun messageHighlight(
        pattern: String,
        isRegex: Boolean,
    ) = MessageHighlightItem(
        id = 1,
        enabled = true,
        type = MessageHighlightItem.Type.Custom,
        pattern = pattern,
        isRegex = isRegex,
        isCaseSensitive = false,
        createNotification = false,
        loggedIn = true,
        notificationsEnabled = true,
        customColor = null,
    )

    private fun messageIgnore(
        pattern: String,
        isRegex: Boolean,
    ) = MessageIgnoreItem(
        id = 1,
        type = MessageIgnoreItem.Type.Custom,
        enabled = true,
        pattern = pattern,
        isRegex = isRegex,
        isCaseSensitive = false,
        isBlockMessage = false,
        replacement = "",
    )

    private companion object {
        const val INVALID = "(forsen"
        const val VALID = "(forsen)+"
    }
}
