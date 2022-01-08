package com.flxrs.dankchat.chat.user

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil.size.Scale
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.TimeoutDialogBinding
import com.flxrs.dankchat.databinding.UserPopupBottomsheetBinding
import com.flxrs.dankchat.main.MainFragment
import com.flxrs.dankchat.utils.extensions.collectFlow
import com.flxrs.dankchat.utils.extensions.loadImage
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class UserPopupDialogFragment : BottomSheetDialogFragment() {
    private val args: UserPopupDialogFragmentArgs by navArgs()
    private val viewModel: UserPopupViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = UserPopupBottomsheetBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
            userMention.text = when {
                args.isWhisperPopup -> getString(R.string.user_popup_whisper)
                else                -> getString(R.string.user_popup_mention)
            }

            userMention.setOnClickListener {
                val displayName = viewModel.displayNameOrNull.orEmpty()
                val result = when {
                    args.isWhisperPopup -> UserPopupResult.Whisper(displayName)
                    else                -> UserPopupResult.Mention(displayName)
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
                            .setTitle(R.string.confirm_user_unfollow_title)
                            .setMessage(R.string.confirm_user_unfollow_message)
                            .setPositiveButton(R.string.confirm_channel_removal_positive_button) { _, _ -> viewModel.unfollowUser() }
                            .setNegativeButton(R.string.dialog_cancel) { d, _ -> d.dismiss() }
                            .show()

                    }
                    else                  -> viewModel.followUser()
                }
            }

            userBlock.setOnClickListener {
                when {
                    viewModel.isBlocked -> viewModel.unblockUser()
                    else                -> MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.confirm_user_block_title)
                        .setMessage(R.string.confirm_user_block_message)
                        .setPositiveButton(R.string.confirm_user_block_positive_button) { _, _ -> viewModel.blockUser() }
                        .setNegativeButton(R.string.dialog_cancel) { d, _ -> d.dismiss() }
                        .show()
                }
            }
            userTimeout.setOnClickListener { showTimeoutDialog() }
            userDelete.setOnClickListener { showDeleteDialog() }
            userBan.setOnClickListener { showBanDialog() }
            userUnban.setOnClickListener {
                viewModel.unbanUser()
                dialog?.dismiss()
            }
            userAvatarCard.setOnClickListener {
                val userName = viewModel.userNameOrNull ?: return@setOnClickListener
                val url = "https://twitch.tv/$userName"
                Intent(Intent.ACTION_VIEW).also {
                    it.data = url.toUri()
                    startActivity(it)
                }
            }
        }

        collectFlow(viewModel.userPopupState) {
            when (it) {
                is UserPopupState.Loading -> binding.showLoadingState()
                is UserPopupState.Success -> binding.updateUserData(it)
                is UserPopupState.Error   -> setErrorResultAndDismiss(it.throwable)
            }
        }

        collectFlow(viewModel.canShowModeration) {
            binding.moderationGroup.isVisible = it
        }

        return binding.root
    }

    private fun UserPopupBottomsheetBinding.showLoadingState() {
        userGroup.isVisible = false
        userLoading.isVisible = true
    }

    private fun UserPopupBottomsheetBinding.updateUserData(userState: UserPopupState.Success) {
        userAvatar.loadImage(userState.avatarUrl) {
            scale(Scale.FILL)
        }
        userLoading.isVisible = false
        userGroup.isVisible = true
        userName.text = userState.displayName
        userCreated.text = getString(R.string.user_popup_created, userState.created)
        userFollow.text = when {
            userState.isFollowing -> getString(R.string.user_popup_unfollow)
            else                  -> getString(R.string.user_popup_follow)
        }
        userFollowage.text = userState.followingSince?.let {
            getString(R.string.user_popup_following_since, it)
        } ?: getString(R.string.user_popup_not_following)
        userBlock.text = when {
            userState.isBlocked -> getString(R.string.user_popup_unblock)
            else                -> getString(R.string.user_popup_block)
        }
    }

    private fun setErrorResultAndDismiss(throwable: Throwable?) {
        findNavController()
            .getBackStackEntry(R.id.mainFragment)
            .savedStateHandle
            .set(MainFragment.USER_POPUP_RESULT_KEY, UserPopupResult.Error(throwable))
        dialog?.dismiss()
    }

    private fun showTimeoutDialog() {
        var currentItem = 0
        val choices = resources.getStringArray(R.array.timeout_entries)
        val dialogContent = TimeoutDialogBinding.inflate(LayoutInflater.from(requireContext()), null, false).apply {
            timeoutSlider.setLabelFormatter { choices[it.toInt()] }
            timeoutSlider.addOnChangeListener { _, value, _ ->
                currentItem = value.toInt()
                timeoutValue.text = choices[value.toInt()]
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_user_timeout_title)
            .setView(dialogContent.root)
            .setPositiveButton(R.string.confirm_user_timeout_positive_button) { _, _ ->
                viewModel.timeoutUser(currentItem)
                dialog?.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel) { d, _ -> d.dismiss() }
            .show()
    }

    private fun showBanDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_user_ban_title)
            .setMessage(R.string.confirm_user_ban_message)
            .setPositiveButton(R.string.confirm_user_ban_positive_button) { _, _ ->
                viewModel.banUser()
                dialog?.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel) { d, _ -> d.dismiss() }
            .show()
    }

    private fun showDeleteDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_user_delete_title)
            .setMessage(R.string.confirm_user_delete_message)
            .setPositiveButton(R.string.confirm_user_delete_positive_button) { _, _ ->
                viewModel.deleteMessage()
                dialog?.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel) { d, _ -> d.dismiss() }
            .show()
    }
}