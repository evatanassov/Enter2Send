package com.ctech.enter2send

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedAppProfilesTest {
    @Test
    fun resolvesSupportedPackages() {
        assertSame(
            SupportedAppProfiles.chatGpt,
            SupportedAppProfiles.forPackage("com.openai.chatgpt")
        )
        assertSame(
            SupportedAppProfiles.messenger,
            SupportedAppProfiles.forPackage("com.facebook.orca")
        )
        assertSame(
            SupportedAppProfiles.claudeRemoteControl,
            SupportedAppProfiles.forPackage("com.anthropic.claude")
        )
        assertSame(
            SupportedAppProfiles.instagram,
            SupportedAppProfiles.forPackage("com.instagram.android")
        )
        assertSame(
            SupportedAppProfiles.instagram,
            SupportedAppProfiles.forPackage("com.instagram.direct")
        )
        assertSame(
            SupportedAppProfiles.viber,
            SupportedAppProfiles.forPackage("com.viber.voip")
        )
        assertSame(
            SupportedAppProfiles.whatsApp,
            SupportedAppProfiles.forPackage("com.whatsapp")
        )
        assertSame(
            SupportedAppProfiles.gemini,
            SupportedAppProfiles.forPackage("com.google.android.apps.bard")
        )
        assertSame(
            SupportedAppProfiles.gemini,
            SupportedAppProfiles.forPackage("com.google.android.googlequicksearchbox")
        )
        assertSame(
            SupportedAppProfiles.slack,
            SupportedAppProfiles.forPackage("com.Slack")
        )
        assertSame(
            SupportedAppProfiles.discord,
            SupportedAppProfiles.forPackage("com.discord")
        )
        assertNull(SupportedAppProfiles.forPackage("com.example.other"))
        assertNull(SupportedAppProfiles.forPackage(null))
    }

    @Test
    fun keepsAdditionalAppsOptInWithoutChangingChatGptDefault() {
        assertTrue(SupportedAppProfiles.chatGpt.enabledByDefault)
        assertFalse(SupportedAppProfiles.messenger.enabledByDefault)
        assertFalse(SupportedAppProfiles.claudeRemoteControl.enabledByDefault)
        assertFalse(SupportedAppProfiles.instagram.enabledByDefault)
        assertFalse(SupportedAppProfiles.viber.enabledByDefault)
        assertFalse(SupportedAppProfiles.whatsApp.enabledByDefault)
        assertFalse(SupportedAppProfiles.gemini.enabledByDefault)
        assertFalse(SupportedAppProfiles.slack.enabledByDefault)
        assertFalse(SupportedAppProfiles.discord.enabledByDefault)
    }

    @Test
    fun matchesOnlyExactSemanticSendDescriptions() {
        val profile = SupportedAppProfiles.messenger

        assertTrue(profile.hasSendIdentity("Send", null))
        assertTrue(profile.hasSendIdentity(" send message ", null))
        assertFalse(profile.hasSendIdentity("Send Like", null))
        assertFalse(profile.hasSendIdentity("Resend", null))
    }

    @Test
    fun matchesOnlyKnownSendViewIdSuffixes() {
        val profile = SupportedAppProfiles.messenger

        assertTrue(profile.hasSendIdentity(null, "com.facebook.orca:id/send_button"))
        assertTrue(profile.hasSendIdentity(null, "COM.FACEBOOK.ORCA:ID/SEND_MESSAGE"))
        assertFalse(profile.hasSendIdentity(null, "com.facebook.orca:id/send_like_button"))
        assertFalse(profile.hasSendIdentity(null, "com.facebook.orca:id/search"))
    }

    @Test
    fun limitsClaudeToItsExactRemoteControlSemantics() {
        val profile = SupportedAppProfiles.claudeRemoteControl

        assertTrue(profile.hasSendIdentity("Send", null))
        assertFalse(profile.hasSendIdentity("Send message", null))
        assertFalse(profile.hasSendIdentity("Send now", null))
        assertFalse(profile.hasSendIdentity("Stop", null))
        assertFalse(profile.hasSendIdentity(null, "com.anthropic.claude:id/send_button"))

        assertTrue(profile.requiresWindowIdentity)
        assertTrue(profile.hasRequiredWindowIdentity("Change mode"))
        assertTrue(profile.hasRequiredWindowIdentity(" change MODE "))
        assertFalse(profile.hasRequiredWindowIdentity("Remote control"))
        assertFalse(profile.hasRequiredWindowIdentity("Voice Mode"))
        assertFalse(profile.hasRequiredWindowIdentity(null))
    }

    @Test
    fun doesNotRequireWindowMarkersForExistingProfiles() {
        assertFalse(SupportedAppProfiles.chatGpt.requiresWindowIdentity)
        assertFalse(SupportedAppProfiles.messenger.requiresWindowIdentity)
        assertFalse(SupportedAppProfiles.chatGpt.hasRequiredWindowIdentity("Change mode"))
    }

    @Test
    fun containsExactlyTheDeclaredSupportedApps() {
        assertEquals(
            setOf(
                "com.openai.chatgpt",
                "com.facebook.orca",
                "com.anthropic.claude",
                "com.instagram.android",
                "com.viber.voip",
                "com.whatsapp",
                "com.google.android.apps.bard",
                "com.Slack",
                "com.discord"
            ),
            SupportedAppProfiles.all.map { it.packageName }.toSet()
        )
    }
}
