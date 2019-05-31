package com.flxrs.dankchat.chat

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.PagerAdapter

class ViewPagerAdapter(private val supportFragmentManager: FragmentManager) : FragmentPagerAdapter(supportFragmentManager) {

	private val fragmentList = arrayListOf<Fragment>()
	private val titleList = arrayListOf<String>()

	override fun getItem(position: Int) = fragmentList[position
	]

	override fun getCount() = fragmentList.size

	override fun getItemPosition(`object`: Any): Int {
		return PagerAdapter.POSITION_NONE
	}

	override fun getPageTitle(position: Int) = titleList[position]

	fun addFragment(fragment: Fragment, title: String) {
		fragmentList.add(fragment)
		titleList.add(title)
		notifyDataSetChanged()
	}

	fun removeFragment(index: Int) {
		if (index < fragmentList.size - 1) {
			fragmentList.removeAt(index)
			titleList.removeAt(index)
			notifyDataSetChanged()
		}
	}
}