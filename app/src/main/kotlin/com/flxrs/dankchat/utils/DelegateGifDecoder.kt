package com.flxrs.dankchat.utils

import coil.bitmap.BitmapPool
import coil.decode.Decoder
import coil.decode.Options
import coil.size.Size
import okio.BufferedSource
import okio.buffer

class DelegateGifDecoder(private val delegate: Decoder): Decoder by delegate {

    override suspend fun decode(
        pool: BitmapPool,
        source: BufferedSource,
        size: Size,
        options: Options
    ) = delegate.decode(pool, FrameDelayRewritingSource(source).buffer(), size, options)
}