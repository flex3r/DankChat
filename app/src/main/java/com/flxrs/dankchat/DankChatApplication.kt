package com.flxrs.dankchat

import android.app.Application
import com.jakewharton.threetenabp.AndroidThreeTen
import org.koin.android.ext.android.startKoin

class DankChatApplication : Application() {
	override fun onCreate() {
		super.onCreate()
		startKoin(this, listOf(appModules))
		AndroidThreeTen.init(this)
	}
}