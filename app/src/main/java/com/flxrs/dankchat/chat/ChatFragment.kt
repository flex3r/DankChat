package com.flxrs.dankchat.chat

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.observe
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.flxrs.dankchat.DankChatViewModel
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.ChatFragmentBinding
import com.flxrs.dankchat.preferences.TwitchAuthStore
import com.flxrs.dankchat.service.twitch.emote.GenericEmote
import com.flxrs.dankchat.utils.BadgeDrawableTarget
import com.flxrs.dankchat.utils.GifDrawableTarget
import com.flxrs.dankchat.utils.SpaceTokenizer
import com.flxrs.dankchat.utils.hideKeyboard
import kotlinx.coroutines.*
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import pl.droidsonroids.gif.GifDrawable

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
			getChat(channel).observe(viewLifecycleOwner) { adapter.submitList(it) }
			getCanType(channel).observe(viewLifecycleOwner) {
				binding.input.isEnabled = it
				binding.input.hint = if (it) "Start chatting" else "Not logged in/Disconnected"
			}
			getEmoteKeywords(channel).observe(viewLifecycleOwner) { list ->
				val adapter = EmoteSuggestionsArrayAdapter(list)
				binding.input.setAdapter(adapter)
			}
		}
		setHasOptionsMenu(true)
		return binding.root
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		when (item.itemId) {
			R.id.menu_reconnect     -> viewModel.reconnect()
			R.id.menu_clear         -> viewModel.clear(channel)
			R.id.menu_reload_emotes -> reloadEmotes()
			else                    -> return false
		}
		return true
	}

	fun clearInputFocus() {
		if (::binding.isInitialized) binding.input.clearFocus()
		hideKeyboard()
	}

	private fun setCompoundDrawable(textView: TextView, drawable: Drawable) {
		if (drawable is GifDrawable) drawable.start()
		val width = Math.round(textView.lineHeight * drawable.intrinsicWidth / drawable.intrinsicHeight.toFloat())
		drawable.setBounds(0, 0, width, textView.lineHeight)
		textView.compoundDrawablePadding = 16
		textView.setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null)
	}

	private fun reloadEmotes() {
		val authStore = TwitchAuthStore(requireContext())
		val oauth = authStore.getOAuthKey() ?: ""
		val userId = authStore.getUserId()
		viewModel.reloadEmotes(channel, oauth, userId)
	}

	private fun handleSendMessage(): Boolean {
		val msg = binding.input.text.toString()
		viewModel.sendMessage(channel, msg)
		binding.input.setText("")
		return true
	}

	private fun mentionUser(user: String) {
		if (binding.input.isEnabled) {
			val current = binding.input.text.trimEnd().toString()
			val currentWithMention = if (current.isBlank()) "$user " else "$current $user "
			binding.input.setText(currentWithMention)
			binding.input.setSelection(currentWithMention.length)
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
			} else if (dy > 0 && !isAtBottom && !recyclerView.canScrollVertically(1)) {
				isAtBottom = true
				binding.scrollBottom.visibility = View.GONE
			}
		}
	}

	private inner class EmoteSuggestionsArrayAdapter(list: List<GenericEmote>) : ArrayAdapter<GenericEmote>(requireContext(), android.R.layout.simple_dropdown_item_1line, list) {
		override fun getCount(): Int {
			val count = super.getCount()
			binding.input.dropDownHeight = (if (count > 2) binding.chat.measuredHeight / 2 else WRAP_CONTENT)
			return count
		}

		override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
			val view = super.getView(position, convertView, parent)
			val item = getItem(position) ?: return view
			view as TextView
			if (item.isGif) Glide.with(this@ChatFragment).`as`(ByteArray::class.java)
					.load(item.url)
					.placeholder(R.drawable.ic_missing_emote)
					.error(R.drawable.ic_missing_emote)
					.into(GifDrawableTarget(item.keyword, false) { setCompoundDrawable(view, it) })
			else Glide.with(this@ChatFragment).asBitmap()
					.load(item.url)
					.placeholder(R.drawable.ic_missing_emote)
					.error(R.drawable.ic_missing_emote)
					.into(BadgeDrawableTarget(requireContext()) { setCompoundDrawable(view, it) })
			return view
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