# Spartan UX Roadmap — Copy-Paste Prompts

Spartan already has a strong foundation: a calm, single-accent daily check-in
(`app/src/main/java/com/spartan/ui/screens/CheckInScreen.kt`) with a readiness ring, animated
progress bar, 48dp tactile check-off (`SpartanCheck`), skeleton loading, TalkBack semantics on every
interactive element, and honest empty/error states; `BigTextStyle` notifications in
`data/reminder/ReminderWorker.kt`; a Glance widget in `ui/widget/NextActivityWidget.kt`; design
tokens (`Spacing`, `Radius`, `Motion`) in `ui/theme/Tokens.kt`; and a SwiftUI port under
`ios/SpartanApp` + `ios/SpartanKit`. The gap to world-class is feel (motion, haptics), habit loop
(actionable notifications, streaks, reflection), depth (interactive trends, plan editing), and
platform reach (Live Activities, widgets, watch). Each block below is a self-contained prompt for
an AI coding agent — paste one per session. Every prompt inherits the non-negotiables: wellness
guidance only, never medical claims or diagnosis; every new user-facing string lives in
`app/src/main/res/values/strings.xml` (or the iOS catalog) and must pass
`SafetyEngine.validateCopy` (`app/src/main/java/com/spartan/domain/engine/SafetyEngine.kt`).

Effort tags: **S** = under half a day, **M** = 1–2 days, **L** = 3+ days including platform setup.

---

## Tier 1 — Feel (make every touch premium)

### 1.1 Check-off micro-interaction: spring scale + haptic + progress pulse — **S**

```text
In /Users/bohueilin/Documents/GitHub/Spartan, make completing an activity feel tactile.
Goal: checking off an activity springs, buzzes, and visibly advances the plan progress bar.
Files: app/src/main/java/com/spartan/ui/screens/CheckInScreen.kt (SpartanCheck, PlanProgress);
ui/theme/Tokens.kt (add a Motion spring spec). Do not touch MainViewModel.kt.
Acceptance:
- SpartanCheck's inner 26dp box scales with a spring (overshoot ~1.15) on toggle; existing
  color/alpha tweens stay.
- On complete, LocalHapticFeedback.performHapticFeedback (Confirm on API 34+, else LongPress);
  lighter tick on un-complete; never fires on recomposition.
- PlanProgress springs to the new fraction (replacing tween(Motion.medium)); the "N of M done"
  label updates the same frame. All motion values come from Tokens.kt, no magic numbers.
Constraints: keep the 48dp toggle target and existing toggleable/Role.Checkbox semantics and
stateDescription untouched; snap instantly under reduced motion (prompt 1.5); no new strings.
```

### 1.2 Readiness ring sweep + count-up on load — **S**

```text
In /Users/bohueilin/Documents/GitHub/Spartan, animate ReadinessRing in
app/src/main/java/com/spartan/ui/screens/CheckInScreen.kt.
Goal: on first display of a day's data, the arc sweeps 0→recovery fraction while the number
counts up to state.recoveryScore.
Files: CheckInScreen.kt only.
Acceptance:
- Sweep via Animatable keyed on recoveryScore, ~800ms decelerate, drawn in the existing Canvas
  drawArc (start -90f preserved); runs once per new score, not on every recomposition or scroll.
- Count-up derives from the same progress value so ring and number never disagree; the "--"
  null state is unchanged.
- Band color from bandColor(state.readinessBand) applies from frame one — no color flash.
Constraints: the merged contentDescription (checkin_recovery_a11y) exposes the FINAL value
immediately, never intermediate counts; snap under reduced motion; recovery stays framed as a
readiness signal, never a health verdict; no new strings.
```

### 1.3 Pull-to-refresh on the check-in screen — **M**

