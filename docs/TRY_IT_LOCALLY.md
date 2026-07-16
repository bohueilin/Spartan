# Try Spartan locally

A step-by-step guide to testing Spartan on your Mac. **No credentials, no accounts, no network
setup** — the app ships in mock mode and runs entirely on clearly-labeled sample data (until you
import your own WHOOP export).

## Android vs iOS — what you can test today

| | Android (repo root) | iOS (`ios/`) |
| --- | --- | --- |
| Full app UI | ✅ Emulator or device (Paths A–C below) | Needs one Xcode pass (see the iOS section) |
| Shared coaching core (rules, safety, CSV parser, video library, CoachingGym) | ✅ `./gradlew :app:testDebugUnitTest` — 129 tests | ✅ `swift run SpartanChecks` — 67 checks, no Xcode needed |
| Real WHOOP data via CSV import | ✅ In-app (Connections screen) | Domain layer ported; UI wiring lands with the Xcode pass |
| Follow-along training videos | ✅ Activity cards + "Train this metric" | Library ported (9 verified videos); UI wiring pending |

The two platforms share one coaching spec: the same rules, thresholds, safety engine, CSV
parsing, video catalog, and the same CoachingGym reward bar (see `docs/COACH_GYM.md`). If a
behavior differs between platforms, that's a bug — the dual test suites exist to catch it.

## What you'll see

Spartan is a daily health-coaching app for WHOOP users: it turns recovery/sleep/strain data into a
short daily plan of concrete activities. On first launch you complete a one-screen onboarding, then
land on the **Daily check-in** — the hero screen. Everything below is driven by the built-in
sample WHOOP client, so the numbers are deterministic and safe to poke at.

| Screen | How to get there | What to inspect |
| --- | --- | --- |
| Onboarding | First launch | Name + optional weight, tone of the medical disclaimer |
| Daily check-in | Home tab | Readiness ring (42, "Take it easy"), activity cards, debrief sheet after checking off a training activity, snooze / skip / find-a-time overflow menu |
| Metrics + detail | Metrics tab → tap a metric | Trend chart, "what is this / why it matters" explainers, **"Train this metric"** — guided video sessions, with an explicit callout when the reading is outside your range |
| Follow-along videos | Expand any training activity card | "Follow along: …" opens a specific curated YouTube session matching the activity (e.g. the 35-min strength day links a 30-min beginner strength video) |
| Review | Review tab | Weekly trends, consistency, "Where this can take you" projection card |
| Connections | Settings → Connections | Consent UX, "Sample data" labels on WHOOP/Calendar, **Import WHOOP export (.csv)** |
| Settings | Settings tab | About, privacy/export/delete sheet, debug-only Diagnostics |
| Widget | Long-press home screen → Widgets → Spartan | "Next activity" glanceable card |
| Notifications | Wait or fake the clock | 07:15 morning digest, 19:00 evening nudge, snooze wake-up |

### Use your REAL WHOOP data (no credentials needed)

WHOOP lets every member export their data as CSV: **WHOOP app → App Settings → Data Export**, and
a zip arrives by email. Unzip it, then:

```bash
# push the CSVs to the emulator/device Downloads folder
adb push my_whoop_data_*/physiological_cycles.csv my_whoop_data_*/sleeps.csv \
        my_whoop_data_*/workouts.csv my_whoop_data_*/journal_entries.csv /sdcard/Download/
```

In the app: **Settings → Connections → Import WHOOP export (.csv)**, multi-select the four files
(long-press the first one in the picker). Today's plan regenerates from your real recovery/sleep/
strain history, the Metrics tab fills with your actual trends, and the "Sample data" labels
disappear. Disconnecting WHOOP deletes the imported data and returns the app to sample mode.

## Path A (recommended): Android Studio

The easiest way to get an emulator. Android Studio bundles its own JDK, so you don't need the
Homebrew JDK on this path.

1. **Install Android Studio** — download the Mac (Apple silicon) build from
   <https://developer.android.com/studio> and drag it into Applications.
2. **First-run setup wizard** — pick "Standard". Studio will either detect the existing SDK at
   `~/android-sdk` or install its own copy under `~/Library/Android/sdk`; both work. Let it finish
   downloading components.
