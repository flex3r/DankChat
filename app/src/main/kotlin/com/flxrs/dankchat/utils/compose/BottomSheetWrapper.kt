package com.flxrs.dankchat.utils.compose

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.google.android.material.composethemeadapter3.Mdc3Theme
import kotlinx.coroutines.launch

fun Fragment.showAsBottomSheet(content: @Composable ColumnScope.() -> Unit) {
    val viewGroup = requireActivity().findViewById(android.R.id.content) as ViewGroup
    addContentToView(viewGroup, content)
}

private fun addContentToView(
    viewGroup: ViewGroup,
    content: @Composable ColumnScope.() -> Unit
) {
    viewGroup.addView(
        ComposeView(viewGroup.context).apply {
            setContent {
                Mdc3Theme(context = viewGroup.context) {
                    BottomSheetWrapper(viewGroup, this, content)
                }
            }
        }
    )
}


@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun BottomSheetWrapper(
    parent: ViewGroup,
    composeView: ComposeView,
    content: @Composable ColumnScope.() -> Unit
) {
    val tag = parent::class.java.simpleName
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
    var isSheetOpened by remember { mutableStateOf(false) }

    BackHandler(sheetState.isVisible) {
        coroutineScope.launch { sheetState.hide() }
    }

    ModalBottomSheetLayout(
        sheetShape = RoundedCornerShape(16.dp),
        sheetState = sheetState,
        sheetContent = content,
        modifier = Modifier.fillMaxSize()
    ) {}

    LaunchedEffect(sheetState.currentValue) {
        if (sheetState.currentValue == ModalBottomSheetValue.Hidden) {
            when {
                isSheetOpened -> parent.removeView(composeView)
                else          -> {
                    isSheetOpened = true
                    sheetState.animateTo(targetValue = ModalBottomSheetValue.HalfExpanded)
                }
            }
        }
    }
}