```text
In /Users/bohueilin/Documents/GitHub/Spartan, add Material 3 pull-to-refresh to the daily plan.
Goal: pulling down re-runs the WHOOP/plan sync with a visible indicator.
Files: app/src/main/java/com/spartan/ui/screens/CheckInScreen.kt (wrap the LazyColumn in
material3 PullToRefreshBox); ui/screens/MainViewModel.kt (isRefreshing flag on MainUiState +
refresh() reusing the sync path behind data/usecase/DailyPlanSync.kt); ui/navigation/SpartanRoot.kt.
Acceptance:
- M3 indicator tinted colorScheme.primary; dismisses on completion or failure.
- Failure sets state.syncFailed so the existing SafetyBanner(checkin_sync_failed) shows; it must
  NOT re-trigger the LoadingPlan skeleton (the loading guard stays valid).
- Second pull while isRefreshing is a no-op; works in mock mode (MockWhoopClient), no network.
- Unit test: refresh() sets/clears isRefreshing on success and failure.
Constraints: announce refreshing/updated state to TalkBack (liveRegion or stateDescription); new
strings go in res/values/strings.xml with a SafetyEngine.validateCopy test; no medical copy.
```

### 1.4 Skeleton → content crossfade — **S**

```text
In /Users/bohueilin/Documents/GitHub/Spartan, soften the skeleton-to-content cut in
app/src/main/java/com/spartan/ui/screens/CheckInScreen.kt (CheckInScreen, LoadingPlan, Skeleton).
Goal: LoadingPlan crossfades into PlanProgress + activity cards instead of popping.
Acceptance:
- Crossfade (or AnimatedContent fadeIn/fadeOut) keyed on the existing `loading` boolean,
  duration Motion.medium from ui/theme/Tokens.kt.
- Give Skeleton a real pulse: replace the static animateFloatAsState(0.9f) with
  rememberInfiniteTransition alpha 0.35f..0.65f (~1s).
- No double layout under the fade; list starts at top afterwards; EmptyPlan and the syncFailed
  banner still appear without ever looping the skeleton.
Constraints: skeletons stay semantics-free (decorative); under reduced motion the crossfade is an
instant swap and the pulse freezes mid-alpha; no new strings.
```

### 1.5 Reduced-motion respect, app-wide — **S**

```text
In /Users/bohueilin/Documents/GitHub/Spartan, honor the system animator scale everywhere.
Goal: when animations are disabled (Settings.Global.ANIMATOR_DURATION_SCALE == 0), Spartan snaps
to final states.
Files: app/src/main/java/com/spartan/ui/theme/Tokens.kt (LocalReducedMotion composition local +
reader helper); ui/theme/Theme.kt (provide it); ui/screens/CheckInScreen.kt and
ui/screens/Screens.kt (route every animate*/animateContentSize call site through it).
Acceptance:
- One spartanAnimationSpec() helper returns snap() when reduced motion is on, normal tween/spring
  otherwise; all call sites use it.
- With scale 0: check-off, ring sweep, progress, crossfade, expand/collapse complete instantly
  with identical final visuals and semantics; haptics from 1.1 still fire.
- Compose UI test (androidTest) forces the local true and asserts instant completed state.
Constraints: read the setting once per composition (remember), never per frame; zero behavior
change for users with animations on; document the pattern in one KDoc block; no new strings.
```

---

## Tier 2 — Habit loop (the app that shows up for you)

### 2.1 Morning digest rich notification with plan preview — **M**

```text
In /Users/bohueilin/Documents/GitHub/Spartan, upgrade the reminder notification in
app/src/main/java/com/spartan/data/reminder/ReminderWorker.kt from BigTextStyle to a morning digest.
Goal: the morning notification shows readiness band + top 3 activities with minutes, at a glance.
Files: ReminderWorker.kt; data/reminder/ReminderScheduler.kt (pass plan context); new
res/layout/notification_digest.xml (+ collapsed variant) using RemoteViews with
DecoratedCustomViewStyle; res/values/strings.xml.
Acceptance:
- Collapsed: band label + "N activities · M min planned". Expanded: up to 3 activity titles with
  minutes, ordered by ActivityPriority like CheckInScreen.
- Falls back to the existing BigTextStyle when the plan is empty or RemoteViews inflation fails
  (try/catch, log via diagnostics/DebugLog.kt).
- Tap opens MainActivity at check-in; reuse the existing CHANNEL_ID, no duplicate channels; unit
  test builds the digest body from a realistic plan and passes SafetyEngine.validateCopy.
Constraints: VISIBILITY_PRIVATE with a redacted public version — nothing beyond band/recovery on
the lockscreen; coaching-toned copy ("Your plan is ready"), never urgency or prognosis; sp text
units only (font-scale safe); custom-layout tap targets >= 48dp.
```

