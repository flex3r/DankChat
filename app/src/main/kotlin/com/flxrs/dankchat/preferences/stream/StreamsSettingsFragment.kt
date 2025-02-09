package com.flxrs.dankchat.preferences.stream

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.findNavController
import com.flxrs.dankchat.R
import com.flxrs.dankchat.preferences.components.NavigationBarSpacer
import com.flxrs.dankchat.preferences.components.SwitchPreferenceItem
import com.flxrs.dankchat.theme.DankChatTheme
import com.google.android.material.transition.MaterialFadeThrough
import org.koin.compose.viewmodel.koinViewModel

class StreamsSettingsFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough()
        returnTransition = MaterialFadeThrough()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        (view.parent as? View)?.doOnPreDraw { startPostponedEnterTransition() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val viewModel = koinViewModel<StreamsSettingsViewModel>()
                val settings = viewModel.settings.collectAsStateWithLifecycle().value

                DankChatTheme {
                    StreamsSettings(
                        settings = settings,
                        onInteraction = { viewModel.onInteraction(it) },
                        onBackPressed = { findNavController().popBackStack() },
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamsSettings(
    settings: StreamsSettings,
    onInteraction: (StreamsSettingsInteraction) -> Unit,
    onBackPressed: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.preference_streams_header)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBackPressed,
                        content = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) },
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SwitchPreferenceItem(
                title = stringResource(R.string.preference_fetch_streams_title),
                summary = stringResource(R.string.preference_fetch_streams_summary),
                isChecked = settings.fetchStreams,
                onClick = { onInteraction(StreamsSettingsInteraction.FetchStreams(it)) },
            )
            SwitchPreferenceItem(
                title = stringResource(R.string.preference_streaminfo_title),
                summary = stringResource(R.string.preference_streaminfo_summary),
                isChecked = settings.showStreamInfo,
                isEnabled = settings.fetchStreams,
                onClick = { onInteraction(StreamsSettingsInteraction.ShowStreamInfo(it)) },
            )
            SwitchPreferenceItem(
                title = stringResource(R.string.preference_retain_webview_title),
                summary = stringResource(R.string.preference_retain_webview_summary),
                isChecked = settings.preventStreamReloads,
                isEnabled = settings.fetchStreams,
                onClick = { onInteraction(StreamsSettingsInteraction.PreventStreamReloads(it)) },
            )

            val activity = LocalActivity.current
            val pipAvailable = remember {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        activity != null && activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
            }
            if (pipAvailable) {
                SwitchPreferenceItem(
                    title = stringResource(R.string.preference_pip_title),
                    summary = stringResource(R.string.preference_pip_summary),
                    isChecked = settings.enablePiP,
                    isEnabled = settings.fetchStreams && settings.preventStreamReloads,
                    onClick = { onInteraction(StreamsSettingsInteraction.EnablePiP(it)) },
                )
            }
            NavigationBarSpacer()
        }
    }
}
