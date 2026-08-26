package com.flxrs.dankchat.utils.compose

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationSource
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.composables.core.DragIndication
import com.composables.core.ModalBottomSheet
import com.composables.core.Scrim
import com.composables.core.Sheet
import com.composables.core.SheetDetent
import com.composables.core.rememberModalBottomSheetState
import com.flxrs.dankchat.R
import java.util.concurrent.CancellationException

@Composable
fun InputBottomSheet(
    title: String,
    hint: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = stringResource(R.string.dialog_ok),
    defaultValue: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Unspecified,
    autoCorrectEnabled: Boolean = true,
    showClearButton: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 5,
    validate: ((String) -> String?)? = null,
) {
    var inputValue by remember { mutableStateOf(TextFieldValue(defaultValue, selection = TextRange(defaultValue.length))) }
    val focusRequester = remember { FocusRequester() }
    val trimmed = inputValue.text.trim()
    val errorText = validate?.invoke(trimmed)
    val isValid = trimmed.isNotBlank() && errorText == null

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val sheetState =
        rememberModalBottomSheetState(
            initialDetent = SheetDetent.FullyExpanded,
            detents = listOf(SheetDetent.Hidden, SheetDetent.FullyExpanded),
        )

    LaunchedEffect(sheetState.currentDetent) {
        if (sheetState.currentDetent == SheetDetent.Hidden) {
            onDismiss()
        }
    }

    ModalBottomSheet(
        state = sheetState,
        onDismiss = onDismiss,
    ) {
        Scrim()

        var backProgress by remember { mutableFloatStateOf(0f) }
        PredictiveBackHandler { progress ->
            try {
                progress.collect { event ->
                    backProgress = event.progress
                }
                onDismiss()
            } catch (_: CancellationException) {
                backProgress = 0f
            }
        }

        val scale = 1f - (backProgress * 0.15f)
        Sheet(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationY = size.height * backProgress * 0.3f
                        alpha = 1f - (backProgress * 0.2f)
                    }.shadow(8.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .navigationBarsPadding()
                        .imePadding(),
            ) {
                // Dismiss on keyboard close
                val density = LocalDensity.current
                val current = WindowInsets.ime.getBottom(density)
                val source = WindowInsets.imeAnimationSource.getBottom(density)
                val target = WindowInsets.imeAnimationTarget.getBottom(density)
                val isClosing = source > 0 && target == 0
                val nearlyDone = current < 200

                LaunchedEffect(isClosing, nearlyDone) {
                    if (isClosing && nearlyDone) {
                        onDismiss()
                    }
                }

                DragIndication(
                    modifier =
                        Modifier
                            .padding(top = 16.dp, bottom = 16.dp)
                            .align(Alignment.CenterHorizontally)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(50),
                            ).size(width = 32.dp, height = 4.dp),
                )

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    label = { Text(hint) },
                    singleLine = singleLine,
                    maxLines = maxLines,
                    isError = errorText != null,
                    trailingIcon =
                        if (showClearButton && inputValue.text.isNotEmpty()) {
                            {
                                IconButton(onClick = { inputValue = TextFieldValue() }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = stringResource(R.string.clear),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = capitalization,
                            autoCorrectEnabled = autoCorrectEnabled,
                            keyboardType = keyboardType,
                            imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
                        ),
                    keyboardActions =
                        KeyboardActions(onDone = {
                            if (isValid) {
                                onConfirm(trimmed)
                            }
                        }),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                )

                AnimatedVisibility(
                    visible = errorText != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Text(
                        text = errorText.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Button(
                    onClick = { onConfirm(trimmed) },
                    enabled = isValid,
                    modifier =
                        Modifier
                            .align(Alignment.End)
                            .padding(top = 8.dp),
                ) {
                    Text(confirmText)
                }
            }
        }
    }
}
