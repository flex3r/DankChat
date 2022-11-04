package com.flxrs.dankchat.preferences.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.SecretDankerModeTrigger

class SecretDankerModePreferenceCategory @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.preferenceCategoryStyle,
    defStyleRes: Int = 0
) : PreferenceCategory(context, attrs, defStyleAttr, defStyleRes) {

    private val dankChatPreferenceStore = DankChatPreferenceStore(context)

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        if (!dankChatPreferenceStore.isSecretDankerModeEnabled) {
            val textView = holder.findViewById(android.R.id.title) as? TextView ?: return
            textView.setBackgroundColor(Color.TRANSPARENT)
            textView.setOnClickListener(SecretDankerModeTrigger(dankChatPreferenceStore))
        }
    }
}