### 2.2 Notification action buttons: Done / Snooze from the shade — **M**

```text
In /Users/bohueilin/Documents/GitHub/Spartan, let users act on activity reminders without opening
the app.
Goal: "Done" and "Snooze 1h" actions on the reminder notification.
Files: new app/src/main/java/com/spartan/data/reminder/ActivityActionReceiver.kt
(BroadcastReceiver); data/reminder/ReminderWorker.kt (addAction + PendingIntent.getBroadcast with
the activity id as extra); AndroidManifest.xml (register receiver, exported=false);
data/repository/HealthRepository.kt (reuse the same status-update path MainViewModel uses);
strings.xml for labels.
Acceptance:
- Done marks the DailyActivity DONE in Room and cancels the notification; check-in reflects it
  via the existing Flow on next resume, no manual refresh.
- Snooze sets SNOOZED with snoozedUntilMillis (+1h), reschedules via ReminderScheduler, and the
  notification shows "Snoozed until h:mm a" before dismissing.
- Receiver work runs off the main thread (goAsync + coroutine or a one-shot WorkManager job);
  unit tests cover intent parsing and both status transitions; actions are idempotent.
Constraints: FLAG_IMMUTABLE PendingIntents with unique request codes per activity id; labels via
strings.xml + SafetyEngine.validateCopy; no health details in extras beyond the opaque id.
```

### 2.3 Streak-safe weekly consistency view — **M**

```text
In /Users/bohueilin/Documents/GitHub/Spartan, surface weekly consistency without guilt mechanics.
MainUiState (ui/screens/MainViewModel.kt) already carries consistencyDays7.
Goal: a compact 7-day dot row that celebrates showing up and never punishes gaps.
Files: app/src/main/java/com/spartan/ui/screens/CheckInScreen.kt (new ConsistencyRow under
PlanProgress); MainViewModel.kt (derive a per-day List<Boolean> for the last 7 days from
completed activities in HealthRepository); strings.xml.
Acceptance:
- Seven dots (>= 12dp visual inside a 48dp-tall row): today highlighted, active days filled
  primary, inactive days neutral outline — never red, never "broken streak" copy.
- Header is additive ("Active 4 of last 7 days") — no loss framing.
- One merged semantics node reads the whole row; dots are clearAndSetSemantics {}.
- Unit test: per-day derivation across a week boundary and with empty history.
Constraints: copy via strings.xml + SafetyEngine.validateCopy test; only a Motion.fast fill-in on
load, snapping under reduced motion (1.5); works in mock mode.
```

### 2.4 End-of-day reflection sheet — **M**

```text
In /Users/bohueilin/Documents/GitHub/Spartan, add an optional evening reflection.
Goal: a ModalBottomSheet after the day's plan winds down asking "How did today feel?" — mood
chips Tough / Okay / Strong plus an optional one-line note, stored locally.
Files: new app/src/main/java/com/spartan/ui/screens/ReflectionSheet.kt;
ui/screens/MainViewModel.kt (show/save logic); ui/navigation/SpartanRoot.kt (host);
data/local/Entities.kt + Dao.kt + AppDatabase.kt (ReflectionEntity + DAO + Room migration with
androidTest); data/local/PreferencesStore.kt ("don't ask again"); strings.xml.
Acceptance:
- Offers itself at most once per day, only after 6pm local, only if >= 1 activity exists; fully
  dismissible; "don't ask again" persisted.
- Mood chips are >= 48dp with Role.RadioButton semantics; the note is never required.
- ReviewEngine (domain/engine/ReviewEngine.kt) surfaces reflections as observation only ("You
  felt Strong on 3 days") — never causal or diagnostic phrasing.
- Migration test passes; reflections flow into LocalExportFormatter export and delete-all.
Constraints: copy via strings.xml + SafetyEngine.validateCopy; TalkBack focus moves to the sheet
title on open; in-app surface only — no notification nagging.
```

