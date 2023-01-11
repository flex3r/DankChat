package com.flxrs.dankchat.preferences.userdisplay

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
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
    val onAddItem: (currentEntries: List<UserDisplayItem>) -> Unit,
    val onDeleteItem: (UserDisplayItem.Entry) -> Unit,
) : ListAdapter<UserDisplayItem, RecyclerView.ViewHolder>(DetectDiff()) {

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
                    picker.color = item.color

                    MaterialAlertDialogBuilder(root.context).setView(picker).setTitle(root.context.getString(R.string.pick_custom_user_color_title))
                        .setNegativeButton(root.context.getString(R.string.dialog_cancel)) { _, _ -> }.setPositiveButton(root.context.getString(R.string.dialog_ok)) { _, _ ->
                            item.color = picker.color
                            userDisplayPickColorButton.setColorAndBg(item)
                        }.show()
                }

                userDisplayEnableColor.setOnCheckedChangeListener { _, checked ->
                    val item = userDisplay ?: return@setOnCheckedChangeListener
                    userDisplayPickColorButton.isEnabled = checked
                    userDisplayPickColorButton.setColorAndBg(item)
                }

                userDisplayEnableAlias.setOnCheckedChangeListener { _, checked ->
                    userDisplayAliasInput.isEnabled = checked
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

    /** set text, text color, background color to represent specified color */
    @SuppressLint("SetTextI18n")
    private fun MaterialButton.setColorAndBg(item: UserDisplayItem.Entry) {
        text = item.displayColor
        setTextColor(
            ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_enabled),
                    intArrayOf(-android.R.attr.state_enabled),
                ),
                intArrayOf(
                    item.color,
                    textColors.getColorForState(intArrayOf(-android.R.attr.state_enabled), Color.BLACK)
                )

            )
        )
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
                holder.binding.userDisplay = entry
                holder.binding.userDisplayEnableColor.isChecked = entry.colorEnabled
                holder.binding.userDisplayPickColorButton.isEnabled = entry.colorEnabled
                holder.binding.userDisplayEnableAlias.isChecked = entry.aliasEnabled
                holder.binding.userDisplayAliasInput.isEnabled = entry.aliasEnabled

                holder.binding.userDisplayPickColorButton.setColorAndBg(entry)
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