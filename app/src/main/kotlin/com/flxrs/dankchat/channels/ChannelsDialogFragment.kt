package com.flxrs.dankchat.channels

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.ChannelsFragmentBinding
import com.flxrs.dankchat.main.MainFragment
import com.flxrs.dankchat.preferences.ChannelWithRename
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.collectFlow
import com.flxrs.dankchat.utils.extensions.navigateSafe
import com.flxrs.dankchat.utils.extensions.swap
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ChannelsDialogFragment : BottomSheetDialogFragment() {

    @Inject
    lateinit var dankChatPreferences: DankChatPreferenceStore

    private var adapter: ChannelsAdapter? = null
    private val args: ChannelsDialogFragmentArgs by navArgs()
    private val navController: NavController by lazy { findNavController() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        adapter = ChannelsAdapter(dankChatPreferences, ::openRenameChannelDialog).also {
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
        val channels = args.channels.toList()
        collectFlow(dankChatPreferences.getChannelsWithRenamesFlow(channels)) {
            adapter?.submitList(it)
        }
    }

    override fun onDestroyView() {
        adapter?.unregisterAdapterDataObserver(dataObserver)
        adapter = null
        super.onDestroyView()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        adapter?.let {
            navController
                .getBackStackEntry(R.id.mainFragment)
                .savedStateHandle[MainFragment.CHANNELS_REQUEST_KEY] = it.currentList.toTypedArray()
        }

    }

    private fun openRenameChannelDialog(channelWithRename: ChannelWithRename) {
        val direction = ChannelsDialogFragmentDirections.actionChannelsFragmentToEditChannelDialogFragment(channelWithRename)
        navigateSafe(direction)
    }

    private val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val adapter = adapter ?: return false
            adapter.currentList.toMutableList().let {
                it.swap(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                adapter.submitList(it)
            }
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
    }

    private val dataObserver = object : RecyclerView.AdapterDataObserver() {
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            val adapter = adapter ?: return
            if (adapter.currentList.isEmpty()) {
                dismiss()
            }
        }
    }
}
