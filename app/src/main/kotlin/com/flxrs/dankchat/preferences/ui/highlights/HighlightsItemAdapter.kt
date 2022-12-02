package com.flxrs.dankchat.preferences.ui.highlights

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.AddItemBinding
import com.flxrs.dankchat.databinding.BlacklistedUserItemBinding
import com.flxrs.dankchat.databinding.MessageHighlightItemBinding
import com.flxrs.dankchat.databinding.UserHighlightItemBinding
import com.flxrs.dankchat.preferences.DankChatPreferenceStore

class HighlightsItemAdapter(
    private val onAddItem: () -> Unit,
    private val onDeleteItem: (item: HighlightItem) -> Unit,
    private val preferenceStore: DankChatPreferenceStore,
) : ListAdapter<HighlightItem, RecyclerView.ViewHolder>(DetectDiff()) {

    inner class MessageItemViewHolder(val binding: MessageHighlightItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.delete.setOnClickListener { onDeleteItem(getItem(bindingAdapterPosition)) }
            binding.regexInfo.setOnClickListener { CUSTOM_TABS_INTENT.launchUrl(binding.root.context, REGEX_INFO_URL) }
        }
    }

    inner class UserItemViewHolder(val binding: UserHighlightItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.delete.setOnClickListener { onDeleteItem(getItem(bindingAdapterPosition)) }
        }
    }

    inner class BlacklistedUserItemViewHolder(val binding: BlacklistedUserItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.delete.setOnClickListener { onDeleteItem(getItem(bindingAdapterPosition)) }
            binding.regexInfo.setOnClickListener { CUSTOM_TABS_INTENT.launchUrl(binding.root.context, REGEX_INFO_URL) }
        }
    }

    inner class AddViewHolder(binding: AddItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.multiEntryAdd.setOnClickListener { onAddItem() }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ITEM_VIEW_TYPE_MESSAGE          -> MessageItemViewHolder(MessageHighlightItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            ITEM_VIEW_TYPE_USER             -> UserItemViewHolder(UserHighlightItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            ITEM_VIEW_TYPE_BLACKLISTED_USER -> BlacklistedUserItemViewHolder(BlacklistedUserItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            ITEM_VIEW_TYPE_ADD              -> AddViewHolder(AddItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            else                            -> throw ClassCastException("Unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is MessageItemViewHolder         -> {
                val highlightItem = getItem(position) as MessageHighlightItem
                with(holder.binding) {
                    item = highlightItem
                    val titleText = when (highlightItem.type) {
                        MessageHighlightItem.Type.Username               -> R.string.highlights_entry_username
                        MessageHighlightItem.Type.Subscription           -> R.string.highlights_ignores_entry_subscriptions
                        MessageHighlightItem.Type.Announcement           -> R.string.highlights_ignores_entry_announcements
                        MessageHighlightItem.Type.FirstMessage           -> R.string.highlights_ignores_entry_first_messages
                        MessageHighlightItem.Type.ElevatedMessage        -> R.string.highlights_ignores_entry_elevated_messages
                        MessageHighlightItem.Type.ChannelPointRedemption -> R.string.highlights_ignores_entry_redemptions
                        MessageHighlightItem.Type.Custom                 -> R.string.highlights_ignores_entry_custom
                    }
                    title.text = root.context.getString(titleText)

                    val isCustomItem = highlightItem.type == MessageHighlightItem.Type.Custom
                    isRegex.isEnabled = isCustomItem
                    isCaseSensitive.isEnabled = isCustomItem
                    pattern.isVisible = isCustomItem
                    delete.isVisible = isCustomItem
                    regexInfo.isVisible = isCustomItem
                    enabled.isEnabled = highlightItem.type != MessageHighlightItem.Type.Username || preferenceStore.isLoggedIn
                    createNotification.isVisible = highlightItem.type == MessageHighlightItem.Type.Username || highlightItem.type == MessageHighlightItem.Type.Custom
                    createNotification.isEnabled = preferenceStore.createNotifications && (highlightItem.type != MessageHighlightItem.Type.Username || preferenceStore.isLoggedIn)
                }
            }

            is UserItemViewHolder            -> {
                val highlightItem = getItem(position) as UserHighlightItem
                with(holder.binding) {
                    item = highlightItem
                    createNotification.isEnabled = preferenceStore.createNotifications
                }
            }

            is BlacklistedUserItemViewHolder -> {
                val item = getItem(position) as BlacklistedUserItem
                holder.binding.item = item
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is MessageHighlightItem -> ITEM_VIEW_TYPE_MESSAGE
            is UserHighlightItem    -> ITEM_VIEW_TYPE_USER
            is BlacklistedUserItem  -> ITEM_VIEW_TYPE_BLACKLISTED_USER
            is AddItem              -> ITEM_VIEW_TYPE_ADD
        }
    }

    private class DetectDiff : DiffUtil.ItemCallback<HighlightItem>() {
        override fun areItemsTheSame(oldItem: HighlightItem, newItem: HighlightItem): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: HighlightItem, newItem: HighlightItem): Boolean = oldItem == newItem
    }

    companion object {
        private const val ITEM_VIEW_TYPE_MESSAGE = 0
        private const val ITEM_VIEW_TYPE_USER = 1
        private const val ITEM_VIEW_TYPE_BLACKLISTED_USER = 2
        private const val ITEM_VIEW_TYPE_ADD = 3
        private val REGEX_INFO_URL = Uri.parse("https://wiki.chatterino.com/Regex/")
        private val CUSTOM_TABS_INTENT = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
    }
}