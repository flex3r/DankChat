package com.flxrs.dankchat.preferences.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.PreviewDynamicColors
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.R
import com.flxrs.dankchat.changelog.DankChatVersion
import com.flxrs.dankchat.main.MainFragment
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.theme.DankChatTheme
import com.flxrs.dankchat.utils.extensions.navigateSafe
import com.google.android.material.transition.MaterialFadeThrough
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class OverviewSettingsFragment : Fragment() {

    private val navController: NavController by lazy { findNavController() }

    @Inject
    lateinit var dankChatPreferences: DankChatPreferenceStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exitTransition = MaterialFadeThrough()
        reenterTransition = MaterialFadeThrough()
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                var secretDankerMode by remember { mutableStateOf(dankChatPreferences.isSecretDankerModeEnabled) }
                OverviewSettings(
                    isLoggedIn = dankChatPreferences.isLoggedIn,
                    hasChangelog = DankChatVersion.HAS_CHANGELOG,
                    isSecretDankerModeEnabled = secretDankerMode,
                    clicksNeeded = dankChatPreferences.secretDankerModeClicks,
                    onSecretDankerModeEnabled = { dankChatPreferences.isSecretDankerModeEnabled = true },
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        (view.parent as? View)?.doOnPreDraw { startPostponedEnterTransition() }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun OverviewSettings(
        isLoggedIn: Boolean,
        hasChangelog: Boolean,
        isSecretDankerModeEnabled: Boolean,
        clicksNeeded: Int,
        onSecretDankerModeEnabled: () -> Unit
    ) {
        Scaffold(
            modifier = Modifier.imePadding(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings)) },
                    navigationIcon = {
                        IconButton(
                            onClick = { navController.popBackStack() },
                            content = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") },
                        )
                    }
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                PreferenceItem(R.string.preference_appearance_header, Icons.Default.Palette) {
                    navigateSafe(R.id.action_overviewSettingsFragment_to_appearanceSettingsFragment)
                }
                PreferenceItem(R.string.preference_highlights_ignores_header, Icons.Default.NotificationsActive) {
                    navigateSafe(R.id.action_overviewSettingsFragment_to_notificationsSettingsFragment)
                }
                PreferenceItem(R.string.preference_chat_header, Icons.Default.Forum) {
                    navigateSafe(R.id.action_overviewSettingsFragment_to_chatSettingsFragment)
                }
                PreferenceItem(R.string.preference_streams_header, Icons.Default.PlayArrow) {
                    navigateSafe(R.id.action_overviewSettingsFragment_to_streamsSettingsFragment)
                }
                PreferenceItem(R.string.preference_tools_header, Icons.Default.Construction) {
                    navigateSafe(R.id.action_overviewSettingsFragment_to_toolsSettingsFragment)
                }
                PreferenceItem(R.string.preference_developer_header, Icons.Default.DeveloperMode) {
                    navigateSafe(R.id.action_overviewSettingsFragment_to_streamsSettingsFragment)
                }

                AnimatedVisibility(hasChangelog) {
                    PreferenceItem(R.string.preference_whats_new_header, Icons.Default.FiberNew) {
                        navigateSafe(R.id.action_overviewSettingsFragment_to_changelogSheetFragment)
                    }
                }

                PreferenceItem(R.string.logout, Icons.AutoMirrored.Default.ExitToApp, isEnabled = isLoggedIn) {
                    with(navController) {
                        previousBackStackEntry?.savedStateHandle?.set(MainFragment.LOGOUT_REQUEST_KEY, true)
                        popBackStack()
                    }
                }
                SecretDankerModeTrigger(
                    clicksNeeded = clicksNeeded,
                    enabled = !isSecretDankerModeEnabled,
                    onEnabled = onSecretDankerModeEnabled,
                ) {
                    PreferenceCategory(
                        title = {
                            PreferenceCategoryTitle(
                                text = stringResource(R.string.preference_about_header),
                                modifier = Modifier.dankClickable(enabled = !isSecretDankerModeEnabled),
                            )
                        }
                    ) {
                        val context = LocalContext.current
                        val annotated = buildAnnotatedString {
                            append(context.getString(R.string.preference_about_summary, BuildConfig.VERSION_NAME))
                            appendLine()
                            withLink(link = buildLinkAnnotation(GITHUB_URL)) {
                                append(GITHUB_URL)
                            }
                            appendLine()
                            appendLine()
                            append(context.getString(R.string.preference_about_tos))
                            appendLine()
                            withLink(link = buildLinkAnnotation(TWITCH_TOS_URL)) {
                                append(TWITCH_TOS_URL)
                            }
                        }
                        PreferenceSummary(annotated, Modifier.padding(top = 16.dp))
                    }

                }
            }
        }
    }

    @LayoutScopeMarker
    interface SecretDankerScope {
        fun Modifier.dankClickable(enabled: Boolean): Modifier
    }

    @Composable
    private fun SecretDankerModeTrigger(
        enabled: Boolean,
        clicksNeeded: Int,
        onEnabled: () -> Unit,
        content: @Composable SecretDankerScope.() -> Unit,
    ) {
        var lastToast by remember { mutableStateOf<Toast?>(null) }
        var currentClicks by remember { mutableIntStateOf(0) }
        val scope = remember {
            object : SecretDankerScope {
                override fun Modifier.dankClickable(enabled: Boolean) = clickable(
                    enabled = enabled,
                    onClick = { currentClicks++ },
                    interactionSource = null,
                    indication = null,
                )
            }
        }
        val context = LocalContext.current
        if (enabled) {
            LaunchedEffect(currentClicks) {
                lastToast?.cancel()
                when (currentClicks) {
                    in 2..<clicksNeeded -> {
                        val remaining = clicksNeeded - currentClicks
                        lastToast = Toast
                            .makeText(context, "$remaining click(s) left to enable secret danker mode", Toast.LENGTH_SHORT)
                            .apply { show() }
                    }

                    clicksNeeded        -> {
                        Toast
                            .makeText(context, "Secret danker mode enabled", Toast.LENGTH_SHORT)
                            .show()
                        lastToast = null
                        onEnabled()
                    }
                }
            }
        }
        content(scope)
    }

    @Composable
    private fun PreferenceItem(
        @StringRes titleRes: Int,
        icon: ImageVector? = null,
        isEnabled: Boolean = true,
        onClick: () -> Unit = { },
    ) {
        val contentColor = LocalContentColor.current
        val color = when {
            isEnabled -> contentColor
            else      -> contentColor.copy(alpha = 0.38f)
        }

        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(
                        enabled = isEnabled,
                        onClick = onClick,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val title = stringResource(titleRes)
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = color,
                    )
                    Spacer(Modifier.width(32.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .basicMarquee(),
                    maxLines = 1,
                    color = color,
                )
            }

        }
    }

    @Composable
    private fun PreferenceCategory(
        title: @Composable () -> Unit,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        Column(
            modifier = Modifier.padding(top = 16.dp),
        ) {
            title()
            content()
        }
    }

    @Composable
    private fun PreferenceCategoryTitle(text: String, modifier: Modifier = Modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            modifier = modifier
                .padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    @Composable
    private fun PreferenceSummary(summary: AnnotatedString, modifier: Modifier = Modifier) {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier,
        )
    }

    @Composable
    private fun buildLinkAnnotation(url: String): LinkAnnotation = LinkAnnotation.Url(
        url = url,
        styles = TextLinkStyles(
            style = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
            )
        )
    )

    @Composable
    @PreviewDynamicColors
    @PreviewLightDark
    private fun OverviewSettingsPreview() {
        DankChatTheme {
            OverviewSettings(
                isLoggedIn = true,
                hasChangelog = true,
                isSecretDankerModeEnabled = false,
                clicksNeeded = 10,
                onSecretDankerModeEnabled = { },
            )
        }
    }

    companion object {
        private const val GITHUB_URL = "https://github.com/flex3r/dankchat"
        private const val TWITCH_TOS_URL = "https://www.twitch.tv/p/terms-of-service"
    }
}
