package com.flxrs.dankchat.preferences.userdisplay

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.AddItemBinding
import com.flxrs.dankchat.databinding.UserDisplayItemBinding
import com.flxrs.dankchat.utils.extensions.getContrastTextColor
import com.flxrs.dankchat.utils.extensions.toARGBInt
import com.flxrs.dankchat.utils.extensions.toHexCode
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rarepebble.colorpicker.ColorPickerView

class UserDisplayAdapter(val entries: MutableList<UserDisplayItem>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    inner class EntryViewHolder(val binding: UserDisplayItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.userDisplayDelete.setOnClickListener {
                entries.removeAt(bindingAdapterPosition)
                notifyItemRemoved(bindingAdapterPosition)
            }

            binding.userDisplayPickColorButton.setOnClickListener {
                val item = binding.userDisplay ?: return@setOnClickListener
                val picker = ColorPickerView(binding.root.context)
                picker.showAlpha(false)

                picker.color = item.color.toARGBInt() // to correctly set initial alpha to 255
                MaterialAlertDialogBuilder(binding.root.context)
                    .setView(picker)
                    .setTitle(binding.root.context.getString(R.string.pick_custom_user_color_title))
                    .setNegativeButton(binding.root.context.getString(R.string.dialog_cancel)) { _, _ -> }
                    .setPositiveButton(binding.root.context.getString(R.string.dialog_ok)) { _, _ ->
                        val pickedColor = picker.color
                        item.color = pickedColor
                        binding.userDisplayPickColorButton.setColorAndBg(item.color)
                    }
                    .show()
            }

            (binding.userDisplayEnableColor).apply {
                setOnCheckedChangeListener { _, checked ->
                    val item = binding.userDisplay ?: return@setOnCheckedChangeListener
                    item.colorEnabled = checked
                    binding.userDisplayPickColorButton.isVisible = checked
                    binding.userDisplayPickColorButton.setColorAndBg(item.color)
                }
            }
            (binding.userDisplayEnableAlias).apply {
                setOnCheckedChangeListener { _, checked ->
                    val item = binding.userDisplay ?: return@setOnCheckedChangeListener
                    item.aliasEnabled = checked
                    binding.userDisplayAliasInput.isVisible = checked
                }
            }
        }
    }

    /** set text, text color, background color to represent specified color */
    @SuppressLint("SetTextI18n")
    private fun MaterialButton.setColorAndBg(colorRGB: Int) {
        text = "#" + colorRGB.toHexCode()
        setTextColor(colorRGB.toARGBInt().getContrastTextColor())
        setBackgroundColor(colorRGB.toARGBInt())
    }

    inner class AddItemViewHolder(val binding: AddItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.multiEntryAdd.setOnClickListener {
                // ID 0, so that the create call generate the ID
                val entry = UserDisplayItem.Entry(id = 0, username = "", color = 0, alias = "")
                val index = entries.lastIndex
                entries.add(index, entry)
                notifyItemInserted(index)
            }
        }

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
                val entry = entries[position] as UserDisplayItem.Entry
                holder.binding.userDisplay = entry
                holder.binding.userDisplayEnableColor.isChecked = entry.colorEnabled
                holder.binding.userDisplayPickColorButton.isVisible = entry.colorEnabled
                holder.binding.userDisplayEnableAlias.isChecked = entry.aliasEnabled
                holder.binding.userDisplayAliasInput.isVisible = entry.aliasEnabled

                // DANK
                holder.binding.userDisplayPickColorButton.setColorAndBg(entry.color)
            }
        }
    }

    override fun getItemCount(): Int = entries.size

    override fun getItemViewType(position: Int): Int {
        return when (entries[position]) {
            is UserDisplayItem.Entry -> ENTRY_VIEW_TYPE
            else                     -> ADD_ITEM_VIEW_TYPE
        }
    }

    companion object {
        private const val ENTRY_VIEW_TYPE = 0
        private const val ADD_ITEM_VIEW_TYPE = 1
    }

}