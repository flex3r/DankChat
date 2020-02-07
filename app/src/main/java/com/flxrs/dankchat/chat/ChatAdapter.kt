package com.flxrs.dankchat.chat

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ImageSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.text.bold
import androidx.core.text.color
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.Coil
import coil.api.get
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.ChatItemBinding
import com.flxrs.dankchat.service.twitch.emote.ChatMessageEmote
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import com.flxrs.dankchat.utils.extensions.normalizeColor
import com.linkedin.urls.detection.UrlDetector
import com.linkedin.urls.detection.UrlDetectorOptions
import kotlinx.coroutines.*
import pl.droidsonroids.gif.MultiCallback
import kotlin.math.roundToInt

class ChatAdapter(
    private val onListChanged: (position: Int) -> Unit,
    private val onUserClicked: (user: String) -> Unit,
    private val onMessageLongClick: (message: String) -> Unit
) : ListAdapter<ChatItem, ChatAdapter.ViewHolder>(DetectDiff()) {

    private val gifCallback = MultiCallback(true)

    inner class ViewHolder(val binding: ChatItemBinding) : RecyclerView.ViewHolder(binding.root) {
        val scope = CoroutineScope(Dispatchers.Main.immediate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ChatItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onCurrentListChanged(
        previousList: MutableList<ChatItem>,
        currentList: MutableList<ChatItem>
    ) {
        onListChanged(currentList.size - 1)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.scope.coroutineContext.cancelChildren()
        holder.binding.executePendingBindings()
        super.onViewRecycled(holder)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        with(holder.binding.itemText) {
            with(getItem(position).message) {
                isClickable = false
                text = ""
                alpha = 1.0f
                movementMethod = LinkMovementMethod.getInstance()
                val darkModePreferenceKey = context.getString(R.string.preference_dark_theme_key)
                val timedOutPreferenceKey =
                    context.getString(R.string.preference_show_timed_out_messages_key)
                val timestampPreferenceKey = context.getString(R.string.preference_timestamp_key)
                val preferences = PreferenceManager.getDefaultSharedPreferences(context)
                val isDarkMode = preferences.getBoolean(darkModePreferenceKey, true)
                val showTimedOutMessages = preferences.getBoolean(timedOutPreferenceKey, true)
                val showTimeStamp = preferences.getBoolean(timestampPreferenceKey, true)
                holder.scope.launch {
                    if (timedOut) {
                        alpha = 0.5f

                        if (!showTimedOutMessages) {
                            text = if (showTimeStamp) {
                                "$time ${context.getString(R.string.timed_out_message)}"
                            } else context.getString(R.string.timed_out_message)
                            return@launch
                        }
                    }

                    var ignoreClicks = false
                    if (!isSystem) setOnLongClickListener {
                        ignoreClicks = true
                        onMessageLongClick(message)
                        true
                    }

                    setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_UP) {
                            CoroutineScope(Dispatchers.Default).launch {
                                delay(200)
                                ignoreClicks = false
                            }
                        }
                        false
                    }

                    val scaleFactor = lineHeight * 1.5 / 112

                    val background = when {
                        isNotify -> if (isDarkMode) R.color.color_highlight_dark else R.color.color_highlight_light
                        isMention -> if (isDarkMode) R.color.color_mention_dark else R.color.color_mention_light
                        else -> android.R.color.transparent
                    }
                    setBackgroundResource(background)

                    val fullName = when {
                        displayName.equals(name, true) -> displayName
                        else -> "$name($displayName)"
                    }

                    val fullDisplayName = when {
                        isAction -> "$fullName "
                        fullName.isBlank() -> ""
                        else -> "$fullName: "
                    }

                    val badgesLength = badges.size * 2
                    val (prefixLength, spannable) = if (showTimeStamp) {
                        time.length + 1 + fullDisplayName.length to SpannableStringBuilder().bold {
                            append(
                                "$time "
                            )
                        }
                    } else {
                        fullDisplayName.length to SpannableStringBuilder()
                    }

                    badges.forEach { badge ->
                        spannable.append("  ")
                        val start = spannable.length - 2
                        val end = spannable.length - 1
                        Coil.get(badge.url).apply {
                            val width =
                                (lineHeight * intrinsicWidth / intrinsicHeight.toFloat()).roundToInt()
                            setBounds(0, 0, width, lineHeight)

                            val imageSpan = ImageSpan(this, ImageSpan.ALIGN_BOTTOM)
                            spannable.setSpan(
                                imageSpan,
                                start,
                                end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                    }

                    val normalizedColor = color.normalizeColor(isDarkMode)
                    spannable.bold { color(normalizedColor) { append(fullDisplayName) } }
                    text = spannable

                    if (isAction) {
                        spannable.color(normalizedColor) { append(message) }
                    } else {
                        spannable.append(message)
                    }

                    //clicking usernames
                    if (fullName.isNotBlank()) {
                        val userClickableSpan = object : ClickableSpan() {
                            override fun updateDrawState(ds: TextPaint) {
                                ds.isUnderlineText = false
                                ds.color = normalizedColor
                            }

                            override fun onClick(v: View) {
                                if (!ignoreClicks) onUserClicked(name)
                            }
                        }
                        spannable.setSpan(
                            userClickableSpan,
                            prefixLength - fullDisplayName.length + badgesLength,
                            prefixLength + badgesLength,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }

                    //links
                    UrlDetector(message, UrlDetectorOptions.Default).detect().forEach { url ->
                        val clickableSpan = object : ClickableSpan() {
                            override fun onClick(v: View) {
                                try {
                                    if (!ignoreClicks)
                                        androidx.browser.customtabs.CustomTabsIntent.Builder()
                                            .addDefaultShareMenuItem()
                                            .setShowTitle(true)
                                            .build().launchUrl(v.context, Uri.parse(url.fullUrl))
                                } catch (e: ActivityNotFoundException) {
                                    Log.e("ViewBinding", Log.getStackTraceString(e))
                                }
                            }
                        }
                        val start = prefixLength + badgesLength + message.indexOf(url.originalUrl)
                        val end = start + url.originalUrl.length
                        spannable.setSpan(
                            clickableSpan,
                            start,
                            end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }

                    if (emotes.filter { it.isGif }.count() > 0) {
                        gifCallback.addView(holder.binding.itemText)
                    }

                    if (emotes.size > 5) {
                        text = spannable
                    }

                    val fullPrefix = prefixLength + badgesLength
                    emotes.forEach { e ->
                        val emoteDrawable = Coil.get(e.url)
                        if (emoteDrawable is Animatable) {
                            emoteDrawable.callback = gifCallback
                            emoteDrawable.start()
                        }
                        emoteDrawable.transformEmoteDrawable(scaleFactor, e)
                        setEmoteSpans(e, fullPrefix, emoteDrawable, spannable)
                    }
                    text = spannable
                }
            }
        }
    }

    private fun setEmoteSpans(
        e: ChatMessageEmote,
        prefix: Int,
        drawable: Drawable,
        spannableStringBuilder: SpannableStringBuilder
    ) {
        e.positions.forEach { pos ->
            val (start, end) = pos.split('-').map { it.toInt() + prefix }
            try {
                spannableStringBuilder.setSpan(
                    ImageSpan(drawable),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_INCLUSIVE
                )
            } catch (t: Throwable) {
                Log.e(
                    "ViewBinding",
                    "$start $end ${e.code} ${spannableStringBuilder.length}"
                )
            }
        }
    }

    private fun Drawable.transformEmoteDrawable(
        scale: Double,
        emote: ChatMessageEmote
    ) {
        val ratio = intrinsicWidth / intrinsicHeight.toFloat()
        val height = when {
            intrinsicHeight < 55 && emote.isTwitch -> (70 * scale).roundToInt()
            intrinsicHeight in 55..111 && emote.isTwitch -> (112 * scale).roundToInt()
            else -> (intrinsicHeight * scale).roundToInt()
        }
        val width = (height * ratio).roundToInt()
        setBounds(0, 0, width * emote.scale, height * emote.scale)
    }
}

private class DetectDiff : DiffUtil.ItemCallback<ChatItem>() {
    override fun areItemsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
        return if (newItem.message.timedOut || newItem.message.isMention) false
        else oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
        return oldItem.message == newItem.message
    }
}