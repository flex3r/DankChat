package com.flxrs.dankchat.chat

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
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
import com.bumptech.glide.Glide
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.ChatItemBinding
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import com.flxrs.dankchat.utils.DrawableTarget
import com.flxrs.dankchat.utils.EmoteDrawableTarget
import com.flxrs.dankchat.utils.GifDrawableTarget
import com.flxrs.dankchat.utils.extensions.normalizeColor
import com.linkedin.urls.detection.UrlDetector
import com.linkedin.urls.detection.UrlDetectorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
        holder.binding.executePendingBindings()
        Glide.with(view).clear(view)

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
            val timedOutPreferenceKey = context.getString(R.string.preference_show_timed_out_messages_key)
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
                if (!this.isSystem) this@with.setOnLongClickListener {
                    ignoreClicks = true
                    onMessageLongClick(this.message)
                    true
                }

                this@with.setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        CoroutineScope(Dispatchers.Default).launch {
                            delay(200)
                            ignoreClicks = false
                        }
                    }
                    false
                }

                val lineHeight = this@with.lineHeight
                val scaleFactor = lineHeight * 1.5 / 112

                val background = when {
                    isNotify  -> if (isDarkMode) R.color.color_highlight_dark else R.color.color_highlight_light
                    isMention -> if (isDarkMode) R.color.color_mention_dark else R.color.color_mention_light
                    else      -> android.R.color.transparent
                }
                this@with.setBackgroundResource(background)

                val name = if (displayName.equals(name, true)) displayName else "$name($displayName)"
                val displayName = if (isAction) "$name " else if (name.isBlank()) "" else "$name: "
                var badgesLength = 0
                val (prefixLength, spannable) = if (showTimeStamp) {
                    time.length + 1 + displayName.length to SpannableStringBuilder().bold { append("$time ") }
                } else {
                    displayName.length to SpannableStringBuilder()
                }

                badges.forEach { badge ->
                    spannable.append("  ")
                    val start = spannable.length - 2
                    val end = spannable.length - 1
                    badgesLength += 2
                    Glide.with(this@with)
                        .asDrawable()
                        .load(badge.url)
                        .placeholder(R.drawable.ic_missing_emote)
                        .error(R.drawable.ic_missing_emote)
                        .into(DrawableTarget {
                            val width =
                                (lineHeight * it.intrinsicWidth / it.intrinsicHeight.toFloat()).roundToInt()
                            it.setBounds(0, 0, width, lineHeight)

                            val imageSpan = ImageSpan(it, ImageSpan.ALIGN_BOTTOM)
                            spannable.setSpan(
                                imageSpan,
                                start,
                                end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            text = spannable
                        })
                }

                val normalizedColor = color.normalizeColor(isDarkMode)
                spannable.bold { color(normalizedColor) { append(displayName) } }

                if (isAction) {
                    spannable.color(normalizedColor) { append(message) }
                } else {
                    spannable.append(message)
                }

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
                    spannable.setSpan(
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
                    spannable.setSpan(clickableSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    text = spannable
                }

                if (emotes.filter { it.isGif }.count() > 0) {
                    EmoteManager.gifCallback.addView(this@with)
                }

                emotes.forEach { e ->
                    e.positions.forEach { pos ->
                        val split = pos.split('-')
                        val start = split[0].toInt() + prefixLength + badgesLength
                        val end = split[1].toInt() + prefixLength + badgesLength
                        if (e.isGif) {
                            val gifDrawable = EmoteManager.gifCache[e.code]
                            if (gifDrawable != null) {
                                val height =
                                    (gifDrawable.intrinsicHeight * scaleFactor).roundToInt()
                                val width = (gifDrawable.intrinsicWidth * scaleFactor).roundToInt()
                                gifDrawable.setBounds(0, 0, width, height)

                                val imageSpan = ImageSpan(gifDrawable, ImageSpan.ALIGN_BOTTOM)
                                spannable.setSpan(
                                    imageSpan,
                                    start,
                                    end,
                                    Spannable.SPAN_EXCLUSIVE_INCLUSIVE
                                )
                                text = spannable
                            } else Glide.with(this@with)
                                .`as`(ByteArray::class.java)
                                .load(e.url)
                                .placeholder(R.drawable.ic_missing_emote)
                                .error(R.drawable.ic_missing_emote)
                                .into(GifDrawableTarget(e.code, true) {
                                    val height = (it.intrinsicHeight * scaleFactor).roundToInt()
                                    val width = (it.intrinsicWidth * scaleFactor).roundToInt()
                                    it.setBounds(0, 0, width, height)

                                    val imageSpan = ImageSpan(it, ImageSpan.ALIGN_BOTTOM)
                                    spannable.setSpan(
                                        imageSpan,
                                        start,
                                        end,
                                        Spannable.SPAN_EXCLUSIVE_INCLUSIVE
                                    )
                                    text = spannable
                                })
                        } else Glide.with(this@with)
                            .asBitmap()
                            .load(e.url)
                            .placeholder(R.drawable.ic_missing_emote)
                            .error(R.drawable.ic_missing_emote)
                            .into(EmoteDrawableTarget(e, context) {
                                val ratio = it.intrinsicWidth / it.intrinsicHeight.toFloat()
                                val height = when {
                                    it.intrinsicHeight < 55 && e.code.isBlank()       -> (70 * scaleFactor).roundToInt()
                                    it.intrinsicHeight in 55..111 && e.code.isBlank() -> (112 * scaleFactor).roundToInt()
                                    else                                              -> (it.intrinsicHeight * scaleFactor).roundToInt()
                                }
                                val width = (height * ratio).roundToInt()
                                it.setBounds(0, 0, width, height)

                                val imageSpan = ImageSpan(it, ImageSpan.ALIGN_BOTTOM)
                                spannable.setSpan(
                                    imageSpan,
                                    start,
                                    end,
                                    Spannable.SPAN_EXCLUSIVE_INCLUSIVE
                                )
                                text = spannable
                            })
                    }
                }
                text = spannable
            }
        }

    private class DetectDiff : DiffUtil.ItemCallback<ChatItem>() {
        override fun areItemsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
            return (!newItem.message.timedOut || !newItem.message.isMention) && oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
            return oldItem.message == newItem.message
        }
    }
}