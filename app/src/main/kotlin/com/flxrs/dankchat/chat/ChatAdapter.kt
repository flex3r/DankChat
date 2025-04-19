package com.flxrs.dankchat.chat

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Animatable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.ImageSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TextAppearanceSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
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
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.clearSpans
import androidx.core.text.color
import androidx.core.text.getSpans
import androidx.core.text.inSpans
import androidx.core.text.set
import androidx.core.text.util.LinkifyCompat
import androidx.core.view.isVisible
import androidx.emoji2.text.EmojiCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.transform.CircleCropTransformation
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.repo.emote.EmoteRepository
import com.flxrs.dankchat.data.repo.emote.EmoteRepository.Companion.cacheKey
import com.flxrs.dankchat.data.twitch.badge.Badge
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmote
import com.flxrs.dankchat.data.twitch.message.Highlight
import com.flxrs.dankchat.data.twitch.message.HighlightType
import com.flxrs.dankchat.data.twitch.message.ModerationMessage
import com.flxrs.dankchat.data.twitch.message.NoticeMessage
import com.flxrs.dankchat.data.twitch.message.PointRedemptionMessage
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import com.flxrs.dankchat.data.twitch.message.SystemMessage
import com.flxrs.dankchat.data.twitch.message.SystemMessageType
import com.flxrs.dankchat.data.twitch.message.UserNoticeMessage
import com.flxrs.dankchat.data.twitch.message.WhisperMessage
import com.flxrs.dankchat.data.twitch.message.aliasOrFormattedName
import com.flxrs.dankchat.data.twitch.message.customOrUserColorOn
import com.flxrs.dankchat.data.twitch.message.hasMention
import com.flxrs.dankchat.data.twitch.message.highestPriorityHighlight
import com.flxrs.dankchat.data.twitch.message.recipientAliasOrFormattedName
import com.flxrs.dankchat.data.twitch.message.recipientColorOnBackground
import com.flxrs.dankchat.data.twitch.message.senderAliasOrFormattedName
import com.flxrs.dankchat.data.twitch.message.senderColorOnBackground
import com.flxrs.dankchat.databinding.ChatItemBinding
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsDataStore
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.preferences.developer.DeveloperSettingsDataStore
import com.flxrs.dankchat.utils.DateTimeUtils
import com.flxrs.dankchat.utils.extensions.forEachLayer
import com.flxrs.dankchat.utils.extensions.indexOfFirst
import com.flxrs.dankchat.utils.extensions.isEven
import com.flxrs.dankchat.utils.extensions.setRunning
import com.flxrs.dankchat.utils.showErrorDialog
import com.flxrs.dankchat.utils.span.LongClickLinkMovementMethod
import com.flxrs.dankchat.utils.span.LongClickableSpan
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class ChatAdapter(
    private val emoteRepository: EmoteRepository,
    private val dankChatPreferenceStore: DankChatPreferenceStore,
    private val chatSettingsDataStore: ChatSettingsDataStore,
    private val developerSettingsDataStore: DeveloperSettingsDataStore,
    private val appearanceSettingsDataStore: AppearanceSettingsDataStore,
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
        private val MASK_FULL = Color.argb(255, 0, 0, 0).toDrawable()
        private val MASK_NONE = Color.argb(0, 0, 0, 0).toDrawable()
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
        (holder.binding.itemText.text as? Spannable)?.clearSpans()

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
                        // add LTR mark if necessary
                        if (itemReply.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                            append('\u200E')
                        }
                        append(" @${message.thread.name}: ")
                        append(message.thread.message)
                    }
                    itemReply.text = formatted
                    itemReply.setOnClickListener { onReplyClick(message.thread.rootId) }
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
        val appearanceSettings = appearanceSettingsDataStore.current()
        val chatSettings = chatSettingsDataStore.current()
        val background = when {
            appearanceSettings.checkeredMessages && holder.isAlternateBackground -> MaterialColors.layer(
                this,
                android.R.attr.colorBackground,
                R.attr.colorSurfaceInverse,
                MaterialColors.ALPHA_DISABLED_LOW
            )

            else                                                                 -> ContextCompat.getColor(context, android.R.color.transparent)
        }
        holder.binding.itemLayout.setBackgroundColor(background)
        setBackgroundColor(background)

        val withTime = when {
            chatSettings.showTimestamps -> SpannableStringBuilder()
                .timestampFont(context) { append(DateTimeUtils.timestampToLocalTime(message.timestamp, chatSettings.formatter)) }
                .append(message.message)

            else                        -> SpannableStringBuilder().append(message.message)
        }

        setTextSize(TypedValue.COMPLEX_UNIT_SP, appearanceSettings.fontSize.toFloat())
        text = withTime
    }

    private fun TextView.handleUserNoticeMessage(message: UserNoticeMessage, holder: ViewHolder) {
        val appearanceSettings = appearanceSettingsDataStore.current()
        val chatSettings = chatSettingsDataStore.current()
        val firstHighlightType = message.highlights.firstOrNull()?.type
        val shouldHighlight = firstHighlightType == HighlightType.Subscription || firstHighlightType == HighlightType.Announcement
        val background = when {
            shouldHighlight                                                      -> ContextCompat.getColor(context, R.color.color_sub_highlight)
            appearanceSettings.checkeredMessages && holder.isAlternateBackground -> MaterialColors.layer(
                this,
                android.R.attr.colorBackground,
                R.attr.colorSurfaceInverse,
                MaterialColors.ALPHA_DISABLED_LOW
            )

            else                                                                 -> ContextCompat.getColor(context, android.R.color.transparent)
        }
        holder.binding.itemLayout.setBackgroundColor(background)
        setBackgroundColor(background)

        val withTime = when {
            chatSettings.showTimestamps -> SpannableStringBuilder()
                .timestampFont(context) { append(DateTimeUtils.timestampToLocalTime(message.timestamp, chatSettings.formatter)) }
                .append(message.message)

            else                        -> SpannableStringBuilder().append(message.message)
        }

        setTextSize(TypedValue.COMPLEX_UNIT_SP, appearanceSettings.fontSize.toFloat())
        text = withTime
    }

    private fun TextView.handleSystemMessage(message: SystemMessage, holder: ViewHolder) {
        val appearanceSettings = appearanceSettingsDataStore.current()
        val chatSettings = chatSettingsDataStore.current()
        val background = when {
            appearanceSettings.checkeredMessages && holder.isAlternateBackground -> MaterialColors.layer(
                this,
                android.R.attr.colorBackground,
                R.attr.colorSurfaceInverse,
                MaterialColors.ALPHA_DISABLED_LOW
            )

            else                                                                 -> ContextCompat.getColor(context, android.R.color.transparent)
        }
        holder.binding.itemLayout.setBackgroundColor(background)
        setRippleBackground(background, enableRipple = false)

        val systemMessageText = when (message.type) {
            is SystemMessageType.Disconnected                  -> context.getString(R.string.system_message_disconnected)
            is SystemMessageType.NoHistoryLoaded               -> context.getString(R.string.system_message_no_history)
            is SystemMessageType.Connected                     -> context.getString(R.string.system_message_connected)
            is SystemMessageType.Reconnected                   -> context.getString(R.string.system_message_reconnected)
            is SystemMessageType.LoginExpired                  -> context.getString(R.string.login_expired)
            is SystemMessageType.ChannelNonExistent            -> context.getString(R.string.system_message_channel_non_existent)
            is SystemMessageType.MessageHistoryIgnored         -> context.getString(R.string.system_message_history_ignored)
            is SystemMessageType.MessageHistoryIncomplete      -> context.getString(R.string.system_message_history_recovering)
            is SystemMessageType.ChannelBTTVEmotesFailed       -> context.getString(R.string.system_message_bttv_emotes_failed, message.type.status)
            is SystemMessageType.ChannelFFZEmotesFailed        -> context.getString(R.string.system_message_ffz_emotes_failed, message.type.status)
            is SystemMessageType.ChannelSevenTVEmotesFailed    -> context.getString(R.string.system_message_7tv_emotes_failed, message.type.status)
            is SystemMessageType.Custom                        -> message.type.message
            is SystemMessageType.MessageHistoryUnavailable     -> when (message.type.status) {
                null -> context.getString(R.string.system_message_history_unavailable)
                else -> context.getString(R.string.system_message_history_unavailable_detailed, message.type.status)
            }

            is SystemMessageType.ChannelSevenTVEmoteAdded      -> context.getString(R.string.system_message_7tv_emote_added, message.type.actorName, message.type.emoteName)
            is SystemMessageType.ChannelSevenTVEmoteRemoved    -> context.getString(R.string.system_message_7tv_emote_removed, message.type.actorName, message.type.emoteName)
            is SystemMessageType.ChannelSevenTVEmoteRenamed    -> context.getString(
                R.string.system_message_7tv_emote_renamed,
                message.type.actorName,
                message.type.oldEmoteName,
                message.type.emoteName
            )

            is SystemMessageType.ChannelSevenTVEmoteSetChanged -> context.getString(R.string.system_message_7tv_emote_set_changed, message.type.actorName, message.type.newEmoteSetName)
        }
        val withTime = when {
            chatSettings.showTimestamps -> SpannableStringBuilder()
                .timestampFont(context) { append(DateTimeUtils.timestampToLocalTime(message.timestamp, chatSettings.formatter)) }
                .append(systemMessageText)

            else                        -> SpannableStringBuilder().append(systemMessageText)
        }

        setTextSize(TypedValue.COMPLEX_UNIT_SP, appearanceSettings.fontSize.toFloat())
        text = withTime
    }

    private fun TextView.handleModerationMessage(message: ModerationMessage, holder: ViewHolder) {
        val appearanceSettings = appearanceSettingsDataStore.current()
        val chatSettings = chatSettingsDataStore.current()
        val background = when {
            appearanceSettings.checkeredMessages && holder.isAlternateBackground -> MaterialColors.layer(
                this,
                android.R.attr.colorBackground,
                R.attr.colorSurfaceInverse,
                MaterialColors.ALPHA_DISABLED_LOW
            )

            else                                                                 -> ContextCompat.getColor(context, android.R.color.transparent)
        }

        holder.binding.itemLayout.setBackgroundColor(background)
        setRippleBackground(background, enableRipple = false)

        val systemMessage = message.getSystemMessage(dankChatPreferenceStore.userName, chatSettings.showTimedOutMessages)
        val withTime = when {
            chatSettings.showTimestamps -> SpannableStringBuilder()
                .timestampFont(context) { append(DateTimeUtils.timestampToLocalTime(message.timestamp, chatSettings.formatter)) }
                .append(systemMessage)

            else                        -> SpannableStringBuilder().append(systemMessage)
        }

        setTextSize(TypedValue.COMPLEX_UNIT_SP, appearanceSettings.fontSize.toFloat())
        text = withTime
    }

    private fun TextView.handlePointRedemptionMessage(message: PointRedemptionMessage, holder: ViewHolder) {
        val appearanceSettings = appearanceSettingsDataStore.current()
        val chatSettings = chatSettingsDataStore.current()
        val background = ContextCompat.getColor(context, R.color.color_redemption_highlight)
        holder.binding.itemLayout.setBackgroundColor(background)
        setRippleBackground(background, enableRipple = false)

        setTextSize(TypedValue.COMPLEX_UNIT_SP, appearanceSettings.fontSize.toFloat())
        val baseHeight = getBaseHeight(textSize)

        holder.scope.launch(holder.coroutineHandler) {

            val spannable = buildSpannedString {
                if (chatSettings.showTimestamps) {
                    timestampFont(context) { append(DateTimeUtils.timestampToLocalTime(message.timestamp, chatSettings.formatter)) }
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
                .image
                ?.asDrawable(resources)
                ?.apply {
                    val width = (baseHeight * intrinsicWidth / intrinsicHeight.toFloat()).roundToInt()
                    setBounds(0, 0, width, baseHeight)
                    (text as Spannable)[imageStart..imageStart + 1] = ImageSpan(this, ImageSpan.ALIGN_BOTTOM)
                }
        }
    }

    private fun TextView.handleWhisperMessage(whisperMessage: WhisperMessage, holder: ViewHolder) = with(whisperMessage) {
        val appearanceSettings = appearanceSettingsDataStore.current()
        val chatSettings = chatSettingsDataStore.current()
        val textView = this@handleWhisperMessage
        isClickable = false
        movementMethod = LongClickLinkMovementMethod
        (text as? Spannable)?.clearSpans()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, appearanceSettings.fontSize.toFloat())
        val textColor = MaterialColors.getColor(textView, R.attr.colorOnSurface)
        setTextColor(textColor)

        val baseHeight = getBaseHeight(textSize)
        val scaleFactor = baseHeight * SCALE_FACTOR_CONSTANT
        val background = when {
            appearanceSettings.checkeredMessages && holder.isAlternateBackground -> MaterialColors.layer(
                textView,
                android.R.attr.colorBackground,
                R.attr.colorSurfaceInverse,
                MaterialColors.ALPHA_DISABLED_LOW
            )

            else                                                                 -> ContextCompat.getColor(context, android.R.color.transparent)
        }
        holder.binding.itemLayout.setBackgroundColor(background)
        setRippleBackground(background, enableRipple = true)

        val allowedBadges = badges.filter { it.type in chatSettings.visibleBadgeTypes }
        val badgesLength = allowedBadges.size * 2

        val spannable = SpannableStringBuilder(StringBuilder())
        if (chatSettings.showTimestamps) {
            spannable.timestampFont(context) { append(DateTimeUtils.timestampToLocalTime(timestamp, chatSettings.formatter)) }
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
        val animateGifs = chatSettings.animateGifs
        var hasAnimatedEmoteOrBadge = false
        holder.scope.launch(holder.coroutineHandler) {
            allowedBadges.forEachIndexed { idx, badge ->
                ensureActive()
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
                            .image
                            ?.asDrawable(resources)
                            ?.apply {
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
                } catch (t: CancellationException) {
                    throw t
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
                        ensureActive()
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
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                handleException(t)
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && animateGifs && hasAnimatedEmoteOrBadge) {
                emoteRepository.gifCallback.addView(holder.binding.itemText)
            }

            ensureActive()
            (text as Spannable)[0..text.length] = messageClickableSpan
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun TextView.handlePrivMessage(privMessage: PrivMessage, holder: ViewHolder, isMentionTab: Boolean): Unit = with(privMessage) {
        val appearanceSettings = appearanceSettingsDataStore.current()
        val chatSettings = chatSettingsDataStore.current()
        val textView = this@handlePrivMessage
        isClickable = false
        movementMethod = LongClickLinkMovementMethod
        (text as? Spannable)?.clearSpans()

        setTextSize(TypedValue.COMPLEX_UNIT_SP, appearanceSettings.fontSize.toFloat())

        val baseHeight = getBaseHeight(textSize)
        val scaleFactor = baseHeight * SCALE_FACTOR_CONSTANT
        val bgColor = when {
            timedOut && !chatSettings.showTimedOutMessages                       -> ContextCompat.getColor(context, android.R.color.transparent)
            highlights.isNotEmpty()                                              -> highlights.toBackgroundColor(context)
            appearanceSettings.checkeredMessages && holder.isAlternateBackground -> MaterialColors.layer(
                textView,
                android.R.attr.colorBackground,
                R.attr.colorSurfaceInverse,
                MaterialColors.ALPHA_DISABLED_LOW
            )

            else                                                                 -> ContextCompat.getColor(context, android.R.color.transparent)
        }
        holder.binding.itemLayout.setBackgroundColor(bgColor)
        setRippleBackground(bgColor, enableRipple = true)

        val textColor = MaterialColors.getColor(textView, R.attr.colorOnSurface)
        setTextColor(textColor)

        if (timedOut && !chatSettings.showTimedOutMessages) {
            text = when {
                chatSettings.showTimestamps -> buildSpannedString {
                    timestampFont(context) { append(DateTimeUtils.timestampToLocalTime(timestamp, chatSettings.formatter)) }
                    append(context.getString(R.string.timed_out_message))
                }

                else                        -> context.getString(R.string.timed_out_message)
            }
            return
        }

        val fullDisplayName = when {
            !chatSettings.showUsernames    -> ""
            isAction                       -> "$aliasOrFormattedName "
            aliasOrFormattedName.isBlank() -> ""
            else                           -> "$aliasOrFormattedName: "
        }

        val allowedBadges = badges.filter { it.type in chatSettings.visibleBadgeTypes }
        val badgesLength = allowedBadges.size * 2

        val messageBuilder = SpannableStringBuilder()
        if (isMentionTab && highlights.hasMention()) {
            messageBuilder.bold { append("#$channel ") }
        }
        if (chatSettings.showTimestamps) {
            messageBuilder.timestampFont(context) { append(DateTimeUtils.timestampToLocalTime(timestamp, chatSettings.formatter)) }
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

        val animateGifs = chatSettings.animateGifs
        var hasAnimatedEmoteOrBadge = false
        holder.scope.launch(holder.coroutineHandler) {
            allowedBadges.forEachIndexed { idx, badge ->
                try {
                    ensureActive()
                    val (start, end) = badgePositions[idx]
                    val cacheKey = badge.takeIf { it.url.isNotEmpty() }?.cacheKey(baseHeight)
                    val cached = cacheKey?.let { emoteRepository.badgeCache[it] }
                    val drawable = when {
                        cached != null -> cached.also {
                            if (it is Animatable) {
                                it.setRunning(animateGifs)
                                hasAnimatedEmoteOrBadge = true
                            }
                        }

                        else           -> {
                            val request = when (badge) {
                                is Badge.SharedChatBadge if badge.url.isEmpty() -> {
                                    ImageRequest.Builder(context)
                                        .data(R.drawable.shared_chat)
                                        .build()
                                }

                                else                                            -> badge.url.toRequest(context, circleCrop = badge is Badge.SharedChatBadge)
                            }
                            context.imageLoader
                                .execute(request)
                                .image
                                ?.asDrawable(resources)
                                ?.apply {
                                    if (badge is Badge.FFZModBadge) {
                                        val modColor = ContextCompat.getColor(context, R.color.color_ffz_mod)
                                        colorFilter = PorterDuffColorFilter(modColor, PorterDuff.Mode.DST_OVER)
                                    }

                                    val width = (baseHeight * intrinsicWidth / intrinsicHeight.toFloat()).roundToInt()
                                    setBounds(0, 0, width, baseHeight)
                                    if (this is Animatable && cacheKey != null) {
                                        emoteRepository.badgeCache.put(cacheKey, this)
                                        setRunning(animateGifs)
                                        hasAnimatedEmoteOrBadge = true
                                    }
                                }
                        }
                    }

                    if (drawable != null) {
                        val imageSpan = ImageSpan(drawable, ImageSpan.ALIGN_BASELINE)
                        (text as Spannable)[start..end] = imageSpan
                    }
                } catch (t: CancellationException) {
                    throw t
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
                        ensureActive()
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
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                handleException(t)
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && animateGifs && hasAnimatedEmoteOrBadge) {
                emoteRepository.gifCallback.addView(holder.binding.itemText)
            }

            ensureActive()
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
                .image
                ?.asDrawable(context.resources)
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

        val left = if (maxWidth > 0) (maxWidth - scaledWidth).div(2).coerceAtLeast(0) else 0
        val top = (maxHeight - scaledHeight).coerceAtLeast(0)

        setBounds(left, top, scaledWidth + left, scaledHeight + top)
        return this
    }

    private fun String.toRequest(context: Context, circleCrop: Boolean = false): ImageRequest = ImageRequest.Builder(context)
        .data(this)
        .apply { if (circleCrop) transformations(CircleCropTransformation()) else this }
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
        val trace = Log.getStackTraceString(throwable)
        Log.e("DankChat-Rendering", trace)

        if (developerSettingsDataStore.current().debugMode) {
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