---

## Tier 3 — Depth (from glanceable to explorable)

### 3.1 Interactive trend charts with Vico + projection bands — **L**

```text
In /Users/bohueilin/Documents/GitHub/Spartan, replace the hand-rolled TrendCard in
app/src/main/java/com/spartan/ui/screens/Screens.kt with interactive Vico charts.
Goal: scrubbable 30-day charts (HRV, RHR, sleep, recovery) with a shaded "your typical range" band.
Files: app/build.gradle.kts (com.patrykandpatrick.vico compose-m3); Screens.kt (TrendCard,
MetricDetailScreen); new ui/screens/TrendChart.kt; domain/engine/MetricEngine.kt (rolling 30-day
mean ± 1 SD per MetricType); strings.xml.
Acceptance:
- Tap/drag scrubbing with a marker bubble (date + value); translucent band behind the line from
  the user's OWN trailing baseline — labeled "your typical range", never a population or
  clinical range.
- Colors from MaterialTheme.colorScheme; correct in dark theme and font scale 2.0 (sp axis
  labels, no clipping).
- < 7 data points falls back to the existing simple card; each chart carries a one-line
  contentDescription summary so TalkBack users get the takeaway without scrubbing.
Constraints: no medical thresholds, red danger zones, or diagnostic annotations; marker copy has
a SafetyEngine.validateCopy test; horizontal drag only (never hijack vertical scroll); entrance
animation respects reduced motion.
```

### 3.2 Metric-explainer deep links from notifications — **M**

```text
In /Users/bohueilin/Documents/GitHub/Spartan, deep-link notifications to metric explanations.
Goal: tapping the metric context in the morning digest opens MetricDetailScreen with a
plain-language explainer card.
Files: app/src/main/java/com/spartan/ui/navigation/SpartanRoot.kt (route args, e.g.
spartan://metric/{type} via MainActivity intent extras); MainActivity.kt (parse intent);
data/reminder/ReminderWorker.kt (deep-link PendingIntent); ui/screens/Screens.kt
(MetricDetailScreen gains an ExplainerCard: what this signal is, what tends to move it, what
Spartan does with it); strings.xml.
Acceptance:
- Cold start, warm start, and app-open all land on the right MetricDetailScreen; back returns to
  check-in with a single synthetic back-stack entry.
- Explainer copy exists per MetricType, wellness-framed ("HRV reflects how your nervous system is
  balancing effort and rest"), each string SafetyEngine-tested.
- Unknown metric types fall back to check-in; PendingIntents FLAG_IMMUTABLE, distinct request
  codes per metric.
Constraints: explainers never diagnose or mention diseases/medication, and each ends with the
existing "wellness guidance, not medical advice" footer string; explainer card is one merged
semantics block.
```

### 3.3 Plan editor: swap an activity for a same-category alternative — **L**

```text
In /Users/bohueilin/Documents/GitHub/Spartan, let users swap a planned activity for an
alternative that serves the same purpose (e.g. zone-2 bike ↔ brisk walk).
Goal: a "Swap" entry in ActivityCard's DropdownMenu (CheckInScreen.kt) opening a 2-3 option
chooser sheet.
Files: domain/engine/CoachingEngine.kt and/or PlanEngine.kt (alternatives(activity) from the
activity catalog, matched on category + ReadinessBand rules); domain/model/CoachingModels.kt
(category metadata if missing); ui/screens/CheckInScreen.kt (menu item + ModalBottomSheet);
ui/screens/MainViewModel.kt (persist via HealthRepository so the swap survives
data/reminder/DailyPlanRefreshWorker.kt); strings.xml.
Acceptance:
- The engine, not the UI, decides what is offered: same category, honoring the current band —
  never escalating intensity above the band's ceiling.
- Swap resets status to PENDING, keeps the slot's priority and time-of-day, and persists across
  plan regeneration for that calendar day; next day reverts to engine defaults (unit tests for both).
- Chooser rows >= 56dp with title + minutes + one-line why, Role.Button semantics.
Constraints: engine-level test runs the WHOLE alternatives catalog through
SafetyEngine.validateCopy; UI copy neutral ("Swap for...", not "This is better for you").
```

