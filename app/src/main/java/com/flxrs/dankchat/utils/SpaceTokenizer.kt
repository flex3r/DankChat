package com.flxrs.dankchat.utils

import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.widget.MultiAutoCompleteTextView


class SpaceTokenizer : MultiAutoCompleteTextView.Tokenizer {
	private val separator: Char = ' '

	override fun findTokenEnd(text: CharSequence?, cursor: Int): Int {
		if (text.isNullOrBlank()) return 0

		var i = cursor
		while (i < text.length) if (text[i] == separator) return i else i++

		return text.length
	}

	override fun findTokenStart(text: CharSequence?, cursor: Int): Int {
		if (text.isNullOrBlank()) return 0

		var i = cursor
		while (i > 0 && text[i - 1] != separator) i--
		while (i < cursor && text[i] == separator) i++

		return i
	}

	override fun terminateToken(text: CharSequence): CharSequence {
		var i = text.length

		while (i > 0 && text[i - 1] == separator) i--

		return if (i > 0 && text[i - 1] == separator) text
		else {
			if (text is Spanned) {
				val sp = SpannableString(text.toString() + separator.toString())
				TextUtils.copySpansFrom(text, 0, text.length, Any::class.java, sp, 0)
				sp
			} else {
				text.toString() + separator
			}
		}
	}
}