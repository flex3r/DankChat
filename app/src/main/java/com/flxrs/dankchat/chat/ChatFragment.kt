package com.flxrs.dankchat.chat

import android.os.Bundle
import android.text.Editable
import android.util.Log
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
import com.flxrs.dankchat.MainActivity
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.ChatFragmentBinding
import org.koin.androidx.viewmodel.ext.android.sharedViewModel

class ChatFragment : Fragment() {
	private val viewModel: DankChatViewModel by sharedViewModel()
	private lateinit var binding: ChatFragmentBinding
	private var isAtBottom = true
	private var channel: String = ""


	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		channel = requireArguments().getString(CHANNEL_ARG, "")

		val manager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false).apply { stackFromEnd = true }
		val chatAdapter = ChatAdapter().apply {
			setHasStableIds(true)
			registerAdapterDataObserver(ChatAdapterDataObserver(this, manager))
		}

		binding = ChatFragmentBinding.inflate(inflater, container, false).apply {
			lifecycleOwner = this@ChatFragment
			vm = viewModel
			chat.setup(chatAdapter, manager)
			input.setOnEditorActionListener { _, actionId, _ ->
				return@setOnEditorActionListener when (actionId) {
					EditorInfo.IME_ACTION_SEND -> handleSendMessage()
					else                       -> false
				}
			}
		}

		if (channel.isNotBlank()) {
			viewModel.run {
				getChat(channel).observe(viewLifecycleOwner, Observer { chatAdapter.submitList(it) })
				getCanType(channel).observe(viewLifecycleOwner, Observer {
					binding.input.isEnabled = it
					binding.input.hint = if (it) "" else "Not logged in"
				})
			}
		}

		setHasOptionsMenu(true)
		return binding.root
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		when (item.itemId) {
			R.id.menu_reconnect     -> viewModel.reconnect()
			R.id.menu_clear         -> viewModel.clear(channel)
			R.id.menu_reload_emotes -> viewModel.reloadEmotes(channel)
			R.id.menu_remove        -> removeChannel()
			else                    -> return false
		}
		return true
	}

	private fun removeChannel() {
		Log.d("ChatFragment", "remove")
		(activity as? MainActivity)?.removeChannel(channel)
	}

	private fun handleSendMessage(): Boolean {
		val msg = binding.input.text.toString()
		viewModel.sendMessage(channel, msg)
		binding.input.text = Editable.Factory().newEditable("")
		return true
	}

	private fun RecyclerView.setup(chatAdapter: ChatAdapter, manager: LinearLayoutManager) {
		setItemViewCacheSize(30)
		adapter = chatAdapter
		layoutManager = manager
		itemAnimator = null
		addOnScrollListener(ChatScrollListener())
	}

	private inner class ChatScrollListener : RecyclerView.OnScrollListener() {
		override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
			super.onScrollStateChanged(recyclerView, newState)
			isAtBottom = !recyclerView.canScrollVertically(1)
		}
	}

	private inner class ChatAdapterDataObserver(private val chatAdapter: ChatAdapter, private val manager: LinearLayoutManager) : RecyclerView.AdapterDataObserver() {
		override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
			super.onItemRangeInserted(positionStart, itemCount)
			if (isAtBottom && chatAdapter.itemCount > 0) {
				manager.scrollToPositionWithOffset(chatAdapter.itemCount - 1, 1)
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