package com.flxrs.dankchat.chat.suggestion

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import coil.api.load
import com.flxrs.dankchat.R
import com.flxrs.dankchat.service.twitch.emote.GenericEmote

class EmoteSuggestionsArrayAdapter(
    context: Context,
    list: List<GenericEmote>,
    private val onCount: (count: Int) -> Unit
) :
    ArrayAdapter<GenericEmote>(
        context,
        R.layout.emote_suggestion_item,
        R.id.suggestion_text,
        list
    ) {
    override fun getCount(): Int = super.getCount().also { onCount(it) }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val textView = view.findViewById<TextView>(R.id.suggestion_text)
        val imageView = view.findViewById<ImageView>(R.id.suggestion_image)

        imageView.setImageDrawable(null)

        getItem(position)?.let { emote ->
            imageView.load(emote.url) {
                size(textView.lineHeight * 2)
                placeholder(R.drawable.ic_missing_emote)
                error(R.drawable.ic_missing_emote)
            }
        }

        return view
    }
}