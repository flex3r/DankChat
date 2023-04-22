package com.flxrs.dankchat.utils.extensions

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.LayerDrawable
import android.text.SpannableString
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.text.getSpans
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
import coil.request.ImageRequest
import com.flxrs.dankchat.R
import com.flxrs.dankchat.main.MainActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar

fun View.showShortSnackbar(text: String, block: Snackbar.() -> Unit = {}) = Snackbar.make(this, text, Snackbar.LENGTH_SHORT)
    .apply(block)
    .show()

fun View.showLongSnackbar(text: String, block: Snackbar.() -> Unit = {}) = Snackbar.make(this, text, Snackbar.LENGTH_LONG)
    .apply(block)
    .show()

fun View.showRestartRequired() {
    showLongSnackbar(context.getString(R.string.restart_required)) {
        setAction(R.string.restart) {
            // KKona
            val restartIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(restartIntent)
            Runtime.getRuntime().exit(0)
        }
    }
}

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
    for (i in 0 until itemCount) {
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
    for (i in 0 until numberOfLayers) {
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

fun MaterialButton.setEnabledColor(@ColorInt color: Int) {
    setTextColor(
        ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_enabled),
                intArrayOf(-android.R.attr.state_enabled),
            ),
            intArrayOf(
                color,
                MaterialColors.layer(
                    MaterialColors.getColor(this, R.attr.colorSurface),
                    MaterialColors.getColor(this, R.attr.colorOnSurface),
                    MaterialColors.ALPHA_DISABLED,
                )
            )
        )
    )
}
