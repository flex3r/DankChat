package com.flxrs.dankchat.preferences.userdisplay

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.AddItemBinding
import com.flxrs.dankchat.databinding.UserDisplayItemBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rarepebble.colorpicker.ColorPickerView
import io.ktor.util.reflect.*

class UserDisplayAdapter(
    val onAddItem: (currentEntries: List<UserDisplayItem.Entry>) -> Unit,
    val onDeleteItem: (UserDisplayItem.Entry) -> Unit,
) :
    ListAdapter<UserDisplayItem, RecyclerView.ViewHolder>(DetectDiff()) {

    inner class EntryViewHolder(val binding: UserDisplayItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.apply {
                userDisplayDelete.setOnClickListener {
                    onDeleteItem(getItem(bindingAdapterPosition) as UserDisplayItem.Entry) // only Entry are delete-able
                }

                userDisplayPickColorButton.setOnClickListener {
                    val item = userDisplay ?: return@setOnClickListener
                    val picker = ColorPickerView(root.context)
                    picker.showAlpha(false)
                    picker.color = item.colorValue

                    MaterialAlertDialogBuilder(root.context)
                        .setView(picker)
                        .setTitle(root.context.getString(R.string.pick_custom_user_color_title))
                        .setNegativeButton(root.context.getString(R.string.dialog_cancel)) { _, _ -> }
                        .setPositiveButton(root.context.getString(R.string.dialog_ok)) { _, _ ->
                            item.color = picker.color
                            userDisplayPickColorButton.setColorAndBg(item)
                        }
                        .show()
                }

                userDisplayEnableColor.setOnCheckedChangeListener { _, checked ->
                    val item = userDisplay ?: return@setOnCheckedChangeListener
                    item.colorEnabled = checked
                    userDisplayPickColorButton.isVisible = checked
                    userDisplayPickColorButton.setColorAndBg(item)
                }

                userDisplayEnableAlias.setOnCheckedChangeListener { _, checked ->
                    val item = userDisplay ?: return@setOnCheckedChangeListener
                    item.aliasEnabled = checked
                    userDisplayAliasInput.isVisible = checked
                }
            }


        }
    }

    inner class AddItemViewHolder(val binding: AddItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.multiEntryAdd.setOnClickListener {
                onAddItem(currentEntries)
            }
        }

    }

    /** set text, text color, background color to represent specified color */
    @SuppressLint("SetTextI18n")
    private fun MaterialButton.setColorAndBg(item: UserDisplayItem.Entry) {
        text = item.displayText
        setTextColor(item.textColor(context = context))
        setBackgroundColor(item.colorValue)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            0    -> EntryViewHolder(UserDisplayItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            1    -> AddItemViewHolder(AddItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            else -> throw ClassCastException("Invalid view type $viewType")

        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is EntryViewHolder -> {
                val entry = currentList[position] as UserDisplayItem.Entry
                holder.binding.userDisplay = entry
                holder.binding.userDisplayEnableColor.isChecked = entry.colorEnabled
                holder.binding.userDisplayPickColorButton.isVisible = entry.colorEnabled
                holder.binding.userDisplayEnableAlias.isChecked = entry.aliasEnabled
                holder.binding.userDisplayAliasInput.isVisible = entry.aliasEnabled

                holder.binding.userDisplayPickColorButton.setColorAndBg(entry)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (currentList[position]) {
            is UserDisplayItem.Entry -> ENTRY_VIEW_TYPE
            else                     -> ADD_ITEM_VIEW_TYPE
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
            if (oldItem is UserDisplayItem.AddEntry) return newItem is UserDisplayItem.AddEntry
            return false
        }

        override fun areContentsTheSame(oldItem: UserDisplayItem, newItem: UserDisplayItem): Boolean {
            if (oldItem is UserDisplayItem.Entry && newItem is UserDisplayItem.Entry) {
                return oldItem == newItem
            }
            if (oldItem is UserDisplayItem.AddEntry) return newItem is UserDisplayItem.AddEntry
            return false
        }

    }

    /** convenient method for retrieving the list without "add entry" */
    val currentEntries: List<UserDisplayItem.Entry> get() = currentList.filterIsInstance<UserDisplayItem.Entry>()


}