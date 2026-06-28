# Mouse Touch Lock

**Nothing OS aesthetic · Android 10–15 · No Root Required**

Lock all finger touches while keeping USB OTG Mouse & Keyboard active.  
A minimal, monochrome floating-button tool built in Kotlin.

---

## Features

| Feature | Detail |
|---|---|
| Floating Button | Always-visible, draggable overlay |
| **Single tap** | Lock all touch input |
| **Double tap** | Unlock touch input |
| Mouse OTG | Best-effort pass-through when locked |
| Keyboard OTG | Always works (overlay is non-focusable) |
| Haptic feedback | Double pulse on lock, single on unlock |
| Toast status | "Touch Locked" / "Touch Unlocked" |
| Boot auto-start | Optional — configurable in Settings |
| Settings | Size · Opacity · Position · Auto-start |
| Accessibility Svc | Optional — auto-starts service on enable |
| Design | Nothing OS — black/white, monospace, dot aesthetic |
| Android support | API 29 (Android 10) → API 35 (Android 15) |

---

## Build Instructions

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK API 29–35

### Steps

1. **Open project**  
   File → Open → select the `MouseTouchLock/` folder.

2. **Sync Gradle**  
   Android Studio will download dependencies automatically.

3. **Debug build** (runs directly on device)
   ```
   ./gradlew assembleDebug
   ```
   APK location: `app/build/outputs/apk/debug/app-debug.apk`

4. **Release build** (requires signing)

   a. Generate a keystore (one-time):
   ```bash
   keytool -genkey -v -keystore app/keystore.jks \
     -alias mousetouchlock -keyalg RSA -keysize 2048 -validity 10000
   ```

   b. Edit `app/build.gradle` → uncomment `signingConfig signingConfigs.release`  
      and set `storePassword` / `keyPassword`.

   c. Build:
   ```
   ./gradlew assembleRelease
   ```
   APK location: `app/build/outputs/apk/release/app-release.apk`

---

## Permissions

| Permission | Purpose |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Show floating overlay windows |
| `FOREGROUND_SERVICE` | Keep service alive |
| `RECEIVE_BOOT_COMPLETED` | Auto-start on reboot |
| `VIBRATE` | Haptic feedback |
| `POST_NOTIFICATIONS` | Status notification (Android 13+) |

> **Accessibility Service** is optional. Enable it in  
> Settings → Accessibility → Mouse Touch Lock → ON

---

## Architecture

```
FloatingButtonService (Foreground Service)
├── LockOverlayView   — full-screen, TYPE_APPLICATION_OVERLAY, z-order BELOW FAB
│   ├── Unlocked: FLAG_NOT_TOUCHABLE ON  → all input passes through
│   └── Locked:   FLAG_NOT_TOUCHABLE OFF → blocks TOOL_TYPE_FINGER
│                                          returns false for TOOL_TYPE_MOUSE
└── FloatingButtonView — TYPE_APPLICATION_OVERLAY, z-order ABOVE overlay
    ├── Always touchable (above overlay in z-order)
    ├── Tap when unlocked → lock()
    └── Double-tap when locked → unlock()
```

### Mouse pass-through note
Android's `WindowManager` does not provide a guaranteed API for  
selectively blocking touchscreen while passing mouse clicks without  
system/root privileges. This app sets `FLAG_NOT_TOUCH_MODAL` and  
checks `MotionEvent.getToolType() == TOOL_TYPE_MOUSE`, returning  
`false` (unhandled) for mouse events — which may cause the dispatcher  
to forward them to the app below on many AOSP-based ROMs.  
Results vary by device manufacturer and Android version.

Keyboard OTG **always works** because `FLAG_NOT_FOCUSABLE` keeps  
keyboard focus on the background app at all times.

---

## Project Structure

```
app/src/main/
├── java/com/mousetouchlock/app/
│   ├── MainActivity.kt
│   ├── SettingsActivity.kt
│   ├── service/
│   │   ├── FloatingButtonService.kt   ← core logic
│   │   └── TouchLockAccessibilityService.kt
│   ├── view/
│   │   ├── FloatingButtonView.kt      ← custom drawn FAB
│   │   └── LockOverlayView.kt         ← touch blocker
│   ├── receiver/
│   │   └── BootReceiver.kt
│   └── util/
│       └── PreferenceHelper.kt
└── res/
    ├── layout/  activity_main.xml · activity_settings.xml
    ├── drawable/ icons · backgrounds · dots
    ├── values/   colors · strings · themes · dimens
    └── xml/      accessibility_service_config · backup_rules
```

---

## License
MIT — free to use, modify, and distribute.
