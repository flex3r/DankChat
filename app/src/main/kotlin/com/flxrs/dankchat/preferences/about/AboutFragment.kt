package com.flxrs.dankchat.preferences.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.flxrs.dankchat.R
import com.flxrs.dankchat.theme.DankChatTheme
import com.google.android.material.transition.MaterialFadeThrough
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.util.htmlReadyLicenseContent
import com.mikepenz.aboutlibraries.util.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AboutFragment : Fragment() {

    private val navController: NavController by lazy { findNavController() }

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DankChatTheme {
                    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
                    Scaffold(
                        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars),
                        modifier = Modifier
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                            .imePadding(),
                        topBar = {
                            TopAppBar(
                                scrollBehavior = scrollBehavior,
                                title = { Text(stringResource(R.string.open_source_licenses)) },
                                navigationIcon = {
                                    IconButton(
                                        onClick = { navController.popBackStack() },
                                        content = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") },
                                    )
                                }
                            )
                        },
                    ) { padding ->
                        val context = LocalContext.current
                        val libraries = produceState<Libs?>(null) {
                            value = withContext(Dispatchers.IO) {
                                Libs.Builder().withContext(context).build()
                            }
                        }
                        LibrariesContainer(
                            libraries = libraries.value,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                            dimensions = LibraryDefaults.libraryDimensions(),
                            contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                            licenseDialogBody = { library ->
                                val license = remember(library) {
                                    library.htmlReadyLicenseContent.takeIf { it.isNotEmpty() }?.let { AnnotatedString.fromHtml(it) }
                                }
                                if (license != null) {
                                    Text(
                                        text = license,
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)
                                    )
                                }
                            },
                            licenseDialogConfirmText = stringResource(R.string.dialog_ok),
                        )
                    }
                }
            }
        }
    }
}
