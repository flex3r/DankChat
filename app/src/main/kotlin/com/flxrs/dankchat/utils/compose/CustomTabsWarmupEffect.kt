package com.flxrs.dankchat.utils.compose

import android.content.ComponentName
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/** Starts the default Custom Tabs browser while this is in composition, so the first tab opens without its cold start. */
@Composable
fun CustomTabsWarmupEffect() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val connection =
            object : CustomTabsServiceConnection() {
                override fun onCustomTabsServiceConnected(
                    name: ComponentName,
                    client: CustomTabsClient,
                ) {
                    client.warmup(0L)
                }

                override fun onServiceDisconnected(name: ComponentName) = Unit
            }
        val packageName = CustomTabsClient.getPackageName(context, null)
        val isBound = packageName != null && CustomTabsClient.bindCustomTabsService(context, packageName, connection)
        onDispose {
            if (isBound) {
                context.unbindService(connection)
            }
        }
    }
}