3. **Open the project** — *File → Open…* → select the repo root
   (not `app/`). Wait for the Gradle sync to finish (first sync downloads
   dependencies; a few minutes is normal).
   - If Studio uses its own SDK at a different path, it will offer to update `local.properties`
     — accept.
4. **Create an emulator** — *Tools → Device Manager → Add a new device (+)* → pick a **Pixel 7**
   (any Pixel is fine) → choose a system image, **API 34 or 35, arm64** → Finish.
5. **Run** — select the device in the toolbar and press **Run ▶**.
6. **Expected first launch** — splash → onboarding (enter any name, weight optional) → Daily
   check-in with the readiness ring at **42 / "Take it easy"** and a rest-leaning plan. Android
   13+ will ask for notification permission; allow it if you want to see the reminders.

## Path B: pure command line (no Android Studio)

This machine already builds the app green from the CLI; the only missing pieces are the emulator
package and `sdkmanager` itself (`~/android-sdk` has no `cmdline-tools/`).

1. **Environment** (put in `~/.zshrc` if you like):

   ```bash
   export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
   export ANDROID_HOME="$HOME/android-sdk"
   export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
   ```

2. **Get `sdkmanager`** (one-time) — download "Command line tools only" from
   <https://developer.android.com/studio#command-line-tools-only>, then:

   ```bash
   mkdir -p "$ANDROID_HOME/cmdline-tools"
   unzip ~/Downloads/commandlinetools-mac-*_latest.zip -d "$ANDROID_HOME/cmdline-tools"
   mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
   ```

3. **Install the emulator and an arm64 system image** (Apple silicon — do not pick x86_64):

   ```bash
   sdkmanager "emulator" "system-images;android-35;google_apis;arm64-v8a"
   sdkmanager --licenses   # review and accept the SDK licenses when prompted
   ```

4. **Create and boot a virtual device:**

   ```bash
   avdmanager create avd -n spartan -k "system-images;android-35;google_apis;arm64-v8a" -d pixel_7
   emulator -avd spartan &
   adb wait-for-device
   ```

5. **Build and install** (from the repo root; run Gradle tasks serially — this Hilt/KSP project
   can race when parallelized):

   ```bash
   cd <repo root>
   ./gradlew --no-daemon :app:assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

6. **Launch** — tap the Spartan icon, or jump straight to the check-in via the deep link:

   ```bash
   adb shell am start -a android.intent.action.VIEW -d "spartan://today" com.spartan
   ```

   `spartan://connections` deep-links to the Connections screen the same way.

## Path C: physical Android device

1. On the phone: *Settings → About phone → tap "Build number" 7 times* to enable Developer
   options, then *Settings → System → Developer options → USB debugging → on*.
2. Plug in via USB, accept the "Allow USB debugging?" prompt on the phone, and confirm the device
   shows as `device` (not `unauthorized`):

   ```bash
   adb devices
   ```

3. Build and install exactly as in Path B step 5.
4. Notes: on Android 13+ the app asks for **notification permission** on first launch — the
   07:15/19:00 reminders and snooze wake-ups are silent if you decline. Real hardware is the best
   place to judge the widget and notification UX.

## iOS: test the shared core now, the app with Xcode

The entire coaching domain is a Swift package at `ios/SpartanKit` — same rules, same copy, same
CSV parser, same video catalog, same CoachingGym as Android. It verifies on any Mac with just
Command Line Tools (**no Xcode required**):

```bash
cd ios/SpartanKit
swift build
swift run SpartanChecks   # 67 checks · 30,000+ assertions · expect "ALL CHECKS PASSED"
```

`SpartanChecks` covers the coaching engine + 756-plan eval sweep, the safety engine, the WHOOP
CSV parser/merger (`csv.*` checks), the video library (`video.*`), and the CoachingGym reward
bar (`gym.*`). With full Xcode installed, `swift test` runs the identical suite via XCTest.

To run the actual iOS app (SwiftUI shell in `ios/SpartanApp`), you need one Xcode pass:

```bash
brew install xcodegen
cd ios/SpartanApp && xcodegen generate   # then open in Xcode → run on a simulator
```

The shell is source-complete but compile-unverified without Xcode — details and the App Store
checklist live in `ios/README.md` and `ios/docs/IOS_RELEASE_CHECKLIST.md`. The CSV-import file
picker and video buttons are Android-only UI today; their shared logic is already in SpartanKit,
so the iOS wiring is thin SwiftUI work during that pass.

