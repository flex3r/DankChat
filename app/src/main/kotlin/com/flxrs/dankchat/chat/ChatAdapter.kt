package com.flxrs.dankchat.chat

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.*
import android.os.Build
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.*
import android.text.util.Linkify
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.Px
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.text.*
import androidx.core.text.util.LinkifyCompat
import androidx.emoji2.text.EmojiCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.imageLoader
import coil.request.ImageRequest
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.twitch.badge.Badge
import com.flxrs.dankchat.data.twitch.badge.BadgeType
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmote
import com.flxrs.dankchat.data.twitch.emote.EmoteManager
import com.flxrs.dankchat.data.twitch.emote.EmoteManager.Companion.cacheKey
import com.flxrs.dankchat.data.twitch.message.*
import com.flxrs.dankchat.databinding.ChatItemBinding
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
    private val onUserClicked: (targetUserId: String?, targetUsername: String, messageId: String, channelName: String, badges: List<Badge>, isLongPress: Boolean) -> Unit,
    private val onMessageLongClick: (message: String) -> Unit
) : ListAdapter<ChatItem, ChatAdapter.ViewHolder>(DetectDiff()) {
    // Using position.isEven for determining which background to use in checkered mode doesn't work,
    // since the LayoutManager uses stackFromEnd and every new message will be even. Instead, keep count of new messages separately.
    private var messageCount = 0
        get() = field++

    companion object {
        private const val SCALE_FACTOR_CONSTANT = 1.5 / 112
        private const val BASE_HEIGHT_CONSTANT = 1.173
        private const val MONOSPACE_FONT_PROPORTION = 0.95f // make monospace font a bit smaller to make looks same sized as normal text
        private val MASK_FULL = ColorDrawable(Color.argb(255, 0, 0, 0))
        private val MASK_NONE = ColorDrawable(Color.argb(0, 0, 0, 0))
        private fun getBaseHeight(@Px textSize: Float): Int = (textSize * BASE_HEIGHT_CONSTANT).roundToInt()
    }

    private val customTabsIntent = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()

    class ViewHolder(val binding: ChatItemBinding) : RecyclerView.ViewHolder(binding.root) {
        val scope = CoroutineScope(Dispatchers.Main.immediate)
        val coroutineHandler = CoroutineExceptionHandler { _, throwable -> binding.itemText.handleException(throwable) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ChatItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onCurrentListChanged(previousList: MutableList<ChatItem>, currentList: MutableList<ChatItem>) {
        onListChanged(currentList.lastIndex)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.scope.coroutineContext.cancelChildren()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            emoteManager.gifCallback.removeView(holder.binding.itemText)
        }

        super.onViewRecycled(holder)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        when (val message = item.message) {
            is SystemMessage          -> holder.binding.itemText.handleSystemMessage(message, holder)
            is TwitchMessage          -> holder.binding.itemText.handleTwitchMessage(message, holder, item.isMentionTab)
            is ClearChatMessage       -> holder.binding.itemText.handleClearChatMessage(message, holder)
            is PointRedemptionMessage -> holder.binding.itemText.handlePointRedemptionMessage(message, holder)
            is WhisperMessage         -> holder.binding.itemText.handleWhisperMessage(message, holder)
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
        setRippleBackground(background, enableRipple = false)

        val systemMessageText = when (message.type) {
            is SystemMessageType.Disconnected              -> context.getString(R.string.system_message_disconnected)
            is SystemMessageType.NoHistoryLoaded           -> context.getString(R.string.system_message_no_history)
            is SystemMessageType.Connected                 -> context.getString(R.string.system_message_connected)
            is SystemMessageType.LoginExpired              -> context.getString(R.string.login_expired)
            is SystemMessageType.ChannelNonExistent        -> context.getString(R.string.system_message_channel_non_existent)
            is SystemMessageType.MessageHistoryUnavailable -> when (message.type.status) {
                null -> context.getString(R.string.system_message_history_unavailable)
                else -> context.getString(R.string.system_message_history_unavailable_detailed, message.type.status)
            }

            is SystemMessageType.MessageHistoryIgnored     -> context.getString(R.string.system_message_history_ignored)
            is SystemMessageType.MessageHistoryIncomplete  -> context.getString(R.string.system_message_history_recovering)
            is SystemMessageType.Custom                    -> message.type.message
        }
        val withTime = when {
            showTimeStamp -> SpannableStringBuilder().timestampFont(context) { append(DateTimeUtils.timestampToLocalTime(message.timestamp)) }.append(systemMessageText)
            else          -> SpannableStringBuilder().append(systemMessageText)
        }

        setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat())
        text = withTime
    }

    private fun TextView.handleClearChatMessage(message: ClearChatMessage, holder: ViewHolder) {
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
        setRippleBackground(background, enableRipple = false)

        val count = message.count
        // TODO localize
        val systemMessageText = when {
            message.isFullChatClear -> "Chat has been cleared by a moderator."
            message.isBan           -> "${message.targetUser} has been permanently banned"
            else                    -> {
                val countOrBlank = if (count > 1) " ($count times)" else ""
                "${message.targetUser} has been timed out for ${DateTimeUtils.formatSeconds(message.duration)}.$countOrBlank"
            }
        }
        val withTime = when {
            showTimeStamp -> SpannableStringBuilder().timestampFont(context) { append(DateTimeUtils.timestampToLocalTime(message.timestamp)) }.append(systemMessageText)
            else          -> SpannableStringBuilder().append(systemMessageText)
        }

        setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat())
        text = withTime
    }

    private fun TextView.handlePointRedemptionMessage(message: PointRedemptionMessage, holder: ViewHolder) {
        alpha = 1.0f

        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val timestampPreferenceKey = context.getString(R.string.preference_timestamp_key)
        val fontSizePreferenceKey = context.getString(R.string.preference_font_size_key)
        val showTimeStamp = preferences.getBoolean(timestampPreferenceKey, true)
        val fontSize = preferences.getInt(fontSizePreferenceKey, 14)

        val background = ContextCompat.getColor(context, R.color.color_reward)
        setRippleBackground(background, enableRipple = false)

        setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat())
        val baseHeight = getBaseHeight(textSize)

        holder.scope.launch(holder.coroutineHandler) {

            val spannable = buildSpannedString {
                if (showTimeStamp) {
                    timestampFont(context) { append(DateTimeUtils.timestampToLocalTime(message.timestamp)) }
                }

                when {
                    message.requiresUserInput -> append("Redeemed ")
                    else                      -> {
                        bold { append(message.displayName) }
                        append(" redeemed ")
                    }
                }

                bold { append(message.title) }
                append("  ")
                append(" ${message.cost}")
            }
            setText(spannable, TextView.BufferType.SPANNABLE)

            val imageStart = spannable.lastIndexOf(' ') - 1
            context.imageLoader
                .execute(message.rewardImageUrl.toRequest(context))
                .drawable?.apply {
                    val width = (baseHeight * intrinsicWidth / intrinsicHeight.toFloat()).roundToInt()
                    setBounds(0, 0, width, baseHeight)
                    (text as Spannable)[imageStart..imageStart + 1] = ImageSpan(this, ImageSpan.ALIGN_BOTTOM)
                }
        }
    }

    private fun TextView.handleWhisperMessage(whisperMessage: WhisperMessage, holder: ViewHolder) = with(whisperMessage) {
        val textView = this@handleWhisperMessage
        isClickable = false
        alpha = 1.0f
        movementMethod = LongClickLinkMovementMethod
        (text as? Spannable)?.clearSpans()

        val darkModeKey = context.getString(R.string.preference_dark_theme_key)
        val themePreferenceKey = context.getString(R.string.preference_theme_key)
        val timestampPreferenceKey = context.getString(R.string.preference_timestamp_key)
        val animateGifsKey = context.getString(R.string.preference_animate_gifs_key)
        val fontSizePreferenceKey = context.getString(R.string.preference_font_size_key)
        val checkeredKey = context.getString(R.string.checkered_messages_key)
        val badgesKey = context.getString(R.string.preference_visible_badges_key)
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val isDarkMode = resources.isSystemNightMode || preferences.getString(themePreferenceKey, darkModeKey) == darkModeKey
        val isCheckeredMode = preferences.getBoolean(checkeredKey, false)
        val showTimeStamp = preferences.getBoolean(timestampPreferenceKey, true)
        val animateGifs = preferences.getBoolean(animateGifsKey, true)
        val fontSize = preferences.getInt(fontSizePreferenceKey, 14)
        val visibleBadges = preferences.getStringSet(badgesKey, resources.getStringArray(R.array.badges_entry_values).toSet()).orEmpty()
        val visibleBadgeTypes = BadgeType.mapFromPreferenceSet(visibleBadges)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat())
        val textColor = MaterialColors.getColor(textView, R.attr.colorOnSurface)
        setTextColor(textColor)

        val baseHeight = getBaseHeight(textSize)
        val scaleFactor = baseHeight * SCALE_FACTOR_CONSTANT
        val background = when {
            isCheckeredMode && holder.isAlternateBackground -> MaterialColors.layer(textView, android.R.attr.colorBackground, R.attr.colorSurfaceInverse, MaterialColors.ALPHA_DISABLED_LOW)
            else                                            -> ContextCompat.getColor(context, android.R.color.transparent)
        }
        setRippleBackground(background, enableRipple = true)

        val fullName = when {
            displayName.equals(name, true) -> displayName
            else                           -> "$name($displayName)"
        }

        val fullRecipientName = when {
            recipientDisplayName.equals(recipientName, true) -> recipientDisplayName
            else                                             -> "$recipientName($recipientDisplayName)"
        }

        val allowedBadges = badges.filter { visibleBadgeTypes.contains(it.type) }
        val badgesLength = allowedBadges.size * 2

        val spannable = SpannableStringBuilder(StringBuilder())
        if (showTimeStamp) {
            spannable.timestampFont(context) { append(DateTimeUtils.timestampToLocalTime(timestamp)) }
        }

        val nameGroupLength = fullName.length + 4 + fullRecipientName.length + 2
        val prefixLength = spannable.length + nameGroupLength
        val badgePositions = allowedBadges.map {
            spannable.append("  ")
            spannable.length - 2 to spannable.length - 1
        }

        val normalizedColor = color.normalizeColor(isDarkMode)
        spannable.bold { color(normalizedColor) { append(fullName) } }
        spannable.append(" -> ")

        val normalizedRecipientColor = recipientColor.normalizeColor(isDarkMode)
        spannable.bold { color(normalizedRecipientColor) { append(fullRecipientName) } }
        spannable.append(": ")
        spannable.append(message)

        val userClickableSpan = object : LongClickableSpan() {
            val mentionName = when {
                name.equals(displayName, ignoreCase = true) -> displayName
                else                                        -> name
            }

            override fun onClick(v: View) = onUserClicked(userId, mentionName, id, "", badges, false)
            override fun onLongClick(view: View) = onUserClicked(userId, mentionName, id, "", badges, true)
            override fun updateDrawState(ds: TextPaint) {
                ds.isUnderlineText = false
                ds.color = normalizedColor
            }
        }
        val userStart = prefixLength + badgesLength - nameGroupLength
        val userEnd = userStart + fullName.length
        spannable[userStart..userEnd] = userClickableSpan

        val emojiCompat = EmojiCompat.get()
        val messageStart = prefixLength + badgesLength
        val messageEnd = messageStart + message.length
        val spannableWithEmojis = when (emojiCompat.loadState) {
            EmojiCompat.LOAD_STATE_SUCCEEDED -> emojiCompat.process(spannable, messageStart, messageEnd, Int.MAX_VALUE, EmojiCompat.REPLACE_STRATEGY_NON_EXISTENT)
            else                             -> spannable
        } as SpannableStringBuilder

        // TODO extract common
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
            spannableWithEmojis[start..end] = clickableSpan
        }

        // copying message
        val messageClickableSpan = object : LongClickableSpan() {
            override fun onClick(v: View) = Unit
            override fun onLongClick(view: View) = onMessageLongClick(originalMessage)
            override fun updateDrawState(ds: TextPaint) {
                ds.isUnderlineText = false
            }

        }
        spannableWithEmojis[messageStart..messageEnd] = messageClickableSpan
        setText(spannableWithEmojis, TextView.BufferType.SPANNABLE)

        // todo extract common badges + emote handling
        holder.scope.launch(holder.coroutineHandler) {
            allowedBadges.forEachIndexed { idx, badge ->
                try {
                    val (start, end) = badgePositions[idx]
                    val cacheKey = badge.cacheKey(baseHeight)
                    val cached = emoteManager.badgeCache[cacheKey]
                    val drawable = when {
                        cached != null -> cached.also { (it as? Animatable)?.setRunning(animateGifs) }
                        else           -> context.imageLoader
                            .execute(badge.url.toRequest(context))
                            .drawable?.apply {
                                if (badge is Badge.FFZModBadge) {
                                    val modColor = ContextCompat.getColor(context, R.color.color_ffz_mod)
                                    colorFilter = PorterDuffColorFilter(modColor, PorterDuff.Mode.DST_OVER)
                                }

                                val width = (baseHeight * intrinsicWidth / intrinsicHeight.toFloat()).roundToInt()
                                setBounds(0, 0, width, baseHeight)
                                if (this is Animatable) {
                                    emoteManager.badgeCache.put(cacheKey, this)
                                    setRunning(animateGifs)
                                }
                            }
                    }

                    if (drawable != null) {
                        val imageSpan = ImageSpan(drawable)
                        (text as Spannable)[start..end] = imageSpan
                    }
                } catch (t: Throwable) {
                    handleException(t)
                }
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && animateGifs) {
                emoteManager.gifCallback.addView(holder.binding.itemText)
            }


            val fullPrefix = prefixLength + badgesLength
            try {
                emotes
                    .groupBy { it.position }
                    .forEach { (_, emotes) ->
                        val key = emotes.cacheKey(baseHeight)
                        // fast path, backed by lru cache
                        val layerDrawable = emoteManager.layerCache[key] ?: calculateLayerDrawable(context, emotes, key, animateGifs, scaleFactor)

                        if (layerDrawable != null) {
                            (text as Spannable).setEmoteSpans(emotes.first(), fullPrefix, layerDrawable)
                        }
                    }
            } catch (t: Throwable) {
                handleException(t)
            }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    @SuppressLint("ClickableViewAccessibility")
    private fun TextView.handleTwitchMessage(twitchMessage: TwitchMessage, holder: ViewHolder, isMentionTab: Boolean): Unit = with(twitchMessage) {
        val textView = this@handleTwitchMessage
        isClickable = false
        alpha = if (timedOut) .5f else 1f
        movementMethod = LongClickLinkMovementMethod
        (text as? Spannable)?.clearSpans()

        val darkModeKey = context.getString(R.string.preference_dark_theme_key)
        val themePreferenceKey = context.getString(R.string.preference_theme_key)
        val timedOutPreferenceKey = context.getString(R.string.preference_show_timed_out_messages_key)
        val timestampPreferenceKey = context.getString(R.string.preference_timestamp_key)
        val usernamePreferenceKey = context.getString(R.string.preference_show_username_key)
        val animateGifsKey = context.getString(R.string.preference_animate_gifs_key)
        val fontSizePreferenceKey = context.getString(R.string.preference_font_size_key)
        val checkeredKey = context.getString(R.string.checkered_messages_key)
        val badgesKey = context.getString(R.string.preference_visible_badges_key)
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val isDarkMode = resources.isSystemNightMode || preferences.getString(themePreferenceKey, darkModeKey) == darkModeKey
        val isCheckeredMode = preferences.getBoolean(checkeredKey, false)
        val showTimedOutMessages = preferences.getBoolean(timedOutPreferenceKey, true)
        val showTimeStamp = preferences.getBoolean(timestampPreferenceKey, true)
        val showUserName = preferences.getBoolean(usernamePreferenceKey, true)
        val animateGifs = preferences.getBoolean(animateGifsKey, true)
        val fontSize = preferences.getInt(fontSizePreferenceKey, 14)
        val visibleBadges = preferences.getStringSet(badgesKey, resources.getStringArray(R.array.badges_entry_values).toSet()).orEmpty()
        val visibleBadgeTypes = BadgeType.mapFromPreferenceSet(visibleBadges)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat())

        val baseHeight = getBaseHeight(textSize)
        val scaleFactor = baseHeight * SCALE_FACTOR_CONSTANT
        val bgColor = when {
            timedOut && !showTimedOutMessages               -> ContextCompat.getColor(context, android.R.color.transparent)
            isNotify                                        -> ContextCompat.getColor(context, R.color.color_highlight)
            isReward                                        -> ContextCompat.getColor(context, R.color.color_reward)
            isMention                                       -> ContextCompat.getColor(context, R.color.color_mention)
            isCheckeredMode && holder.isAlternateBackground -> MaterialColors.layer(textView, android.R.attr.colorBackground, R.attr.colorSurfaceInverse, MaterialColors.ALPHA_DISABLED_LOW)
            else                                            -> ContextCompat.getColor(context, android.R.color.transparent)
        }
        setRippleBackground(bgColor, enableRipple = true)

        val textColor = MaterialColors.getColor(textView, R.attr.colorOnSurface)
        setTextColor(textColor)

        if (timedOut && !showTimedOutMessages) {
            text = when {
                showTimeStamp -> "${DateTimeUtils.timestampToLocalTime(timestamp)} ${context.getString(R.string.timed_out_message)}"
                else          -> context.getString(R.string.timed_out_message)
            }
            return
        }

        val fullName = when {
            displayName.equals(name, true) -> displayName
            else                           -> "$name($displayName)"
        }

        val fullDisplayName = when {
            !showUserName      -> ""
            isAction           -> "$fullName "
            fullName.isBlank() -> ""
            else               -> "$fullName: "
        }

        val allowedBadges = badges.filter { visibleBadgeTypes.contains(it.type) }
        val badgesLength = allowedBadges.size * 2

        val timeAndWhisperBuilder = StringBuilder()
        if (isMentionTab && isMention) timeAndWhisperBuilder.append("#$channel ")
        if (showTimeStamp) timeAndWhisperBuilder.append(DateTimeUtils.timestampToLocalTime(timestamp))

        val spannable = SpannableStringBuilder().timestampFont(context) { append(timeAndWhisperBuilder) }
        val prefixLength = spannable.length + fullDisplayName.length // spannable.length is timestamp's length (plus some extra length from extra methods call above)

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
                val mentionName = when {
                    name.equals(displayName, ignoreCase = true) -> displayName
                    else                                        -> name
                }

                override fun onClick(v: View) = onUserClicked(userId, mentionName, id, channel, badges, false)
                override fun onLongClick(view: View) = onUserClicked(userId, mentionName, id, channel, badges, true)
                override fun updateDrawState(ds: TextPaint) {
                    ds.isUnderlineText = false
                    ds.color = normalizedColor
                }
            }
            val start = prefixLength - fullDisplayName.length + badgesLength
            val end = prefixLength + badgesLength
            spannable[start..end] = userClickableSpan
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
            spannableWithEmojis[start..end] = clickableSpan
        }

        // copying message
        val messageClickableSpan = object : LongClickableSpan() {
            override fun onClick(v: View) = Unit
            override fun onLongClick(view: View) = onMessageLongClick(originalMessage)
            override fun updateDrawState(ds: TextPaint) {
                ds.isUnderlineText = false
            }

        }
        spannableWithEmojis[messageStart..messageEnd] = messageClickableSpan
        setText(spannableWithEmojis, TextView.BufferType.SPANNABLE)

        holder.scope.launch(holder.coroutineHandler) {
            allowedBadges.forEachIndexed { idx, badge ->
                try {
                    val (start, end) = badgePositions[idx]
                    val cacheKey = badge.cacheKey(baseHeight)
                    val cached = emoteManager.badgeCache[cacheKey]
                    val drawable = when {
                        cached != null -> cached.also { (it as? Animatable)?.setRunning(animateGifs) }
                        else           -> context.imageLoader
                            .execute(badge.url.toRequest(context))
                            .drawable?.apply {
                                if (badge is Badge.FFZModBadge) {
                                    val modColor = ContextCompat.getColor(context, R.color.color_ffz_mod)
                                    colorFilter = PorterDuffColorFilter(modColor, PorterDuff.Mode.DST_OVER)
                                }

                                val width = (baseHeight * intrinsicWidth / intrinsicHeight.toFloat()).roundToInt()
                                setBounds(0, 0, width, baseHeight)
                                if (this is Animatable) {
                                    emoteManager.badgeCache.put(cacheKey, this)
                                    setRunning(animateGifs)
                                }
                            }
                    }

                    if (drawable != null) {
                        val imageSpan = ImageSpan(drawable, ImageSpan.ALIGN_BASELINE)
                        (text as Spannable)[start..end] = imageSpan
                    }
                } catch (t: Throwable) {
                    handleException(t)
                }
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && animateGifs) {
                emoteManager.gifCallback.addView(holder.binding.itemText)
            }


            val fullPrefix = prefixLength + badgesLength
            try {
                emotes
                    .groupBy { it.position }
                    .forEach { (_, emotes) ->
                        val key = emotes.cacheKey(baseHeight)
                        // fast path, backed by lru cache
                        val layerDrawable = emoteManager.layerCache[key]?.also {
                            it.forEachLayer<Animatable> { animatable -> animatable.setRunning(animateGifs) }
                        } ?: calculateLayerDrawable(context, emotes, key, animateGifs, scaleFactor)

                        if (layerDrawable != null) {
                            (text as Spannable).setEmoteSpans(emotes.first(), fullPrefix, layerDrawable)
                        }
                    }
            } catch (t: Throwable) {
                handleException(t)
            }
        }
    }

    private suspend fun calculateLayerDrawable(
        context: Context,
        emotes: List<ChatMessageEmote>,
        cacheKey: String,
        animateGifs: Boolean,
        scaleFactor: Double,
    ): LayerDrawable? {
        val drawables = emotes.mapNotNull {
            val request = it.url.toRequest(context)
            context.imageLoader
                .execute(request)
                .drawable
                ?.transformEmoteDrawable(scaleFactor, it)
        }.toTypedArray()

        val bounds = drawables.map { it.bounds }
        if (bounds.isEmpty()) {
            return null
        }

        return drawables.toLayerDrawable(bounds, scaleFactor, emotes).also {
            emoteManager.layerCache.put(cacheKey, it)
            it.forEachLayer<Animatable> { animatable -> animatable.setRunning(animateGifs) }
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

    private fun Spannable.setEmoteSpans(e: ChatMessageEmote, prefix: Int, drawable: Drawable) {
        try {
            val start = e.position.first + prefix
            val end = e.position.last + prefix
            this[start..end] = ImageSpan(drawable)
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

    /** make the font monospaced, also add an extra space after it */
    private inline fun SpannableStringBuilder.timestampFont(
        context: Context, // this is required just because we need to retrieve the R.style stuff
        builderAction: SpannableStringBuilder.() -> Unit
    ): SpannableStringBuilder = inSpans(
        TypefaceSpan("monospace"),
        StyleSpan(Typeface.BOLD),
        // style adjustments to make the monospaced text looks "same size" as the normal text
        RelativeSizeSpan(MONOSPACE_FONT_PROPORTION),
        TextAppearanceSpan(context, R.style.timestamp_and_whisper), // set letter spacing using this, can't set directly in code
        builderAction = builderAction
    ).append(" ")

    /** set background color, and enable/disable ripple (whether enable or disable should match the "clickability" of that message */
    private fun TextView.setRippleBackground(@ColorInt backgroundColor: Int, enableRipple: Boolean = false) {
        val rippleBg = background as? RippleDrawable
        if (rippleBg != null) { // background is expected set to RippleDrawable via XML layout
            rippleBg.setDrawableByLayerId(R.id.ripple_color_layer, ColorDrawable(backgroundColor))
            val rippleMask = if (enableRipple) MASK_FULL else MASK_NONE
            rippleBg.setDrawableByLayerId(android.R.id.mask, rippleMask)
        } else {
            // handle some unexpected case
            setBackgroundColor(backgroundColor)
        }
    }
}

private class DetectDiff : DiffUtil.ItemCallback<ChatItem>() {
    override fun areItemsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
        return oldItem.message.id == newItem.message.id
    }

    override fun areContentsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
        return when {
            oldItem.message is TwitchMessage && newItem.message is TwitchMessage &&
                    ((!oldItem.message.timedOut && newItem.message.timedOut) || newItem.message.isMention) -> false

            else                                                                                           -> oldItem.message == newItem.message
        }
    }
}

private fun TextView.handleException(throwable: Throwable) {
    if (throwable is CancellationException) return // Ignore job cancellations

    val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    val debugKey = context.getString(R.string.preference_debug_mode_key)
    val isDebugEnabled = preferences.getBoolean(debugKey, false)

    val trace = Log.getStackTraceString(throwable)
    Log.e("DankChat-Rendering", trace)

    if (isDebugEnabled) {
        showErrorDialog(throwable, stackTraceString = trace)
    }
}