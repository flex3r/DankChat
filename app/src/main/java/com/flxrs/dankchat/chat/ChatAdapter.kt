package com.flxrs.dankchat.chat

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
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
import android.widget.TextView
import androidx.core.text.bold
import androidx.core.text.color
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.ChatItemBinding
import com.flxrs.dankchat.service.twitch.emote.ChatEmote
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import com.flxrs.dankchat.utils.extensions.normalizeColor
import com.linkedin.urls.detection.UrlDetector
import com.linkedin.urls.detection.UrlDetectorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.droidsonroids.gif.GifDrawable
import kotlin.math.roundToInt

class ChatAdapter(
    private val onListChanged: (position: Int) -> Unit,
    private val onUserClicked: (user: String) -> Unit,
    private val onMessageLongClick: (message: String) -> Unit
) : ListAdapter<ChatItem, ChatAdapter.ViewHolder>(DetectDiff()) {
    inner class ViewHolder(val binding: ChatItemBinding) : RecyclerView.ViewHolder(binding.root)

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
        val view = holder.binding.itemText
        EmoteManager.gifCallback.removeView(view)
        Glide.with(view).clear(view)
        holder.binding.executePendingBindings()
        super.onViewRecycled(holder)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int): Unit =
        with(holder.binding.itemText) {
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
            getItem(position).message.apply {
                if (timedOut) {
                    alpha = 0.5f

                    if (!showTimedOutMessages) {
                        text = if (showTimeStamp) {
                            "$time ${context.getString(R.string.timed_out_message)}"
                        } else context.getString(R.string.timed_out_message)
                        return@with
                    }
                }

                var ignoreClicks = false
                if (!this.isSystem) setOnLongClickListener {
                    ignoreClicks = true
                    onMessageLongClick(this.message)
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

                val name =
                    if (displayName.equals(name, true)) displayName else "$name($displayName)"
                val displayName = if (isAction) "$name " else if (name.isBlank()) "" else "$name: "
                val badgesLength = badges.size * 2
                val (prefixLength, spannable) = if (showTimeStamp) {
                    time.length + 1 + displayName.length to SpannableStringBuilder().bold { append("$time ") }
                } else {
                    displayName.length to SpannableStringBuilder()
                }

                badges.forEach { badge ->
                    spannable.append("  ")
                    val start = spannable.length - 2
                    val end = spannable.length - 1
                    Glide.with(this@with)
                        .asDrawable()
                        .load(badge.url)
                        .into(object : CustomTarget<Drawable>() {
                            override fun onLoadCleared(placeholder: Drawable?) = Unit

                            override fun onResourceReady(
                                resource: Drawable,
                                transition: Transition<in Drawable>?
                            ) {
                                val width =
                                    (lineHeight * resource.intrinsicWidth / resource.intrinsicHeight.toFloat()).roundToInt()
                                resource.setBounds(0, 0, width, lineHeight)

                                val imageSpan = ImageSpan(resource, ImageSpan.ALIGN_BOTTOM)
                                spannable.setSpan(
                                    imageSpan,
                                    start,
                                    end,
                                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                                text = spannable
                            }
                        })
                }

                val normalizedColor = color.normalizeColor(isDarkMode)
                spannable.bold { color(normalizedColor) { append(displayName) } }

                if (isAction) {
                    spannable.color(normalizedColor) { append(message) }
                } else {
                    spannable.append(message)
                }

                setText(spannable, TextView.BufferType.SPANNABLE)

                //clicking usernames
                if (name.isNotBlank()) {
                    val userClickableSpan = object : ClickableSpan() {
                        override fun updateDrawState(ds: TextPaint) {
                            ds.isUnderlineText = false
                            ds.color = normalizedColor
                        }

                        override fun onClick(v: View) {
                            if (!ignoreClicks) onUserClicked(name)
                        }
                    }
                    (text as Spannable).setSpan(
                        userClickableSpan,
                        prefixLength - displayName.length + badgesLength,
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
                    (text as Spannable).setSpan(
                        clickableSpan,
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                if (emotes.filter { it.isGif }.count() > 0) {
                    EmoteManager.gifCallback.addView(this@with)
                }

                val fullPrefix = prefixLength + badgesLength
                emotes.forEach { e ->
                    if (e.isGif) {
                        val gifDrawable = EmoteManager.gifCache[e.code]
                        if (gifDrawable != null) {
                            gifDrawable.transformEmoteDrawable(scaleFactor)
                            setEmoteSpans(e, fullPrefix, gifDrawable)
                        } else Glide.with(this@with)
                            .`as`(ByteArray::class.java)
                            .load(e.url)
                            .placeholder(R.drawable.ic_missing_emote)
                            .into(object : CustomTarget<ByteArray>() {
                                override fun onLoadStarted(placeholder: Drawable?) {
                                    if (placeholder != null) {
                                        placeholder.transformEmoteDrawable(scaleFactor)
                                        setEmoteSpans(e, fullPrefix, placeholder)
                                    }
                                }
                                override fun onLoadCleared(placeholder: Drawable?) = Unit
                                override fun onResourceReady(
                                    resource: ByteArray,
                                    transition: Transition<in ByteArray>?
                                ) {
                                    val drawable = GifDrawable(resource)
                                    drawable.callback = EmoteManager.gifCallback
                                    EmoteManager.gifCache.put(e.code, drawable)
                                    drawable.start()
                                    drawable.transformEmoteDrawable(scaleFactor)
                                    setEmoteSpans(e, fullPrefix, drawable)
                                }
                            })
                    } else Glide.with(this@with)
                        .asDrawable()
                        .load(e.url)
                        .placeholder(R.drawable.ic_missing_emote)
                        .into(object : CustomTarget<Drawable>() {
                            override fun onLoadStarted(placeholder: Drawable?) {
                                if (placeholder != null) {
                                    placeholder.transformEmoteDrawable(scaleFactor, e)
                                    setEmoteSpans(e, fullPrefix, placeholder)
                                }
                            }
                            override fun onLoadCleared(placeholder: Drawable?) = Unit
                            override fun onResourceReady(
                                resource: Drawable,
                                transition: Transition<in Drawable>?
                            ) {
                                resource.transformEmoteDrawable(scaleFactor, e)
                                setEmoteSpans(e, fullPrefix, resource)
                            }
                        })
                }
            }
        }

    private fun TextView.setEmoteSpans(
        e: ChatEmote,
        prefix: Int,
        drawable: Drawable
    ) {
        e.positions.forEach { pos ->
            val split = pos.split('-')
            val start = split[0].toInt() + prefix
            val end = split[1].toInt() + prefix
            try {
                (text as SpannableString).setSpan(
                    ImageSpan(drawable),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_INCLUSIVE
                )
            } catch (t: Throwable) {
                Log.e("ViewBinding", "$start $end ${e.code} ${(text as SpannableString).length}")
            }
        }
    }

    private fun Drawable.transformEmoteDrawable(scale: Double) {
        val height = (intrinsicHeight * scale).roundToInt()
        val width = (intrinsicWidth * scale).roundToInt()
        setBounds(0, 0, width, height)
    }

    private fun Drawable.transformEmoteDrawable(
        scale: Double,
        emote: ChatEmote
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