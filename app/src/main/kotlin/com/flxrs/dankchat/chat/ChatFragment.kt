package com.flxrs.dankchat.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.SharedPreferences
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.text.style.ImageSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.ChatFragmentBinding
import com.flxrs.dankchat.main.MainFragment
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import com.flxrs.dankchat.utils.extensions.*
import com.google.android.material.color.MaterialColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.droidsonroids.gif.GifDrawable
import javax.inject.Inject

@AndroidEntryPoint
open class ChatFragment : Fragment() {
    private val args: ChatFragmentArgs by navArgs()
    private val viewModel: ChatViewModel by viewModels()

    protected var bindingRef: ChatFragmentBinding? = null
    protected val binding get() = bindingRef!!
    protected open lateinit var adapter: ChatAdapter
    protected open lateinit var manager: LinearLayoutManager
    protected open lateinit var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener
    protected open lateinit var preferences: SharedPreferences

    protected open var isAtBottom = true

    @Inject
    lateinit var emoteManager: EmoteManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        bindingRef = ChatFragmentBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = this@ChatFragment
            scrollBottom.setOnClickListener {
                scrollBottom.visibility = View.GONE
                isAtBottom = true
                binding.chat.stopScroll()
                scrollToPosition(adapter.itemCount - 1)
            }
        }

        if (args.channel.isNotBlank()) {
            collectFlow(viewModel.chat) { adapter.submitList(it) }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val itemDecoration = DividerItemDecoration(view.context, LinearLayoutManager.VERTICAL)
        manager = LinearLayoutManager(view.context, RecyclerView.VERTICAL, false).apply { stackFromEnd = true }
        adapter = ChatAdapter(emoteManager, ::scrollToPosition, ::onUserClick, ::copyMessage).apply { stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY }
        binding.chat.setup(adapter, manager)

        preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { pref, key ->
            context ?: return@OnSharedPreferenceChangeListener
            when (key) {
                getString(R.string.preference_timestamp_key),
                getString(R.string.preference_timestamp_format_key),
                getString(R.string.preference_show_timed_out_messages_key),
                getString(R.string.preference_animate_gifs_key),
                getString(R.string.preference_show_username_key),
                getString(R.string.preference_visible_badges_key) -> binding.chat.swapAdapter(adapter, false)
                getString(R.string.preference_line_separator_key) -> when {
                    pref.getBoolean(key, false) -> binding.chat.addItemDecoration(itemDecoration)
                    else                        -> binding.chat.removeItemDecoration(itemDecoration)
                }
            }
        }
        preferences = PreferenceManager.getDefaultSharedPreferences(view.context).apply {
            registerOnSharedPreferenceChangeListener(preferenceListener)
            if (getBoolean(getString(R.string.preference_line_separator_key), false)) {
                binding.chat.addItemDecoration(itemDecoration)
            }

            val background = if (getBoolean(getString(R.string.preference_true_dark_theme_key), false) && !isSystemLightMode) {
                ContextCompat.getColor(view.context, android.R.color.black)
            } else {
                MaterialColors.getColor(view, R.attr.colorSurface)
            }
            view.setBackgroundColor(background)
        }
    }

    override fun onStart() {
        super.onStart()

        // Trigger a redraw of last 50 items to start gifs again
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && ::preferences.isInitialized && preferences.getBoolean(getString(R.string.preference_animate_gifs_key), true)) binding.chat.post {
            val start = (adapter.itemCount - MAX_MESSAGES_REDRAW_AMOUNT).coerceAtLeast(minimumValue = 0)
            val itemCount = MAX_MESSAGES_REDRAW_AMOUNT.coerceAtMost(maximumValue = adapter.itemCount)
            adapter.notifyItemRangeChanged(start, itemCount)
        }
    }

    override fun onDestroyView() {
        bindingRef = null
        if (::preferences.isInitialized) {
            preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        }

        super.onDestroyView()
    }

    override fun onStop() {
        // Stop animated drawables and related invalidation callbacks
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && activity?.isChangingConfigurations == false && ::adapter.isInitialized) {
            binding.chat.cleanupActiveDrawables(adapter.itemCount)
        }

        super.onStop()
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        savedInstanceState?.let {
            isAtBottom = it.getBoolean(AT_BOTTOM_STATE)
            binding.scrollBottom.isVisible = !isAtBottom
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(AT_BOTTOM_STATE, isAtBottom)
    }

    protected open fun onUserClick(targetUserId: String?, targetUserName: String, channel: String, isLongPress: Boolean) {
        targetUserId ?: return
        val shouldLongClickMention = preferences.getBoolean(getString(R.string.preference_user_long_click_key), true)
        val shouldMention = (isLongPress && shouldLongClickMention) || (!isLongPress && !shouldLongClickMention)

        when {
            shouldMention -> (parentFragment as? MainFragment)?.mentionUser(targetUserName)
            else          -> (parentFragment as? MainFragment)?.openUserPopup(targetUserId, channel, isWhisperPopup = false)
        }
    }

    private fun copyMessage(message: String) {
        getSystemService(requireContext(), ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("twitch message", message))
        binding.root.showShortSnackbar(getString(R.string.snackbar_message_copied)) {
            setAction(R.string.snackbar_paste) { (parentFragment as? MainFragment)?.insertText(message) }
        }
    }

    protected open fun scrollToPosition(position: Int) {
        bindingRef ?: return
        if (position > 0 && isAtBottom) {
            binding.chat.stopScroll()
            binding.chat.scrollToPosition(position)
            lifecycleScope.launch {
                delay(50)
                if (isAtBottom && position != adapter.itemCount - 1) binding.chat.post {
                    manager.scrollToPositionWithOffset(position, 0)
                }
            }
        }
    }

    private fun RecyclerView.setup(chatAdapter: ChatAdapter, manager: LinearLayoutManager) {
        setItemViewCacheSize(30)
        adapter = chatAdapter
        layoutManager = manager
        itemAnimator = null
        isNestedScrollingEnabled = false
        addOnScrollListener(ChatScrollListener())
    }

    private inner class ChatScrollListener : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dy < 0) {
                isAtBottom = false
                bindingRef?.scrollBottom?.show()
            } else if (dy > 0 && !isAtBottom && !recyclerView.canScrollVertically(1)) {
                isAtBottom = true
                bindingRef?.scrollBottom?.visibility = View.GONE
            }
        }
    }

    private fun RecyclerView.cleanupActiveDrawables(itemCount: Int) =
        forEachViewHolder<ChatAdapter.ViewHolder>(itemCount) { holder ->
            holder.binding.itemText.forEachSpan<ImageSpan> { imageSpan ->
                (imageSpan.drawable as? LayerDrawable)?.forEachLayer(GifDrawable::stop)
            }
        }

    companion object {
        private const val AT_BOTTOM_STATE = "chat_at_bottom_state"
        private const val MAX_MESSAGES_REDRAW_AMOUNT = 50

        fun newInstance(channel: String) = ChatFragment().apply {
            arguments = ChatFragmentArgs(channel).toBundle()
        }

    }
}