package com.flxrs.dankchat.chat

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ImageSpan
import android.text.style.URLSpan
import android.text.util.Linkify
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.TextView
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.text.bold
import androidx.core.text.color
import androidx.core.text.getSpans
import androidx.core.text.util.LinkifyCompat
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
import com.flxrs.dankchat.service.twitch.message.SystemMessage
import com.flxrs.dankchat.service.twitch.message.TwitchMessage
import com.flxrs.dankchat.utils.TimeUtils
import com.flxrs.dankchat.utils.extensions.isEven
import com.flxrs.dankchat.utils.extensions.normalizeColor
import com.flxrs.dankchat.utils.extensions.setRunning
import com.flxrs.dankchat.utils.showErrorDialog
import kotlinx.coroutines.*
import pl.droidsonroids.gif.GifDrawable
import kotlin.math.roundToInt

class ChatAdapter(
    private val emoteManager: EmoteManager,
    private val onListChanged: (position: Int) -> Unit,
    private val onUserClicked: (user: String) -> Unit,
    private val onMessageLongClick: (message: String) -> Unit
) : ListAdapter<ChatItem, ChatAdapter.ViewHolder>(DetectDiff()) {
    // Using position.isEven for determining which background to use in checkered mode doesn't work,
    // since the LayoutManager uses stackFromEnd and every new message will be even. Instead, keep count of new messages separately.
    private var messageCount = 0
        get() = field++

    private val customTabsIntent = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()

    class ViewHolder(val binding: ChatItemBinding) : RecyclerView.ViewHolder(binding.root) {
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
        emoteManager.gifCallback.removeView(holder.binding.itemText)
        super.onViewRecycled(holder)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        when (val message = item.message) {
            is SystemMessage -> holder.binding.itemText.handleSystemMessage(message, holder)
            is TwitchMessage -> holder.binding.itemText.handleTwitchMessage(message, holder, item.isMentionTab)
        }
    }

    private val ViewHolder.isAlternateBackground
        get() = when (bindingAdapterPosition) {
            itemCount - 1 -> messageCount.isEven
            else -> (bindingAdapterPosition - itemCount - 1).isEven
        }

    private fun TextView.handleSystemMessage(message: SystemMessage, holder: ViewHolder) {
        alpha = 1.0f

        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val timestampPreferenceKey = context.getString(R.string.preference_timestamp_key)
        val fontSizePreferenceKey = context.getString(R.string.preference_font_size_key)
        val checkeredKey = context.getString(R.string.checkered_messages_key)
        val showTimeStamp = preferences.getBoolean(timestampPreferenceKey, true)
        val fontSize = preferences.getInt(fontSizePreferenceKey, 14)
        val isCheckeredMode = preferences.getBoolean(checkeredKey, false)

        val background = when {
            isCheckeredMode && holder.isAlternateBackground -> R.color.color_transparency_20
            else -> android.R.color.transparent
        }
        setBackgroundResource(background)

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
    private fun TextView.handleTwitchMessage(twitchMessage: TwitchMessage, holder: ViewHolder, isMentionTab: Boolean): Unit = with(twitchMessage) {
        isClickable = false
        alpha = 1.0f
        movementMethod = LinkMovementMethod.getInstance()

        val darkModePreferenceKey = context.getString(R.string.preference_dark_theme_key)
        val timedOutPreferenceKey = context.getString(R.string.preference_show_timed_out_messages_key)
        val timestampPreferenceKey = context.getString(R.string.preference_timestamp_key)
        val usernamePreferenceKey = context.getString(R.string.preference_show_username_key)
        val animateGifsKey = context.getString(R.string.preference_animate_gifs_key)
        val fontSizePreferenceKey = context.getString(R.string.preference_font_size_key)
        val debugKey = context.getString(R.string.preference_debug_mode_key)
        val checkeredKey = context.getString(R.string.checkered_messages_key)
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val isDarkMode = preferences.getBoolean(darkModePreferenceKey, true)
        val isCheckeredMode = preferences.getBoolean(checkeredKey, false)
        val isDebugEnabled = preferences.getBoolean(debugKey, false)
        val showTimedOutMessages = preferences.getBoolean(timedOutPreferenceKey, true)
        val showTimeStamp = preferences.getBoolean(timestampPreferenceKey, true)
        val showUserName = preferences.getBoolean(usernamePreferenceKey, true)
        val animateGifs = preferences.getBoolean(animateGifsKey, true)
        val fontSize = preferences.getInt(fontSizePreferenceKey, 14)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat())

        fun handleException(throwable: Throwable) {
            if (throwable is CancellationException) return // Ignore job cancellations

            val trace = Log.getStackTraceString(throwable)
            Log.e(TAG, trace)

            if (isDebugEnabled) {
                showErrorDialog(throwable, stackTraceString = trace)
            }
        }

        val coroutineHandler = CoroutineExceptionHandler { _, throwable -> handleException(throwable) }

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
                onMessageLongClick(originalMessage)
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
                isCheckeredMode && holder.isAlternateBackground -> R.color.color_transparency_20
                else -> android.R.color.transparent
            }
            setBackgroundResource(background)

            val fullName = when {
                displayName.equals(name, true) -> displayName
                else -> "$name($displayName)"
            }

            val fullDisplayName = when {
                isWhisper && whisperRecipient.isNotBlank() -> "$fullName -> $whisperRecipient: "
                !showUserName -> ""
                isAction -> "$fullName "
                fullName.isBlank() -> ""
                else -> "$fullName: "
            }

            val badgesLength = when {
                showUserName -> badges.size * 2
                else -> 0
            }
            val channelOrBlank = when {
                isWhisper -> ""
                else -> "#$channel"
            }
            val timeAndWhisperBuilder = StringBuilder()
            if (isMentionTab && isMention) timeAndWhisperBuilder.append("$channelOrBlank ")
            if (showTimeStamp) timeAndWhisperBuilder.append("${TimeUtils.timestampToLocalTime(timestamp)} ")
            val (prefixLength, spannable) = timeAndWhisperBuilder.length + fullDisplayName.length to SpannableStringBuilder().bold { append(timeAndWhisperBuilder) }

            val badgePositions = when {
                showUserName -> badges.map {
                    spannable.append("  ")
                    spannable.length - 2 to spannable.length - 1
                }
                else -> listOf()
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
                val mention = when {
                    name.equals(displayName, ignoreCase = true) -> displayName
                    else -> name
                }
                val userClickableSpan = object : ClickableSpan() {
                    override fun updateDrawState(ds: TextPaint) {
                        ds.isUnderlineText = false
                        ds.color = normalizedColor
                    }

                    override fun onClick(v: View) {
                        if (!ignoreClicks) onUserClicked(mention)
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
            LinkifyCompat.addLinks(spannableWithEmojis, Linkify.WEB_URLS)
            spannableWithEmojis.getSpans<URLSpan>().forEach {
                val start = spannableWithEmojis.getSpanStart(it)
                val end = spannableWithEmojis.getSpanEnd(it)
                spannableWithEmojis.removeSpan(it)
                val clickableSpan = object : ClickableSpan() {
                    override fun onClick(v: View) {
                        try {
                            if (!ignoreClicks)
                                customTabsIntent.launchUrl(v.context, it.url.toUri())
                        } catch (e: ActivityNotFoundException) {
                            Log.e("ViewBinding", Log.getStackTraceString(e))
                        }
                    }
                }
                spannableWithEmojis.setSpan(clickableSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            setText(spannableWithEmojis, TextView.BufferType.SPANNABLE)
            if (showUserName) {
                badges.forEachIndexed { idx, badge ->
                    try {
                        val (start, end) = badgePositions[idx]
                        Coil.get(badge.url).apply {
                            if (badge is Badge.FFZModBadge) {
                                colorFilter = PorterDuffColorFilter(ContextCompat.getColor(context, R.color.color_ffz_mod), PorterDuff.Mode.DST_OVER)
                            }

                            val width = (lineHeight * intrinsicWidth / intrinsicHeight.toFloat()).roundToInt()
                            setBounds(0, 0, width, lineHeight)
                            val imageSpan = ImageSpan(this, ImageSpan.ALIGN_BOTTOM)
                            (text as SpannableString).setSpan(imageSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    } catch (t: Throwable) {
                        handleException(t)
                    }
                }
            }

            if (animateGifs && emotes.any(ChatMessageEmote::isGif)) {
                emoteManager.gifCallback.addView(holder.binding.itemText)
            }
            val fullPrefix = prefixLength + badgesLength
            emotes.groupBy { it.position }.forEach { (_, emotes) ->
                try {
                    val drawables = emotes.map { it.toDrawable(animateGifs, useCache = emotes.size == 1).run { transformEmoteDrawable(scaleFactor, it) } }.toTypedArray()
                    val bounds = drawables.map { it.bounds }
                    val layerDrawable = drawables.toLayerDrawable(bounds, scaleFactor, emotes)

                    (text as SpannableString).setEmoteSpans(emotes.first(), fullPrefix, layerDrawable)
                } catch (t: Throwable) {
                    handleException(t)
                }
            }
        }
    }

    private fun Array<Drawable>.toLayerDrawable(bounds: List<Rect>, scaleFactor: Double, emotes: List<ChatMessageEmote>): LayerDrawable = LayerDrawable(this).apply {
        val maxWidth = bounds.maxOf { it.width() }
        val maxHeight = bounds.maxOf { it.height() }
        setBounds(0, 0, maxWidth, maxHeight)

        // set bounds again but adjust by maximum width/height of stacked drawables
        forEachIndexed { idx, dr -> dr.transformEmoteDrawable(scaleFactor, emotes[idx], maxWidth, maxHeight) }

        if (emotes.any(ChatMessageEmote::isGif)) {
            callback = emoteManager.gifCallback
        }
    }

    private suspend fun ChatMessageEmote.toDrawable(animateGifs: Boolean, useCache: Boolean): Drawable = when {
        !isGif -> Coil.get(url)
        else -> {
            val cached = emoteManager.gifCache[url]?.also { it.setRunning(animateGifs) }
            when {
                useCache && cached != null -> cached
                else -> Coil.get(url).apply {
                    this as GifDrawable
                    // try to sync gif
                    withContext(Dispatchers.Default) {
                        cached?.let { seekToBlocking(it.currentPosition.coerceAtLeast(0)) } ?: emoteManager.gifCache.put(url, this@apply)
                        setRunning(animateGifs)
                    }
                }
            }
        }
    }

    private fun SpannableString.setEmoteSpans(e: ChatMessageEmote, prefix: Int, drawable: Drawable) {
        try {
            setSpan(ImageSpan(drawable), e.position.first + prefix, e.position.last + prefix, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
        } catch (t: Throwable) {
            Log.e("ViewBinding", "$t $this ${e.position} ${e.code} $length")
        }
    }

    private fun Drawable.transformEmoteDrawable(scale: Double, emote: ChatMessageEmote, maxWidth: Int = 0, maxHeight: Int = 0): Drawable {
        val ratio = intrinsicWidth / intrinsicHeight.toFloat()
        val height = when {
            intrinsicHeight < 55 && emote.isTwitch -> (70 * scale).roundToInt()
            intrinsicHeight in 55..111 && emote.isTwitch -> (112 * scale).roundToInt()
            else -> (intrinsicHeight * scale).roundToInt()
        }
        val width = (height * ratio).roundToInt()

        val scaledWidth = width * emote.scale
        val scaledHeight = height * emote.scale

        val left = (maxWidth - scaledWidth).coerceAtLeast(0)
        val top = (maxHeight - scaledHeight).coerceAtLeast(0)

        setBounds(left, top, scaledWidth + left, scaledHeight + top)
        return this
    }

    companion object {
        private val TAG = ChatAdapter::class.java.simpleName
    }
}

private class DetectDiff : DiffUtil.ItemCallback<ChatItem>() {
    override fun areItemsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
        return if (oldItem.message is TwitchMessage && newItem.message is TwitchMessage && (newItem.message.timedOut || newItem.message.isMention)) false
        else oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean = oldItem == newItem
}