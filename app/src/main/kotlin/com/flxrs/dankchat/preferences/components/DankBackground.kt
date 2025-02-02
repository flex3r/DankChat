package com.flxrs.dankchat.preferences.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.flxrs.dankchat.R

@Composable
fun DankBackground(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring()) + scaleIn(spring()),
        exit = scaleOut(spring()) + fadeOut(spring()),
    ) {
        val dank = painterResource(R.drawable.ic_dank_chat_mono)
        Box(Modifier.fillMaxSize()) {
            Icon(
                tint = MaterialTheme.colorScheme.inverseOnSurface,
                painter = dank,
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
                contentDescription = null,
            )
        }
    }
}
