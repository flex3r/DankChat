package com.flxrs.dankchat.channels

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.ChannelsFragmentBinding
import com.flxrs.dankchat.main.MainFragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import java.util.*

@AndroidEntryPoint
class ChannelsDialogFragment : BottomSheetDialogFragment() {

    private lateinit var adapter: ChannelsAdapter
    private val args: ChannelsDialogFragmentArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val channels = args.channels.toList()
        adapter = ChannelsAdapter().also {
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

    override fun onDestroy() {
        adapter.unregisterAdapterDataObserver(dataObserver)
        super.onDestroy()
    }

    override fun onDismiss(dialog: DialogInterface) {
        with(findNavController()) {
            getBackStackEntry(R.id.mainFragment)
                .savedStateHandle
                .set(MainFragment.CHANNELS_REQUEST_KEY, adapter.currentList.toTypedArray())
            popBackStack(R.id.mainFragment, false)
        }
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
    }
}