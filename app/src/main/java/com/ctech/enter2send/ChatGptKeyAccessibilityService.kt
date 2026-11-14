package com.ctech.enter2send

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatGptKeyAccessibilityService : AccessibilityService() {
    private var consumedKeyCode: Int? = null
    private var sendOperation: SendOperation? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var sendPoll: Runnable? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val operationPackage = sendOperation?.composerAnchor?.packageName ?: return
        val eventPkg = event?.packageName?.toString() ?: return
        val profile = SupportedAppProfiles.forPackage(operationPackage)
        if (eventPkg != operationPackage && profile?.matchesPackage(eventPkg) != true) {
            resetSendOperation()
        }
    }

    override fun onInterrupt() {
        consumedKeyCode = null
        resetSendOperation()
    }

    override fun onDestroy() {
        consumedKeyCode = null
        resetSendOperation()
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!BridgePreferences.isMasterEnabled(this)) {
            consumedKeyCode = null
            resetSendOperation()
            return false
        }

        if (event.keyCode != KeyEvent.KEYCODE_ENTER &&
            event.keyCode != KeyEvent.KEYCODE_NUMPAD_ENTER
        ) {
            return false
        }

        if (event.action == KeyEvent.ACTION_UP) {
            val consume = consumedKeyCode == event.keyCode
            consumedKeyCode = null
            return consume
        }

        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (event.repeatCount > 0) return consumedKeyCode == event.keyCode

        val diag = StringBuilder()
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        diag.append("Time: ").append(timeStr)
            .append(" | Key: ").append(event.keyCode)
            .append(" | Shift: ").append(event.isShiftPressed).append("\n")

        val activeApp = activeAppRoot(diag) ?: run {
            BridgePreferences.setLastDiagnostic(this, diag.toString())
            resetSendOperation()
            return false
        }

        if (event.isShiftPressed) {
            val composer = findFocusedComposer(activeApp.root, activeApp.profile, diag) ?: run {
                diag.append("Result: Shift+Enter ignored (no composer found)\n")
                BridgePreferences.setLastDiagnostic(this, diag.toString())
                return false
            }
            val text = composer.text?.toString() ?: ""
            val start = composer.textSelectionStart
            val end = composer.textSelectionEnd
            val newText = if (start >= 0 && end >= start && end <= text.length) {
                text.substring(0, start) + "\n" + text.substring(end)
            } else {
                text + "\n"
            }
            val args = android.os.Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    newText
                )
            }
            val setSuccess = composer.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (setSuccess) {
                val newCursor = if (start >= 0 && end >= start && end <= text.length) start + 1 else newText.length
                val selectionArgs = android.os.Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newCursor)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newCursor)
                }
                composer.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
                consumedKeyCode = event.keyCode
                diag.append("Result: Shift+Enter inserted newline\n")
                BridgePreferences.setLastDiagnostic(this, diag.toString())
                return true
            }
            diag.append("Result: Shift+Enter ACTION_SET_TEXT returned false\n")
            BridgePreferences.setLastDiagnostic(this, diag.toString())
            return false
        }

        // Clear any stale send poll before handling a fresh physical Enter key press
        resetSendOperation()

        val composer = findFocusedComposer(activeApp.root, activeApp.profile, diag) ?: run {
            diag.append("Result: Pass-through (no focused composer found)\n")
            appendWindowClickablesSummary(activeApp.root, diag)
            BridgePreferences.setLastDiagnostic(this, diag.toString())
            return false
        }

        diag.append("Composer: ").append(describeNode(composer, includeText = false)).append("\n")
        appendNearbyClickablesSummary(composer, activeApp.root, diag)

        val sendButton = findUniqueSendButtonNearComposer(composer, activeApp.profile, activeApp.root)
        if (sendButton != null) {
            diag.append("SendButton: ").append(describeNode(sendButton, includeText = true)).append("\n")
            val composerAnchor = createComposerAnchor(composer, activeApp.profile)
            val clicked = performSendClick(sendButton)
            diag.append("Result: Clicked sendButton = ").append(clicked).append("\n")
            BridgePreferences.setLastDiagnostic(this, diag.toString())
            if (clicked) {
                consumedKeyCode = event.keyCode
                beginSendConfirmation(composerAnchor)
            }
            return clicked
        }

        // Fallback for Antigravity / WebView: try ACTION_IME_ENTER on composer if supported
        if (activeApp.profile.allowUnlabeledComposerButtonFallback &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            supportsAction(composer, AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        ) {
            val imeClicked = composer.performAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
            )
            diag.append("Result: Fallback ACTION_IME_ENTER = ").append(imeClicked).append("\n")
            BridgePreferences.setLastDiagnostic(this, diag.toString())
            if (imeClicked) {
                consumedKeyCode = event.keyCode
                return true
            }
        }

        diag.append("Result: Pass-through (no unique Send button matched)\n")
        BridgePreferences.setLastDiagnostic(this, diag.toString())
        return false
    }

    private fun performSendClick(sendButton: AccessibilityNodeInfo): Boolean {
        if (sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        var parent = sendButton.parent
        repeat(3) {
            val current = parent ?: return@repeat
            if (current.isClickable || supportsAction(current, AccessibilityNodeInfo.ACTION_CLICK)) {
                if (current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
            }
            parent = current.parent
        }
        for (i in 0 until sendButton.childCount) {
            val child = sendButton.getChild(i) ?: continue
            if (child.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
        }
        return false
    }

    private fun activeAppRoot(diag: StringBuilder? = null): ActiveAppRoot? {
        val allWindows = interactiveWindows()
        val focusedWindow = InputFocusedWindowSelector.select(allWindows) { it.isFocused }
            ?: InputFocusedWindowSelector.select(
                allWindows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            ) { it.isFocused }
            ?: allWindows.firstOrNull {
                it.isFocused && it.type == AccessibilityWindowInfo.TYPE_APPLICATION
            }

        val root = focusedWindow?.root?.takeIf { it.windowId == focusedWindow.id }
            ?: rootInActiveWindow

        if (root == null) {
            diag?.append("Window: root is null (windows=${allWindows.size})\n")
            return null
        }

        val rootPkg = root.packageName?.toString()
        diag?.append("Package: ${rootPkg ?: "null"} (windowId=${root.windowId})\n")

        val profile = SupportedAppProfiles.forPackage(rootPkg) ?: run {
            diag?.append("Profile: None matched package $rootPkg\n")
            return null
        }

        val enabled = BridgePreferences.isAppEnabled(this, profile)
        diag?.append("Profile: ${profile.displayName} (enabled=$enabled)\n")

        if (!enabled) return null
        if (!hasRequiredWindowIdentity(root, profile)) {
            diag?.append("WindowIdentity: Required identity missing\n")
            return null
        }
        return ActiveAppRoot(profile, root)
    }

    private fun interactiveWindows(): List<AccessibilityWindowInfo> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return windows

        val windowsByDisplay = windowsOnAllDisplays
        return buildList {
            for (index in 0 until windowsByDisplay.size()) {
                addAll(windowsByDisplay.valueAt(index))
            }
        }
    }

    private fun hasRequiredWindowIdentity(
        root: AccessibilityNodeInfo,
        profile: SupportedAppProfile
    ): Boolean {
        if (!profile.requiresWindowIdentity) return true

        fun visit(node: AccessibilityNodeInfo): Boolean {
            if (profile.matchesPackage(node.packageName?.toString()) &&
                node.isVisibleToUser &&
                node.isEnabled &&
                !node.isEditable
            ) {
                val semanticMatch =
                    profile.hasRequiredWindowIdentity(node.contentDescription) ||
                        node.actionList.any {
                            profile.hasRequiredWindowIdentity(it.label)
                        }
                if (semanticMatch) return true
            }

            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                if (visit(child)) return true
            }
            return false
        }

        return visit(root)
    }

    private fun beginSendConfirmation(composerAnchor: ComposerAnchor) {
        val now = SystemClock.uptimeMillis()
        sendOperation = SendOperation.AwaitingClear(
            composerAnchor = composerAnchor,
            deadline = now + SEND_CONFIRM_TIMEOUT_MS
        )
        startSendPolling()
    }

    private fun refreshSendOperation(
        root: AccessibilityNodeInfo,
        profile: SupportedAppProfile
    ) {
        val operation = sendOperation ?: return
        if (profile.packageName != operation.composerAnchor.packageName ||
            root.windowId != operation.composerAnchor.windowId
        ) {
            resetSendOperation()
            return
        }

        val focusedComposer = findFocusedComposer(root, profile)
        if (focusedComposer != null &&
            !matchesComposerAnchor(focusedComposer, operation.composerAnchor)
        ) {
            resetSendOperation()
            return
        }

        val now = SystemClock.uptimeMillis()
        when (operation) {
            is SendOperation.AwaitingClear -> {
                if (now >= operation.deadline) {
                    resetSendOperation()
                    return
                }

                val composer = findAnchoredComposer(root, profile, operation.composerAnchor)
                if (composer == null) {
                    operation.clearPolls = 0
                    return
                }

                when (findSendButtonNearComposer(composer, profile, root, includeUnlabeledFallback = false)) {
                    SendButtonMatch.Absent -> {
                        operation.clearPolls += 1
                        if (operation.clearPolls >= SEND_CLEAR_CONFIRM_POLLS) {
                            beginRefocusing(operation.composerAnchor, composer, now)
                        }
                    }

                    is SendButtonMatch.Unique,
                    SendButtonMatch.Ambiguous -> operation.clearPolls = 0
                }
            }

            is SendOperation.Refocusing -> {
                if (now >= operation.deadline) {
                    resetSendOperation()
                    return
                }

                val composer = findAnchoredComposer(root, profile, operation.composerAnchor)
                    ?: return
                advanceRefocus(operation, composer, now)
            }
        }
    }

    private fun beginRefocusing(
        composerAnchor: ComposerAnchor,
        composer: AccessibilityNodeInfo,
        now: Long
    ) {
        val operation = SendOperation.Refocusing(
            composerAnchor = composerAnchor,
            deadline = now + FOCUS_RESTORE_TIMEOUT_MS,
            clickAllowedAt = now + FOCUS_CLICK_FALLBACK_DELAY_MS
        )
        sendOperation = operation
        advanceRefocus(operation, composer, now)
    }

    private fun advanceRefocus(
        operation: SendOperation.Refocusing,
        composer: AccessibilityNodeInfo,
        now: Long
    ) {
        if (composer.isFocused) {
            resetSendOperation()
            return
        }

        if (!operation.focusAttempted) {
            operation.focusAttempted = true
            if (supportsAction(composer, AccessibilityNodeInfo.ACTION_FOCUS)) {
                composer.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            }
            return
        }

        if (!operation.clickAttempted &&
            now >= operation.clickAllowedAt &&
            supportsAction(composer, AccessibilityNodeInfo.ACTION_CLICK)
        ) {
            operation.clickAttempted = true
            composer.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    private fun startSendPolling() {
        cancelSendPolling()
        val poll = object : Runnable {
            override fun run() {
                if (sendOperation == null) {
                    sendPoll = null
                    return
                }
                if (!BridgePreferences.isMasterEnabled(this@ChatGptKeyAccessibilityService)) {
                    resetSendOperation()
                    return
                }

                val activeApp = activeAppRoot()
                if (activeApp == null) {
                    resetSendOperation()
                    return
                }

                refreshSendOperation(activeApp.root, activeApp.profile)
                if (sendOperation != null) {
                    mainHandler.postDelayed(this, SEND_POLL_INTERVAL_MS)
                } else {
                    sendPoll = null
                }
            }
        }
        sendPoll = poll
        mainHandler.post(poll)
    }

    private fun cancelSendPolling() {
        sendPoll?.let(mainHandler::removeCallbacks)
        sendPoll = null
    }

    private fun resetSendOperation() {
        cancelSendPolling()
        sendOperation = null
    }

    private fun findFocusedComposer(
        root: AccessibilityNodeInfo,
        profile: SupportedAppProfile,
        diag: StringBuilder? = null
    ): AccessibilityNodeInfo? {
        val inputFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (inputFocus != null) {
            diag?.append("FOCUS_INPUT: ${describeNode(inputFocus, includeText = false)}\n")
            if (isEditableComposerCandidate(inputFocus, profile, requireFocus = false)) {
                return inputFocus
            }
            // If FOCUS_INPUT is a WebView or container, look inside its subtree first
            findEditableInSubtree(inputFocus, profile, requireFocus = true)?.let { return it }
            findEditableInSubtree(inputFocus, profile, requireFocus = false)?.let { return it }
        } else {
            diag?.append("FOCUS_INPUT: null\n")
        }

        findEditableInSubtree(root, profile, requireFocus = true)?.let { return it }
        if (profile.allowUnlabeledComposerButtonFallback) {
            findEditableInSubtree(root, profile, requireFocus = false)?.let { return it }
        }
        return null
    }

    private fun findAnchoredComposer(
        root: AccessibilityNodeInfo,
        profile: SupportedAppProfile,
        composerAnchor: ComposerAnchor
    ): AccessibilityNodeInfo? {
        val composer = findEditableInSubtree(root, profile, requireFocus = false) ?: return null
        return composer.takeIf { matchesComposerAnchor(it, composerAnchor) }
    }

    private fun findEditableInSubtree(
        root: AccessibilityNodeInfo,
        profile: SupportedAppProfile,
        requireFocus: Boolean
    ): AccessibilityNodeInfo? {
        val matches = mutableListOf<AccessibilityNodeInfo>()

        fun visit(node: AccessibilityNodeInfo) {
            if (isEditableComposerCandidate(node, profile, requireFocus)) {
                matches += node
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(::visit)
            }
        }

        visit(root)
        if (matches.size == 1) return matches.single()
        if (matches.size > 1 && profile.allowUnlabeledComposerButtonFallback) {
            // If multiple editable nodes exist (e.g. search bar + bottom chat composer), pick the bottom-most one
            return matches.maxByOrNull { node ->
                val rect = Rect()
                node.getBoundsInScreen(rect)
                rect.bottom
            }
        }
        return null
    }

    private fun isEditableComposerCandidate(
        node: AccessibilityNodeInfo,
        profile: SupportedAppProfile,
        requireFocus: Boolean
    ): Boolean {
        if (!node.isVisibleToUser || !node.isEnabled) return false
        if (!matchesNodePackage(node.packageName?.toString(), profile)) return false

        val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
        if (viewId.contains("url_bar") ||
            viewId.contains("location_bar") ||
            viewId.contains("omnibox") ||
            viewId.contains("search_box")
        ) {
            return false
        }

        val className = node.className?.toString() ?: ""
        if (className == "android.webkit.WebView") return false

        val isEditableOrInput = node.isEditable ||
            className.contains("Edit", ignoreCase = true) ||
            className.contains("TextField", ignoreCase = true) ||
            supportsAction(node, AccessibilityNodeInfo.ACTION_SET_TEXT) ||
            (profile.allowUnlabeledComposerButtonFallback &&
                node.isFocused &&
                (supportsAction(node, AccessibilityNodeInfo.ACTION_PASTE) ||
                    supportsAction(node, AccessibilityNodeInfo.ACTION_SET_SELECTION) ||
                    node.inputType != 0))

        return isEditableOrInput && (!requireFocus || node.isFocused)
    }

    private fun matchesNodePackage(
        pkg: String?,
        profile: SupportedAppProfile
    ): Boolean =
        pkg == null ||
            profile.matchesPackage(pkg) ||
            pkg == "android" ||
            pkg.contains("webview", ignoreCase = true) ||
            pkg.contains("chrome", ignoreCase = true)

    private fun createComposerAnchor(
        composer: AccessibilityNodeInfo,
        profile: SupportedAppProfile
    ): ComposerAnchor =
        ComposerAnchor(
            packageName = profile.packageName,
            windowId = composer.windowId,
            className = composer.className?.toString(),
            viewIdResourceName = composer.viewIdResourceName,
            ancestorClassNames = composerAncestorClassNames(composer)
        )

    private fun matchesComposerAnchor(
        composer: AccessibilityNodeInfo,
        anchor: ComposerAnchor
    ): Boolean {
        val profile = SupportedAppProfiles.forPackage(anchor.packageName)
        val pkgMatches = composer.packageName?.toString() == anchor.packageName ||
            (profile != null && matchesNodePackage(composer.packageName?.toString(), profile))
        return pkgMatches &&
            composer.className?.toString() == anchor.className &&
            composer.viewIdResourceName == anchor.viewIdResourceName &&
            composerAncestorClassNames(composer) == anchor.ancestorClassNames
    }

    private fun composerAncestorClassNames(composer: AccessibilityNodeInfo): List<String?> {
        val classNames = mutableListOf<String?>()
        var ancestor = composer.parent
        repeat(MAX_COMPOSER_ANCHOR_ANCESTORS) {
            val current = ancestor ?: return@repeat
            classNames += current.className?.toString()
            ancestor = current.parent
        }
        return classNames
    }

    private fun findUniqueSendButtonNearComposer(
        composer: AccessibilityNodeInfo,
        profile: SupportedAppProfile,
        windowRoot: AccessibilityNodeInfo? = null
    ): AccessibilityNodeInfo? =
        (findSendButtonNearComposer(composer, profile, windowRoot, includeUnlabeledFallback = true) as? SendButtonMatch.Unique)?.node

    private fun findSendButtonNearComposer(
        composer: AccessibilityNodeInfo,
        profile: SupportedAppProfile,
        windowRoot: AccessibilityNodeInfo? = null,
        includeUnlabeledFallback: Boolean = false
    ): SendButtonMatch {
        var ancestor: AccessibilityNodeInfo? = composer
        repeat(MAX_COMPOSER_ANCESTOR_LEVELS) {
            ancestor = ancestor?.parent
            val scope = ancestor ?: return@repeat
            val candidates = mutableListOf<AccessibilityNodeInfo>()
            collectIdentityTargets(
                scope,
                null,
                profile,
                candidates
            )
            when (candidates.size) {
                1 -> return SendButtonMatch.Unique(candidates.single())
                in 2..Int.MAX_VALUE -> return SendButtonMatch.Ambiguous
            }
        }

        // Fallback 1: search window root for semantic Send identity if ancestor search yielded none
        val root = windowRoot ?: rootInActiveWindow
        if (root != null) {
            val rootCandidates = mutableListOf<AccessibilityNodeInfo>()
            collectIdentityTargets(root, null, profile, rootCandidates)
            if (rootCandidates.size == 1) {
                return SendButtonMatch.Unique(rootCandidates.single())
            } else if (rootCandidates.size > 1) {
                return SendButtonMatch.Ambiguous
            }
        }

        // Fallback 2: for Antigravity / WebView composers where the Send button may be an unlabeled SVG/icon button
        if (includeUnlabeledFallback && profile.allowUnlabeledComposerButtonFallback) {
            findUnlabeledComposerSendButton(composer, profile)?.let {
                return SendButtonMatch.Unique(it)
            }
        }

        return SendButtonMatch.Absent
    }

    private fun findUnlabeledComposerSendButton(
        composer: AccessibilityNodeInfo,
        profile: SupportedAppProfile
    ): AccessibilityNodeInfo? {
        val composerBounds = Rect()
        composer.getBoundsInScreen(composerBounds)
        if (composerBounds.isEmpty) return null

        var ancestor: AccessibilityNodeInfo? = composer
        repeat(5) {
            ancestor = ancestor?.parent
            val scope = ancestor ?: return@repeat
            val scopeBounds = Rect()
            scope.getBoundsInScreen(scopeBounds)
            // Keep search scoped to the composer bar/container rather than the entire screen
            if (scopeBounds.height() > composerBounds.height() * 4 && scopeBounds.height() > 600) {
                return@repeat
            }

            val candidates = mutableListOf<AccessibilityNodeInfo>()
            collectUnlabeledClickableButtons(scope, composer, profile, composerBounds, candidates)
            if (candidates.isNotEmpty()) {
                val rightmost = candidates.maxWithOrNull(
                    compareBy<AccessibilityNodeInfo> { node ->
                        val r = Rect()
                        node.getBoundsInScreen(r)
                        r.right
                    }.thenBy { node ->
                        val r = Rect()
                        node.getBoundsInScreen(r)
                        r.bottom
                    }
                )
                if (rightmost != null) return rightmost
            }
        }
        return null
    }

    private fun collectUnlabeledClickableButtons(
        node: AccessibilityNodeInfo,
        composer: AccessibilityNodeInfo,
        profile: SupportedAppProfile,
        composerBounds: Rect,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        if (node == composer) return
        if (node.isVisibleToUser && node.isEnabled && !node.isEditable && matchesNodePackage(node.packageName?.toString(), profile)) {
            val className = node.className?.toString() ?: ""
            val isButtonLike = node.isClickable ||
                supportsAction(node, AccessibilityNodeInfo.ACTION_CLICK) ||
                className.contains("Button", ignoreCase = true) ||
                className.contains("Image", ignoreCase = true)

            if (isButtonLike && !isExcludedComposerActionNode(node)) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                // Must be a compact button-sized element to the right of the composer's left edge
                if (!bounds.isEmpty &&
                    bounds.width() in 16..400 &&
                    bounds.height() in 16..400 &&
                    bounds.centerX() >= composerBounds.left
                ) {
                    out += node
                    return
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectUnlabeledClickableButtons(child, composer, profile, composerBounds, out)
        }
    }

    private fun isExcludedComposerActionNode(node: AccessibilityNodeInfo): Boolean {
        val tooltip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) node.tooltipText else null
        val combined = listOfNotNull(
            node.contentDescription?.toString(),
            node.text?.toString(),
            tooltip?.toString(),
            node.viewIdResourceName
        ).joinToString(" ").lowercase(Locale.ROOT)

        if (combined.isBlank()) return false

        val excludedTokens = listOf(
            "attach", "add", "plus", "upload", "file", "image", "photo", "camera",
            "mic", "voice", "audio", "record", "dictat", "stop", "cancel", "close",
            "clear", "delete", "model", "mode", "menu", "more", "option", "setting",
            "history", "back", "new", "search", "copy", "edit", "expand", "collapse"
        )
        return excludedTokens.any { combined.contains(it) }
    }

    private fun collectIdentityTargets(
        node: AccessibilityNodeInfo,
        clickableAncestor: AccessibilityNodeInfo?,
        profile: SupportedAppProfile,
        matches: MutableList<AccessibilityNodeInfo>
    ) {
        if (matches.size > 1) return

        val clickableTarget =
            if (isClickableActionNode(node, profile)) node else clickableAncestor
        val tooltipText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            node.tooltipText
        } else {
            null
        }
        if (isIdentityBearingActionNode(node, profile) &&
            profile.hasSendIdentity(
                node.contentDescription,
                node.viewIdResourceName,
                node.text,
                tooltipText
            )
        ) {
            val resolvedTarget = clickableTarget
                ?: findClickableChild(node, profile)
                ?: if (profile.allowUnlabeledComposerButtonFallback) node else null
            if (resolvedTarget != null && matches.none { it == resolvedTarget }) {
                matches += resolvedTarget
            }
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            collectIdentityTargets(
                child,
                clickableTarget,
                profile,
                matches
            )
            if (matches.size > 1) return
        }
    }

    private fun findClickableChild(
        node: AccessibilityNodeInfo,
        profile: SupportedAppProfile
    ): AccessibilityNodeInfo? {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (isClickableActionNode(child, profile)) return child
        }
        return null
    }

    private fun isClickableActionNode(
        node: AccessibilityNodeInfo,
        profile: SupportedAppProfile
    ): Boolean =
        matchesNodePackage(node.packageName?.toString(), profile) &&
            node.isVisibleToUser &&
            node.isEnabled &&
            (node.isClickable || supportsAction(node, AccessibilityNodeInfo.ACTION_CLICK))

    private fun isIdentityBearingActionNode(
        node: AccessibilityNodeInfo,
        profile: SupportedAppProfile
    ): Boolean {
        return matchesNodePackage(node.packageName?.toString(), profile) &&
            node.isVisibleToUser &&
            node.isEnabled &&
            !node.isEditable
    }

    private fun supportsAction(node: AccessibilityNodeInfo, action: Int): Boolean =
        node.actionList.any { it.id == action }

    private fun describeNode(node: AccessibilityNodeInfo, includeText: Boolean): String {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val tooltip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) node.tooltipText else null
        return buildString {
            append(node.className ?: "null")
            if (!node.viewIdResourceName.isNullOrBlank()) {
                append(" id=").append(node.viewIdResourceName)
            }
            if (!node.contentDescription.isNullOrBlank()) {
                append(" desc=\"").append(node.contentDescription.toString().take(40)).append("\"")
            }
            if (includeText && !node.text.isNullOrBlank()) {
                append(" text=\"").append(node.text.toString().take(40)).append("\"")
            }
            if (!tooltip.isNullOrBlank()) {
                append(" tooltip=\"").append(tooltip.toString().take(40)).append("\"")
            }
            append(" [edit=").append(node.isEditable)
                .append(",focus=").append(node.isFocused)
                .append(",click=").append(node.isClickable)
                .append(",bounds=").append(bounds.toShortString())
                .append("]")
        }
    }

    private fun appendNearbyClickablesSummary(
        composer: AccessibilityNodeInfo,
        root: AccessibilityNodeInfo,
        diag: StringBuilder
    ) {
        val items = mutableListOf<String>()
        var scope: AccessibilityNodeInfo? = composer
        repeat(4) { scope = scope?.parent ?: scope }
        val searchRoot = scope ?: root

        fun visit(node: AccessibilityNodeInfo) {
            if (items.size >= 12) return
            if (node != composer && node.isVisibleToUser && node.isEnabled) {
                val hasLabel = !node.contentDescription.isNullOrBlank() ||
                    !node.viewIdResourceName.isNullOrBlank() ||
                    (!node.isEditable && !node.text.isNullOrBlank())
                if (node.isClickable || supportsAction(node, AccessibilityNodeInfo.ACTION_CLICK) || hasLabel) {
                    items += "  - " + describeNode(node, includeText = !node.isEditable)
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(::visit)
                if (items.size >= 12) return
            }
        }

        visit(searchRoot)
        if (items.isNotEmpty()) {
            diag.append("Nearby nodes:\n").append(items.joinToString("\n")).append("\n")
        }
    }

    private fun appendWindowClickablesSummary(
        root: AccessibilityNodeInfo,
        diag: StringBuilder
    ) {
        val items = mutableListOf<String>()
        fun visit(node: AccessibilityNodeInfo) {
            if (items.size >= 10) return
            if (node.isVisibleToUser && node.isEnabled &&
                (node.isEditable || node.isFocused || node.isClickable || !node.contentDescription.isNullOrBlank())
            ) {
                items += "  - " + describeNode(node, includeText = false)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(::visit)
                if (items.size >= 10) return
            }
        }
        visit(root)
        if (items.isNotEmpty()) {
            diag.append("Window nodes:\n").append(items.joinToString("\n")).append("\n")
        }
    }

    private sealed class SendOperation {
        abstract val composerAnchor: ComposerAnchor
        abstract val deadline: Long

        data class AwaitingClear(
            override val composerAnchor: ComposerAnchor,
            override val deadline: Long,
            var clearPolls: Int = 0
        ) : SendOperation()

        data class Refocusing(
            override val composerAnchor: ComposerAnchor,
            override val deadline: Long,
            val clickAllowedAt: Long,
            var focusAttempted: Boolean = false,
            var clickAttempted: Boolean = false
        ) : SendOperation()
    }

    private sealed class SendButtonMatch {
        data object Absent : SendButtonMatch()
        data class Unique(val node: AccessibilityNodeInfo) : SendButtonMatch()
        data object Ambiguous : SendButtonMatch()
    }

    private data class ComposerAnchor(
        val packageName: String,
        val windowId: Int,
        val className: String?,
        val viewIdResourceName: String?,
        val ancestorClassNames: List<String?>
    )

    private data class ActiveAppRoot(
        val profile: SupportedAppProfile,
        val root: AccessibilityNodeInfo
    )

    companion object {
        private const val CLASS_BUTTON = "android.widget.Button"
        private const val CLASS_VIEW = "android.view.View"
        private const val MAX_COMPOSER_ANCESTOR_LEVELS = 10
        private const val MAX_COMPOSER_ANCHOR_ANCESTORS = 3
        private const val SEND_POLL_INTERVAL_MS = 100L
        private const val SEND_CONFIRM_TIMEOUT_MS = 3_000L
        private const val SEND_CLEAR_CONFIRM_POLLS = 2
        private const val FOCUS_RESTORE_TIMEOUT_MS = 3_000L
        private const val FOCUS_CLICK_FALLBACK_DELAY_MS = 250L
    }
}
