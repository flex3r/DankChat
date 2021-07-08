package com.flxrs.dankchat.utils.extensions

import android.graphics.drawable.Animatable


fun Animatable.setRunning(running: Boolean) = if (running) start() else stop()