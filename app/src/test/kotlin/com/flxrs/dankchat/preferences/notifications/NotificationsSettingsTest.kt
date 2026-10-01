package com.flxrs.dankchat.preferences.notifications

import com.flxrs.dankchat.data.toUserName
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class NotificationsSettingsTest {
    @Test
    fun `channel notifications can be disabled and enabled`() {
        val disabled = NotificationsSettings().withChannelNotificationsEnabled("forsen".toUserName(), enabled = false)

        assertFalse(disabled.areChannelNotificationsEnabled("forsen".toUserName()))
        assertTrue(disabled.areChannelNotificationsEnabled("iore".toUserName()))

        val enabled = disabled.withChannelNotificationsEnabled("forsen".toUserName(), enabled = true)
        assertTrue(enabled.areChannelNotificationsEnabled("forsen".toUserName()))
    }
}
