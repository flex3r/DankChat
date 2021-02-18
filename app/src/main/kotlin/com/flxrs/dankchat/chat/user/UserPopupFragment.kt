package com.flxrs.dankchat.chat.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import com.flxrs.dankchat.MainFragment
import com.flxrs.dankchat.databinding.UserPopupBottomsheetBinding
import com.flxrs.dankchat.utils.loadImage
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class UserPopupFragment : BottomSheetDialogFragment() {

    private val viewModel: UserPopupViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = UserPopupBottomsheetBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner

            userMention.setOnClickListener {
                val mentionName = arguments?.getString(NAME_ARG) ?: return@setOnClickListener
                (parentFragment?.parentFragment as? MainFragment)?.mentionUser(mentionName) // TODO flow event
                dialog?.dismiss()
            }

            userFollow.setOnClickListener {
                when {
                    viewModel.isFollowing -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Confirm unfollow")
                            .setMessage("Are you sure you want to unfollow?")
                            .setPositiveButton("Unfollow") { _, _ ->
                                userFollow.updateFollowButton(false)
                                viewModel.unfollowUser()
                            }
                            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
                            .show()

                    }
                    else -> {
                        userFollow.updateFollowButton(true)
                        viewModel.followUser()
                    }
                }
            }
        }

        viewModel.userFollows.observe(viewLifecycleOwner) {
            binding.userFollow.updateFollowButton(it.isFollowing)
            binding.userFollowage.updateFollowingSince(it.followingSince)
        }

        viewModel.user.observe(viewLifecycleOwner) {
            binding.userName.updateUserName(it.displayName)
            binding.userCreated.updateCreated(it.created)
            binding.userAvatar.loadImage(it.avatarUrl)
        }

        if (savedInstanceState == null) {
            viewModel.refresh()
        }

        return binding.root
    }

    private fun TextView.updateUserName(name: String) {
        text = name
    }

    private fun TextView.updateCreated(created: String) {
        text = "Created: $created"
    }

    private fun MaterialButton.updateFollowButton(following: Boolean) {
        text = when {
            following -> "Unfollow"
            else -> "Follow"
        }
    }

    private fun TextView.updateFollowingSince(followingSince: String?) {
        text = followingSince?.let {
            "Following since $it"
        } ?: "Not following"
    }

    companion object {
        fun newInstance(currentUserId: String, targetUserId: String, channel: String, mentionName: String, oAuth: String): UserPopupFragment {
            return UserPopupFragment().apply {
                arguments = bundleOf(
                    CURRENT_USER_ID_ARG to currentUserId,
                    TARGET_USER_ID_ARG to targetUserId,
                    CHANNEL_ARG to channel,
                    NAME_ARG to mentionName,
                    OAUTH_ARG to oAuth
                )
            }
        }

        const val CURRENT_USER_ID_ARG = "current_user_id_arg"
        const val TARGET_USER_ID_ARG = "target_user_id_arg"
        const val CHANNEL_ARG = "channel_arg"
        const val NAME_ARG = "arg_name"
        const val OAUTH_ARG = "oauth_arg"
    }
}