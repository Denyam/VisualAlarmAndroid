# AGENTS.md — VisualAlarmAndroid

Durable project conventions and facts that persist across iterations.
Iteration-specific work belongs in `PLAN.md`.

## What this project is

A simple Android alarm clock app (working name: **Visual Alarm**). Sound-only in v1;
a visual variant (torch strobe + screen brightness flashing) is a planned follow-up.

## Platform requirements (hard requirements, persist across iterations)

- `minSdk = 24` (Android 7.0)
- `targetSdk` / `compileSdk` = **latest stable Android release** (currently **37**,
  Android 17). When a new stable API ships, bump and re-verify.
- Kotlin + Jetpack Compose + Material 3 for all new UI. XML views only where forced.
- Single `:app` module, single-activity architecture. Add modules only with clear need.

## Tooling

- **AGP 9.4.x** (built-in Kotlin — do NOT apply `org.jetbrains.kotlin.android`)
- **Gradle 9.x** wrapper (9.6.0+ for AGP 9.4)
- **JDK 21**: `/Applications/Android Studio.app/Contents/jbr/Contents/Home`
- Build scripts: Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`)
- Dependency versions always declared in the version catalog, never inline in
  `app/build.gradle.kts`

## Local environment facts

- Android SDK: `~/Library/Android/sdk` (no `ANDROID_HOME` env var set; use
  `local.properties` `sdk.dir=`)
- Pre-existing platforms were 34/35; cmdline-tools may need installing via
  `cmdline-tools/latest/bin/sdkmanager`
- Emulator images available locally: API 34, API 35 (`google_apis_playstore`)
- No system `java` — always point Gradle at the JBR above (`org.gradle.java.home`
  or `JAVA_HOME`)

## Architecture decisions (do not re-litigate without reason)

- **Persistence**: Jetpack DataStore (single alarm in v1; switch to Room only if
  multi-alarm support demands it)
- **Scheduling**: `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, …)` — the only
  reliable way to wake in Doze
- **Permissions strategy**: alarm-app special permissions
  (`USE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`) are install-time granted — declare
  them, never request at runtime. Only runtime request: `POST_NOTIFICATIONS` (33+).
- **Ringing**: receiver → foreground service (`mediaPlayback` type) → high-priority
  notification with full-screen intent → lock-screen activity. Never start an
  activity directly from a background broadcast.
- **Sound**: loop on `USAGE_ALARM` / `STREAM_ALARM` with `AudioAttributes` so it
  plays in Doze and ignores silent mode. Vibration via `VibrationEffect` (26+) with
  a pre-26 fallback.
- **Receivers**: reschedule alarms on `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`,
  `TIME_SET`, `TIMEZONE_CHANGED`.

## Android platform knowledge (verified, applies to design decisions)

- `CameraManager.setTorchMode()`: no permission, works from background — key enabler
  for the future visual-alarm feature
- Screen brightness: own-window brightness needs no permission while visible;
  system brightness needs `WRITE_SETTINGS`
- Android 17 FGS while-in-use restriction exempts apps with exact-alarm permission
  that use the `USAGE_ALARM` stream (this app qualifies)
- Users can revoke the full-screen intent permission on 34+ — handle the
  notifications-disabled case with graceful degradation

## Code conventions

- Kotlin idiomatic, no Java in app code
- Compose: state hoisting, ViewModels where state survives config changes;
  no navigation library until multi-screen is needed
- Next-occurrence time logic must live in a pure, unit-testable class
- Strings in `res/values/strings.xml`; test-covered logic has no `Log.d` spam in
  release paths

## Backlog / future iterations (not current scope)

- v2: visual alarm (torch strobe + window-brightness flashing while ringing)
- Multiple alarms, labels, custom alarm tone picker, configurable snooze duration
- Homescreen widget, quick-settings tile