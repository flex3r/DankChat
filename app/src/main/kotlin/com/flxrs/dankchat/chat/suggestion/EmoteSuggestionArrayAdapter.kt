package com.flxrs.dankchat.chat.suggestion

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import coil.api.clear
import coil.api.load
import com.flxrs.dankchat.R
import com.flxrs.dankchat.utils.extensions.getDrawableAndSetSurfaceTint
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
                is Suggestion.UserSuggestion -> imageView.setImageDrawable(context.getDrawableAndSetSurfaceTint(R.drawable.ic_notification_icon))
                is Suggestion.CommandSuggestion -> imageView.setImageDrawable(context.getDrawableAndSetSurfaceTint(R.drawable.ic_baseline_android_24))
            }
        }

        return view
    }
}