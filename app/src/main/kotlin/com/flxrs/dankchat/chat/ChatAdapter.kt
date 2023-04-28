package com.flxrs.dankchat.chat

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.*
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
import androidx.core.view.isVisible
import androidx.emoji2.text.EmojiCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.imageLoader
import coil.request.ImageRequest
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.*
import com.flxrs.dankchat.data.repo.emote.EmoteRepository
import com.flxrs.dankchat.data.repo.emote.EmoteRepository.Companion.cacheKey
import com.flxrs.dankchat.data.twitch.badge.Badge
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmote
import com.flxrs.dankchat.data.twitch.message.*
import com.flxrs.dankchat.databinding.ChatItemBinding
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.DateTimeUtils
import com.flxrs.dankchat.utils.extensions.*
import com.flxrs.dankchat.utils.showErrorDialog
import com.flxrs.dankchat.utils.span.LongClickLinkMovementMethod
import com.flxrs.dankchat.utils.span.LongClickableSpan
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.*
import kotlin.math.roundToInt

class ChatAdapter(
    private val emoteRepository: EmoteRepository,
    private val dankChatPreferenceStore: DankChatPreferenceStore,
    private val onListChanged: (position: Int) -> Unit,
    private val onUserClick: (targetUserId: UserId?, targetUsername: UserName, targetDisplayName: DisplayName, channelName: UserName?, badges: List<Badge>, isLongPress: Boolean) -> Unit,
    private val onMessageLongClick: (messageId: String, channel: UserName?, fullMessage: String) -> Unit,
    private val onReplyClick: (messageId: String) -> Unit,
    private val onEmoteClick: (emotes: List<ChatMessageEmote>) -> Unit,
) : ListAdapter<ChatItem, ChatAdapter.ViewHolder>(DetectDiff()) {
    // Using position.isEven for determining which background to use in checkered mode doesn't work,
    // since the LayoutManager uses stackFromEnd and every new message will be even. Instead, keep count of new messages separately.
    private var messageCount = 0
        get() = field++

    companion object {
        private val DISALLOWED_URL_CHARS = """<>\{}|^"`""".toSet()
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

    inner class ViewHolder(val binding: ChatItemBinding) : RecyclerView.ViewHolder(binding.root) {
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
        with(holder.binding) {
            (itemText.text as? Spannable)?.clearSpans()
            itemText.text = ""
            itemReply.text = ""
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            emoteRepository.gifCallback.removeView(holder.binding.itemText)
        }

        super.onViewRecycled(holder)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.scope.coroutineContext.cancelChildren()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            emoteRepository.gifCallback.removeView(holder.binding.itemText)
        }

        holder.binding.replyGroup.isVisible = false
        holder.binding.itemLayout.setBackgroundColor(Color.TRANSPARENT)
        holder.binding.itemText.alpha = when (item.importance) {
            ChatImportance.SYSTEM  -> .75f
            ChatImportance.DELETED -> .5f
            ChatImportance.REGULAR -> 1f
        }

        when (val message = item.message) {
            is SystemMessage          -> holder.binding.itemText.handleSystemMessage(message, holder)
            is NoticeMessage          -> holder.binding.itemText.handleNoticeMessage(message, holder)
            is UserNoticeMessage      -> holder.binding.itemText.handleUserNoticeMessage(message, holder)
            is PrivMessage            -> with(holder.binding) {
                if (message.thread != null && !item.isInReplies) {
                    replyGroup.isVisible = true
                    val formatted = buildString {
                        append(itemReply.context.getString(R.string.reply_to))
                        append(" @${message.thread.name}: ")
                        append(message.thread.message)
                    }
                    itemReply.text = formatted
                    itemReply.setOnClickListener { onReplyClick(message.thread.id) }
                }

                itemText.handlePrivMessage(message, holder, item.isMentionTab)
            }

            is ModerationMessage      -> holder.binding.itemText.handleModerationMessage(message, holder)
            is PointRedemptionMessage -> holder.binding.itemText.handlePointRedemptionMessage(message, holder)
            is WhisperMessage         -> holder.binding.itemText.handleWhisperMessage(message, holder)
        }
    }

    private val ViewHolder.isAlternateBackground
        get() = when (bindingAdapterPosition) {
            itemCount - 1 -> messageCount.isEven
            else          -> (bindingAdapterPosition - itemCount - 1).isEven
        }

    private fun TextView.handleNoticeMessage(message: NoticeMessage, holder: ViewHolder) {
        val background = when {
            dankChatPreferenceStore.isCheckeredMode && holder.isAlternateBackground -> MaterialColors.layer(
                this,
                android.R.attr.colorBackground,
                R.attr.colorSurfaceInverse,
                MaterialColors.ALPHA_DISABLED_LOW
            )

            else                                                                    -> ContextCompat.getColor(context, android.R.color.transparent)
        }
        holder.binding.itemLayout.setBackgroundColor(background)
        setBackgroundColor(background)

        val withTime = when {
            dankChatPreferenceStore.showTimestamps -> SpannableStringBuilder()
                .timestampFont(context) { append(DateTimeUtils.timestampToLocalTime(message.timestamp)) }
                .append(message.message)

            else                                   -> SpannableStringBuilder().append(message.message)
        }

        setTextSize(TypedValue.COMPLEX_UNIT_SP, dankChatPreferenceStore.fontSize)
        text = withTime
    }

    private fun TextView.handleUserNoticeMessage(message: UserNoticeMessage, holder: ViewHolder) {
        val firstHighlightType = message.highlights.firstOrNull()?.type
        val shouldHighlight = firstHighlightType == HighlightType.Subscription || firstHighlightType == HighlightType.Announcement
        val background = when {
            shouldHighlight                                                         -> ContextCompat.getColor(context, R.color.color_sub_highlight)
            dankChatPreferenceStore.isCheckeredMode && holder.isAlternateBackground -> MaterialColors.layer(
                this,
                android.R.attr.colorBackground,
                R.attr.colorSurfaceInverse,
                MaterialColors.ALPHA_DISABLED_LOW
            )

            else                                                                    -> ContextCompat.getColor(context, android.R.color.transparent)
        }
        holder.binding.itemLayout.setBackgroundColor(background)
        setBackgroundColor(background)

        val withTime = when {
            dankChatPreferenceStore.showTimestamps -> SpannableStringBuilder()
                .timestampFont(context) { append(DateTimeUtils.timestampToLocalTime(message.timestamp)) }
                .append(message.message)

            else                                   -> SpannableStringBuilder().append(message.message)
        }

        setTextSize(TypedValue.COMPLEX_UNIT_SP, dankChatPreferenceStore.fontSize)
        text = withTime
    }

    private fun TextView.handleSystemMessage(message: SystemMessage, holder: ViewHolder) {
        val background = when {
            dankChatPreferenceStore.isCheckeredMode && holder.isAlternateBackground -> MaterialColors.layer(
                this,
                android.R.attr.colorBackground,
                R.attr.colorSurfaceInverse,
                MaterialColors.ALPHA_DISABLED_LOW
            )

            else                                                                    -> ContextCompat.getColor(context, android.R.color.transparent)
        }
        holder.binding.itemLayout.setBackgroundColor(background)
        setRippleBackground(background, enableRipple = false)

        val systemMessageText = when (message.type) {
            is SystemMessageType.Disconnected               -> context.getString(R.string.system_message_disconnected)
            is SystemMessageType.NoHistoryLoaded            -> context.getString(R.string.system_message_no_history)
            is SystemMessageType.Connected                  -> context.getString(R.string.system_message_connected)
            is SystemMessageType.Reconnected                -> context.getString(R.string.system_message_reconnected)
            is SystemMessageType.LoginExpired               -> context.getString(R.string.login_expired)
            is SystemMessageType.ChannelNonExistent         -> context.getString(R.string.system_message_channel_non_existent)
            is SystemMessageType.MessageHistoryIgnored      -> context.getString(R.string.system_message_history_ignored)
            is SystemMessageType.MessageHistoryIncomplete   -> context.getString(R.string.system_message_history_recovering)
            is SystemMessageType.ChannelBTTVEmotesFailed    -> context.getString(R.string.system_message_bttv_emotes_failed, message.type.status)
            is SystemMessageType.ChannelFFZEmotesFailed     -> context.getString(R.string.system_message_ffz_emotes_failed, message.type.status)
            is SystemMessageType.ChannelSevenTVEmotesFailed -> context.getString(R.string.system_message_7tv_emotes_failed, message.type.status)
            is SystemMessageType.Custom                     -> message.type.message
            is SystemMessageType.MessageHistoryUnavailable  -> when (message.type.status) {
                null -> context.getString(R.string.system_message_history_unavailable)
                else -> context.getString(R.string.system_message_history_unavailable_detailed, message.type.status)
            }
        }
        val withTime = when {
            dankChatPreferenceStore.showTimestamps -> SpannableStringBuilder()
                .timestampFont(context) { append(DateTimeUtils.timestampToLocalTime(message.timestamp)) }
                .append(systemMessageText)

            else                                   -> SpannableStringBuilder().append(systemMessageText)
        }

        setTextSize(TypedValue.COMPLEX_UNIT_SP, dankChatPreferenceStore.fontSize)
        text = withTime
    }

    private fun TextView.handleModerationMessage(message: ModerationMessage, holder: ViewHolder) {
        val background = when {
            dankChatPreferenceStore.isCheckeredMode && holder.isAlternateBackground -> MaterialColors.layer(
                this,
                android.R.attr.colorBackground,
                R.attr.colorSurfaceInverse,
                MaterialColors.ALPHA_DISABLED_LOW
            )

            else                                                                    -> ContextCompat.getColor(context, android.R.color.transparent)
        }

        holder.binding.itemLayout.setBackgroundColor(background)
        setRippleBackground(background, enableRipple = false)

        val systemMessage = message.getSystemMessage(dankChatPreferenceStore.userName, dankChatPreferenceStore.showTimedOutMessages)
        val withTime = when {
            dankChatPreferenceStore.showTimestamps -> SpannableStringBuilder()
                .timestampFont(context) { append(DateTimeUtils.timestampToLocalTime(message.timestamp)) }
                .append(systemMessage)

            else                                   -> SpannableStringBuilder().append(systemMessage)
        }

        setTextSize(TypedValue.COMPLEX_UNIT_SP, dankChatPreferenceStore.fontSize)
        text = withTime
    }

    private fun TextView.handlePointRedemptionMessage(message: PointRedemptionMessage, holder: ViewHolder) {
        val background = ContextCompat.getColor(context, R.color.color_redemption_highlight)
        holder.binding.itemLayout.setBackgroundColor(background)
        setRippleBackground(background, enableRipple = false)

        setTextSize(TypedValue.COMPLEX_UNIT_SP, dankChatPreferenceStore.fontSize)
        val baseHeight = getBaseHeight(textSize)

        holder.scope.launch(holder.coroutineHandler) {

            val spannable = buildSpannedString {
                if (dankChatPreferenceStore.showTimestamps) {
                    timestampFont(context) { append(DateTimeUtils.timestampToLocalTime(message.timestamp)) }
                }

                when {
                    message.requiresUserInput -> append("Redeemed ")
                    else                      -> {
                        bold { append(message.aliasOrFormattedName) }
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
        movementMethod = LongClickLinkMovementMethod
        (text as? Spannable)?.clearSpans()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, dankChatPreferenceStore.fontSize)
        val textColor = MaterialColors.getColor(textView, R.attr.colorOnSurface)
        setTextColor(textColor)

        val baseHeight = getBaseHeight(textSize)
        val scaleFactor = baseHeight * SCALE_FACTOR_CONSTANT
        val background = when {
            dankChatPreferenceStore.isCheckeredMode && holder.isAlternateBackground -> MaterialColors.layer(
                textView,
                android.R.attr.colorBackground,
                R.attr.colorSurfaceInverse,
                MaterialColors.ALPHA_DISABLED_LOW
            )

            else                                                                    -> ContextCompat.getColor(context, android.R.color.transparent)
        }
        holder.binding.itemLayout.setBackgroundColor(background)
        setRippleBackground(background, enableRipple = true)

        val allowedBadges = badges.filter { it.type in dankChatPreferenceStore.visibleBadgeTypes }
        val badgesLength = allowedBadges.size * 2

        val spannable = SpannableStringBuilder(StringBuilder())
        if (dankChatPreferenceStore.showTimestamps) {
            spannable.timestampFont(context) { append(DateTimeUtils.timestampToLocalTime(timestamp)) }
        }

        val nameGroupLength = senderAliasOrFormattedName.length + 4 + recipientAliasOrFormattedName.length + 2
        val prefixLength = spannable.length + nameGroupLength
        val badgePositions = allowedBadges.map {
            spannable.append("⠀ ")
            spannable.length - 2 to spannable.length - 1
        }

        val senderColor = senderColorOnBackground(background)
        spannable.bold { color(senderColor) { append(senderAliasOrFormattedName) } }
        spannable.append(" -> ")

        val recipientColor = recipientColorOnBackground(background)
        spannable.bold { color(recipientColor) { append(recipientAliasOrFormattedName) } }
        spannable.append(": ")
        spannable.append(message)

        val userClickableSpan = object : LongClickableSpan() {
            override fun onClick(v: View) = onUserClick(userId, name, displayName, null, badges, false)
            override fun onLongClick(view: View) = onUserClick(userId, name, displayName, null, badges, true)
            override fun updateDrawState(ds: TextPaint) {
                ds.isUnderlineText = false
                ds.color = senderColor
            }
        }
        val userStart = prefixLength + badgesLength - nameGroupLength
        val userEnd = userStart + senderAliasOrFormattedName.length
        spannable[userStart..userEnd] = userClickableSpan

        val emojiCompat = EmojiCompat.get()
        val messageStart = prefixLength + badgesLength
        val messageEnd = messageStart + message.length
        val spannableWithEmojis = when (emojiCompat.loadState) {
            EmojiCompat.LOAD_STATE_SUCCEEDED -> emojiCompat.process(spannable, messageStart, messageEnd, Int.MAX_VALUE, EmojiCompat.REPLACE_STRATEGY_NON_EXISTENT)
            else                             -> spannable
        } as SpannableStringBuilder

        val onWhisperMessageClick = {
            onMessageLongClick(id, null, spannableWithEmojis.toString().replace("⠀ ", ""))
        }

        addLinks(spannableWithEmojis, onWhisperMessageClick)

        // copying message
        val messageClickableSpan = object : LongClickableSpan(checkBounds = false) {
            override fun onClick(v: View) = Unit
            override fun onLongClick(view: View) = onWhisperMessageClick()
            override fun updateDrawState(ds: TextPaint) {
                ds.isUnderlineText = false
            }

        }
        spannableWithEmojis[0..spannableWithEmojis.length] = messageClickableSpan
        setText(spannableWithEmojis, TextView.BufferType.SPANNABLE)

        // todo extract common badges + emote handling
        val animateGifs = dankChatPreferenceStore.animateGifs
        var hasAnimatedEmoteOrBadge = false
        holder.scope.launch(holder.coroutineHandler) {
            allowedBadges.forEachIndexed { idx, badge ->
                try {
                    val (start, end) = badgePositions[idx]
                    val cacheKey = badge.cacheKey(baseHeight)
                    val cached = emoteRepository.badgeCache[cacheKey]
                    val drawable = when {
                        cached != null -> cached.also {
                            if (it is Animatable) {
                                it.setRunning(animateGifs)
                                hasAnimatedEmoteOrBadge = true
                            }
                        }

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
                                    emoteRepository.badgeCache.put(cacheKey, this)
                                    setRunning(animateGifs)
                                    hasAnimatedEmoteOrBadge = true
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

            // Remove message clickable span because emote spans have to be set before we add the span covering the full message
            (text as Spannable).removeSpan(messageClickableSpan)

            val fullPrefix = prefixLength + badgesLength
            try {
                emotes
                    .groupBy { it.position }
                    .forEach { (_, emotes) ->
                        val key = emotes.cacheKey(baseHeight)
                        // fast path, backed by lru cache
                        val layerDrawable = emoteRepository.layerCache[key] ?: calculateLayerDrawable(context, emotes, key, animateGifs, scaleFactor)
                        if (layerDrawable != null) {
                            layerDrawable.forEachLayer<Animatable> { animatable ->
                                hasAnimatedEmoteOrBadge = true
                                animatable.setRunning(animateGifs)
                            }
                            (text as Spannable).setEmoteSpans(emotes, fullPrefix, layerDrawable, onWhisperMessageClick)
                        }
                    }
            } catch (t: Throwable) {
                handleException(t)
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && animateGifs && hasAnimatedEmoteOrBadge) {
                emoteRepository.gifCallback.addView(holder.binding.itemText)
            }

            (text as Spannable)[0..text.length] = messageClickableSpan
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun TextView.handlePrivMessage(privMessage: PrivMessage, holder: ViewHolder, isMentionTab: Boolean): Unit = with(privMessage) {
        val textView = this@handlePrivMessage
        isClickable = false
        movementMethod = LongClickLinkMovementMethod
        (text as? Spannable)?.clearSpans()

        setTextSize(TypedValue.COMPLEX_UNIT_SP, dankChatPreferenceStore.fontSize)

        val baseHeight = getBaseHeight(textSize)
        val scaleFactor = baseHeight * SCALE_FACTOR_CONSTANT
        val bgColor = when {
            timedOut && !dankChatPreferenceStore.showTimedOutMessages               -> ContextCompat.getColor(context, android.R.color.transparent)
            highlights.isNotEmpty()                                                 -> highlights.toBackgroundColor(context)
            dankChatPreferenceStore.isCheckeredMode && holder.isAlternateBackground -> MaterialColors.layer(
                textView,
                android.R.attr.colorBackground,
                R.attr.colorSurfaceInverse,
                MaterialColors.ALPHA_DISABLED_LOW
            )

            else                                                                    -> ContextCompat.getColor(context, android.R.color.transparent)
        }
        holder.binding.itemLayout.setBackgroundColor(bgColor)
        setRippleBackground(bgColor, enableRipple = true)

        val textColor = MaterialColors.getColor(textView, R.attr.colorOnSurface)
        setTextColor(textColor)

        if (timedOut && !dankChatPreferenceStore.showTimedOutMessages) {
            text = when {
                dankChatPreferenceStore.showTimestamps -> buildSpannedString {
                    timestampFont(context) { append(DateTimeUtils.timestampToLocalTime(timestamp)) }
                    append(context.getString(R.string.timed_out_message))
                }

                else                                   -> context.getString(R.string.timed_out_message)
            }
            return
        }

        val fullDisplayName = when {
            !dankChatPreferenceStore.showUsername -> ""
            isAction                              -> "$aliasOrFormattedName "
            aliasOrFormattedName.isBlank()        -> ""
            else                                  -> "$aliasOrFormattedName: "
        }

        val allowedBadges = badges.filter { it.type in dankChatPreferenceStore.visibleBadgeTypes }
        val badgesLength = allowedBadges.size * 2

        val messageBuilder = SpannableStringBuilder()
        if (isMentionTab && highlights.hasMention()) {
            messageBuilder.bold { append("#$channel ") }
        }
        if (dankChatPreferenceStore.showTimestamps) {
            messageBuilder.timestampFont(context) { append(DateTimeUtils.timestampToLocalTime(timestamp)) }
        }

        val prefixLength = messageBuilder.length + fullDisplayName.length // spannable.length is timestamp's length (plus some extra length from extra methods call above)

        val badgePositions = allowedBadges.map {
            messageBuilder.append("⠀ ")
            messageBuilder.length - 2 to messageBuilder.length - 1
        }

        val nameColor = customOrUserColorOn(bgColor = bgColor)
        messageBuilder.bold { color(nameColor) { append(fullDisplayName) } }

        when {
            isAction -> messageBuilder.color(nameColor) { append(message) }
            else     -> messageBuilder.append(message)
        }

        // clicking usernames
        if (aliasOrFormattedName.isNotBlank()) {
            val userClickableSpan = object : LongClickableSpan() {
                override fun onClick(v: View) = onUserClick(userId, name, displayName, channel, badges, false)
                override fun onLongClick(view: View) = onUserClick(userId, name, displayName, channel, badges, true)
                override fun updateDrawState(ds: TextPaint) {
                    ds.isUnderlineText = false
                    ds.color = nameColor
                }
            }
            val start = prefixLength - fullDisplayName.length + badgesLength
            val end = prefixLength + badgesLength
            messageBuilder[start..end] = userClickableSpan
        }

        val emojiCompat = EmojiCompat.get()
        val messageStart = prefixLength + badgesLength
        val messageEnd = messageStart + message.length
        val spannableWithEmojis = when (emojiCompat.loadState) {
            EmojiCompat.LOAD_STATE_SUCCEEDED -> emojiCompat.process(messageBuilder, messageStart, messageEnd, Int.MAX_VALUE, EmojiCompat.REPLACE_STRATEGY_NON_EXISTENT)
            else                             -> messageBuilder
        } as SpannableStringBuilder

        val onMessageClick = {
            onMessageLongClick(id, channel, spannableWithEmojis.toString().replace("⠀ ", ""))
        }

        addLinks(spannableWithEmojis, onMessageClick)
        if (thread != null) {
            holder.binding.itemReply.setOnLongClickListener {
                onMessageClick()
                true
            }
        }

        // copying message
        val messageClickableSpan = object : LongClickableSpan(checkBounds = false) {
            override fun onClick(v: View) = Unit
            override fun onLongClick(view: View) = onMessageClick()
            override fun updateDrawState(ds: TextPaint) {
                ds.isUnderlineText = false
            }
        }
        spannableWithEmojis[0..spannableWithEmojis.length] = messageClickableSpan
        setText(spannableWithEmojis, TextView.BufferType.SPANNABLE)

        val animateGifs = dankChatPreferenceStore.animateGifs
        var hasAnimatedEmoteOrBadge = false
        holder.scope.launch(holder.coroutineHandler) {
            allowedBadges.forEachIndexed { idx, badge ->
                try {
                    val (start, end) = badgePositions[idx]
                    val cacheKey = badge.cacheKey(baseHeight)
                    val cached = emoteRepository.badgeCache[cacheKey]
                    val drawable = when {
                        cached != null -> cached.also {
                            if (it is Animatable) {
                                it.setRunning(animateGifs)
                                hasAnimatedEmoteOrBadge = true
                            }
                        }

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
                                    emoteRepository.badgeCache.put(cacheKey, this)
                                    setRunning(animateGifs)
                                    hasAnimatedEmoteOrBadge = true
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

            // Remove message clickable span because emote spans have to be set before we add the span covering the full message
            (text as Spannable).removeSpan(messageClickableSpan)

            val fullPrefix = prefixLength + badgesLength
            try {
                emotes
                    .groupBy { it.position }
                    .forEach { (_, emotes) ->
                        val key = emotes.cacheKey(baseHeight)
                        // fast path, backed by lru cache
                        val layerDrawable = emoteRepository.layerCache[key] ?: calculateLayerDrawable(context, emotes, key, animateGifs, scaleFactor)
                        if (layerDrawable != null) {
                            layerDrawable.forEachLayer<Animatable> { animatable ->
                                hasAnimatedEmoteOrBadge = true
                                animatable.setRunning(animateGifs)
                            }
                            (text as Spannable).setEmoteSpans(emotes, fullPrefix, layerDrawable, onMessageClick)
                        }
                    }
            } catch (t: Throwable) {
                handleException(t)
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && animateGifs && hasAnimatedEmoteOrBadge) {
                emoteRepository.gifCallback.addView(holder.binding.itemText)
            }

            (text as Spannable)[0..text.length] = messageClickableSpan
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

        return drawables.toLayerDrawable(bounds, scaleFactor, emotes).also { layerDrawable ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && animateGifs && drawables.any { it is Animatable }) {
                layerDrawable.callback = emoteRepository.gifCallback
            }
            emoteRepository.layerCache.put(cacheKey, layerDrawable)
        }
    }

    private fun Array<Drawable>.toLayerDrawable(bounds: List<Rect>, scaleFactor: Double, emotes: List<ChatMessageEmote>): LayerDrawable = LayerDrawable(this).apply {
        val maxWidth = bounds.maxOf { it.width() }
        val maxHeight = bounds.maxOf { it.height() }
        setBounds(0, 0, maxWidth, maxHeight)

        // set bounds again but adjust by maximum width/height of stacked drawables
        forEachIndexed { idx, dr -> dr.transformEmoteDrawable(scaleFactor, emotes[idx], maxWidth, maxHeight) }
    }

    private fun Spannable.setEmoteSpans(emotes: List<ChatMessageEmote>, prefix: Int, drawable: Drawable, onLongClick: () -> Unit) {
        try {
            val position = emotes.first().position
            val start = position.first + prefix
            val end = position.last + prefix
            this[start..end] = ImageSpan(drawable)
            this[start..end] = object : LongClickableSpan() {
                override fun onLongClick(view: View) = onLongClick()
                override fun onClick(widget: View) = onEmoteClick(emotes)
            }
        } catch (t: Throwable) {
            val firstEmote = emotes.firstOrNull()
            Log.e("ViewBinding", "$t $this ${firstEmote?.position} ${firstEmote?.code} $length")
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
    private fun View.setRippleBackground(@ColorInt backgroundColor: Int, enableRipple: Boolean = false) {
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

    @ColorInt
    private fun Set<Highlight>.toBackgroundColor(context: Context): Int {
        val highlight = highestPriorityHighlight() ?: return ContextCompat.getColor(context, android.R.color.transparent)
        return when (highlight.type) {
            HighlightType.Subscription, HighlightType.Announcement -> ContextCompat.getColor(context, R.color.color_sub_highlight)
            HighlightType.ChannelPointRedemption                   -> ContextCompat.getColor(context, R.color.color_redemption_highlight)
            HighlightType.ElevatedMessage                          -> ContextCompat.getColor(context, R.color.color_elevated_message_highlight)
            HighlightType.FirstMessage                             -> ContextCompat.getColor(context, R.color.color_first_message_highlight)
            HighlightType.Username                                 -> ContextCompat.getColor(context, R.color.color_mention_highlight)
            HighlightType.Custom                                   -> ContextCompat.getColor(context, R.color.color_mention_highlight)
            HighlightType.Reply                                    -> ContextCompat.getColor(context, R.color.color_mention_highlight)
            HighlightType.Notification                             -> ContextCompat.getColor(context, R.color.color_mention_highlight)
        }
    }

    private fun TextView.addLinks(spannableWithEmojis: SpannableStringBuilder, onLongClick: () -> Unit) {
        LinkifyCompat.addLinks(spannableWithEmojis, Linkify.WEB_URLS)
        spannableWithEmojis.getSpans<URLSpan>().forEach { urlSpan ->
            val start = spannableWithEmojis.getSpanStart(urlSpan)
            val end = spannableWithEmojis.getSpanEnd(urlSpan)
            spannableWithEmojis.removeSpan(urlSpan)

            val fixedEnd = spannableWithEmojis
                .indexOfFirst(startIndex = end) { it.isWhitespace() || it in DISALLOWED_URL_CHARS }
                .takeIf { it != -1 } ?: end
            val fixedUrl = when (fixedEnd) {
                end  -> urlSpan.url
                else -> urlSpan.url + spannableWithEmojis.substring(end..fixedEnd)
            }

            // skip partial link matches
            val previousChar = spannableWithEmojis.getOrNull(index = start - 1)
            if (previousChar != null && !previousChar.isWhitespace()) {
                return@forEach
            }

            val clickableSpan = object : LongClickableSpan() {
                override fun onLongClick(view: View) = onLongClick()
                override fun onClick(v: View) {
                    try {
                        customTabsIntent.launchUrl(context, fixedUrl.toUri())
                    } catch (e: ActivityNotFoundException) {
                        Log.e("ViewBinding", Log.getStackTraceString(e))
                    }
                }

                override fun updateDrawState(ds: TextPaint) {
                    ds.color = ds.linkColor
                    ds.isUnderlineText = false
                }
            }
            spannableWithEmojis[start..fixedEnd] = clickableSpan
        }
    }

    private fun TextView.handleException(throwable: Throwable) {
        if (throwable is CancellationException) return // Ignore job cancellations

        val trace = Log.getStackTraceString(throwable)
        Log.e("DankChat-Rendering", trace)

        if (dankChatPreferenceStore.debugEnabled) {
            showErrorDialog(throwable, stackTraceString = trace)
        }
    }
}

private class DetectDiff : DiffUtil.ItemCallback<ChatItem>() {
    override fun areItemsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
        return oldItem.tag == newItem.tag && oldItem.message.id == newItem.message.id
    }

    override fun areContentsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
        return when {
            newItem.message.highlights.hasMention() || (oldItem.importance != newItem.importance) -> false
            else                                                                                  -> oldItem.message == newItem.message
        }
    }
}
