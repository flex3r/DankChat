package com.flxrs.dankchat.chat

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.*
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextPaint
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
import androidx.emoji2.text.EmojiCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.Coil
import coil.request.ImageRequest
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.ChatItemBinding
import com.flxrs.dankchat.service.twitch.badge.Badge
import com.flxrs.dankchat.service.twitch.badge.BadgeType
import com.flxrs.dankchat.service.twitch.emote.ChatMessageEmote
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import com.flxrs.dankchat.service.twitch.message.SystemMessage
import com.flxrs.dankchat.service.twitch.message.SystemMessageType
import com.flxrs.dankchat.service.twitch.message.TwitchMessage
import com.flxrs.dankchat.utils.DateTimeUtils
import com.flxrs.dankchat.utils.extensions.*
import com.flxrs.dankchat.utils.showErrorDialog
import com.flxrs.dankchat.utils.span.LongClickLinkMovementMethod
import com.flxrs.dankchat.utils.span.LongClickableSpan
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.*
import kotlin.math.roundToInt

class ChatAdapter(
    private val emoteManager: EmoteManager,
    private val onListChanged: (position: Int) -> Unit,
    private val onUserClicked: (targetUserId: String?, targetUsername: String, channelName: String, isLongPress: Boolean) -> Unit,
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            emoteManager.gifCallback.removeView(holder.binding.itemText)
        }

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
            else          -> (bindingAdapterPosition - itemCount - 1).isEven
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
            isCheckeredMode && holder.isAlternateBackground -> MaterialColors.layer(this, android.R.attr.colorBackground, R.attr.colorSurfaceInverse, MaterialColors.ALPHA_DISABLED_LOW)
            else                                            -> ContextCompat.getColor(context, android.R.color.transparent)
        }
        setBackgroundColor(background)

        val systemMessageText = when (message.type) {
            SystemMessageType.DISCONNECTED      -> context.getString(R.string.system_message_disconnected)
            SystemMessageType.NO_HISTORY_LOADED -> context.getString(R.string.system_message_no_history)
            SystemMessageType.CONNECTED         -> context.getString(R.string.system_message_connected)
            SystemMessageType.LOGIN_EXPIRED     -> context.getString(R.string.login_expired)
        }
        val withTime = when {
            showTimeStamp -> SpannableStringBuilder().bold { append("${DateTimeUtils.timestampToLocalTime(message.timestamp)} ") }.append(systemMessageText)
            else          -> SpannableStringBuilder().append(systemMessageText)
        }

        setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat())
        text = withTime
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    @SuppressLint("ClickableViewAccessibility")
    private fun TextView.handleTwitchMessage(twitchMessage: TwitchMessage, holder: ViewHolder, isMentionTab: Boolean): Unit = with(twitchMessage) {
        val textView = this@handleTwitchMessage
        isClickable = false
        alpha = 1.0f
        movementMethod = LongClickLinkMovementMethod

        val darkModePreferenceKey = context.getString(R.string.preference_dark_theme_key)
        val timedOutPreferenceKey = context.getString(R.string.preference_show_timed_out_messages_key)
        val timestampPreferenceKey = context.getString(R.string.preference_timestamp_key)
        val usernamePreferenceKey = context.getString(R.string.preference_show_username_key)
        val animateGifsKey = context.getString(R.string.preference_animate_gifs_key)
        val fontSizePreferenceKey = context.getString(R.string.preference_font_size_key)
        val debugKey = context.getString(R.string.preference_debug_mode_key)
        val checkeredKey = context.getString(R.string.checkered_messages_key)
        val badgesKey = context.getString(R.string.preference_visible_badges_key)
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val isDarkMode = resources.isSystemNightMode || preferences.getBoolean(darkModePreferenceKey, false)
        val isCheckeredMode = preferences.getBoolean(checkeredKey, false)
        val isDebugEnabled = preferences.getBoolean(debugKey, false)
        val showTimedOutMessages = preferences.getBoolean(timedOutPreferenceKey, true)
        val showTimeStamp = preferences.getBoolean(timestampPreferenceKey, true)
        val showUserName = preferences.getBoolean(usernamePreferenceKey, true)
        val animateGifs = preferences.getBoolean(animateGifsKey, true)
        val fontSize = preferences.getInt(fontSizePreferenceKey, 14)
        val visibleBadges = preferences.getStringSet(badgesKey, resources.getStringArray(R.array.badges_entry_values).toSet()).orEmpty()
        val visibleBadgeTypes = BadgeType.mapFromPreferenceSet(visibleBadges)
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
            val scaleFactor = lineHeight * 1.5 / 112
            val background = when {
                isNotify                                        -> ContextCompat.getColor(context, R.color.color_highlight).harmonize(context)
                isReward                                        -> ContextCompat.getColor(context, R.color.color_reward).harmonize(context)
                isMention                                       -> ContextCompat.getColor(context, R.color.color_mention).harmonize(context)
                isCheckeredMode && holder.isAlternateBackground -> MaterialColors.layer(textView, android.R.attr.colorBackground, R.attr.colorSurfaceInverse, MaterialColors.ALPHA_DISABLED_LOW)
                else                                            -> ContextCompat.getColor(context, android.R.color.transparent)
            }
            setBackgroundColor(background)

            val textColor = when {
                isNotify  -> MaterialColors.getColor(textView, R.attr.colorOnPrimaryContainer)
                isReward  -> MaterialColors.getColor(textView, R.attr.colorOnTertiaryContainer)
                isMention -> MaterialColors.getColor(textView, R.attr.colorOnSecondaryContainer)
                else      -> MaterialColors.getColor(textView, R.attr.colorOnSurface)
            }
            setTextColor(textColor)

            if (timedOut) {
                alpha = 0.5f

                if (!showTimedOutMessages) {
                    text = when {
                        showTimeStamp -> "${DateTimeUtils.timestampToLocalTime(timestamp)} ${context.getString(R.string.timed_out_message)}"
                        else          -> context.getString(R.string.timed_out_message)
                    }
                    return@launch
                }
            }

            val fullName = when {
                displayName.equals(name, true) -> displayName
                else                           -> "$name($displayName)"
            }

            val fullDisplayName = when {
                isWhisper && whisperRecipient.isNotBlank() -> "$fullName -> $whisperRecipient: "
                !showUserName                              -> ""
                isAction                                   -> "$fullName "
                fullName.isBlank()                         -> ""
                else                                       -> "$fullName: "
            }

            val allowedBadges = badges.filter { visibleBadgeTypes.contains(it.type) }
            val badgesLength = allowedBadges.size * 2

            val channelOrBlank = when {
                isWhisper -> ""
                else      -> "#$channel"
            }
            val timeAndWhisperBuilder = StringBuilder()
            if (isMentionTab && isMention) timeAndWhisperBuilder.append("$channelOrBlank ")
            if (showTimeStamp) timeAndWhisperBuilder.append("${DateTimeUtils.timestampToLocalTime(timestamp)} ")
            val (prefixLength, spannable) = timeAndWhisperBuilder.length + fullDisplayName.length to SpannableStringBuilder().bold { append(timeAndWhisperBuilder) }

            val badgePositions = allowedBadges.map {
                spannable.append("  ")
                spannable.length - 2 to spannable.length - 1
            }

            val normalizedColor = color.normalizeColor(isDarkMode)
            spannable.bold { color(normalizedColor) { append(fullDisplayName) } }

            when {
                isAction -> spannable.color(normalizedColor) { append(message) }
                else     -> spannable.append(message)
            }

            // clicking usernames
            if (fullName.isNotBlank()) {
                val userClickableSpan = object : LongClickableSpan() {
                    override fun onClick(v: View) = onUserClicked(userId, displayName, channel, false)
                    override fun onLongClick(view: View) = onUserClicked(userId, displayName, channel, true)
                    override fun updateDrawState(ds: TextPaint) {
                        ds.isUnderlineText = false
                        ds.color = normalizedColor
                    }
                }
                spannable.setSpan(userClickableSpan, prefixLength - fullDisplayName.length + badgesLength, prefixLength + badgesLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            val emojiCompat = EmojiCompat.get()
            val messageStart = prefixLength + badgesLength
            val messageEnd = messageStart + message.length
            val spannableWithEmojis = when (emojiCompat.loadState) {
                EmojiCompat.LOAD_STATE_SUCCEEDED -> emojiCompat.process(spannable, messageStart, messageEnd, Int.MAX_VALUE, EmojiCompat.REPLACE_STRATEGY_NON_EXISTENT)
                else                             -> spannable
            } as SpannableStringBuilder

            // links
            LinkifyCompat.addLinks(spannableWithEmojis, Linkify.WEB_URLS)
            spannableWithEmojis.getSpans<URLSpan>().forEach {
                val start = spannableWithEmojis.getSpanStart(it)
                val end = spannableWithEmojis.getSpanEnd(it)
                spannableWithEmojis.removeSpan(it)

                // skip partial link matches
                val previousChar = spannableWithEmojis.getOrNull(index = start - 1)
                if (previousChar != null && !previousChar.isWhitespace()) return@forEach

                val clickableSpan = object : LongClickableSpan() {
                    override fun onLongClick(view: View) = onMessageLongClick(originalMessage)
                    override fun onClick(v: View) {
                        try {
                            customTabsIntent.launchUrl(context, it.url.toUri())
                        } catch (e: ActivityNotFoundException) {
                            Log.e("ViewBinding", Log.getStackTraceString(e))
                        }
                    }
                }
                spannableWithEmojis.setSpan(clickableSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            // copying message
            val messageClickableSpan = object : LongClickableSpan() {
                override fun onClick(v: View) = Unit
                override fun onLongClick(view: View) = onMessageLongClick(originalMessage)
                override fun updateDrawState(ds: TextPaint) {
                    ds.isUnderlineText = false
                }

            }
            spannableWithEmojis.setSpan(messageClickableSpan, messageStart, messageEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            setText(spannableWithEmojis, TextView.BufferType.SPANNABLE)
            allowedBadges.forEachIndexed { idx, badge ->
                try {
                    val (start, end) = badgePositions[idx]
                    val cached = emoteManager.gifCache[badge.url]
                    val drawable = when {
                        cached != null -> cached.also { (it as? Animatable)?.setRunning(animateGifs) }
                        else           -> Coil.execute(badge.url.toRequest(context)).drawable?.apply {
                            if (badge is Badge.FFZModBadge) {
                                val modColor = ContextCompat.getColor(context, R.color.color_ffz_mod)
                                val harmonized = MaterialColors.harmonizeWithPrimary(context, modColor)
                                colorFilter = PorterDuffColorFilter(harmonized, PorterDuff.Mode.DST_OVER)
                            }

                            val width = (lineHeight * intrinsicWidth / intrinsicHeight.toFloat()).roundToInt()
                            setBounds(0, 0, width, lineHeight)
                            if (this is Animatable) {
                                emoteManager.gifCache.put(badge.url, this)
                                setRunning(animateGifs)
                            }
                        }
                    }

                    if (drawable != null) {
                        val imageSpan = ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM)
                        (text as SpannableString).setSpan(imageSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                } catch (t: Throwable) {
                    handleException(t)
                }
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && animateGifs) {
                emoteManager.gifCallback.addView(holder.binding.itemText)
            }

            val fullPrefix = prefixLength + badgesLength
            emotes.groupBy { it.position }.forEach { (_, emotes) ->
                try {
                    val key = emotes.joinToString(separator = "-") { it.id }
                    val layerDrawable = emoteManager.layerCache[key] ?: calculateLayerDrawable(context, emotes, key, animateGifs, scaleFactor)
                    (text as SpannableString).setEmoteSpans(emotes.first(), fullPrefix, layerDrawable)
                } catch (t: Throwable) {
                    handleException(t)
                }
            }
        }
    }

    private suspend fun calculateLayerDrawable(
        context: Context,
        emotes: List<ChatMessageEmote>,
        cacheKey: String,
        animateGifs: Boolean,
        scaleFactor: Double,
    ): LayerDrawable {
        val drawables = emotes.mapNotNull { it.toDrawable(context, animateGifs, useCache = !it.isOverlayEmote)?.run { transformEmoteDrawable(scaleFactor, it) } }.toTypedArray()
        val bounds = drawables.map { it.bounds }
        return drawables.toLayerDrawable(bounds, scaleFactor, emotes).also {
            if (emotes.size > 1) {
                emoteManager.layerCache.put(cacheKey, it)
            }
        }
    }

    private fun Array<Drawable>.toLayerDrawable(bounds: List<Rect>, scaleFactor: Double, emotes: List<ChatMessageEmote>): LayerDrawable = LayerDrawable(this).apply {
        val maxWidth = bounds.maxOf { it.width() }
        val maxHeight = bounds.maxOf { it.height() }
        setBounds(0, 0, maxWidth, maxHeight)

        // set bounds again but adjust by maximum width/height of stacked drawables
        forEachIndexed { idx, dr -> dr.transformEmoteDrawable(scaleFactor, emotes[idx], maxWidth, maxHeight) }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            callback = emoteManager.gifCallback
        }
    }

    private suspend fun ChatMessageEmote.toDrawable(context: Context, animateGifs: Boolean, useCache: Boolean): Drawable? {
        val cached = emoteManager.gifCache[url]
        return when {
            useCache && cached != null -> cached.also { (it as? Animatable)?.setRunning(animateGifs) }
            else                       -> Coil.execute(url.toRequest(context)).drawable?.apply {
                if (this is Animatable) {
                    if (useCache) {
                        emoteManager.gifCache.put(url, this)
                    }
                    setRunning(animateGifs)
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
            intrinsicHeight < 55 && emote.isTwitch       -> (70 * scale).roundToInt()
            intrinsicHeight in 55..111 && emote.isTwitch -> (112 * scale).roundToInt()
            else                                         -> (intrinsicHeight * scale).roundToInt()
        }
        val width = (height * ratio).roundToInt()

        val scaledWidth = width * emote.scale
        val scaledHeight = height * emote.scale

        val left = (maxWidth - scaledWidth).coerceAtLeast(0)
        val top = (maxHeight - scaledHeight).coerceAtLeast(0)

        setBounds(left, top, scaledWidth + left, scaledHeight + top)
        return this
    }

    private fun String.toRequest(context: Context): ImageRequest = ImageRequest.Builder(context)
        .data(this)
        .build()

    companion object {
        private val TAG = ChatAdapter::class.java.simpleName
    }
}

private class DetectDiff : DiffUtil.ItemCallback<ChatItem>() {
    override fun areItemsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
        return oldItem.message.id == newItem.message.id
    }

    override fun areContentsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
        return if (oldItem.message is TwitchMessage && newItem.message is TwitchMessage && (newItem.message.timedOut || newItem.message.isMention)) false
        else oldItem.message == newItem.message
    }
}