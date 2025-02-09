package com.flxrs.dankchat.utils.extensions

import android.graphics.drawable.LayerDrawable
import android.text.SpannableString
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.text.getSpans
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil3.load
import coil3.request.ImageRequest
import coil3.request.error
import coil3.request.placeholder
import com.flxrs.dankchat.R
import com.google.android.material.snackbar.Snackbar

fun View.showShortSnackbar(text: String, block: Snackbar.() -> Unit = {}) = Snackbar.make(this, text, Snackbar.LENGTH_SHORT)
    .apply(block)
    .show()

fun View.showLongSnackbar(text: String, block: Snackbar.() -> Unit = {}) = Snackbar.make(this, text, Snackbar.LENGTH_LONG)
    .apply(block)
    .show()

inline fun ImageView.loadImage(
    url: String,
    @DrawableRes placeholder: Int? = R.drawable.ic_missing_emote,
    noinline afterLoad: (() -> Unit)? = null,
    block: ImageRequest.Builder.() -> Unit = {}
) {
    load(url) {
        error(R.drawable.ic_missing_emote)
        placeholder?.let { placeholder(it) }
        afterLoad?.let {
            listener(
                onCancel = { it() },
                onSuccess = { _, _ -> it() },
                onError = { _, _ -> it() }
            )
        }
        block()
    }
}

inline fun <reified T : RecyclerView.ViewHolder> RecyclerView.forEachViewHolder(itemCount: Int, action: (Int, T) -> Unit) {
    for (i in 0..<itemCount) {
        val holder = findViewHolderForAdapterPosition(i) ?: continue
        if (holder is T) {
            action(i, holder)
        }
    }
}

inline fun <reified T : Any> TextView.forEachSpan(action: (T) -> Unit) {
    (text as? SpannableString)
        ?.getSpans<T>()
        .orEmpty()
        .forEach(action)
}

inline fun <reified T : Any> LayerDrawable.forEachLayer(action: (T) -> Unit) {
    for (i in 0..<numberOfLayers) {
        val drawable = getDrawable(i)
        if (drawable is T) {
            action(drawable)
        }
    }
}

val ViewPager2.recyclerView: RecyclerView?
    get() = runCatching {
        when (val view = getChildAt(0)) {
            is RecyclerView -> view
            else            -> null
        }
    }.getOrNull()

fun ViewPager2.reduceDragSensitivity() = runCatching {
    val recyclerView = recyclerView
    val touchSlopField = RecyclerView::class.java.getDeclaredField("mTouchSlop").apply { isAccessible = true }
    val touchSlop = touchSlopField.get(recyclerView) as Int
    touchSlopField.set(recyclerView, touchSlop * 2)
}

fun ViewPager2.disableNestedScrolling() = runCatching {
    val recyclerView = recyclerView
    recyclerView?.isNestedScrollingEnabled = false
    isNestedScrollingEnabled = false
}
