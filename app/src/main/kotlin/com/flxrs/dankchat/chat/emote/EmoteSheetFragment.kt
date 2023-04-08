package com.flxrs.dankchat.chat.emote

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.EmoteBottomsheetBinding
import com.flxrs.dankchat.main.MainFragment
import com.flxrs.dankchat.utils.extensions.isLandscape
import com.flxrs.dankchat.utils.extensions.loadImage
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class EmoteSheetFragment : BottomSheetDialogFragment() {

    private val viewModel: EmoteSheetViewModel by viewModels()
    private var bindingRef: EmoteBottomsheetBinding? = null
    private val binding get() = bindingRef!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        bindingRef = EmoteBottomsheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val state = viewModel.state
        with(binding) {
            emoteImage.loadImage(state.imageUrl, placeholder = null, afterLoad = { emoteImageLoading.isVisible = false })
            emoteName.text = state.name
            emoteType.text = buildString {
                append(getString(state.emoteType))
                if (state.isZeroWidth) {
                    append(" ")
                    append(getString(R.string.emote_sheet_zero_width_emote))
                }
            }
            when (state.baseName) {
                null -> emoteBaseName.isVisible = false
                else -> {
                    emoteBaseName.isVisible = true
                    emoteBaseName.text = getString(R.string.emote_sheet_alias_of, state.baseName)
                }
            }
            when (state.creatorName) {
                null -> emoteCreator.isVisible = false
                else -> {
                    emoteCreator.isVisible = true
                    emoteCreator.text = getString(R.string.emote_sheet_created_by, state.creatorName.value)
                }
            }

            emoteUse.setOnClickListener { sendResultAndDismiss(EmoteSheetResult.Use(state.name, state.id)) }
            emoteCopy.setOnClickListener { sendResultAndDismiss(EmoteSheetResult.Copy(state.name)) }
            emoteOpenLink.setOnClickListener {
                Intent(Intent.ACTION_VIEW).also {
                    it.data = state.providerUrl.toUri()
                    startActivity(it)
                }
            }
        }

        dialog?.takeIf { isLandscape }?.let {
            with(it as BottomSheetDialog) {
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
    }

    override fun onDestroyView() {
        bindingRef = null
        super.onDestroyView()
    }

    private fun sendResultAndDismiss(result: EmoteSheetResult) {
        findNavController()
            .getBackStackEntry(R.id.mainFragment)
            .savedStateHandle[MainFragment.EMOTE_SHEET_RESULT_KEY] = result
        dialog?.dismiss()
    }
}
