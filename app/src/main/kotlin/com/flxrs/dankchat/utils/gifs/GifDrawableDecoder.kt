package com.flxrs.dankchat.utils.gifs

import coil.bitmap.BitmapPool
import coil.decode.DecodeResult
import coil.decode.DecodeUtils
import coil.decode.Decoder
import coil.decode.Options
import coil.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSource
import pl.droidsonroids.gif.GifDrawable

class GifDrawableDecoder : Decoder {

    override fun handles(source: BufferedSource, mimeType: String?): Boolean {
        return DecodeUtils.isGif(source)
    }

    override suspend fun decode(pool: BitmapPool, source: BufferedSource, size: Size, options: Options): DecodeResult = withContext(Dispatchers.Default) {
        val drawable = source.use {
            GifDrawable(it.readByteArray())
        }

        DecodeResult(
            drawable = drawable,
            isSampled = false
        )
    }
}