package com.flxrs.dankchat.main

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.KeyEvent
import android.widget.AdapterView
import androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView
import com.flxrs.dankchat.chat.suggestion.SuggestionsArrayAdapter

class DankChatInput : AppCompatMultiAutoCompleteTextView {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M && event?.keyCode == KeyEvent.KEYCODE_BACK) {
            clearFocus()
        }

        return super.onKeyPreIme(keyCode, event)
    }


    fun setSuggestionAdapter(enabled: Boolean, adapter: SuggestionsArrayAdapter) {
        if (enabled) {
            setAdapter(adapter)
        } else {
            setAdapter(null)
        }
    }

    fun isItemSelected() = this.listSelection != AdapterView.INVALID_POSITION
}