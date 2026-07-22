package com.flxrs.dankchat.preferences.about

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import com.flxrs.dankchat.R
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.utils.compose.BottomSheetNestedScrollConnection
import com.flxrs.dankchat.utils.compose.rememberModalSheetState
import com.flxrs.dankchat.utils.compose.textLinkStyles
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.util.htmlReadyLicenseContent
import com.mikepenz.aboutlibraries.util.withContext
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import sh.calvin.autolinktext.TextRuleDefaults
import sh.calvin.autolinktext.annotateString

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val dispatchersProvider: DispatchersProvider = koinInject()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars),
        modifier =
            Modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .imePadding(),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.open_source_licenses)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        content = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) },
                    )
                },
            )
        },
    ) { padding ->
        val context = LocalContext.current
        val libraries =
            produceState<Libs?>(null) {
                value =
                    withContext(dispatchersProvider.io) {
                        Libs.Builder().withContext(context).build()
                    }
            }
        var selectedLibrary by remember { mutableStateOf<Library?>(null) }
        LibrariesContainer(
            libraries = libraries.value,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            onLibraryClick = {
                selectedLibrary = it
                true
            },
        )
        selectedLibrary?.let { library ->
            LibraryLicenseSheet(
                library = library,
                onDismiss = { selectedLibrary = null },
            )
        }
    }
}

@Composable
private fun LibraryLicenseSheet(
    library: Library,
    onDismiss: () -> Unit,
) {
    val linkStyles = textLinkStyles()
    val rules = TextRuleDefaults.defaultList()
    val license =
        remember(library, rules) {
            val mappedRules = rules.map { it.copy(styles = linkStyles) }
            library.htmlReadyLicenseContent
                .takeIf { it.isNotEmpty() }
                ?.let { content ->
                    val html =
                        AnnotatedString.fromHtml(
                            htmlString = content,
                            linkStyles = linkStyles,
                        )
                    mappedRules.annotateString(html.text)
                }
        } ?: return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentWindowInsets = { BottomSheetDefaults.modalWindowInsets.exclude(WindowInsets.navigationBars) },
    ) {
        Text(
            text = library.name,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val scrollState = rememberScrollState()
        Text(
            text = license,
            style = MaterialTheme.typography.bodySmall,
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .nestedScroll(BottomSheetNestedScrollConnection)
                    .verticalScroll(scrollState)
                    .padding(start = 16.dp, end = 16.dp, bottom = navBarBottom),
        )
    }
}
