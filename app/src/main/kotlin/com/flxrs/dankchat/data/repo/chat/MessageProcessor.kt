package com.flxrs.dankchat.data.repo.chat

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.chat.ChatItem
import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.repo.HighlightsRepository
import com.flxrs.dankchat.data.repo.IgnoresRepository
import com.flxrs.dankchat.data.repo.RepliesRepository
import com.flxrs.dankchat.data.repo.UserDisplayRepository
import com.flxrs.dankchat.data.repo.channel.ChannelRepository
import com.flxrs.dankchat.data.repo.emote.EmoteRepository
import com.flxrs.dankchat.data.twitch.message.Message
import com.flxrs.dankchat.data.twitch.message.WhisperMessage
import org.koin.core.annotation.Single

/**
 * Orchestrates the message transformation pipeline: parse → ignore → thread → display → emotes → highlights → thread update.
 *
 * Eliminates the duplicated processing chains that were scattered across ChatRepository.
 */
@Single
class MessageProcessor(
    private val ignoresRepository: IgnoresRepository,
    private val highlightsRepository: HighlightsRepository,
    private val emoteRepository: EmoteRepository,
    private val userDisplayRepository: UserDisplayRepository,
    private val repliesRepository: RepliesRepository,
    private val channelRepository: ChannelRepository,
) {
    /** Full pipeline: parse IRC → ignore → thread → display → emotes → highlights → thread update. Returns null if ignored or parse fails. */
    suspend fun processIrcMessage(
        ircMessage: IrcMessage,
        findMessageById: (UserName, String) -> Message? = { _, _ -> null },
    ): Message? = Message
        .parse(ircMessage, channelRepository::tryGetUserNameById)
        ?.let { process(it, findMessageById) }

    /** Full pipeline on an already-parsed message. Returns null if ignored. */
    suspend fun process(
        message: Message,
        findMessageById: (UserName, String) -> Message? = { _, _ -> null },
    ): Message? = message
        .applyIgnores()
        ?.calculateMessageThread(findMessageById)
        ?.calculateUserDisplays()
        ?.parseEmotesAndBadges()
        ?.calculateHighlightState()
        ?.updateMessageInThread()

    /** Partial pipeline for PubSub reward messages (no thread/emote steps). */
    suspend fun processReward(message: Message): Message? = message
        .applyIgnores()
        ?.calculateHighlightState()
        ?.calculateUserDisplays()

    /** Partial pipeline for whisper messages (no thread step). */
    suspend fun processWhisper(message: Message): Message? = message
        .applyIgnores()
        ?.calculateHighlightState()
        ?.calculateUserDisplays()
        ?.parseEmotesAndBadges()

    fun processInlineWhisper(message: WhisperMessage): WhisperMessage = highlightsRepository.calculateInlineWhisperHighlightState(message)

    /** Re-parse emotes and badges (e.g. after emote set changes). */
    suspend fun reparseEmotesAndBadges(message: Message): Message = message.parseEmotesAndBadges().updateMessageInThread()

    fun isUserBlocked(userId: UserId?): Boolean = ignoresRepository.isUserBlocked(userId)

    fun onMessageRemoved(item: ChatItem) = repliesRepository.cleanupMessageThread(item.message)

    fun cleanupMessageThreadsInChannel(channel: UserName) = repliesRepository.cleanupMessageThreadsInChannel(channel)

    private fun Message.applyIgnores(): Message? = ignoresRepository.applyIgnores(this)

    private suspend fun Message.calculateHighlightState(): Message = highlightsRepository.calculateHighlightState(this)

    private suspend fun Message.parseEmotesAndBadges(): Message = emoteRepository.parseEmotesAndBadges(this)

    private fun Message.calculateUserDisplays(): Message = userDisplayRepository.calculateUserDisplay(this)

    private fun Message.calculateMessageThread(find: (UserName, String) -> Message?): Message = repliesRepository.calculateMessageThread(this, find)

    private fun Message.updateMessageInThread(): Message = repliesRepository.updateMessageInThread(this)
}
