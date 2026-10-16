package com.music.konnekt

import com.music.konnekt.data.discord.DiscordRPC
import org.junit.Assert.assertEquals
import org.junit.Test

class DiscordPresenceDefaultsTest {

    @Test
    fun firstPresenceButtonInvitesTheKonnektDiscord() {
        assertEquals("Join The Discord (discord.gg/x7ysPnHs)", DiscordRPC.DEFAULT_BUTTON_1)
        assertEquals("https://discord.gg/x7ysPnHs", DiscordRPC.DISCORD_INVITE_URL)
    }
}
