package com.flxrs.dankchat.chat.suggestion

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.ImageView
import android.widget.TextView
import coil.dispose
import coil.size.Scale
import com.flxrs.dankchat.R
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.getDrawableAndSetSurfaceTint
import com.flxrs.dankchat.utils.extensions.loadImage
import com.flxrs.dankchat.utils.extensions.replaceAll

class SuggestionsArrayAdapter(
    context: Context,
    private val preferenceStore: DankChatPreferenceStore,
    private val onCount: (count: Int) -> Unit
) : ArrayAdapter<Suggestion>(context, R.layout.emote_suggestion_item, R.id.suggestion_text) {
    private val emotes = mutableListOf<Suggestion.EmoteSuggestion>()
    private val users = mutableListOf<Suggestion.UserSuggestion>()
    private val commands = mutableListOf<Suggestion.CommandSuggestion>()
    private val lock = Object()

    fun setSuggestions(suggestions: Triple<List<Suggestion.UserSuggestion>, List<Suggestion.EmoteSuggestion>, List<Suggestion.CommandSuggestion>>) {
        synchronized(lock) {
            users.replaceAll(suggestions.first)
            emotes.replaceAll(suggestions.second)
            commands.replaceAll(suggestions.third)
            val all = suggestions.first + suggestions.second + suggestions.third
            replaceAll(all)
        }
    }

    override fun getCount(): Int = super.getCount().also { onCount(it) }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val textView = view.findViewById<TextView>(R.id.suggestion_text)
        val imageView = view.findViewById<ImageView>(R.id.suggestion_image)

        imageView.dispose()
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
            val userSuggestions = users.filterUsers(constraintString)
            val emoteSuggestions = emotes.filterEmotes(constraintString)
            val commandsSuggestions = commands.filterCommands(constraintString)

            val suggestions = when {
                preferenceStore.shouldPreferEmoteSuggestions -> emoteSuggestions + userSuggestions + commandsSuggestions
                else                                         -> userSuggestions + emoteSuggestions + commandsSuggestions
            }

            return FilterResults().apply {
                values = suggestions
                count = suggestions.size
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            val resultList = (results?.values as? Collection<Suggestion>).orEmpty()
            synchronized(lock) {
                replaceAll(resultList)
            }
            notifyDataSetChanged()
        }
    }

    private fun List<Suggestion.UserSuggestion>.filterUsers(constraint: String): List<Suggestion.UserSuggestion> = mapNotNull { suggestion ->
        when {
            constraint.startsWith('@') -> suggestion.copy(withLeadingAt = true)
            else                       -> suggestion
        }.takeIf { it.toString().startsWith(constraint, ignoreCase = true) }
    }

    private fun List<Suggestion.EmoteSuggestion>.filterEmotes(constraint: String): List<Suggestion.EmoteSuggestion> {
        val exactSuggestions = filter { it.emote.code.contains(constraint) }
        val caseInsensitiveSuggestions = (this - exactSuggestions.toSet()).filter {
            it.emote.code.contains(constraint, ignoreCase = true)
        }
        return exactSuggestions + caseInsensitiveSuggestions
    }

    private fun List<Suggestion.CommandSuggestion>.filterCommands(constraint: String): List<Suggestion.CommandSuggestion> {
        return filter { it.command.startsWith(constraint, ignoreCase = true) }
    }

    private fun replaceAll(list: Collection<Suggestion>) {
        clear()
        addAll(list)
    }
}