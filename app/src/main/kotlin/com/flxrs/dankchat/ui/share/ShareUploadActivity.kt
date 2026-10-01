package com.flxrs.dankchat.ui.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.repo.data.DataRepository
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.ui.theme.DankChatTheme
import com.flxrs.dankchat.utils.createMediaFile
import com.flxrs.dankchat.utils.extensions.parcelable
import com.flxrs.dankchat.utils.removeExifAttributes
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.io.File

class ShareUploadActivity : ComponentActivity() {
    private val dataRepository: DataRepository by inject()
    private val dispatchersProvider: DispatchersProvider by inject()
    private var uploadState by mutableStateOf<ShareUploadState>(ShareUploadState.Loading)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DankChatTheme {
                ShareUploadDialog(
                    state = uploadState,
                    onRetry = { retryUpload() },
                    onDismiss = { finish() },
                )
            }
        }

        if (savedInstanceState == null) {
            handleShareIntent(intent)
        }
    }

    private fun handleShareIntent(intent: Intent) {
        val uri = intent.parcelable<Uri>(Intent.EXTRA_STREAM)
        if (uri == null) {
            uploadState = ShareUploadState.Error(getString(R.string.snackbar_upload_failed))
            return
        }

        val mimeType = contentResolver.getType(uri)
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "tmp"

        lifecycleScope.launch {
            uploadState = ShareUploadState.Loading
            val file =
                withContext(dispatchersProvider.io) {
                    try {
                        val copy = createMediaFile(this@ShareUploadActivity, extension)
                        contentResolver.openInputStream(uri)?.use { input ->
                            copy.outputStream().use { input.copyTo(it) }
                        }
                        if (copy.extension == "jpg" || copy.extension == "jpeg") {
                            copy.removeExifAttributes()
                        }
                        copy
                    } catch (_: Throwable) {
                        null
                    }
                }

            if (file == null) {
                uploadState = ShareUploadState.Error(getString(R.string.snackbar_upload_failed))
                return@launch
            }

            performUpload(file)
        }
    }

    private fun retryUpload() {
        handleShareIntent(intent)
    }

    private suspend fun performUpload(file: File) {
        val result = withContext(dispatchersProvider.io) { dataRepository.uploadMedia(file) }
        result.fold(
            onSuccess = { url -> uploadState = ShareUploadState.Success(url) },
            onFailure = { error ->
                uploadState =
                    ShareUploadState.Error(
                        message = error.message ?: getString(R.string.snackbar_upload_failed),
                    )
            },
        )
    }
}

@Immutable
sealed interface ShareUploadState {
    data object Loading : ShareUploadState

    data class Success(
        val url: String,
    ) : ShareUploadState

    data class Error(
        val message: String,
    ) : ShareUploadState
}

@Composable
private fun ShareUploadDialog(
    state: ShareUploadState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        modifier = Modifier.widthIn(min = 280.dp, max = 400.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.upload_media),
                style = MaterialTheme.typography.headlineSmall,
            )

            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "uploadState",
            ) { currentState ->
                when (currentState) {
                    is ShareUploadState.Loading -> LoadingContent()
                    is ShareUploadState.Success -> SuccessContent(url = currentState.url)
                    is ShareUploadState.Error -> ErrorContent(message = currentState.message)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (state) {
                    is ShareUploadState.Error -> {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.dialog_dismiss))
                        }
                        TextButton(onClick = onRetry) {
                            Text(stringResource(R.string.snackbar_retry))
                        }
                    }

                    is ShareUploadState.Success -> {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.dialog_ok))
                        }
                    }

                    is ShareUploadState.Loading -> {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.dialog_cancel))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Text(
            text = stringResource(R.string.uploading_image),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SuccessContent(url: String) {
    val context = LocalContext.current
    val clipboardManager = remember { context.getSystemService(ClipboardManager::class.java) }

    SideEffect(url) {
        clipboardManager.setPrimaryClip(ClipData.newPlainText("dankchat_media_url", url))
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = url,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.share_upload_copied),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("dankchat_media_url", url))
        }) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.share_upload_copy),
            )
        }
    }
}

@Composable
private fun ErrorContent(message: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
