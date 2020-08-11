package com.flxrs.dankchat.utils

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.widget.AdapterView
import androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView
import com.flxrs.dankchat.chat.suggestion.EmoteSuggestionsArrayAdapter

class CustomMultiAutoCompleteTextView : AppCompatMultiAutoCompleteTextView {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.keyCode == KeyEvent.KEYCODE_BACK) clearFocus()

        return super.onKeyPreIme(keyCode, event)
    }

    fun setSuggestionAdapter(enabled: Boolean, adapter: EmoteSuggestionsArrayAdapter) {
        if (enabled) {
            setAdapter(adapter)
        } else {
            setAdapter(null)
        }
    }

    fun isItemSelected() = this.listSelection != AdapterView.INVALID_POSITION
}