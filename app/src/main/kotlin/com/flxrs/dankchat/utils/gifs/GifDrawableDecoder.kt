package com.flxrs.dankchat.utils.gifs

import coil.ImageLoader
import coil.decode.*
import coil.fetch.SourceResult
import coil.request.Options

import pl.droidsonroids.gif.GifDrawable

class GifDrawableDecoder(private val source: ImageSource,) : Decoder {

    override suspend fun decode(): DecodeResult {
        val drawable = source.use {
            GifDrawable(it.source().readByteArray())
        }

        return DecodeResult(
            drawable = drawable,
            isSampled = false
        )
    }

    class Factory: Decoder.Factory {

        override fun create(result: SourceResult, options: Options, imageLoader: ImageLoader): Decoder? {
            if (!DecodeUtils.isGif(result.source.source())) return null
            return GifDrawableDecoder(result.source)
        }

        override fun equals(other: Any?) = other is Factory

        override fun hashCode() = javaClass.hashCode()
    }
}