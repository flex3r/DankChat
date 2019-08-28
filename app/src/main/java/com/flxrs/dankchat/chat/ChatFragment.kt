package com.flxrs.dankchat.chat

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.flxrs.dankchat.DankChatViewModel
import com.flxrs.dankchat.MainActivity
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.ChatFragmentBinding
import com.flxrs.dankchat.service.api.TwitchApi
import com.flxrs.dankchat.service.twitch.emote.GenericEmote
import com.flxrs.dankchat.utils.GifDrawableTarget
import com.flxrs.dankchat.utils.SpaceTokenizer
import com.flxrs.dankchat.utils.hideKeyboard
import com.flxrs.dankchat.utils.timer
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
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
    private var fetchJob: Job? = null
    private var roomState = ""
    private var liveInfo = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        channel = requireArguments().getString(CHANNEL_ARG, "")

        manager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false).apply {
            stackFromEnd = true
        }
        adapter = ChatAdapter(::scrollToPosition, ::mentionUser, ::copyMessage)

        binding = ChatFragmentBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = this@ChatFragment
            vm = viewModel
            chat.setup(adapter, manager)
            input.apply {
                setTokenizer(SpaceTokenizer())
                setOnEditorActionListener { _, actionId, _ ->
                    return@setOnEditorActionListener when (actionId) {
                        EditorInfo.IME_ACTION_SEND -> handleSendMessage()
                        else -> false
                    }
                }
                setOnKeyListener { _, keyCode, _ ->
                    return@setOnKeyListener when (keyCode) {
                        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> if (!isPopupShowing) handleSendMessage() else false
                        else -> false
                    }
                }
                chatLayout.layoutTransition.setAnimateParentHierarchy(false)
            }
            scrollBottom.setOnClickListener {
                isAtBottom = true
                binding.chat.stopScroll()
                scrollToPosition(adapter.itemCount - 1)
                scrollBottom.hide()
            }
            inputLayout.setEndIconOnClickListener { handleSendMessage() }
        }

        if (channel.isNotBlank()) viewModel.run {
            getChat(channel).observe(viewLifecycleOwner) { adapter.submitList(it) }
            getCanType(channel).observe(viewLifecycleOwner) {
                binding.input.isEnabled = it == "Start chatting"
                binding.inputLayout.hint = it
            }
            getEmoteKeywords(channel).observe(viewLifecycleOwner) { list ->
                val adapter = EmoteSuggestionsArrayAdapter(list)
                binding.input.setAdapter(adapter)
            }
            getRoomState(channel).observe(viewLifecycleOwner) { updateChannelData(roomState = it.toString()) }
        }
        updateStreamInfo()
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        binding.input.setDropDownBackgroundResource(R.color.colorPrimary)

        preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                getString(R.string.preference_timestamp_key) -> binding.chat.swapAdapter(
                    adapter,
                    false
                )
                getString(R.string.preference_roomstate_key) -> updateChannelData()
                getString(R.string.preference_streaminfo_key) -> updateStreamInfo()
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

    fun clearInputFocus() {
        if (::binding.isInitialized) binding.input.clearFocus()
        hideKeyboard()
    }

    private fun updateChannelData(
        roomState: String = this.roomState,
        liveInfo: String = this.liveInfo
    ) {
        this.roomState = roomState
        this.liveInfo = liveInfo

        if (::preferences.isInitialized) {
            val roomStateKey = getString(R.string.preference_roomstate_key)
            val liveInfoKey = getString(R.string.preference_streaminfo_key)

            val roomStateEnabled =
                preferences.getBoolean(roomStateKey, true) && roomState.isNotBlank()
            val liveInfoEnabled = preferences.getBoolean(liveInfoKey, true) && liveInfo.isNotBlank()
            val text = when {
                roomStateEnabled && liveInfoEnabled -> "$roomState - $liveInfo"
                roomStateEnabled -> roomState
                liveInfoEnabled -> liveInfo
                else -> ""
            }
            binding.inputLayout.apply {
                helperText = text
                isHelperTextEnabled = roomStateEnabled || liveInfoEnabled
            }
        }
    }

    private fun updateStreamInfo() {
        val key = getString(R.string.preference_streaminfo_key)
        fetchJob?.cancel()

        fetchJob = lifecycleScope.launchWhenCreated {
            if (::preferences.isInitialized && preferences.getBoolean(key, true)) {
                timer(STREAM_REFRESH_RATE) {
                    val stream = TwitchApi.getStream(channel)
                    if (stream != null) {
                        val text = resources.getQuantityString(
                            R.plurals.viewers,
                            stream.viewers,
                            stream.viewers
                        )
                        updateChannelData(liveInfo = text)
                    } else updateChannelData(liveInfo = "")
                }
            } else updateChannelData(liveInfo = "")
        }
    }

    private fun handleSendMessage(): Boolean {
        val msg = binding.input.text.toString()
        (requireActivity() as? MainActivity)?.handleSendMessage(channel, msg)
        binding.input.setText("")
        return true
    }

    private fun mentionUser(user: String) {
        if (binding.input.isEnabled) {
            val current = binding.input.text.trimEnd().toString()
            val template = PreferenceManager.getDefaultSharedPreferences(requireContext()).let {
                it.getString(getString(R.string.preference_mention_format_key), "name") ?: "name"
            }
            val mention = template.replace("name", user)
            val inputWithMention = if (current.isBlank()) "$mention " else "$current $mention "

            binding.input.setText(inputWithMention)
            binding.input.setSelection(inputWithMention.length)
        }
    }

    private fun copyMessage(message: String) {
        (getSystemService(
            requireContext(),
            android.content.ClipboardManager::class.java
        ) as android.content.ClipboardManager).apply {
            setPrimaryClip(android.content.ClipData.newPlainText("twitch message", message))
        }
        Snackbar.make(binding.root, R.string.snackbar_message_copied, Snackbar.LENGTH_SHORT).apply {
            view.setBackgroundResource(R.color.colorPrimary)
            setTextColor(Color.WHITE)
        }.show()
    }

    private fun scrollToPosition(position: Int) {
        if (position > 0 && isAtBottom) {
            binding.chat.stopScroll()
            binding.chat.smoothSnapToPositon(position)
            lifecycleScope.launch {
                delay(50)
                if (isAtBottom && position != adapter.itemCount - 1) binding.chat.post {
                    manager.scrollToPositionWithOffset(
                        position,
                        0
                    )
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

    private fun RecyclerView.smoothSnapToPositon(
        position: Int,
        snapMode: Int = LinearSmoothScroller.SNAP_TO_END
    ) {
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
                binding.scrollBottom.show()
            } else if (dy > 0 && !isAtBottom && !recyclerView.canScrollVertically(1)) {
                isAtBottom = true
                binding.scrollBottom.hide()
            }
        }
    }

    private inner class EmoteSuggestionsArrayAdapter(list: List<GenericEmote>) :
        ArrayAdapter<GenericEmote>(
            requireContext(),
            R.layout.emote_suggestion_item,
            R.id.suggestion_text,
            list
        ) {
        override fun getCount(): Int {
            val count = super.getCount()
            binding.input.apply {
                dropDownHeight = (if (count > 2) binding.chat.measuredHeight / 2 else WRAP_CONTENT)
                dropDownWidth = binding.chat.measuredWidth / 2
            }

            return count
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            val textView = view.findViewById<TextView>(R.id.suggestion_text)
            val imageView = view.findViewById<ImageView>(R.id.suggestion_image)
            imageView.setImageDrawable(null)
            Glide.with(imageView).clear(imageView)
            getItem(position)?.let { emote ->
                if (emote.isGif) Glide.with(imageView)
                    .`as`(ByteArray::class.java)
                    .override(textView.lineHeight)
                    .centerInside()
                    .load(emote.url)
                    .placeholder(R.drawable.ic_missing_emote)
                    .error(R.drawable.ic_missing_emote)
                    .into(
                        GifDrawableTarget(
                            emote.keyword,
                            false
                        ) { imageView.setImageDrawable(it) })
                else Glide.with(imageView)
                    .asDrawable()
                    .override(textView.lineHeight * 2)
                    .centerInside()
                    .load(emote.url)
                    .placeholder(R.drawable.ic_missing_emote)
                    .error(R.drawable.ic_missing_emote)
                    .into(imageView)
            }

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

        private const val STREAM_REFRESH_RATE = 30000L
        const val CHANNEL_ARG = "channel"
    }
}