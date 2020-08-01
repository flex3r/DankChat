package com.flxrs.dankchat.chat.suggestion

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import coil.api.clear
import coil.api.load
import com.flxrs.dankchat.R
import com.flxrs.dankchat.utils.extensions.dp
import pl.droidsonroids.gif.GifImageView

class EmoteSuggestionsArrayAdapter(context: Context, private val onCount: (count: Int) -> Unit) : ArrayAdapter<Suggestion>(context, R.layout.emote_suggestion_item, R.id.suggestion_text) {
    override fun getCount(): Int = super.getCount().also { onCount(it) }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val textView = view.findViewById<TextView>(R.id.suggestion_text)
        val imageView = view.findViewById<GifImageView>(R.id.suggestion_image)

        imageView.clear()
        imageView.setImageDrawable(null)
        getItem(position)?.let { suggestion: Suggestion ->
            when (suggestion) {
                is Suggestion.EmoteSuggestion -> {
                    imageView.load(suggestion.emote.url) {
                        size(textView.lineHeight * 2)
                        placeholder(R.drawable.ic_missing_emote)
                        error(R.drawable.ic_missing_emote)
                    }
                }
                is Suggestion.UserSuggestion -> {
                    val drawable = context.getDrawable(R.drawable.ic_notification_icon)?.apply {
                        DrawableCompat.setTint(this, ContextCompat.getColor(context, R.color.color_on_surface))
                    }
                    imageView.setImageDrawable(drawable)
                }
            }
        }

        return view
    }
}