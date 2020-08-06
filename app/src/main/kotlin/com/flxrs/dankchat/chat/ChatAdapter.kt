package com.flxrs.dankchat.chat

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ImageSpan
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.bold
import androidx.core.text.color
import androidx.core.view.postDelayed
import androidx.emoji.text.EmojiCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.Coil
import coil.api.get
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.ChatItemBinding
import com.flxrs.dankchat.service.twitch.badge.Badge
import com.flxrs.dankchat.service.twitch.connection.SystemMessageType
import com.flxrs.dankchat.service.twitch.emote.ChatMessageEmote
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import com.flxrs.dankchat.service.twitch.message.Message
import com.flxrs.dankchat.utils.TimeUtils
import com.flxrs.dankchat.utils.extensions.normalizeColor
import com.flxrs.dankchat.utils.extensions.setRunning
import com.flxrs.dankchat.utils.showErrorDialog
import com.linkedin.urls.detection.UrlDetector
import com.linkedin.urls.detection.UrlDetectorOptions
import kotlinx.coroutines.*
import pl.droidsonroids.gif.GifDrawable
import kotlin.math.roundToInt

class ChatAdapter(
    private val onListChanged: (position: Int) -> Unit,
    private val onUserClicked: (user: String) -> Unit,
    private val onMessageLongClick: (message: String) -> Unit
) : ListAdapter<ChatItem, ChatAdapter.ViewHolder>(DetectDiff()) {
    inner class ViewHolder(val binding: ChatItemBinding) : RecyclerView.ViewHolder(binding.root) {
        val scope = CoroutineScope(Dispatchers.Main.immediate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ChatItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onCurrentListChanged(previousList: MutableList<ChatItem>, currentList: MutableList<ChatItem>) {
        onListChanged(currentList.size - 1)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.scope.coroutineContext.cancelChildren()
        holder.binding.executePendingBindings()
        EmoteManager.gifCallback.removeView(holder.binding.itemText)
        super.onViewRecycled(holder)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        with(holder.binding.itemText) {
            when (val message = getItem(position).message) {
                is Message.SystemMessage -> handleSystemMessage(message)
                is Message.TwitchMessage -> handleTwitchMessage(message, holder)
            }
        }
    }

    private fun TextView.handleSystemMessage(message: Message.SystemMessage) {
        alpha = 1.0f
        setBackgroundResource(android.R.color.transparent)

        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val timestampPreferenceKey = context.getString(R.string.preference_timestamp_key)
        val fontSizePreferenceKey = context.getString(R.string.preference_font_size_key)
        val showTimeStamp = preferences.getBoolean(timestampPreferenceKey, true)
        val fontSize = preferences.getInt(fontSizePreferenceKey, 14)

        val connectionText = when (message.state) {
            SystemMessageType.DISCONNECTED -> context.getString(R.string.system_message_disconnected)
            SystemMessageType.NO_HISTORY_LOADED -> context.getString(R.string.system_message_no_history)
            else -> context.getString(R.string.system_message_connected)
        }
        val withTime = when {
            showTimeStamp -> SpannableStringBuilder().bold { append("${TimeUtils.timestampToLocalTime(message.timestamp)} ") }.append(connectionText)
            else -> SpannableStringBuilder().append(connectionText)
        }

        setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat())
        text = withTime
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    @SuppressLint("ClickableViewAccessibility")
    private fun TextView.handleTwitchMessage(twitchMessage: Message.TwitchMessage, holder: ChatAdapter.ViewHolder) = with(twitchMessage) {
        isClickable = false
        text = ""
        alpha = 1.0f
        movementMethod = LinkMovementMethod.getInstance()

        val darkModePreferenceKey = context.getString(R.string.preference_dark_theme_key)
        val timedOutPreferenceKey = context.getString(R.string.preference_show_timed_out_messages_key)
        val timestampPreferenceKey = context.getString(R.string.preference_timestamp_key)
        val animateGifsKey = context.getString(R.string.preference_animate_gifs_key)
        val fontSizePreferenceKey = context.getString(R.string.preference_font_size_key)
        val debugKey = context.getString(R.string.preference_debug_mode_key)
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val isDarkMode = preferences.getBoolean(darkModePreferenceKey, true)
        val isDebugEnabled = preferences.getBoolean(debugKey, false)
        val showTimedOutMessages = preferences.getBoolean(timedOutPreferenceKey, true)
        val showTimeStamp = preferences.getBoolean(timestampPreferenceKey, true)
        val animateGifs = preferences.getBoolean(animateGifsKey, true)
        val fontSize = preferences.getInt(fontSizePreferenceKey, 14)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat())


        val coroutineHandler = CoroutineExceptionHandler { _, throwable ->
            val trace = Log.getStackTraceString(throwable)
            Log.e(TAG, trace)

            if (isDebugEnabled) {
                showErrorDialog(throwable, stackTraceString = trace)
            }
        }

        holder.scope.launch(coroutineHandler) {
            if (timedOut) {
                alpha = 0.5f

                if (!showTimedOutMessages) {
                    text = if (showTimeStamp) {
                        "${TimeUtils.timestampToLocalTime(timestamp)} ${context.getString(R.string.timed_out_message)}"
                    } else context.getString(R.string.timed_out_message)
                    return@launch
                }
            }

            var ignoreClicks = false
            setOnLongClickListener {
                ignoreClicks = true
                onMessageLongClick(message)
                true
            }

            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    postDelayed(200) {
                        ignoreClicks = false
                    }
                }
                false
            }

            val scaleFactor = lineHeight * 1.5 / 112

            val background = when {
                isNotify -> if (isDarkMode) R.color.color_highlight_dark else R.color.color_highlight_light
                isReward -> if (isDarkMode) R.color.color_reward_dark else R.color.color_reward_light
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
            val timeWithWhisperNotice = when {
                isWhisper -> "${TimeUtils.timestampToLocalTime(timestamp)} (Whisper)"
                else -> TimeUtils.timestampToLocalTime(timestamp)
            }
            val (prefixLength, spannable) = if (showTimeStamp) {
                timeWithWhisperNotice.length + 1 + fullDisplayName.length to SpannableStringBuilder().bold { append("$timeWithWhisperNotice ") }
            } else {
                fullDisplayName.length to SpannableStringBuilder()
            }

            val badgePositions = badges.map {
                spannable.append("  ")
                spannable.length - 2 to spannable.length - 1
            }

            val normalizedColor = color.normalizeColor(isDarkMode)
            spannable.bold { color(normalizedColor) { append(fullDisplayName) } }

            when {
                message.startsWith("Login authentication", true) -> spannable.append(context.getString(R.string.login_expired))
                isAction -> spannable.color(normalizedColor) { append(message) }
                else -> spannable.append(message)
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
                spannable.setSpan(userClickableSpan, prefixLength - fullDisplayName.length + badgesLength, prefixLength + badgesLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            val emojiCompat = EmojiCompat.get()
            val messageStart = prefixLength + badgesLength
            val messageEnd = messageStart + message.length
            val spannableWithEmojis = when (emojiCompat.loadState) {
                EmojiCompat.LOAD_STATE_SUCCEEDED -> emojiCompat.process(spannable, messageStart, messageEnd, Int.MAX_VALUE, EmojiCompat.REPLACE_STRATEGY_NON_EXISTENT)
                else -> spannable
            } as SpannableStringBuilder

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
                spannableWithEmojis.setSpan(clickableSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (animateGifs && emotes.filter { it.isGif }.count() > 0) {
                EmoteManager.gifCallback.addView(holder.binding.itemText)
            }

            text = spannableWithEmojis

            badges.forEachIndexed { idx, badge ->
                val (start, end) = badgePositions[idx]

                Coil.get(badge.url).apply {
                    if (badge is Badge.FFZModBadge)
                        colorFilter = PorterDuffColorFilter(ContextCompat.getColor(context, R.color.color_ffz_mod), PorterDuff.Mode.DST_OVER)
//                val result = Coil.execute(GetRequest.Builder(context).data(badge.url).build())
//                if (result is SuccessResult) {
//                    result.drawable.apply {
                    val width = (lineHeight * intrinsicWidth / intrinsicHeight.toFloat()).roundToInt()
                    setBounds(0, 0, width, lineHeight)
                    val imageSpan = ImageSpan(this, ImageSpan.ALIGN_BOTTOM)
                    spannable.setSpan(imageSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                //}
            }

            val fullPrefix = prefixLength + badgesLength
            emotes.forEach { e ->
                val drawable = when {
                    e.isGif -> EmoteManager.gifCache[e.url]?.also { it.setRunning(animateGifs) } ?: Coil.get(e.url).apply {
                        this as GifDrawable
                        setRunning(animateGifs)
                        callback = EmoteManager.gifCallback
                        EmoteManager.gifCache.put(e.url, this)
                    }
                    else -> Coil.get(e.url)
                }
                drawable.transformEmoteDrawable(scaleFactor, e)
                setEmoteSpans(e, fullPrefix, drawable, spannableWithEmojis)
            }

            text = spannableWithEmojis
        }
    }

    private fun setEmoteSpans(e: ChatMessageEmote, prefix: Int, drawable: Drawable, spannableStringBuilder: SpannableStringBuilder) {
        e.positions.forEach { pos ->
            val (start, end) = pos.split('-').map { it.toInt() + prefix }
            try {
                spannableStringBuilder.setSpan(ImageSpan(drawable), start, end, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
            } catch (t: Throwable) {
                Log.e(
                    "ViewBinding",
                    "$start $end ${e.code} ${spannableStringBuilder.length}"
                )
            }
        }
    }

    private fun Drawable.transformEmoteDrawable(scale: Double, emote: ChatMessageEmote) {
        val ratio = intrinsicWidth / intrinsicHeight.toFloat()
        val height = when {
            intrinsicHeight < 55 && emote.isTwitch -> (70 * scale).roundToInt()
            intrinsicHeight in 55..111 && emote.isTwitch -> (112 * scale).roundToInt()
            else -> (intrinsicHeight * scale).roundToInt()
        }
        val width = (height * ratio).roundToInt()
        setBounds(0, 0, width * emote.scale, height * emote.scale)
    }

    companion object {
        private val TAG = ChatAdapter::class.java.simpleName
    }
}

private class DetectDiff : DiffUtil.ItemCallback<ChatItem>() {
    override fun areItemsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
        return if (oldItem.message is Message.TwitchMessage && newItem.message is Message.TwitchMessage && (newItem.message.timedOut || newItem.message.isMention)) false
        else oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean = oldItem == newItem
}