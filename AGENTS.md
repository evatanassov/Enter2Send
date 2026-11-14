# AGENTS.md — Developer & AI Agent Guide for Enter2Send

## Overview

**Enter2Send** is an Android Accessibility Service designed to give physical keyboard users (especially on Samsung DeX and Android tablets) desktop-style messaging behavior:
- **Enter / Numpad Enter**: Clicks the target app's native **Send** button without sending dummy touch events or key code injections.
- **Shift + Enter**: Inserts a newline (`\n`) directly into the active text composer at the cursor position.

---

## Core Architecture & File Structure

```text
app/src/main/
├── java/com/ctech/enter2send/
│   ├── ChatGptKeyAccessibilityService.kt   # Core AccessibilityService & key event logic
│   ├── SupportedAppProfile.kt              # App profile data model & registry
│   ├── BridgePreferences.kt                # SharedPreferences helper for app toggles
│   └── MainActivity.kt                     # Settings UI (dynamically renders app cards)
└── res/
    ├── values/strings.xml                  # App strings & summaries
    └── xml/accessibility_service_config.xml # Package filter & accessibility flags
```

---

## Technical Design & Key Operations

### 1. Key Interception (`ChatGptKeyAccessibilityService.kt`)
- `onKeyEvent(event)` catches physical keyboard `KEYCODE_ENTER` and `KEYCODE_NUMPAD_ENTER`.
- Verifies master switch (`BridgePreferences.isMasterEnabled`) and active app profile (`activeAppRoot`).
- **Shift+Enter**: Calls `insertNewlineIntoComposer()`, modifying text via `AccessibilityNodeInfo.ACTION_SET_TEXT` and updating cursor position with `ACTION_SET_SELECTION`.
- **Enter**: Finds focused composer, locates unique Send button, invokes `AccessibilityNodeInfo.ACTION_CLICK`, and starts send confirmation polling.

### 2. Candidate & Send Button Matching
- `findFocusedComposer()`: Inspects `FOCUS_INPUT` or searches editable nodes matching package filters or webview containers.
- `collectIdentityTargets()`: Matches nodes by `contentDescription`, `text`, or `viewIdResourceName` against the active `SupportedAppProfile`.
- **Fallback Search**: If ancestor traversal up 8-10 levels yields no unique Send button (e.g. complex Instagram/React Native layouts), `findSendButtonNearComposer()` falls back to searching `rootInActiveWindow` for a single matching Send control.

### 3. App Profiles (`SupportedAppProfile.kt`)
Supported apps in `SupportedAppProfiles.all`:
1. **ChatGPT**: `com.openai.chatgpt` (enabled by default)
2. **Messenger**: `com.facebook.orca`
3. **Claude Remote Control**: `com.anthropic.claude`
4. **Instagram**: `com.instagram.android`, `com.instagram.direct`, `com.instagram.threadsapp`
5. **Viber**: `com.viber.voip`
6. **WhatsApp**: `com.whatsapp`
7. **Gemini**: `com.google.android.apps.bard`, `com.google.android.googlequicksearchbox`
8. **Slack**: `com.Slack`
9. **Discord**: `com.discord`
10. **Antigravity**: `com.google.android.apps.antigravity`, `com.google.antigravity`, `com.google.android.antigravity`, `com.antigravity`

---

## Build & Test Instructions

### Environment Prerequisites
- Android SDK installed (`ANDROID_HOME=/data/data/com.termux/files/home/android-sdk`).
- On Termux / ARM Linux environments, set `-Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2`.

### Build & Run Tests
```bash
ANDROID_HOME=/data/data/com.termux/files/home/android-sdk \
bash ./gradlew testDebugUnitTest assembleDebug \
-Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
```

### Artifact Locations
- **Built Debug APK**: `app/build/outputs/apk/debug/app-debug.apk` (or `Enter2Send-debug.apk` in root).

---

## How to Add Support for a New App

1. **Add App Profile in `SupportedAppProfile.kt`**:
   - Define package names (`packageNames = setOf(...)`).
   - Define send button content descriptions (`sendDescriptions`).
   - Define view ID suffixes (`sendViewIdSuffixes`).
   - Add to `SupportedAppProfiles.all`.
2. **Add String Resource in `strings.xml`**:
   - Create `<string name="YOUR_APP_summary">...</string>`.
3. **Update Accessibility Filter in `accessibility_service_config.xml`**:
   - Append package name to `android:packageNames` comma-separated list.
4. **Add Unit Tests in `SupportedAppProfilesTest.kt`**:
   - Add assertions for package resolution, defaults, and app list contents.
