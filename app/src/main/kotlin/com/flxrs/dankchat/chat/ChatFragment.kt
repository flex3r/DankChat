package com.flxrs.dankchat.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.MainFragment
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.ChatFragmentBinding
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import com.flxrs.dankchat.utils.extensions.showShortSnackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
open class ChatFragment : Fragment() {

    private val viewModel: ChatViewModel by viewModels()
    protected var bindingRef: ChatFragmentBinding? = null
    protected val binding get() = bindingRef!!
    protected open lateinit var adapter: ChatAdapter
    protected open lateinit var manager: LinearLayoutManager
    protected open lateinit var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener
    protected open lateinit var preferences: SharedPreferences

    protected open var isAtBottom = true
    private var channel: String = ""

    @Inject
    lateinit var emoteManager: EmoteManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        channel = requireArguments().getString(CHANNEL_ARG, "")
        bindingRef = ChatFragmentBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = this@ChatFragment
            scrollBottom.setOnClickListener {
                scrollBottom.visibility = View.GONE
                isAtBottom = true
                binding.chat.stopScroll()
                scrollToPosition(adapter.itemCount - 1)
            }
        }

        if (channel.isNotBlank()) {
            viewModel.chat.observe(viewLifecycleOwner) { adapter.submitList(it) }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val itemDecoration = DividerItemDecoration(view.context, LinearLayoutManager.VERTICAL)
        manager = LinearLayoutManager(view.context, RecyclerView.VERTICAL, false).apply { stackFromEnd = true }
        adapter = ChatAdapter(emoteManager, ::scrollToPosition, ::mentionUser, ::copyMessage).apply { stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY }
        binding.chat.setup(adapter, manager)

        preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { pref, key ->
            context ?: return@OnSharedPreferenceChangeListener
            when (key) {
                getString(R.string.preference_timestamp_key),
                getString(R.string.preference_timestamp_format_key),
                getString(R.string.preference_show_timed_out_messages_key),
                getString(R.string.preference_animate_gifs_key),
                getString(R.string.preference_show_username_key) -> binding.chat.swapAdapter(adapter, false)
                getString(R.string.preference_line_separator_key) -> when {
                    pref.getBoolean(key, false) -> binding.chat.addItemDecoration(itemDecoration)
                    else -> binding.chat.removeItemDecoration(itemDecoration)
                }
            }
        }
        preferences = PreferenceManager.getDefaultSharedPreferences(view.context).apply {
            registerOnSharedPreferenceChangeListener(preferenceListener)
            if (getBoolean(getString(R.string.preference_line_separator_key), false)) {
                binding.chat.addItemDecoration(itemDecoration)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bindingRef = null
        if (::preferences.isInitialized) {
            preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        }
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

    protected open fun mentionUser(user: String) {
        (requireParentFragment() as? MainFragment)?.mentionUser(user)
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

    companion object {
        fun newInstance(channel: String): ChatFragment {
            return ChatFragment().apply {
                arguments = bundleOf(CHANNEL_ARG to channel)
            }
        }

        const val CHANNEL_ARG = "channel"
        private const val AT_BOTTOM_STATE = "chat_at_bottom_state"
    }
}