### 3.4 Personal baselines view — **M**

```text
In /Users/bohueilin/Documents/GitHub/Spartan, add a "Your baselines" screen showing how today
compares to the user's own recent normal.
Goal: from MetricsScreen (ui/screens/Screens.kt), list each MetricType with 30-day baseline,
today's value, and a delta chip ("near / above / below your baseline").
Files: domain/engine/MetricEngine.kt (share the baseline mean/SD calc with prompt 3.1); new
app/src/main/java/com/spartan/ui/screens/BaselinesScreen.kt; ui/navigation/SpartanRoot.kt
(route); ui/screens/MainViewModel.kt (expose baselines in MainUiState); strings.xml.
Acceptance:
- Baseline requires >= 14 days of data; below that show "still learning your baseline" — no number.
- Delta chips are direction-neutral in color (no red/green judgment) with the info line
  "Baselines are your own trailing 30-day average."
- Each row is one merged semantics node ("Resting heart rate, 58, near your baseline of 57").
- Unit tests: mean/SD math, insufficient-data gate, delta classification bounds.
Constraints: never label a deviation good/bad/risky; no clinical reference ranges; strings via
strings.xml + SafetyEngine tests; rows >= 48dp; survives font scale 2.0 without truncation.
```

---

## Tier 4 — Platform (meet users where they already look)

### 4.1 iOS Live Activities + Dynamic Island activity timer — **L**

```text
In /Users/bohueilin/Documents/GitHub/Spartan/ios, add a Live Activity for an in-progress activity.
Goal: starting an activity from CheckInView shows a lock-screen Live Activity + Dynamic Island
timer (elapsed vs estimatedMinutes) with a Done action.
Files: new ios/SpartanWidgets/ ActivityKit extension (SpartanActivityAttributes: title,
estimatedMinutes, startedAt; compact/minimal/expanded Island views);
ios/SpartanApp/Sources/CheckInView.swift + CheckInViewModel.swift (start/finish hooks);
ios/SpartanKit/Sources/SpartanKit/Models.swift if the model needs startedAt.
Acceptance:
- Start begins the Live Activity; Done (in-app or via the Live Activity App Intent button) ends
  it and completes the activity through the same view-model path.
- Compact Island: ring/timer. Expanded: title, elapsed/estimate, Done button >= 44pt. Lock
  screen mirrors it. Auto-ends after 3h with a neutral "wrapped up" state.
- Works with MockWhoopClient.swift, no network; degrades to lock-screen-only without Dynamic
  Island.
Constraints: title + timer ONLY on the lock screen — no recovery/HRV numbers; copy checked
against the blocked phrases in ios/SpartanKit/Sources/SpartanKit/SafetyEngine.swift; VoiceOver
labels on every Island element; ring animation respects accessibilityReduceMotion.
```

### 4.2 iOS WidgetKit widget (parity with the Android Glance widget) — **M**

```text
In /Users/bohueilin/Documents/GitHub/Spartan/ios, build a WidgetKit home-screen widget matching
app/src/main/java/com/spartan/ui/widget/NextActivityWidget.kt on Android.
Goal: small + medium widgets showing readiness band, recovery, and the next pending activity,
tapping through to CheckInView.
Files: new ios/SpartanWidgets/NextActivityWidget.swift (TimelineProvider + views); an App Group
shared store written by ios/SpartanApp/Sources/CheckInViewModel.swift after each plan refresh;
ios/SpartanApp/Sources/SpartanApp.swift (widget deep-link handling).
Acceptance:
- Small: band + recovery + next activity title. Medium: adds minutes, time-of-day, done count.
  Placeholder and "open Spartan to set up" empty states included.
- Timeline refreshes after each in-app sync and at local midnight — never shows yesterday's plan.
- System backgrounds, light/dark, Dynamic Type up to accessibility sizes without clipping.
- Widget tap deep-links to check-in; per-activity tap (medium) opens that activity expanded.
Constraints: band + recovery only on this glanceable surface — no HRV/RHR/sleep detail; strings
covered by a SpartanChecks SafetyEngine test; VoiceOver reads one coherent summary per widget.
```

