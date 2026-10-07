package com.flxrs.dankchat.ui.chat.messages

import android.content.res.Resources
import androidx.compose.runtime.Immutable
import com.flxrs.dankchat.ui.chat.ChatMessageUiState
import com.flxrs.dankchat.utils.TextResource

private const val ARG_START = ''
private const val ARG_END = ''

@Immutable
data class ModerationSpan(
    val start: Int,
    val length: Int,
    val role: Role,
) {
    enum class Role { Creator, Target, Argument }
}

@Immutable
data class ResolvedModerationText(
    val text: String,
    val spans: List<ModerationSpan>,
)

/**
 * Resolves the message and locates the creator, target and arguments by their position in the format string,
 * so a value that also occurs in the surrounding template text is never highlighted in the wrong place.
 */
fun ChatMessageUiState.ModerationMessageUi.resolveWithSpans(resources: Resources): ResolvedModerationText = ModerationSpanResolver(resources, creatorName, targetName, arguments).resolve(message)

private data class ResolvedArg(
    val text: String,
    val role: ModerationSpan.Role? = null,
    val nestedSpans: List<ModerationSpan> = emptyList(),
)

private class ModerationSpanResolver(
    private val resources: Resources,
    creatorName: String?,
    targetName: String?,
    arguments: List<Any>,
) {
    private var pendingCreator = creatorName
    private var pendingTarget = targetName
    private val pendingArguments = arguments.toMutableList()

    fun resolve(resource: TextResource): ResolvedModerationText = when (resource) {
        is TextResource.Plain -> ResolvedModerationText(resource.value, emptyList())
        is TextResource.PluralRes -> ResolvedModerationText(resolvePlural(resource), emptyList())
        is TextResource.Res -> resolveRes(resource)
    }

    private fun resolveRes(resource: TextResource.Res): ResolvedModerationText {
        // Arguments are matched in declaration order, so a value repeated later (e.g. a deleted message equal to a name) keeps its own role
        val resolvedArgs = resource.args.map(::resolveArg)
        val formatArgs =
            resource.args.mapIndexed { index, arg ->
                when (arg) {
                    is String, is TextResource -> "$ARG_START$index$ARG_END"
                    else -> arg
                }
            }
        val template = resources.getString(resource.id, *formatArgs.toTypedArray())

        val text = StringBuilder()
        val spans = mutableListOf<ModerationSpan>()
        var cursor = 0
        while (cursor < template.length) {
            val argStart = template.indexOf(ARG_START, cursor)
            if (argStart < 0) {
                text.append(template, cursor, template.length)
                break
            }

            val argEnd = template.indexOf(ARG_END, argStart)
            text.append(template, cursor, argStart)
            val arg = resolvedArgs[template.substring(argStart + 1, argEnd).toInt()]
            val start = text.length
            text.append(arg.text)
            arg.role?.let { spans += ModerationSpan(start, arg.text.length, it) }
            arg.nestedSpans.mapTo(spans) { it.copy(start = it.start + start) }
            cursor = argEnd + 1
        }
        return ResolvedModerationText(text.toString(), spans)
    }

    private fun resolveArg(arg: Any): ResolvedArg = when {
        arg is String && arg == pendingCreator -> {
            pendingCreator = null
            ResolvedArg(arg, ModerationSpan.Role.Creator)
        }

        arg is String && arg == pendingTarget -> {
            pendingTarget = null
            ResolvedArg(arg, ModerationSpan.Role.Target)
        }

        pendingArguments.remove(arg) -> {
            ResolvedArg(resolveValue(arg).toString(), ModerationSpan.Role.Argument)
        }

        arg is TextResource -> {
            val nested = resolve(arg)
            ResolvedArg(nested.text, nestedSpans = nested.spans)
        }

        else -> {
            ResolvedArg(arg.toString())
        }
    }

    private fun resolveValue(arg: Any): Any = when (arg) {
        is TextResource.Plain -> arg.value
        is TextResource.Res -> resources.getString(arg.id, *arg.args.map(::resolveValue).toTypedArray())
        is TextResource.PluralRes -> resolvePlural(arg)
        else -> arg
    }

    private fun resolvePlural(resource: TextResource.PluralRes): String = resources.getQuantityString(resource.id, resource.quantity, *resource.args.map(::resolveValue).toTypedArray())
}
