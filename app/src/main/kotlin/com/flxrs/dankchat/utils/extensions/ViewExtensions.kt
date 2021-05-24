package com.flxrs.dankchat.utils.extensions

import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.text.SpannableString
import android.view.View
import android.widget.TextView
import androidx.core.text.getSpans
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar

fun View.showShortSnackbar(text: String, block: Snackbar.() -> Unit = {}) = Snackbar.make(this, text, Snackbar.LENGTH_SHORT)
    .apply(block)
    .show()

fun View.showLongSnackbar(text: String, block: Snackbar.() -> Unit = {}) = Snackbar.make(this, text, Snackbar.LENGTH_LONG)
    .apply(block)
    .show()

inline fun <reified T : RecyclerView.ViewHolder> RecyclerView.forEachViewHolder(itemCount: Int, action: (T) -> Unit) {
    for (i in 0 until itemCount) {
        val holder = findViewHolderForAdapterPosition(i)
        if (holder is T) {
            action(holder)
        }
    }
}

inline fun <reified T : Any> TextView.forEachSpan(action: (T) -> Unit) {
    (text as? SpannableString)
        ?.getSpans<T>()
        .orEmpty()
        .forEach(action)
}

inline fun <reified T : Drawable> LayerDrawable.forEachLayer(action: (T) -> Unit) {
    for (i in 0 until numberOfLayers) {
        val drawable = getDrawable(i)
        if (drawable is T) {
            action(drawable)
        }
    }
}