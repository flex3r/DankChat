package com.flxrs.dankchat.changelog

import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.ChangelogBottomsheetBinding
import com.flxrs.dankchat.utils.extensions.isLandscape
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ChangelogSheetFragment : BottomSheetDialogFragment() {

    private val viewModel: ChangelogSheetViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val adapter = ChangelogAdapter()
        val binding = ChangelogBottomsheetBinding.inflate(inflater, container, false).apply {
            changelogEntries.adapter = ChangelogAdapter()
            when (val state = viewModel.state) {
                null -> root.post { dialog?.dismiss() }
                else -> {
                    changelogSubtitle.text = getString(R.string.changelog_sheet_subtitle, state.version)
                    val entries = getString(state.changelog).split("\n")
                    Log.d("Changelog", entries.toString())
                    adapter.submitList(entries)
                }
            }
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        dialog?.takeIf { isLandscape }?.let {
            with(it as BottomSheetDialog) {
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        viewModel.onDismiss()
        super.onDismiss(dialog)
    }
}
