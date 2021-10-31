package com.flxrs.dankchat.main

import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.get
import com.flxrs.dankchat.R
import com.google.android.material.color.MaterialColors
import com.google.android.material.tabs.TabLayout

class TabSelectionListener : TabLayout.OnTabSelectedListener {
    override fun onTabReselected(tab: TabLayout.Tab?) = Unit

    override fun onTabUnselected(tab: TabLayout.Tab?) {
        tab?.setTextColor(R.attr.colorSecondary)
    }

    override fun onTabSelected(tab: TabLayout.Tab?) {
        tab?.setTextColor(R.attr.colorPrimary)
    }
}

fun TabLayout.Tab.setTextColor(@AttrRes id: Int) {
    val textView = this.view[1] as? TextView ?: return
    textView.setTextColor(MaterialColors.getColor(textView, id))
}