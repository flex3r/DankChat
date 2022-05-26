package com.flxrs.dankchat.chat.user

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.twitch.badge.Badge
import com.flxrs.dankchat.databinding.UserPopupBadgeItemBinding
import com.flxrs.dankchat.utils.extensions.loadImage

class UserPopupBadgeAdapter : ListAdapter<Badge, UserPopupBadgeAdapter.BadgeViewHolder>(DetectDiff()) {

    class BadgeViewHolder(val binding: UserPopupBadgeItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BadgeViewHolder {
        return BadgeViewHolder(UserPopupBadgeItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: BadgeViewHolder, position: Int) {
        val badge = getItem(position)
        val tooltip = badge.getTooltip(holder.binding.root.context)
        with(holder.binding.badgeImage) {
            if (badge is Badge.FFZModBadge) {
                val modColor = ContextCompat.getColor(context, R.color.color_ffz_mod)
                colorFilter = PorterDuffColorFilter(modColor, PorterDuff.Mode.DST_OVER)
            }

            TooltipCompat.setTooltipText(this, tooltip)
            contentDescription = tooltip
            loadImage(badge.url)
        }
    }

    private fun Badge.getTooltip(context: Context): String {
        if (this is Badge.DankChatBadge) {
            return title.orEmpty()
        }

        val tag = badgeTag ?: return title.orEmpty()
        val key = tag.substringBefore('/').ifBlank { return title.orEmpty() }
        val value = tag.substringAfter('/').ifBlank { return title.orEmpty() }
        return when (key) {
            "bits"                  -> context.getString(R.string.badge_tooltip_bits, value)
            "moderator"             -> context.getString(R.string.badge_tooltip_moderator)
            "vip"                   -> context.getString(R.string.badge_tooltip_vip)
            "predictions"           -> {
                val info = badgeInfo ?: return title.orEmpty()
                context.getString(R.string.badge_tooltip_predictions, info.replace("⸝", ","))
            }
            "subscriber", "founder" -> {
                val info = badgeInfo ?: return title.orEmpty()
                val subTier = if (value.length > 3) value.first() else "1"
                val months = info.toIntOrNull()?.let {
                    context.resources.getQuantityString(R.plurals.months, it, it)
                } ?: return title.orEmpty()

                buildString {
                    append(title)
                    append(" (")
                    if (subTier != "1") {
                        val tier = context.getString(R.string.badge_tooltip_sub_tier, subTier)
                        append(tier)
                        append(", ")
                    }
                    append(months)
                    append(")")
                }
            }
            else                    -> title.orEmpty()
        }
    }
}

private class DetectDiff : DiffUtil.ItemCallback<Badge>() {
    override fun areItemsTheSame(oldItem: Badge, newItem: Badge): Boolean = oldItem.url == newItem.url
    override fun areContentsTheSame(oldItem: Badge, newItem: Badge): Boolean = oldItem.url == newItem.url
}