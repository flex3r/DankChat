package com.flxrs.dankchat.preferences

import android.view.View
import android.widget.Toast

class SecretDankerModeTrigger(
    private val dankChatPreferenceStore: DankChatPreferenceStore
) : View.OnClickListener {

    private var currentClicks = 0
    private var lastToast: Toast? = null

    override fun onClick(v: View) {
        lastToast?.cancel()
        currentClicks++
        when (currentClicks) {
            in 2..<SECRET_DANKER_MODE_CLICKS -> {
                val clicksNeeded = SECRET_DANKER_MODE_CLICKS - currentClicks
                lastToast = Toast
                    .makeText(v.context, "$clicksNeeded click(s) left to enable secret danker mode", Toast.LENGTH_SHORT)
                    .apply { show() }
            }

            SECRET_DANKER_MODE_CLICKS            -> {
                Toast
                    .makeText(v.context, "Secret danker mode enabled", Toast.LENGTH_SHORT)
                    .show()
                dankChatPreferenceStore.isSecretDankerModeEnabled = true
                v.isClickable = false
                v.isFocusable = false
            }
        }
    }

    companion object {
        private const val SECRET_DANKER_MODE_CLICKS = 5
    }

}
