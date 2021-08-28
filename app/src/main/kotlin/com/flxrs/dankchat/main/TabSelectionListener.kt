package com.flxrs.dankchat.main

import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.get
import com.flxrs.dankchat.R
import com.google.android.material.tabs.TabLayout

class TabSelectionListener : TabLayout.OnTabSelectedListener {
    override fun onTabReselected(tab: TabLayout.Tab?) = Unit

    override fun onTabUnselected(tab: TabLayout.Tab?) {
        tab?.setTextColor(R.color.color_tab_unselected)
    }

    override fun onTabSelected(tab: TabLayout.Tab?) {
        tab?.setTextColor(R.color.color_tab_selected)
    }
}

fun TabLayout.Tab.setTextColor(@ColorRes id: Int) {
    val textView = this.view[1] as? TextView ?: return
    textView.setTextColor(ContextCompat.getColor(textView.context, id))
}