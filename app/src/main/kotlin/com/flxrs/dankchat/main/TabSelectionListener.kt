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
        tab?.setTextColor(R.attr.colorOnSurfaceVariant, layerWithSurface = true)
    }

    override fun onTabSelected(tab: TabLayout.Tab?) {
        tab?.setTextColor(R.attr.colorPrimary)
    }
}

fun TabLayout.setInitialColors() {
    val surfaceVariant = MaterialColors.getColor(this, R.attr.colorOnSurfaceVariant)
    val surface = MaterialColors.getColor(this, R.attr.colorSurface)
    val primary = MaterialColors.getColor(this, R.attr.colorPrimary)
    val layeredUnselectedColor = MaterialColors.layer(surfaceVariant, surface, UNSELECTED_TAB_OVERLAY_ALPHA)
    setTabTextColors(layeredUnselectedColor, primary)
}

fun TabLayout.Tab.setTextColor(@AttrRes id: Int, layerWithSurface: Boolean = false) {
    val textView = this.view[1] as? TextView ?: return
    val textColor = MaterialColors.getColor(textView, id).let { color ->
        when {
            layerWithSurface -> {
                val surface = MaterialColors.getColor(textView, R.attr.colorSurface)
                MaterialColors.layer(color, surface, UNSELECTED_TAB_OVERLAY_ALPHA)
            }

            else             -> color
        }
    }
    textView.setTextColor(textColor)
}

private const val UNSELECTED_TAB_OVERLAY_ALPHA = 0.25f
