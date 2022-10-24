package com.flxrs.dankchat.main

import android.content.Context
import android.util.AttributeSet
import android.widget.AdapterView
import androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView
import com.flxrs.dankchat.chat.suggestion.SuggestionsArrayAdapter

class DankChatInput : AppCompatMultiAutoCompleteTextView {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    fun setSuggestionAdapter(enabled: Boolean, adapter: SuggestionsArrayAdapter) {
        if (enabled) {
            setAdapter(adapter)
        } else {
            setAdapter(null)
        }
    }

    fun isItemSelected() = this.listSelection != AdapterView.INVALID_POSITION
}