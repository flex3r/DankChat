package com.flxrs.dankchat

import android.app.Application
import androidx.emoji.bundled.BundledEmojiCompatConfig
import androidx.emoji.text.EmojiCompat
import com.jakewharton.threetenabp.AndroidThreeTen
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class DankChatApplication : Application() {
	override fun onCreate() {
		super.onCreate()
		startKoin {
			androidLogger()
			androidContext(this@DankChatApplication)

			modules(appModules)
		}

		AndroidThreeTen.init(this)

		val config = BundledEmojiCompatConfig(this)
		EmojiCompat.init(config)
	}
}