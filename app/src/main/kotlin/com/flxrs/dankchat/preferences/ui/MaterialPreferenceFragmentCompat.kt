package com.flxrs.dankchat.preferences.ui

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.findNavController
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.flxrs.dankchat.utils.insets.RootViewDeferringInsetsCallback

abstract class MaterialPreferenceFragmentCompat : PreferenceFragmentCompat() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner) {
            findNavController().popBackStack()
        }

        val deferringInsetsListener = RootViewDeferringInsetsCallback(
            persistentInsetTypes = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            deferredInsetTypes = WindowInsetsCompat.Type.ime()
        )
        ViewCompat.setWindowInsetsAnimationCallback(view, deferringInsetsListener)
        ViewCompat.setOnApplyWindowInsetsListener(view, deferringInsetsListener)
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        when (preference) {
            is ListPreference            -> showListPreferenceDialog(preference)
            is MultiSelectListPreference -> showMultiSelectListPreferenceDialog(preference)
            else                         -> super.onDisplayPreferenceDialog(preference)
        }

    }
}
