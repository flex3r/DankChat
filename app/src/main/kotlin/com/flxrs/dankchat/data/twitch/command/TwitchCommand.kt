package com.flxrs.dankchat.data.twitch.command

@Suppress("SpellCheckingInspection")
enum class TwitchCommand(val trigger: String) {
    Announce(trigger = "announce"),
    AnnounceBlue(trigger = "announceblue"),
    AnnounceGreen(trigger = "announcegreen"),
    AnnounceOrange(trigger = "announceorange"),
    AnnouncePurple(trigger = "announcepurple"),
    Ban(trigger = "ban"),
    Clear(trigger = "clear"),
    Color(trigger = "color"),
    Commercial(trigger = "commercial"),
    Delete(trigger = "delete"),
    EmoteOnly(trigger = "emoteonly"),
    EmoteOnlyOff(trigger = "emoteonlyoff"),
    Followers(trigger = "followers"),
    FollowersOff(trigger = "followersoff"),
    Marker(trigger = "marker"),
    Mod(trigger = "mod"),
    Mods(trigger = "mods"),
    R9kBeta(trigger = "r9kbeta"),
    R9kBetaOff(trigger = "r9kbetaoff"),
    Raid(trigger = "raid"),
    Shield(trigger = "shield"),
    ShieldOff(trigger = "shieldoff"),
    Shoutout(trigger = "shoutout"),
    Slow(trigger = "slow"),
    SlowOff(trigger = "slowoff"),
    Subscribers(trigger = "subscribers"),
    SubscribersOff(trigger = "subscribersoff"),
    Timeout(trigger = "timeout"),
    Unban(trigger = "unban"),
    UniqueChat(trigger = "uniquechat"),
    UniqueChatOff(trigger = "uniquechatoff"),
    Unmod(trigger = "unmod"),
    Unraid(trigger = "unraid"),
    Untimeout(trigger = "untimeout"),
    Unvip(trigger = "unvip"),
    Vip(trigger = "vip"),
    Vips(trigger = "vips"),
    Whisper(trigger = "w");

    companion object {
        val ALL_COMMANDS = TwitchCommand.entries
        val MODERATOR_COMMANDS = TwitchCommand.entries - listOf(Commercial, Mods, Mod, Unmod, Raid, Unraid, Vips, Vip, Unvip)
        val USER_COMMANDS = listOf(Color, Whisper)
    }
}
