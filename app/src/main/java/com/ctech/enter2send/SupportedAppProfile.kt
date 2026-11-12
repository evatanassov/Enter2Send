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
    private val requiredWindowIdentities: Set<String> = emptySet()
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

    fun matchesPackage(pkg: String?): Boolean =
        pkg != null && packageNames.contains(pkg)

    internal val requiresWindowIdentity: Boolean
        get() = requiredWindowIdentities.isNotEmpty()

    fun hasSendIdentity(
        contentDescription: CharSequence?,
        viewIdResourceName: String?,
        text: CharSequence? = null
    ): Boolean {
        val viewId = viewIdResourceName?.lowercase(Locale.ROOT)
        if (viewId != null && sendViewIdSuffixes.any { suffix ->
                val clean = suffix.removePrefix("/").lowercase(Locale.ROOT)
                viewId.endsWith("/$clean") || viewId.endsWith(":$clean") || viewId == clean
            }
        ) {
            return true
        }

        val description = contentDescription?.toString()?.trim()
        if (description != null && sendDescriptions.any {
                it.equals(description, ignoreCase = true)
            }
        ) {
            return true
        }

        val nodeText = text?.toString()?.trim()
        if (nodeText != null && sendDescriptions.any {
                it.equals(nodeText, ignoreCase = true)
            }
        ) {
            return true
        }

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

    val all: List<SupportedAppProfile> = listOf(
        chatGpt,
        messenger,
        claudeRemoteControl,
        instagram,
        viber,
        whatsApp,
        gemini,
        slack,
        discord
    )

    fun forPackage(packageName: String?): SupportedAppProfile? =
        all.singleOrNull { it.matchesPackage(packageName) }
}
