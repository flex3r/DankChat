package com.flxrs.dankchat.preferences.overview

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import org.koin.compose.koinInject

@LayoutScopeMarker
interface SecretDankerScope {
    fun Modifier.dankClickable(): Modifier
}

@Composable
fun SecretDankerModeTrigger(content: @Composable SecretDankerScope.() -> Unit) {
    if (LocalInspectionMode.current) {
        val scope = object : SecretDankerScope {
            override fun Modifier.dankClickable(): Modifier = this
        }
        content(scope)
        return
    }

    val preferences = koinInject<DankChatPreferenceStore>()
    var secretDankerMode by remember { mutableStateOf(preferences.isSecretDankerModeEnabled) }
    var lastToast by remember { mutableStateOf<Toast?>(null) }
    var currentClicks by remember { mutableIntStateOf(0) }
    val scope = remember {
        object : SecretDankerScope {
            override fun Modifier.dankClickable() = clickable(
                enabled = !secretDankerMode,
                onClick = { currentClicks++ },
                interactionSource = null,
                indication = null,
            )
        }
    }
    val context = LocalContext.current
    if (!secretDankerMode) {
        val clicksNeeded = preferences.secretDankerModeClicks
        LaunchedEffect(currentClicks) {
            lastToast?.cancel()
            when (currentClicks) {
                in 2..<clicksNeeded -> {
                    val remaining = clicksNeeded - currentClicks
                    lastToast = Toast
                        .makeText(context, "$remaining click(s) left to enable secret danker mode", Toast.LENGTH_SHORT)
                        .apply { show() }
                }

                clicksNeeded        -> {
                    Toast
                        .makeText(context, "Secret danker mode enabled", Toast.LENGTH_SHORT)
                        .show()
                    lastToast = null
                    secretDankerMode = true
                    preferences.isSecretDankerModeEnabled = true
                }
            }
        }
    }
    content(scope)
}
