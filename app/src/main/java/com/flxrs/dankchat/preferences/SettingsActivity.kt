package com.flxrs.dankchat.preferences

import android.os.Bundle
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.SettingsActivityBinding

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataBindingUtil.setContentView<SettingsActivityBinding>(this, R.layout.settings_activity).apply {
            setSupportActionBar(settingsToolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowHomeEnabled(true)
        }
        PreferenceManager.setDefaultValues(this, R.xml.settings, false)
    }

    override fun onSupportNavigateUp() = true.also { finish() }

    override fun onBackPressed() = finish()
}