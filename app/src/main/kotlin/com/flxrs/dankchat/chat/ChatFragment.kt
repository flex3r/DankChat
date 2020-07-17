package com.flxrs.dankchat.chat

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.DankChatViewModel
import com.flxrs.dankchat.MainFragment
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.ChatFragmentBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class ChatFragment : Fragment() {

    private val viewModel: ChatViewModel by viewModel { parametersOf(channel) }
    private lateinit var binding: ChatFragmentBinding
    private lateinit var adapter: ChatAdapter
    private lateinit var manager: LinearLayoutManager
    private lateinit var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener
    private lateinit var preferences: SharedPreferences

    private var isAtBottom = true
    private var channel: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        channel = requireArguments().getString(CHANNEL_ARG, "")
        binding = ChatFragmentBinding.inflate(inflater, container, false).apply {
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
        adapter = ChatAdapter(::scrollToPosition, ::mentionUser, ::copyMessage).apply { stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY }
        binding.chat.setup(adapter, manager)

        preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { pref, key ->
            context ?: return@OnSharedPreferenceChangeListener
            when (key) {
                getString(R.string.preference_timestamp_key),
                getString(R.string.preference_timestamp_format_key),
                getString(R.string.preference_show_timed_out_messages_key),
                getString(R.string.preference_animate_gifs_key) -> binding.chat.swapAdapter(adapter, false)
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

    override fun onDestroy() {
        super.onDestroy()
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

    private fun mentionUser(user: String) {
        (requireParentFragment() as? MainFragment)?.mentionUser(user)
    }

    private fun copyMessage(message: String) {
        getSystemService(requireContext(), android.content.ClipboardManager::class.java)?.setPrimaryClip(android.content.ClipData.newPlainText("twitch message", message))
        Snackbar.make(binding.root, R.string.snackbar_message_copied, Snackbar.LENGTH_SHORT).show()
    }

    private fun scrollToPosition(position: Int) {
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
                binding.scrollBottom.show()
            } else if (dy > 0 && !isAtBottom && !recyclerView.canScrollVertically(1)) {
                isAtBottom = true
                binding.scrollBottom.visibility = View.GONE
            }
        }
    }

    companion object {
        fun newInstance(channel: String): ChatFragment {
            return ChatFragment().apply {
                arguments = Bundle().apply { putString(CHANNEL_ARG, channel) }
            }
        }

        const val CHANNEL_ARG = "channel"
        private const val AT_BOTTOM_STATE = "chat_at_bottom_state"
    }
}