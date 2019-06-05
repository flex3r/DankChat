package com.flxrs.dankchat.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.DankChatViewModel
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.ChatFragmentBinding
import org.koin.androidx.viewmodel.ext.android.sharedViewModel

class ChatFragment : Fragment() {
	private val viewModel: DankChatViewModel by sharedViewModel()
	private lateinit var binding: ChatFragmentBinding
	private lateinit var adapter: ChatAdapter
	private lateinit var manager: LinearLayoutManager
	private var isAtBottom = false
	private var channel: String = ""

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		channel = requireArguments().getString(CHANNEL_ARG, "")

		manager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false).apply {
			stackFromEnd = true
		}
		adapter = ChatAdapter().apply {
			setHasStableIds(true)
			registerAdapterDataObserver(ChatAdapterDataObserver())
		}

		binding = ChatFragmentBinding.inflate(inflater, container, false).apply {
			lifecycleOwner = this@ChatFragment
			vm = viewModel
			chat.setup(adapter, manager)
			input.setOnEditorActionListener { _, actionId, _ ->
				return@setOnEditorActionListener when (actionId) {
					EditorInfo.IME_ACTION_SEND -> handleSendMessage()
					else                       -> false
				}
			}
			scrollBottom.setOnClickListener {
				isAtBottom = true
				binding.chat.stopScroll()
				scrollToPosition(adapter.itemCount - 1)
				it.visibility = View.GONE
			}
		}

		if (channel.isNotBlank()) viewModel.run {
			getChat(channel).observe(viewLifecycleOwner, Observer {
				adapter.submitList(it)
				if (it.isNotEmpty() && it.last().historic) {
					scrollToPosition(it.size - 1)
				}
			})
			getCanType(channel).observe(viewLifecycleOwner, Observer {
				binding.input.isEnabled = it
				binding.input.hint = if (it) "Start chatting" else "Not logged in"
			})
		}

		setHasOptionsMenu(true)
		return binding.root
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		when (item.itemId) {
			R.id.menu_reconnect     -> viewModel.reconnect()
			R.id.menu_clear         -> viewModel.clear(channel)
			R.id.menu_reload_emotes -> viewModel.reloadEmotes(channel)
			else                    -> return false
		}
		return true
	}

	private fun handleSendMessage(): Boolean {
		val msg = binding.input.text.toString()
		viewModel.sendMessage(channel, msg)
		binding.input.setText("")
		return true
	}

	private fun scrollToPosition(position: Int) {
		if (position > 0) {
			binding.chat.smoothScrollToPosition(position)
			binding.chat.smoothScrollBy(0, 100)
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
				binding.scrollBottom.visibility = View.VISIBLE
			}
		}
	}

	private inner class ChatAdapterDataObserver : RecyclerView.AdapterDataObserver() {
		override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
			if (isAtBottom) scrollToPosition(positionStart + itemCount)
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