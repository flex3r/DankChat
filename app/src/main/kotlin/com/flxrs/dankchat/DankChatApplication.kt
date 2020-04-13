package com.flxrs.dankchat

import android.app.Application
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.jakewharton.threetenabp.AndroidThreeTen
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class DankChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(applicationContext)

            modules(appModules)
        }

        Coil.setDefaultImageLoader {
            ImageLoader(this) {
                componentRegistry {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        add(ImageDecoderDecoder())
                    } else {
                        add(GifDecoder())
                    }
                }
            }
        }

        AndroidThreeTen.init(this)

        val nightMode = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(getString(R.string.preference_dark_theme_key), true)
            .let {
                when {
                    // Force dark theme on < Android 8.1 because of statusbar/navigationbar issues
                    it || Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_NO
                }
            }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}