package com.flxrs.dankchat.utils.compose

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Returns an action that checks the voice data of the TTS engine. Missing data opens its installation, otherwise [onAvailable] runs.
 * Without an engine that handles the check [onAvailable] runs as well, the player reports the failure then.
 */
@Composable
fun rememberTTSVoiceDataCheck(onAvailable: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnAvailable by rememberUpdatedState(onAvailable)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when (result.resultCode) {
                TextToSpeech.Engine.CHECK_VOICE_DATA_PASS -> {
                    currentOnAvailable()
                }

                else -> {
                    try {
                        context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                    } catch (_: ActivityNotFoundException) {
                        currentOnAvailable()
                    }
                }
            }
        }
    return remember(launcher) {
        {
            try {
                launcher.launch(Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA))
            } catch (_: ActivityNotFoundException) {
                currentOnAvailable()
            }
        }
    }
}
