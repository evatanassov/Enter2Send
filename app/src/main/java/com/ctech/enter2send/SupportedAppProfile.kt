package com.ctech.enter2send

import java.util.Locale

data class SupportedAppProfile(
    val displayName: String,
    val summaryResId: Int,
    val packageNames: Set<String>,
    internal val preferenceKey: String,
    internal val enabledByDefault: Boolean,
    private val sendDescriptions: Set<String>,
    private val sendViewIdSuffixes: Set<String>,
    private val requiredWindowIdentities: Set<String> = emptySet(),
    private val packageSubstrings: Set<String> = emptySet(),
    private val sendDescriptionPrefixes: Set<String> = emptySet(),
    internal val allowUnlabeledComposerButtonFallback: Boolean = false
) {
    constructor(
        displayName: String,
        summaryResId: Int,
        packageName: String,
        preferenceKey: String,
        enabledByDefault: Boolean,
        sendDescriptions: Set<String>,
        sendViewIdSuffixes: Set<String>,
        requiredWindowIdentities: Set<String> = emptySet()
    ) : this(
        displayName = displayName,
        summaryResId = summaryResId,
        packageNames = setOf(packageName),
        preferenceKey = preferenceKey,
        enabledByDefault = enabledByDefault,
        sendDescriptions = sendDescriptions,
        sendViewIdSuffixes = sendViewIdSuffixes,
        requiredWindowIdentities = requiredWindowIdentities
    )

    val packageName: String
        get() = packageNames.first()

    fun matchesPackage(pkg: String?): Boolean {
        if (pkg == null) return false
        if (packageNames.contains(pkg)) return true
        return packageSubstrings.any { pkg.contains(it, ignoreCase = true) }
    }

    internal val requiresWindowIdentity: Boolean
        get() = requiredWindowIdentities.isNotEmpty()

    fun hasSendIdentity(
        contentDescription: CharSequence?,
        viewIdResourceName: String?,
        text: CharSequence? = null,
        tooltipText: CharSequence? = null
    ): Boolean {
        val viewId = viewIdResourceName?.lowercase(Locale.ROOT)
        if (viewId != null && sendViewIdSuffixes.any { suffix ->
                val clean = suffix.removePrefix("/").lowercase(Locale.ROOT)
                viewId.endsWith("/$clean") || viewId.endsWith(":$clean") || viewId == clean
            }
        ) {
            return true
        }

        if (allowUnlabeledComposerButtonFallback && viewId != null) {
            val idPart = viewId.substringAfterLast('/')
            if (idPart.contains("send") || idPart.contains("submit")) {
                return true
            }
        }

        fun matchesDescription(candidate: CharSequence?): Boolean {
            val value = candidate?.toString()?.trim() ?: return false
            if (value.isEmpty()) return false
            if (sendDescriptions.any { it.equals(value, ignoreCase = true) }) {
                return true
            }
            if (sendDescriptionPrefixes.any { value.startsWith(it, ignoreCase = true) }) {
                return true
            }
            return false
        }

        if (matchesDescription(contentDescription)) return true
        if (matchesDescription(text)) return true
        if (matchesDescription(tooltipText)) return true

        return false
    }

    fun hasRequiredWindowIdentity(contentDescription: CharSequence?): Boolean {
        val description = contentDescription?.toString()?.trim() ?: return false
        return requiredWindowIdentities.any {
            it.equals(description, ignoreCase = true)
        }
    }
}

object SupportedAppProfiles {
    val chatGpt = SupportedAppProfile(
        displayName = "ChatGPT",
        summaryResId = R.string.chatgpt_summary,
        packageName = "com.openai.chatgpt",
        preferenceKey = "app_chatgpt_enabled",
        enabledByDefault = true,
        sendDescriptions = setOf("Send", "Send message"),
        sendViewIdSuffixes = setOf(
            "/send",
            "/send_button",
            "/send_message",
            "/send_message_button"
        )
    )

    val messenger = SupportedAppProfile(
        displayName = "Messenger",
        summaryResId = R.string.messenger_summary,
        packageName = "com.facebook.orca",
        preferenceKey = "app_messenger_enabled",
        enabledByDefault = false,
        sendDescriptions = setOf("Send", "Send message"),
        sendViewIdSuffixes = setOf(
            "/send",
            "/send_button",
            "/send_message",
            "/send_message_button"
        )
    )

    val claudeRemoteControl = SupportedAppProfile(
        displayName = "Claude Remote Control",
        summaryResId = R.string.claude_remote_summary,
        packageName = "com.anthropic.claude",
        preferenceKey = "app_claude_remote_control_enabled",
        enabledByDefault = false,
        sendDescriptions = setOf("Send"),
        sendViewIdSuffixes = emptySet(),
        requiredWindowIdentities = setOf("Change mode")
    )

