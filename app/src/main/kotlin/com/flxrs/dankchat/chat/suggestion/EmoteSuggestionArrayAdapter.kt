package com.flxrs.dankchat.chat.suggestion

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.ImageView
import android.widget.TextView
import coil.clear
import coil.size.Scale
import com.flxrs.dankchat.R
import com.flxrs.dankchat.utils.extensions.getDrawableAndSetSurfaceTint
import com.flxrs.dankchat.utils.extensions.loadImage
import com.flxrs.dankchat.utils.extensions.replaceAll

class EmoteSuggestionsArrayAdapter(
    context: Context,
    private val onCount: (count: Int) -> Unit
) : ArrayAdapter<Suggestion>(context, R.layout.emote_suggestion_item, R.id.suggestion_text) {
    private val items = mutableListOf<Suggestion>()

    fun setSuggestions(list: List<Suggestion>) {
        setNotifyOnChange(false)
        items.replaceAll(list)
        replaceAll(list)
    }

    override fun getCount(): Int = super.getCount().also { onCount(it) }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val textView = view.findViewById<TextView>(R.id.suggestion_text)
        val imageView = view.findViewById<ImageView>(R.id.suggestion_image)

        imageView.clear()
        imageView.setImageDrawable(null)
        getItem(position)?.let { suggestion: Suggestion ->
            when (suggestion) {
                is Suggestion.EmoteSuggestion   -> imageView.loadImage(suggestion.emote.url) {
                    scale(Scale.FIT)
                    size(textView.lineHeight * 2)
                }
                is Suggestion.UserSuggestion    -> {
                    textView.text = suggestion.name
                    imageView.setImageDrawable(context.getDrawableAndSetSurfaceTint(R.drawable.ic_notification_icon))
                }
                is Suggestion.CommandSuggestion -> imageView.setImageDrawable(context.getDrawableAndSetSurfaceTint(R.drawable.ic_android))
            }
        }

        return view
    }

    override fun getFilter(): Filter = filter

    private val filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            constraint ?: return FilterResults()
            val constraintString = constraint.toString()
            val suggestions = items.mapNotNull { suggestion ->
                when (suggestion) {
                    is Suggestion.CommandSuggestion -> suggestion.takeIf { it.command.startsWith(constraintString, ignoreCase = true) }
                    is Suggestion.EmoteSuggestion   -> suggestion.takeIf { it.emote.code.contains(constraintString, ignoreCase = true) }
                    is Suggestion.UserSuggestion    -> {
                        val (userSuggestion, userConstraint) = when {
                            constraintString.startsWith('@') -> suggestion.copy(withLeadingAt = true) to constraintString.substringAfter('@')
                            else                             -> suggestion to constraintString
                        }

                        userSuggestion.takeIf { it.name.startsWith(userConstraint, ignoreCase = true) }
                    }
                }
            }
            return FilterResults().apply {
                values = suggestions
                count = suggestions.size
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            val resultList = (results?.values as? List<Suggestion>).orEmpty()
            replaceAll(resultList)

            if (resultList.isEmpty()) {
                notifyDataSetInvalidated()
            } else {
                notifyDataSetChanged()
            }
        }
    }

    private fun replaceAll(list: List<Suggestion>) {
        clear()
        addAll(list)
    }
}