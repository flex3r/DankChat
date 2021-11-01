package com.flxrs.dankchat.channels

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.ChannelsFragmentBinding
import com.flxrs.dankchat.main.MainFragment
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.navigateSafe
import com.flxrs.dankchat.utils.extensions.withData
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import java.util.*

@AndroidEntryPoint
class ChannelsDialogFragment : BottomSheetDialogFragment() {

    private lateinit var adapter: ChannelsAdapter
    private val args: ChannelsDialogFragmentArgs by navArgs()
    private lateinit var dankChatPreferences: DankChatPreferenceStore
    private val navController: NavController by lazy { findNavController() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val channels = args.channels.toList()
        adapter = ChannelsAdapter(::openRenameChannelDialog).also {
            it.submitList(channels)
            it.registerAdapterDataObserver(dataObserver)
        }
        val binding = ChannelsFragmentBinding.inflate(inflater, container, false).apply {
            channelsList.adapter = adapter
            val helper = ItemTouchHelper(itemTouchHelperCallback)
            helper.attachToRecyclerView(channelsList)
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        dankChatPreferences = DankChatPreferenceStore(view.context)
        val navBackStackEntry = navController.getBackStackEntry(R.id.channelsDialogFragment)
        val handle = navBackStackEntry.savedStateHandle
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            handle.keys().forEach { key ->
                when (key) {
                    RENAME_TAB_REQUEST_KEY -> handle.withData(key, ::renameChannel)
                }
            }
        }
        navBackStackEntry.lifecycle.addObserver(observer)
        viewLifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                navBackStackEntry.lifecycle.removeObserver(observer)
            }
        })
    }

    override fun onDestroy() {
        adapter.unregisterAdapterDataObserver(dataObserver)
        super.onDestroy()
    }

    override fun dismiss() {
        with(findNavController()) {
            getBackStackEntry(R.id.mainFragment)
                .savedStateHandle
                .set(MainFragment.CHANNELS_REQUEST_KEY, adapter.currentList.toTypedArray())
        }
        super.dismiss()
    }

    private fun openRenameChannelDialog(channel: String, renamedChannel: String?) {
        val direction = ChannelsDialogFragmentDirections.actionChannelsFragmentToEditChannelDialogFragment(channel, renamedChannel)
        navigateSafe(direction)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun renameChannel(rename: Pair<String, String>) {
        val (channel, name) = rename
        dankChatPreferences.setRenamedChannel(channel, name)
        adapter.notifyDataSetChanged()
    }

    private val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            adapter.currentList.toMutableList().let {
                Collections.swap(it, viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                adapter.submitList(it)
            }
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
    }

    private val dataObserver = object : RecyclerView.AdapterDataObserver() {
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            if (adapter.currentList.isEmpty()) {
                dismiss()
            }
        }
    }

    companion object {
        private val TAG = ChannelsDialogFragment::class.java.simpleName

        const val RENAME_TAB_REQUEST_KEY = "rename_channel_key"
    }
}