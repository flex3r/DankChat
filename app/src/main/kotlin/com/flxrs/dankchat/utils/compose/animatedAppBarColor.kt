package com.flxrs.dankchat.utils.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

@Composable
fun animatedAppBarColor(scrollBehavior: TopAppBarScrollBehavior): State<Color> {
    val colors = TopAppBarDefaults.topAppBarColors()
    val targetColor by remember(colors, scrollBehavior) {
        derivedStateOf {
            val overlappingFraction = scrollBehavior.state.overlappedFraction
            lerp(
                colors.containerColor,
                colors.scrolledContainerColor,
                FastOutLinearInEasing.transform(if (overlappingFraction > 0.01f) 1f else 0f)
            )
        }
    }
    return animateColorAsState(targetColor, spring(dampingRatio = 1f, stiffness = 1600f))
}
