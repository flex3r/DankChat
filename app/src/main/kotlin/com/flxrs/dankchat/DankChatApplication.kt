package com.flxrs.dankchat

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.provider.FontRequest
import androidx.emoji.text.EmojiCompat
import androidx.emoji.text.FontRequestEmojiCompatConfig
import androidx.preference.PreferenceManager
import coil.Coil
import coil.ImageLoader
import coil.util.CoilUtils
import com.flxrs.dankchat.di.EmoteOkHttpClient
import com.flxrs.dankchat.utils.GifDrawableDecoder
import dagger.hilt.android.HiltAndroidApp
import okhttp3.CacheControl
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class DankChatApplication : Application()/*, ImageLoaderFactory*/ {
    @Inject
    @EmoteOkHttpClient
    lateinit var client: OkHttpClient

    override fun onCreate() {
        super.onCreate()
        Coil.setDefaultImageLoader {
            ImageLoader(this) {
                okHttpClient { client }
                componentRegistry {
                    add(GifDrawableDecoder())
                }
            }
        }

        val nightMode = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(getString(R.string.preference_dark_theme_key), true)
            .let { darkMode ->
                when {
                    // Force dark theme on < Android 8.1 because of statusbar/navigationbar issues
                    darkMode || Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_NO
                }
            }
        AppCompatDelegate.setDefaultNightMode(nightMode)

        val fontRequest = FontRequest(
            "com.google.android.gms.fonts",
            "com.google.android.gms",
            "Noto Color Emoji Compat",
            R.array.com_google_android_gms_fonts_certs
        )
        val config = FontRequestEmojiCompatConfig(applicationContext, fontRequest)
            .registerInitCallback(object : EmojiCompat.InitCallback() {
                override fun onInitialized() {
                    Log.i(TAG, "EmojiCompat initialized")
                }

                override fun onFailed(throwable: Throwable?) {
                    Log.e(TAG, "EmojiCompat initialization failed", throwable)
                }
            })
        EmojiCompat.init(config)
    }

//    override fun newImageLoader(): ImageLoader {
//        return ImageLoader.Builder(this)
//            .okHttpClient {
//                OkHttpClient.Builder()
//                    .cache(CoilUtils.createDefaultCache(this))
//                    .build()
//            }
//            .componentRegistry {
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//                    add(ImageDecoderDecoder())
//                } else {
//                    add(GifDecoder())
//                }
//            }
//            .build()
//    }

    companion object {
        private val TAG = DankChatApplication::class.java.simpleName
    }
}