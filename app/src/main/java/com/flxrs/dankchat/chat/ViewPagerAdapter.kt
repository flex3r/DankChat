package com.flxrs.dankchat.chat

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter

class ViewPagerAdapter(supportFragmentManager: FragmentManager) : FragmentStatePagerAdapter(supportFragmentManager) {

	private val fragmentList = arrayListOf<Fragment>()
	private val titleList = arrayListOf<String>()

	override fun getItem(position: Int) = fragmentList[position]

	override fun getCount() = fragmentList.size

	override fun getPageTitle(position: Int) = titleList[position]

	fun addFragment(fragment: Fragment, title: String) {
		fragmentList.add(fragment)
		titleList.add(title)
	}

}