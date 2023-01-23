package com.flxrs.dankchat.preferences.ui.userdisplay

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.AddItemBinding
import com.flxrs.dankchat.databinding.UserDisplayItemBinding
import com.flxrs.dankchat.utils.extensions.setEnabledColor
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rarepebble.colorpicker.ColorPickerView

class UserDisplayAdapter(
    val onAddItem: (currentEntries: List<UserDisplayItem>) -> Unit,
    val onDeleteItem: (UserDisplayItem.Entry) -> Unit,
) : ListAdapter<UserDisplayItem, RecyclerView.ViewHolder>(DetectDiff()) {

    inner class EntryViewHolder(val binding: UserDisplayItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            with(binding) {
                userDisplayDelete.setOnClickListener {
                    onDeleteItem(getItem(bindingAdapterPosition) as UserDisplayItem.Entry) // only Entry is deletable
                }

                userDisplayPickColorButton.setOnClickListener {
                    val item = userDisplay ?: return@setOnClickListener
                    val picker = ColorPickerView(root.context)
                    picker.showAlpha(false)
                    picker.color = item.color

                    MaterialAlertDialogBuilder(root.context)
                        .setView(picker)
                        .setTitle(root.context.getString(R.string.pick_custom_user_color_title))
                        .setNegativeButton(root.context.getString(R.string.dialog_cancel)) { _, _ -> }
                        .setPositiveButton(root.context.getString(R.string.dialog_ok)) { _, _ ->
                            item.color = picker.color
                            userDisplayPickColorButton.updateTextAndTextColor(item)
                        }.show()
                }

                userDisplayEnable.setOnCheckedChangeListener { _, checked ->
                    val item = userDisplay ?: return@setOnCheckedChangeListener
                    item.enabled = checked
                    userDisplayEnableAlias.isEnabled = checked
                    userDisplayEnableColor.isEnabled = checked

                    userDisplayPickColorButton.isEnabled = checked && item.colorEnabled
                    userDisplayAliasInput.isEnabled = checked && item.aliasEnabled
                }

                userDisplayEnableColor.setOnCheckedChangeListener { _, checked ->
                    val item = userDisplay ?: return@setOnCheckedChangeListener
                    item.colorEnabled = checked

                    userDisplayPickColorButton.isEnabled = item.enabled && checked
                }

                userDisplayEnableAlias.setOnCheckedChangeListener { _, checked ->
                    val item = userDisplay ?: return@setOnCheckedChangeListener
                    item.aliasEnabled = checked
                    userDisplayAliasInput.isEnabled = item.enabled && checked
                }
            }
        }
    }

    inner class AddItemViewHolder(val binding: AddItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.multiEntryAdd.setOnClickListener {
                onAddItem(currentList)
            }
        }
    }

    private fun MaterialButton.updateTextAndTextColor(item: UserDisplayItem.Entry) {
        text = item.formattedDisplayColor
        setEnabledColor(item.color)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ENTRY_VIEW_TYPE    -> EntryViewHolder(UserDisplayItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            ADD_ITEM_VIEW_TYPE -> AddItemViewHolder(AddItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            else               -> throw ClassCastException("Invalid view type $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is EntryViewHolder -> {
                val entry = currentList[position] as UserDisplayItem.Entry
                with(holder.binding) {
                    Log.i("Doge", "bind handler: $entry")
                    userDisplay = entry
                    // enable checkbox handler
                    userDisplayEnable.isChecked = entry.enabled
                    userDisplayEnableColor.isEnabled = entry.enabled
                    userDisplayEnableAlias.isEnabled = entry.enabled
                    // colorEnable checkbox handler
                    userDisplayEnableColor.isChecked = entry.colorEnabled
                    userDisplayPickColorButton.isEnabled = entry.enabled && entry.colorEnabled
                    userDisplayPickColorButton.updateTextAndTextColor(entry)
                    // aliasEnable checkbox handler
                    userDisplayEnableAlias.isChecked = entry.aliasEnabled
                    userDisplayAliasInput.isEnabled = entry.enabled && entry.aliasEnabled

                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (currentList[position]) {
            is UserDisplayItem.Entry    -> ENTRY_VIEW_TYPE
            is UserDisplayItem.AddEntry -> ADD_ITEM_VIEW_TYPE
        }
    }

    companion object {
        private const val ENTRY_VIEW_TYPE = 0
        private const val ADD_ITEM_VIEW_TYPE = 1
    }

    private class DetectDiff : DiffUtil.ItemCallback<UserDisplayItem>() {
        override fun areItemsTheSame(oldItem: UserDisplayItem, newItem: UserDisplayItem): Boolean {
            if (oldItem is UserDisplayItem.Entry && newItem is UserDisplayItem.Entry) {
                return oldItem.id == newItem.id
            }
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: UserDisplayItem, newItem: UserDisplayItem) = oldItem == newItem
    }
}