### 4.3 Wear OS tile + watchOS complication — **L**

```text
In /Users/bohueilin/Documents/GitHub/Spartan, extend to the wrist with read-only glanceables.
Goal: a Wear OS tile and a watchOS complication showing readiness band + next activity,
tapping through to the phone/iOS app.
Files (Android): new :wear module (androidx.wear.tiles + ProtoLayout) fed via DataClient written
from data/usecase/DailyPlanSync.kt; register the tile service in the wear module's manifest.
Files (iOS): extend ios/SpartanWidgets with watchOS accessory families (accessoryCircular: band
ring; accessoryRectangular: band + next activity) reading the prompt-4.2 App Group store.
Acceptance:
- Shows band, recovery, next activity title; refreshes on each phone sync and at midnight; data
  older than 24h shows "Open Spartan to sync" instead of stale numbers.
- Wear tap opens the phone check-in via RemoteActivityHelper; watch tap opens the iOS app;
  renders on round + rectangular faces / all accessory families, including ambient/always-on
  (no large pure-white fills); graceful zero states when no plan exists.
Constraints: band + recovery + title ONLY — no raw physiological detail on the wrist; strings
reused from the existing catalogs with SafetyEngine tests per platform; Wear targets >= 48dp,
watchOS >= 44pt; no independent health computation on the watch — the phone is the single
source of truth.
```

### 4.4 App Shortcuts / Siri + Google Assistant App Actions — **M**

```text
In /Users/bohueilin/Documents/GitHub/Spartan, add voice/launcher shortcuts on both platforms.
Goal: "What's my plan today?" and "Mark my next activity done" work from Siri/Shortcuts and
Android long-press shortcuts / Assistant App Actions.
Files (Android): app/src/main/res/xml/shortcuts.xml (static shortcuts + capability bindings)
referenced from AndroidManifest.xml; new
app/src/main/java/com/spartan/shortcuts/ShortcutIntentHandler.kt routing into MainActivity.
Files (iOS): new ios/SpartanApp/Sources/SpartanAppIntents.swift (AppIntents OpenTodaysPlan and
CompleteNextActivity with confirmation) + AppShortcutsProvider phrases.
Acceptance:
- Open-plan cold-starts into check-in in <= 1 nav hop on both platforms.
- Complete-next resolves "next" exactly like the widget (first pending by priority, then time of
  day), asks confirmation, persists through the normal repository/view-model path, updates widgets.
- Responses are short and neutral with no metric values by default ("Marked Morning walk done.
  3 of 5 left."); no-plan case degrades to "Your plan isn't ready yet — open Spartan."
Constraints: every response phrase lives in the string catalogs with SafetyEngine tests on both
platforms; nothing beyond activity titles and counts reaches the assistant transcript; intents
are idempotent; VoiceOver/TalkBack users get identical confirmation flows.
```

---

## Suggested order

1.5 → 1.1 → 1.4 → 1.2 (one motion system first, then the hero interactions) → 1.3 → 2.2 → 2.1
(actions before rich layouts — utility beats decoration) → 2.3 → 2.4 → 3.1 → 3.4 (shared baseline
math) → 3.2 → 3.3 → 4.2 → 4.1 → 4.4 → 4.3.

Every prompt assumes: keep mock-first (`SPARTAN_USE_MOCK_WHOOP=true`) working with zero
credentials, never commit secrets, and add a `SafetyEngine.validateCopy` unit test whenever a
prompt introduces user-facing copy.