    val instagram = SupportedAppProfile(
        displayName = "Instagram",
        summaryResId = R.string.instagram_summary,
        packageNames = setOf(
            "com.instagram.android",
            "com.instagram.direct",
            "com.instagram.threadsapp"
        ),
        preferenceKey = "app_instagram_enabled",
        enabledByDefault = false,
        sendDescriptions = setOf(
            "Send",
            "Send message",
            "Send message button",
            "Direct send",
            "Direct send button",
            "Submit",
            "Send...",
            "Send Direct Message",
            "Send media",
            "Message"
        ),
        sendViewIdSuffixes = setOf(
            "/row_thread_composer_button_send",
            "/row_thread_composer_send_button_container",
            "/row_thread_composer_send_button",
            "/direct_text_composer_send_button",
            "/direct_send_button",
            "/send_button",
            "/send_message",
            "/send_message_button",
            "/send",
            "/button_send",
            "/action_bar_button_action",
            "/send_text_button",
            "/send_icon",
            "/send_view",
            "/composer_send_btn",
            "/thread_send_button"
        )
    )

    val viber = SupportedAppProfile(
        displayName = "Viber",
        summaryResId = R.string.viber_summary,
        packageName = "com.viber.voip",
        preferenceKey = "app_viber_enabled",
        enabledByDefault = false,
        sendDescriptions = setOf("Send", "Send message"),
        sendViewIdSuffixes = setOf(
            "/send_btn",
            "/btn_send",
            "/send",
            "/send_button"
        )
    )

    val whatsApp = SupportedAppProfile(
        displayName = "WhatsApp",
        summaryResId = R.string.whatsapp_summary,
        packageName = "com.whatsapp",
        preferenceKey = "app_whatsapp_enabled",
        enabledByDefault = false,
        sendDescriptions = setOf("Send", "Send message"),
        sendViewIdSuffixes = setOf(
            "/send",
            "/send_button",
            "/entry_send"
        )
    )

    val gemini = SupportedAppProfile(
        displayName = "Gemini",
        summaryResId = R.string.gemini_summary,
        packageNames = setOf(
            "com.google.android.apps.bard",
            "com.google.android.googlequicksearchbox"
        ),
        preferenceKey = "app_gemini_enabled",
        enabledByDefault = false,
        sendDescriptions = setOf("Send", "Send message", "Send prompt", "Submit prompt", "Submit"),
        sendViewIdSuffixes = setOf(
            "/send_button",
            "/send",
            "/send_icon",
            "/submit_button",
            "/send_prompt_button"
        )
    )

    val slack = SupportedAppProfile(
        displayName = "Slack",
        summaryResId = R.string.slack_summary,
        packageName = "com.Slack",
        preferenceKey = "app_slack_enabled",
        enabledByDefault = false,
        sendDescriptions = setOf("Send", "Send message"),
        sendViewIdSuffixes = setOf(
            "/send_button",
            "/send",
            "/send_icon"
        )
    )

    val discord = SupportedAppProfile(
        displayName = "Discord",
        summaryResId = R.string.discord_summary,
        packageName = "com.discord",
        preferenceKey = "app_discord_enabled",
        enabledByDefault = false,
        sendDescriptions = setOf("Send", "Send message"),
        sendViewIdSuffixes = setOf(
            "/send_button",
            "/send",
            "/chat_send_button"
        )
    )

    val antigravity = SupportedAppProfile(
        displayName = "Antigravity",
        summaryResId = R.string.antigravity_summary,
        packageNames = setOf(
            "com.google.android.apps.antigravity",
            "com.google.antigravity",
            "com.google.android.antigravity",
            "com.antigravity",
            "com.google.android.apps.jetski",
            "com.google.jetski",
            "com.google.android.jetski",
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.sec.android.app.sbrowser",
            "com.brave.browser",
            "com.microsoft.emmx",
            "org.mozilla.firefox"
        ),
        packageSubstrings = setOf(
            "antigravity",
            "jetski",
            "org.chromium.webapk"
        ),
        preferenceKey = "app_antigravity_enabled",
        enabledByDefault = true,
        sendDescriptions = setOf(
            "Send",
            "Send message",
            "Send prompt",
            "Submit",
            "Submit prompt",
            "Submit message",
            "Send to agent",
            "arrow_upward",
            "arrow_upward_alt",
            "north",
            "arrow_forward",
            "send"
        ),
        sendDescriptionPrefixes = setOf(
            "Send (",
            "Send message (",
            "Send prompt (",
            "Submit (",
            "Submit prompt ("
        ),
        sendViewIdSuffixes = setOf(
            "/send_button",
            "/send",
            "/send_btn",
            "/btn_send",
            "/send_icon",
            "/send_message",
            "/send_message_button",
            "/submit_button",
            "/submit",
            "/send_prompt_button",
            "/send_prompt",
            "/chat_send_button",
            "/composer_send_button"
        ),
        allowUnlabeledComposerButtonFallback = true
    )

    val all: List<SupportedAppProfile> = listOf(
        chatGpt,
        messenger,
        claudeRemoteControl,
        instagram,
        viber,
        whatsApp,
        gemini,
        slack,
        discord,
        antigravity
    )

    fun forPackage(packageName: String?): SupportedAppProfile? =
        all.singleOrNull { it.matchesPackage(packageName) }
}
