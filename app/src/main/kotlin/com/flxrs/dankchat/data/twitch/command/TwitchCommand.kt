package com.flxrs.dankchat.data.twitch.command

@Suppress("SpellCheckingInspection")
enum class TwitchCommand(val trigger: String) {
    Announce(trigger = "announce"),
    AnnounceBlue(trigger = "announceblue"),
    AnnounceGreen(trigger = "announcegreen"),
    AnnounceOrange(trigger = "announceorange"),
    AnnouncePurple(trigger = "announcepurple"),
    Ban(trigger = "ban"),
    Unban(trigger = "unban"),
    Clear(trigger = "clear"),
    Color(trigger = "color"),
    Commercial(trigger = "commercial"),
    Delete(trigger = "delete"),
    EmoteOnly(trigger = "emoteonly"),
    EmoteOnlyOff(trigger = "emoteonlyoff"),
    Followers(trigger = "followers"),
    FollowersOff(trigger = "followersoff"),
    Marker(trigger = "marker"),
    Mods(trigger = "mods"),
    Mod(trigger = "mod"),
    Unmod(trigger = "unmod"),
    R9kBeta(trigger = "r9kbeta"),
    R9kBetaOff(trigger = "r9kbetaoff"),
    Raid(trigger = "raid"),
    Unraid(trigger = "unraid"),
    Slow(trigger = "slow"),
    SlowOff(trigger = "slowoff"),
    Subscribers(trigger = "subscribers"),
    SubscribersOff(trigger = "subscribersoff"),
    Timeout(trigger = "timeout"),
    Untimeout(trigger = "untimeout"),
    UniqueChat(trigger = "uniquechat"),
    UniqueChatOff(trigger = "uniquechatoff"),
    Vips(trigger = "vips"),
    Vip(trigger = "vip"),
    Unvip(trigger = "unvip"),
    Whisper(trigger = "w");

    companion object {
        val ALL_COMMANDS = TwitchCommand.values().toList()
        val MODERATOR_COMMANDS = TwitchCommand.values().toList() - listOf(Commercial, Mods, Mod, Unmod, Raid, Unraid, Vips, Vip, Unvip)
        val USER_COMMANDS = listOf(Color, Whisper)
    }
}
