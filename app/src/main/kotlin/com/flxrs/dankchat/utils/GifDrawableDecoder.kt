package com.flxrs.dankchat.utils

import coil.bitmappool.BitmapPool
import coil.decode.DecodeResult
import coil.decode.DecodeUtils
import coil.decode.Decoder
import coil.decode.Options
import coil.size.Size
import okio.BufferedSource
import pl.droidsonroids.gif.GifDrawable

class GifDrawableDecoder : Decoder {

    override fun handles(source: BufferedSource, mimeType: String?): Boolean {
        return DecodeUtils.isGif(source)
    }

    override suspend fun decode(pool: BitmapPool, source: BufferedSource, size: Size, options: Options): DecodeResult {
        val drawable = source.use {
            GifDrawable(it.readByteArray())
        }

        return DecodeResult(
            drawable = drawable,
            isSampled = false
        )
    }
}
