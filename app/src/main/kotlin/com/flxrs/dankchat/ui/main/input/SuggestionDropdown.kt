package com.flxrs.dankchat.ui.main.input

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flxrs.dankchat.ui.chat.emote.EmoteImage
import com.flxrs.dankchat.ui.chat.suggestion.Suggestion
import kotlinx.collections.immutable.ImmutableList

private val SUGGESTION_DROPDOWN_MAX_HEIGHT = 250.dp

@Composable
fun SuggestionDropdown(
    suggestions: ImmutableList<Suggestion>,
    animateEmotes: Boolean,
    onSuggestionClick: (Suggestion) -> Unit,
    modifier: Modifier = Modifier,
    availableMaxHeightDp: Dp = SUGGESTION_DROPDOWN_MAX_HEIGHT,
) {
    AnimatedVisibility(
        visible = suggestions.isNotEmpty(),
        modifier = modifier,
        enter =
            slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight / 4 },
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
            ) +
                fadeIn(
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                ) +
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                ),
        exit =
            slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight / 4 },
            ) + fadeOut() + scaleOut(targetScale = 0.92f),
    ) {
        val listState = rememberLazyListState()
        LaunchedEffect(suggestions) {
            listState.scrollToItem(0)
        }

        OutlinedCard(
            modifier =
                Modifier
                    .padding(horizontal = 2.dp)
                    .fillMaxWidth(0.66f)
                    .heightIn(max = minOf(availableMaxHeightDp, SUGGESTION_DROPDOWN_MAX_HEIGHT)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 5.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
            ) {
                itemsIndexed(
                    suggestions,
                    // Position-independent keys so filter changes reuse rows instead of rebuilding them
                    key = { _, suggestion ->
                        when (suggestion) {
                            is Suggestion.EmoteSuggestion -> "emote-${suggestion.emote.emoteType}-${suggestion.emote.id}-${suggestion.emote.code}"
                            is Suggestion.UserSuggestion -> "user-${suggestion.name.value}"
                            is Suggestion.EmojiSuggestion -> "emoji-${suggestion.emoji.code}"
                            is Suggestion.CommandSuggestion -> "cmd-${suggestion.command}"
                            is Suggestion.FilterSuggestion -> "filter-${suggestion.keyword}"
                        }
                    },
                ) { _, suggestion ->
                    SuggestionItem(
                        suggestion = suggestion,
                        animateEmotes = animateEmotes,
                        onClick = { onSuggestionClick(suggestion) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionItem(
    suggestion: Suggestion,
    animateEmotes: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconSize = 36.dp
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (suggestion) {
            is Suggestion.EmoteSuggestion -> {
                EmoteImage(
                    emote = suggestion.emote,
                    animate = animateEmotes,
                    modifier = Modifier.size(iconSize),
                )
            }

            is Suggestion.UserSuggestion -> {
                Box(modifier = Modifier.size(iconSize), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            is Suggestion.EmojiSuggestion -> {
                Box(modifier = Modifier.size(iconSize), contentAlignment = Alignment.Center) {
                    Text(
                        text = suggestion.emoji.unicode,
                        fontSize = 24.sp,
                    )
                }
            }

            is Suggestion.CommandSuggestion -> {
                Box(modifier = Modifier.size(iconSize), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            is Suggestion.FilterSuggestion -> {
                Box(modifier = Modifier.size(iconSize), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        when (suggestion) {
            is Suggestion.EmoteSuggestion -> {
                Text(text = suggestion.emote.code, style = MaterialTheme.typography.bodyLarge)
            }

            is Suggestion.UserSuggestion -> {
                Text(text = suggestion.name.value, style = MaterialTheme.typography.bodyLarge)
            }

            is Suggestion.EmojiSuggestion -> {
                Text(text = ":${suggestion.emoji.code}:", style = MaterialTheme.typography.bodyLarge)
            }

            is Suggestion.CommandSuggestion -> {
                Text(text = suggestion.command, style = MaterialTheme.typography.bodyLarge)
            }

            is Suggestion.FilterSuggestion -> {
                Column {
                    Text(
                        text = suggestion.displayText ?: suggestion.keyword,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(suggestion.descriptionRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