## Coaching quality bar (both platforms)

Beyond unit tests, the **CoachingGym** grades every generated plan on readiness alignment,
hard-gated safety, and coaching quality across ~790 gold scenarios, and fails the build if the
engine drops below its reward floor (see `docs/COACH_GYM.md`):

```bash
# Android
./gradlew :app:testDebugUnitTest --tests "com.spartan.domain.CoachingGymTest"
# iOS (included in SpartanChecks as the gym.* checks)
cd ios/SpartanKit && swift run SpartanChecks
```

## Inspecting and tweaking the UI

- **Screens** live in `app/src/main/java/com/spartan/ui/screens/` — `CheckInScreen.kt` (hero),
  `ExerciseDebriefSheet.kt`, `MetricExplainerSection.kt`, `TrajectoryCard.kt`,
  `ConnectionsScreen.kt`, `DiagnosticsScreen.kt`, and `Screens.kt` (Onboarding, Metrics,
  MetricDetail, Review, Settings). Navigation is in `ui/navigation/SpartanRoot.kt`; the
  home-screen widget in `ui/widget/NextActivityWidget.kt`.
- **Design tokens** (colors, readiness bands, spacing) are in `ui/theme/Tokens.kt` and
  `ui/theme/Theme.kt` — edit here for app-wide visual changes.
- **Compose previews** (Android Studio only): open a screen file and use the Split/Design pane.
  `ui/screens/FontScalePreviews.kt` renders the check-in at **1.0×, 1.5×, and 2.0× font scale** —
  the fastest way to check large-text behavior without a device.
- **Dark/light**: the app follows the system theme — flip it in the emulator via
  *Settings → Display → Dark theme*.
- **Font scale on device**: *Settings → Display → Display size and text* — drag Font size up and
  watch the layouts reflow.
- **TalkBack**: *Settings → Accessibility → TalkBack* — the ring, cards, and menus all carry
  content descriptions (see `docs/ACCESSIBILITY.md`).
- **Diagnostics screen**: in debug builds only, *Settings → Diagnostics* shows the operational
  log (scheduling decisions, worker runs) — useful for understanding why a reminder fired.

## Quick verification checklist

- [ ] Onboarding completes with just a name; disclaimer copy reads as wellness, not medical.
- [ ] Readiness ring shows **42** with the **"Take it easy"** band.
- [ ] A **"Sample data"** label is visible on WHOOP/Calendar in Connections.
- [ ] Checking off a training activity opens the **debrief sheet** (effort + pain questions).
- [ ] Snoozing an activity updates its status line ("Snoozed until HH:MM").
- [ ] Review shows trend rows plus the **"Where this can take you"** projection card.
- [ ] A metric detail screen shows its explainer sections below the chart.
- [ ] Expanding a training activity shows a **"Follow along: …"** video button that opens YouTube.
- [ ] A metric outside its range (e.g. resting HR above a target) shows the highlighted **"Train this metric"** callout with video session cards.
- [ ] The next-activity widget (listed as **Spartan** in the widget picker) adds to the home screen and shows the top activity.
- [ ] Tapping a notification deep-links into the app (Today screen).
- [ ] Settings → privacy sheet → **Delete local data** wipes state and returns to onboarding.

## Troubleshooting

- **Gradle fails with a JDK/toolchain error** — `JAVA_HOME` isn't set in that shell. Re-export it
  (Path B step 1) and retry. `java -version` should report 17.
- **"SDK location not found"** — `local.properties` must contain `sdk.dir=/Users/<you>/android-sdk`
  (already set in this checkout; Android Studio rewrites it if it uses its own SDK).
- **Emulator won't start / crawls on Apple silicon** — you installed an `x86_64` image. Delete the
  AVD and recreate it with the `arm64-v8a` image from Path B step 3.
- **First build is slow** — Gradle downloads the whole dependency tree once (several minutes).
  Later builds are incremental. Keep `--no-daemon` and run tasks one at a time.
- **Want a clean slate** — Settings → privacy sheet → *Delete local data*, or
  `adb uninstall com.spartan` and reinstall; onboarding starts over.
