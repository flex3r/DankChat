package com.flxrs.dankchat.ui.onboarding

import kotlinx.serialization.Serializable

@Serializable
data class OnboardingSettings(
    val hasCompletedOnboarding: Boolean = false,
    val hasRunExistingUserMigration: Boolean = false,
    val featureTourVersion: Int = 0,
    val hasShownAddChannelHint: Boolean = false,
    val hasShownToolbarHint: Boolean = false,
    val onboardingPage: Int = 0,
)
