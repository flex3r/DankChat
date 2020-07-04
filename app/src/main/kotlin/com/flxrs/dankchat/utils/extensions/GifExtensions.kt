package com.flxrs.dankchat.utils.extensions

import pl.droidsonroids.gif.GifDrawable

fun GifDrawable.setRunning(running: Boolean) = if (running) start() else stop()