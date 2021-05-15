package com.flxrs.dankchat.chat.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.MainFragmentBinding
import com.flxrs.dankchat.databinding.UserPopupBottomsheetBinding
import com.flxrs.dankchat.main.MainFragment
import com.flxrs.dankchat.utils.extensions.collectFlow
import com.flxrs.dankchat.utils.extensions.showShortSnackbar
import com.flxrs.dankchat.utils.loadImage
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class UserPopupDialogFragment : BottomSheetDialogFragment() {
    private val viewModel: UserPopupViewModel by viewModels()
    private val args: UserPopupDialogFragmentArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = UserPopupBottomsheetBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
            userMention.text = when {
                args.isWhisperPopup -> getString(R.string.user_popup_whisper)
                else -> getString(R.string.user_popup_mention)
            }

            userMention.setOnClickListener {
                val displayName = viewModel.displayNameOrNull.orEmpty()
                val result = when {
                    args.isWhisperPopup -> UserPopupResult.Whisper(displayName)
                    else -> UserPopupResult.Mention(displayName)
                }

                findNavController()
                    .getBackStackEntry(R.id.mainFragment)
                    .savedStateHandle
                    .set(MainFragment.USER_POPUP_RESULT_KEY, result)
                dialog?.dismiss()
            }

            userFollow.setOnClickListener {
                when {
                    viewModel.isFollowing -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.confirm_channel_unfollow_title)
                            .setMessage(R.string.confirm_channel_unfollow_message)
                            .setPositiveButton(R.string.confirm_channel_removal_positive_button) { _, _ -> viewModel.unfollowUser() }
                            .setNegativeButton(R.string.dialog_cancel) { d, _ -> d.dismiss() }
                            .show()

                    }
                    else -> viewModel.followUser()
                }
            }
        }

        collectFlow(viewModel.userPopupState) {
            when (it) {
                is UserPopupViewModel.UserPopupState.Loading -> binding.showLoadingState()
                is UserPopupViewModel.UserPopupState.Success -> binding.updateUserData(it)
                is UserPopupViewModel.UserPopupState.Error -> setErrorResultAndDismiss(it.throwable)
            }
        }

        return binding.root
    }

    override fun dismiss() {
        findNavController().popBackStack(R.id.mainFragment, false)
    }

    private fun UserPopupBottomsheetBinding.showLoadingState() {
        userGroup.isVisible = false
        userLoading.isVisible = true
    }

    private fun UserPopupBottomsheetBinding.updateUserData(userState: UserPopupViewModel.UserPopupState.Success) {
        userLoading.isVisible = false
        userGroup.isVisible = true
        userName.text = userState.displayName
        userCreated.text = getString(R.string.user_popup_created, userState.created)
        userFollow.text = when {
            userState.isFollowing -> getString(R.string.user_popup_unfollow)
            else -> getString(R.string.user_popup_follow)
        }
        userFollowage.text = userState.followingSince?.let {
            getString(R.string.user_popup_following_since, it)
        } ?: getString(R.string.user_popup_not_following)
        userAvatar.loadImage(userState.avatarUrl)
    }

    private fun setErrorResultAndDismiss(throwable: Throwable?) {
        findNavController()
            .getBackStackEntry(R.id.mainFragment)
            .savedStateHandle
            .set(MainFragment.USER_POPUP_RESULT_KEY, UserPopupResult.Error(throwable))
        dialog?.dismiss()
    }

    companion object {
        const val TARGET_USER_ID_ARG = "targetUserId"
        const val CURRENT_USER_ID_ARG = "currentUserId"
        const val OAUTH_ARG = "oAuth"
    }
}