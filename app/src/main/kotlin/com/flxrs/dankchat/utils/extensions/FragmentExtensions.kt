package com.flxrs.dankchat.utils.extensions

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import androidx.navigation.fragment.findNavController

private const val TAG = "FragmentExtensions"
const val NAV_DESTINATION_ID = 42420012

// From https://medium.com/@ffvanderlaan/fixing-the-dreaded-is-unknown-to-this-navcontroller-68c4003824ce
fun Fragment.navigateSafe(
    @IdRes resId: Int,
    args: Bundle? = null,
    navOptions: NavOptions? = null,
    navigatorExtras: Navigator.Extras? = null
) {
    if (mayNavigate()) findNavController().navigate(
        resId, args,
        navOptions, navigatorExtras
    )
}

fun Fragment.navigateSafe(direction: NavDirections) {
    if (mayNavigate()) findNavController().navigate(direction)
}

fun Fragment.mayNavigate(): Boolean {

    val navController = findNavController()
    val destinationIdInNavController = navController.currentDestination?.id
    val destinationIdOfThisFragment = view?.getTag(NAV_DESTINATION_ID) ?: destinationIdInNavController

    // check that the navigation graph is still in 'this' fragment, if not then the app already navigated:
    return if (destinationIdInNavController == destinationIdOfThisFragment) {
        view?.setTag(NAV_DESTINATION_ID, destinationIdOfThisFragment)
        true
    } else {
        Log.d(TAG, "May not navigate: current destination is not the current fragment.")
        false
    }
}

fun Fragment.hideKeyboard() {
    view?.let { activity?.hideKeyboard(it) }
}

val Fragment.isLandscape: Boolean
    get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

val Resources.isSystemNightMode: Boolean
    get() = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

val Resources.isSystemLightMode: Boolean
    get() = !isSystemNightMode

val Fragment.isSystemLightMode: Boolean
    get() = resources.isSystemLightMode

fun Fragment.keepScreenOn(keep: Boolean) {
    (activity as? AppCompatActivity)?.keepScreenOn(keep)
}

private fun Context.hideKeyboard(view: View) {
    val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
}

private fun AppCompatActivity.keepScreenOn(keep: Boolean) {
    if (keep) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}