package com.flxrs.dankchat.preferences

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.preference.PreferenceManager
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.SettingsActivityBinding

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataBindingUtil.setContentView<SettingsActivityBinding>(this, R.layout.settings_activity)
            .apply {
                setSupportActionBar(settingsToolbar)
                supportActionBar?.apply {
                    setDisplayHomeAsUpEnabled(true)
                    title = getString(R.string.settings)
                }
            }
        PreferenceManager.setDefaultValues(this, R.xml.settings, false)
    }

    override fun onSupportNavigateUp() = true.also { finish() }

    override fun onBackPressed() = finish()
}