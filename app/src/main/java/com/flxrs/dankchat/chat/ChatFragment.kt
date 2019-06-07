package com.flxrs.dankchat.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.DankChatViewModel
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.ChatFragmentBinding
import com.flxrs.dankchat.utils.SpaceTokenizer
import kotlinx.coroutines.*
import org.koin.androidx.viewmodel.ext.android.sharedViewModel

class ChatFragment : Fragment() {
	private val viewModel: DankChatViewModel by sharedViewModel()
	private lateinit var binding: ChatFragmentBinding
	private lateinit var adapter: ChatAdapter
	private lateinit var manager: LinearLayoutManager
	private var isAtBottom = true
	private var channel: String = ""

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		channel = requireArguments().getString(CHANNEL_ARG, "")

		manager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false).apply {
			stackFromEnd = true
		}
		adapter = ChatAdapter(::scrollToPosition, ::mentionUser).apply {
			setHasStableIds(true)
		}

		binding = ChatFragmentBinding.inflate(inflater, container, false).apply {
			lifecycleOwner = this@ChatFragment
			vm = viewModel
			chat.setup(adapter, manager)
			input.setTokenizer(SpaceTokenizer())
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
			getChat(channel).observe(viewLifecycleOwner, Observer { adapter.submitList(it) })
			getCanType(channel).observe(viewLifecycleOwner, Observer {
				binding.input.isEnabled = it
				binding.input.hint = if (it) "Start chatting" else "Not logged in"
			})
			getEmoteKeywords(channel).observe(viewLifecycleOwner, Observer {
				val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, it)
				binding.input.setAdapter(adapter)
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

	private fun mentionUser(user: String) {
		if (binding.input.isEnabled) {
			val current = binding.input.text.trimEnd().toString().plus(" @$user, ")
			binding.input.setText(current)
		}
	}

	private fun scrollToPosition(position: Int) {
		if (position > 0 && isAtBottom) {
			binding.chat.stopScroll()
			binding.chat.smoothSnapToPositon(position)
			CoroutineScope(Dispatchers.Default + Job()).launch {
				delay(50)
				if (isAtBottom && position == adapter.itemCount - 1) {
					binding.chat.post { manager.scrollToPositionWithOffset(position, 0) }
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

	private fun RecyclerView.smoothSnapToPositon(position: Int, snapMode: Int = LinearSmoothScroller.SNAP_TO_END) {
		val smoothScroller = object : LinearSmoothScroller(this.context) {
			override fun getVerticalSnapPreference(): Int = snapMode

			override fun getHorizontalSnapPreference(): Int = snapMode
		}
		smoothScroller.targetPosition = position
		layoutManager?.startSmoothScroll(smoothScroller)
	}

	private inner class ChatScrollListener : RecyclerView.OnScrollListener() {
		override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
			if (dy < 0) {
				isAtBottom = false
				binding.scrollBottom.visibility = View.VISIBLE
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