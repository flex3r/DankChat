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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.flxrs.dankchat.R
import com.flxrs.dankchat.theme.DankChatTheme
import com.flxrs.dankchat.utils.compose.textLinkStyles
import com.google.android.material.transition.MaterialFadeThrough
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.util.htmlReadyLicenseContent
import com.mikepenz.aboutlibraries.util.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sh.calvin.autolinktext.TextRuleDefaults
import sh.calvin.autolinktext.annotateString

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
                        var selectedLibrary by remember { mutableStateOf<Library?>(null) }
                        LibrariesContainer(
                            libraries = libraries.value,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                            contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                            onLibraryClick = { selectedLibrary = it },
                        )
                        selectedLibrary?.let { library ->
                            val linkStyles = textLinkStyles()
                            val rules = TextRuleDefaults.defaultList()
                            val license = remember(library, rules) {
                                val mappedRules = rules.map { it.copy(styles = linkStyles) }
                                library.htmlReadyLicenseContent
                                    .takeIf { it.isNotEmpty() }
                                    ?.let { content ->
                                        val html = AnnotatedString.fromHtml(
                                            htmlString = content,
                                            linkStyles = linkStyles,
                                        )
                                        mappedRules.annotateString(html.text)
                                    }
                            }
                            if (license != null) {
                                AlertDialog(
                                    onDismissRequest = { selectedLibrary = null },
                                    title = { Text(text = library.name) },
                                    confirmButton = { TextButton(onClick = { selectedLibrary = null }) { Text(stringResource(R.string.dialog_ok)) } },
                                    text = {
                                        Text(
                                            text = license,
                                            modifier = Modifier.verticalScroll(rememberScrollState()),
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
