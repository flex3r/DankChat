package com.flxrs.dankchat.main

import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.core.view.get
import com.flxrs.dankchat.R
import com.google.android.material.color.MaterialColors
import com.google.android.material.tabs.TabLayout

class TabSelectionListener : TabLayout.OnTabSelectedListener {
    override fun onTabReselected(tab: TabLayout.Tab?) = Unit

    override fun onTabUnselected(tab: TabLayout.Tab?) {
        tab?.setTextColor(R.attr.colorOnSurfaceVariant)
    }

    override fun onTabSelected(tab: TabLayout.Tab?) {
        tab?.setTextColor(R.attr.colorPrimary)
    }
}

fun TabLayout.Tab.setTextColor(@AttrRes id: Int, layerWithOnSurface: Boolean = false) {
    val textView = this.view[1] as? TextView ?: return
    val textColor = MaterialColors.getColor(textView, id).let { color ->
        when {
            layerWithOnSurface -> {
                val onSurface = MaterialColors.getColor(textView, R.attr.colorOnSurface)
                MaterialColors.layer(color, onSurface)
            }
            else               -> color
        }
    }
    textView.setTextColor(textColor)
}