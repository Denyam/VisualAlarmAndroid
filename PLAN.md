# PLAN.md — Iteration 1: minimal sound alarm

Iteration-only plan. Durable conventions, tooling versions, and platform facts live in
`AGENTS.md` — consult it before starting; do not duplicate its content here.

## Scope (this iteration only)

One alarm with: time picker, repeat days, enable/disable, snooze (fixed 5 min),
dismiss, ringing with sound + vibration over the lock screen. Sound-only, no visual
alert. Persist across reboot and app restart.

Explicitly out of scope: visual alarm, multiple alarms, labels, tone picker,
configurable snooze — see AGENTS.md backlog.

## Steps

### 1. Environment prep
- Install cmdline-tools into `~/Library/Android/sdk/cmdline-tools/latest/` if missing
- `sdkmanager "platforms;android-37"` (+ 36 if AGP requires), accept licenses
- Create `local.properties` with `sdk.dir=`, set JBR 21 as Gradle JDK

### 2. Scaffold
- Gradle wrapper 9.6.0, `settings.gradle.kts`, root `build.gradle.kts`,
  `gradle/libs.versions.toml` (AGP 9.4.x, compileSdk/targetSdk 37, minSdk 24)
- `:app` module with Compose + Material 3, launcher icon, dark theme support
- `AndroidManifest.xml`: declare permissions listed in AGENTS.md; receivers, service,
  `AlarmActivity` (`showWhenLocked`, `turnScreenOn`, FSI on service notification)

### 3. Data + scheduling
- `AlarmRepository` (DataStore): `hour, minute, repeatDays, enabled, snoozeEnabled`
- `AlarmScheduler`: exact alarm set/cancel + pure `nextTriggerTime()` (bitmask days,
  one-shot vs repeating, empty-mask fallback)
- `BootReceiver`: reschedule on the four system actions from AGENTS.md

### 4. Ringing
- `AlarmReceiver` → start `AlarmRingService` (FGS `mediaPlayback`)
- Service: looping `USAGE_ALARM` sound + vibration pattern; posts full-screen
  notification; 10-min auto-stop safety net; clean `onDestroy` release
- `AlarmActivity`: lock-screen UI with time, **Dismiss**, **Snooze**
- Dismiss → stop service, cancel notification, reschedule next occurrence
- Snooze → stop service, exact alarm at now + 5 min

### 5. UI
- `MainActivity` (permission request for notifications on 33+) + `AlarmScreen`:
  big time text, enable `Switch`, Material 3 `TimePicker` dialog, 7 day chips
- Save-through-change; scheduler re-synced on every edit

### 6. Tests + verification
- Unit tests: `nextTriggerTime()` — future/past today, week wraparound, DST day,
  empty mask
- `./gradlew assembleDebug` green
- Emulator (API 35) smoke test: fire in ~2 min (full-screen ring over lock screen),
  snooze refires, dismiss stops + repeats reschedule, toggle-off doesn't fire,
  reboot keeps alarm alive

## Acceptance criteria

1. Fresh install on emulator: user can set a repeating 07:00 alarm and see next
   trigger reflected when toggling
2. Alarm fires in Doze with sound at alarm volume and survives dismiss/snooze flows
3. Alarm survives device reboot and app force-stop followed by reboot
4. All unit tests pass; debug build has no crashes or leaked services

## Risks / open issues

- Play Store review note for later: FSI + exact-alarm declarations require the app
  to genuinely be an alarm clock (it is) — no action this iteration
- If the API 37 emulator image isn't available locally, smoke-test on API 35 and
  rely on `targetSdk 37` behavior-change docs review (none blocking for this app)
