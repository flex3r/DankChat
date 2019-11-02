package com.flxrs.dankchat.chat

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.DankChatViewModel
import com.flxrs.dankchat.MainActivity
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.ChatFragmentBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.sharedViewModel

class ChatFragment : Fragment() {

    private val viewModel: DankChatViewModel by sharedViewModel()
    private lateinit var binding: ChatFragmentBinding
    private lateinit var adapter: ChatAdapter
    private lateinit var manager: LinearLayoutManager
    private lateinit var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener
    private lateinit var preferences: SharedPreferences

    private var isAtBottom = true
    private var channel: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        channel = requireArguments().getString(CHANNEL_ARG, "")

        manager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false).apply {
            stackFromEnd = true
        }
        adapter = ChatAdapter({ scrollToPosition(it) }, ::mentionUser, ::copyMessage)

        binding = ChatFragmentBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = this@ChatFragment
            vm = viewModel
            chat.setup(adapter, manager)
            chatLayout.layoutTransition.setAnimateParentHierarchy(false)
            scrollBottom.setOnClickListener {
                scrollBottom.visibility = View.GONE
                isAtBottom = true
                binding.chat.stopScroll()
                scrollToPosition(adapter.itemCount - 1)
            }
        }

        if (channel.isNotBlank())
            viewModel.getChat(channel).observe(viewLifecycleOwner) { adapter.submitList(it) }

        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                getString(R.string.preference_timestamp_key),
                getString(R.string.preference_show_timed_out_messages_key) -> {
                    binding.chat.swapAdapter(adapter, false)
                }
            }
        }
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext()).apply {
            registerOnSharedPreferenceChangeListener(preferenceListener)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    private fun mentionUser(user: String) = (requireActivity() as MainActivity).mentionUser(user)

    private fun copyMessage(message: String) {
        (getSystemService(
            requireContext(),
            android.content.ClipboardManager::class.java
        ) as android.content.ClipboardManager).apply {
            setPrimaryClip(android.content.ClipData.newPlainText("twitch message", message))
        }
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
            val fragment = ChatFragment()
            Bundle().apply {
                putString(CHANNEL_ARG, channel)
                fragment.arguments = this
            }
            return fragment
        }

        const val CHANNEL_ARG = "channel"
    }
}