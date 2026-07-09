# Spartan — Google Play release checklist

The engineer's step-by-step path from this repo to a live Play listing.

Scope: **Spartan 1.0.0** (`com.spartan`, `minSdk 26`, `targetSdk 35`). Companion documents:
[PLAY_STORE_LISTING.md](PLAY_STORE_LISTING.md) (store copy + data safety mapping),
[PRIVACY_POLICY.md](PRIVACY_POLICY.md) (must be hosted at a public URL),
[SECURITY_PRIVACY_CHECKLIST.md](SECURITY_PRIVACY_CHECKLIST.md) (per-release security gate).

Contact email used throughout: `support@spartan.app` — **placeholder; replace with a monitored
mailbox before submission.**

Conventions: run Gradle tasks **serially** (this Hilt/KSP project can race on generated files when
parallelized). All commands assume the repo root.

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="$HOME/android-sdk"
```

---

## 1. Pre-flight code checklist

- [ ] **Tests green.** `./gradlew --no-daemon :app:testDebugUnitTest`
- [ ] **Lint green.** `./gradlew --no-daemon :app:lintDebug` — resolve or explicitly baseline every
      release-blocking issue.
- [ ] **Release build compiles.** `./gradlew --no-daemon :app:assembleRelease` (after §2 signing is
      configured). If R8/minify is enabled, smoke-test the release APK on a device — Hilt, Room,
      AppAuth, and kotlinx.serialization all need correct keep rules.
- [ ] **No secrets tracked.** All of the following must come back clean:
  ```bash
  git ls-files | grep -E '\.env$|\.env\.local|local\.properties|\.keystore|\.jks' && echo LEAK || echo clean
  git grep -iE 'client_secret|api[_-]?key.*=.*[A-Za-z0-9]{16,}' -- ':!*.md' ':!.env.example'
  ```
  Only `.env.example` (placeholders) may be tracked.
- [ ] **Version bumped.** `app/build.gradle.kts` already reads `versionName = "1.0.0"` /
      `versionCode = 1` for this release; every subsequent upload needs a strictly higher
      `versionCode`.
- [ ] **Decide `USE_MOCK_*` for the shipped build.** Flags: `SPARTAN_USE_MOCK_WHOOP` /
      `SPARTAN_USE_MOCK_CALENDAR` → `BuildConfig.USE_MOCK_WHOOP` / `USE_MOCK_CALENDAR`
      (default `true` ⇒ mock/stub).
  - **1.0 may honestly ship in sample-data mode** (both flags `true`): the UI already labels mock
    data clearly, no network calls are made, and no credentials are needed. If you do this, the
    store listing and the app must both say "sample data" plainly — no implication that live WHOOP
    sync is active.
  - Shipping with real integrations (`false`) requires **production** OAuth apps for WHOOP and
    Google (§5), which have external review lead time. Do not flip the flags until §5 is complete.
- [ ] **Manifest posture unchanged.** `allowBackup="false"`, `dataExtractionRules` excluding the
      database/prefs/DataStore, cleartext traffic disabled. (Verified present; do not regress.)
- [ ] **Security gate.** Complete the "Pre-release gate" section of
      [SECURITY_PRIVACY_CHECKLIST.md](SECURITY_PRIVACY_CHECKLIST.md).
- [ ] **SafetyEngine copy pass.** Spot-check generated plan text on device: wellness framing only,
      no diagnosis, `CLINICIAN_REFERRAL` nudge renders as "talk to a clinician", never a diagnosis.

## 2. Signing

### 2.1 Generate the upload keystore (once)

Keep the keystore **outside the repo** (e.g. `~/keystores/`). `*.keystore`, `*.jks`, and
`local.properties` are gitignored, but the safest keystore is one that never enters the worktree.

```bash
mkdir -p ~/keystores
keytool -genkeypair -v \
  -keystore ~/keystores/spartan-upload.jks \
  -alias spartan-upload \
  -keyalg RSA -keysize 4096 \
  -validity 10000 \
  -storetype PKCS12
```

Record the store password and the distinguished-name answers in your password manager. Losing the
upload key is recoverable (Play lets you register a new one), but it costs support round-trips.

### 2.2 Gradle signing config (local only — NEVER commit keystore or passwords)

Put credentials in **`~/.gradle/gradle.properties`** (user-level, never in the repo):

```properties
SPARTAN_UPLOAD_STORE_FILE=/Users/<you>/keystores/spartan-upload.jks
SPARTAN_UPLOAD_STORE_PASSWORD=...
SPARTAN_UPLOAD_KEY_ALIAS=spartan-upload
SPARTAN_UPLOAD_KEY_PASSWORD=...
```

Then add to `android { ... }` in `app/build.gradle.kts` (the repo has no `signingConfigs` or
`buildTypes` block yet — add both; property lookup degrades gracefully so CI/dev machines without
the keystore still build debug):

```kotlin
signingConfigs {
    create("release") {
        val storeFilePath = (findProperty("SPARTAN_UPLOAD_STORE_FILE") as String?)
        if (storeFilePath != null) {
            storeFile = file(storeFilePath)
            storePassword = findProperty("SPARTAN_UPLOAD_STORE_PASSWORD") as String?
            keyAlias = findProperty("SPARTAN_UPLOAD_KEY_ALIAS") as String?
            keyPassword = findProperty("SPARTAN_UPLOAD_KEY_PASSWORD") as String?
        }
    }
}

buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        signingConfig = signingConfigs.getByName("release")
    }
}
```

- [ ] Keystore generated and stored outside the repo; passwords in a password manager.
- [ ] `~/.gradle/gradle.properties` populated; `git status` shows no keystore/credential files.
- [ ] If R8 breaks the release build, either add keep rules to `app/proguard-rules.pro` or ship 1.0
      with `isMinifyEnabled = false` (acceptable for a small app; revisit in 1.1).

### 2.3 Play App Signing

- [ ] Enroll in **Play App Signing** when creating the app in Play Console (default for new apps).
      Google holds the app signing key; your keystore above is only the **upload key**.
- [ ] After enrollment, note the **app signing key SHA-1** from Play Console → Setup → App
      integrity — you will need it in §5.3.

## 3. Build the release bundle (AAB)

```bash
JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" \
ANDROID_HOME="$HOME/android-sdk" \
./gradlew --no-daemon :app:bundleRelease
```

Artifacts:

| Artifact | Path |
|---|---|
| Release bundle (upload this) | `app/build/outputs/bundle/release/app-release.aab` |
| R8 mapping file (upload with each release if minify is on) | `app/build/outputs/mapping/release/mapping.txt` |
| Release APK (device smoke tests) | `app/build/outputs/apk/release/app-release.apk` via `:app:assembleRelease` |

- [ ] AAB builds and is signed with the upload key (`jarsigner -verify` or upload dry-run).
- [ ] Install the release APK on a physical device and run the §6.3 smoke list before uploading.
- [ ] Keep `mapping.txt` alongside every uploaded `versionCode` (needed to deobfuscate Play crash
      stacks — Spartan ships no crash SDK, so Play vitals is the only crash source, §7).

## 4. Play Console setup

- [ ] **Create the app.** Play Console → Create app → name "Spartan", default language en-US,
      app (not game), **free**. One-time developer registration fee applies if the account is new.
- [ ] **Store listing.** Copy every field verbatim from
      [PLAY_STORE_LISTING.md](PLAY_STORE_LISTING.md) — title, short description, full description,
      screenshots, feature graphic, icon. Do not improvise copy; the listing text has been written
      to the same safety constraints as the app (no medical claims, no hype).
- [ ] **Privacy policy URL.** Host [PRIVACY_POLICY.md](PRIVACY_POLICY.md) at a stable public URL
      (e.g. GitHub Pages or the product site) and paste that URL into App content → Privacy policy.
      A reachable privacy policy URL is mandatory for apps that declare health data.
- [ ] **App content declarations** (Play Console → App content):
  - **Health apps declaration:** declare Spartan as a **health & wellness** app (fitness/wellness
    category). It is not a medical device and makes no diagnostic claims; the listing and in-app
    copy must agree with this.
  - **Data safety form:** fill it from the **data safety mapping table in
    [PLAY_STORE_LISTING.md](PLAY_STORE_LISTING.md)**. Summary of the truthful position: all data is
    stored on device; nothing is collected by the developer or shared with third parties; no
    analytics/telemetry/ads SDKs; OAuth traffic (when real integrations are enabled) goes directly
    from the device to WHOOP/Google under the user's own consent; users can delete all data in-app.
  - **Ads:** declare **contains no ads**.
  - **Target audience:** 18 and over (health coaching context; not designed for or directed at
    children). Do not opt into any families program.
  - **Financial features / government app / news app:** none.
- [ ] **Content rating questionnaire (IARC):** complete honestly — no violence, no sexual content,
      no gambling, no user-generated content, no user-to-user interaction. Health/fitness reference
      content only. Expected outcome: Everyone / PEGI 3-equivalent.
- [ ] **App access:** if reviewers need anything beyond a clean install, say so. In sample-data
      mode the app runs fully with no credentials — state "no login required; app ships with
      labeled sample data" in the App access notes.

## 5. OAuth production readiness (required before flipping `USE_MOCK_*` to false)

Skip this section entirely if 1.0 ships in sample-data mode, but read the lead-time notes in §8.

### 5.1 WHOOP developer app

- [ ] Create a production app in the WHOOP developer dashboard.
- [ ] Redirect URI: **`com.spartan.oauth://whoop`** (must match `.env.example` and
      `BuildConfig.WHOOP_REDIRECT_URI` verbatim).
- [ ] Scopes (least privilege, read-only): `read:recovery read:sleep read:workout read:cycles
      read:profile offline`. Do **not** request `read:body_measurement` (omitted from MVP).
- [ ] Put `WHOOP_CLIENT_ID` / `WHOOP_CLIENT_SECRET` in `local.properties` on the build machine
      only; confirm neither appears in `git status` or the AAB's string resources.
- [ ] Confirm any WHOOP developer-program review/approval requirements for production apps and
      their turnaround time.

### 5.2 Google Cloud OAuth consent screen

- [ ] Create a Google Cloud project; configure the OAuth consent screen as **External /
      In production**.
- [ ] Scopes: **`https://www.googleapis.com/auth/calendar.freebusy`** for reads. Add
      **`calendar.events`** only if the opt-in "create calendar event" write ships in this build —
      otherwise leave it off (each scope widens the review).
- [ ] **Sensitive-scope verification:** `calendar.freebusy` and `calendar.events` are sensitive
      scopes. Google requires app verification — homepage, hosted privacy policy URL (§4), an
      explanation of scope usage, and often a demo video. **Budget weeks, not days** (§8). Until
      verification completes, consent shows an "unverified app" warning and is capped at 100 test
      users.
- [ ] Redirect URI: **`com.spartan.oauth://google`** (custom-scheme, via AppAuth).

### 5.3 Android client registration (package name + SHA-1)

- [ ] Create an **Android** OAuth client ID in Google Cloud → Credentials with:
  - Package name: `com.spartan`
  - SHA-1: the **app signing key** SHA-1 from Play Console (§2.3) — this is what Play-delivered
    builds are signed with.
- [ ] Add a second Android client (or additional fingerprint) for the **upload key** SHA-1 so
      internal-track and locally installed release builds can also complete OAuth.
- [ ] Set `GOOGLE_OAUTH_CLIENT_ID` in `local.properties`; rebuild; verify the round trip
      (consent → redirect → token in `EncryptedTokenStore`, never logged).

## 6. Testing tracks and rollout

### 6.1 Track progression

- [ ] **Internal testing** (up to 100 testers, minutes to propagate): upload the AAB, run the §6.3
      smoke list on Play-delivered builds (this catches Play App Signing + OAuth SHA-1 mismatches
      that sideloaded builds hide).
- [ ] **Closed beta:** a small external cohort; watch pre-launch report, ANRs, and battery. Hold
      here at least one week of real daily use (the product is a daily loop — one day of testing
      does not exercise snooze/missed/reschedule paths).
- [ ] **Production, staged rollout:** release at **10%**, monitor vitals for 2–3 days, then
      **50%**, then **100%**. Never jump straight to 100% on a first release.

### 6.2 Play pre-launch report

- [ ] Review the pre-launch report on every upload: crawler crashes, rendering issues across
      device classes, accessibility warnings, and the security scan. Treat accessibility findings
      as fix-before-launch, not advisories.

### 6.3 Physical-device smoke test (run on the release build, every upload)

1. [ ] Fresh install → onboarding → Today screen renders a labeled **sample** daily plan with no
       credentials and no network.
2. [ ] Check off an activity → progress updates ("1 of N · x/y min") → force-stop the app →
       relaunch → state persisted.
3. [ ] **Notification tap:** wait for (or trigger) the daily check-in notification; tapping it
       opens the Today screen directly.
4. [ ] **Snooze wake:** snooze an activity → the follow-up reminder fires at the snoozed time and
       respects quiet hours.
5. [ ] Skip and reschedule an activity; verify the schedule flow proposes a free/busy gap and the
       card shows its scheduled time.
6. [ ] Connect/disconnect flow: consent screen shows plain-language scopes; disconnect returns the
       integration to not-connected and (Phase 2) clears tokens.
7. [ ] **Delete all data:** run the in-app delete → every table and preference cleared → app
       returns to first-run state; no residue after relaunch.
8. [ ] **Dark/light:** toggle system theme; every screen readable in both, no unreadable
       contrast, no clipped layouts.
9. [ ] **TalkBack pass:** navigate onboarding → Today → check off an activity entirely by
       TalkBack; every actionable element has a sensible label and 48dp touch target.
10. [ ] Airplane mode end-to-end: the full daily loop works offline (local-first claim must be
        literally true); no error toasts caused by absent network.

## 7. Post-launch

- [ ] **Play vitals is the only telemetry — by design.** Spartan ships no in-app
      analytics/telemetry/crash SDKs, so Android vitals (crash rate, ANR rate, wake locks,
      excessive background work) in Play Console is the monitoring surface. Check daily for the
      first two weeks, then weekly. Alert thresholds: crash rate > 1.09% or ANR > 0.47% (Google's
      bad-behavior bars) — these also suppress store visibility.
- [ ] **Crash triage without PII:** deobfuscate stacks with the archived `mapping.txt` (§3). Play
      crash reports contain no Spartan health data; keep it that way — never add logging of metric
      values, plan contents, or tokens in a hotfix.
- [ ] **Update cadence:** patch releases as vitals demand; otherwise a small monthly release keeps
      the pre-launch report, targetSdk requirements, and dependency updates from batching up.
      Every release repeats §1, §3, and §6.3, and bumps `versionCode`.
- [ ] **Rollback plan:**
  - During a staged rollout: **halt the rollout** in Play Console (stops new users getting the
    bad build; it does not uninstall from users who already updated).
  - After 100%: create a new release from the **previous known-good AAB** with a **higher
    `versionCode`** (Play cannot ship a lower versionCode). Keep every shipped AAB + mapping file
    archived for this reason.

## 8. Known 1.0 launch decisions and risks

| Decision / risk | Position for 1.0 | Impact / mitigation |
|---|---|---|
| Sample-data default (`USE_MOCK_* = true`) | Ship 1.0 in clearly-labeled sample-data mode | Honest and reviewable with zero credentials, but the listing must say "sample data" plainly or reviews will call it out. Real integrations arrive in an update once §5 completes. |
| Google sensitive-scope verification lead time | Not started until a public privacy policy URL and homepage exist | **Weeks** of lead time; unverified apps are capped at 100 users with a warning screen. Start verification the day the privacy policy is hosted, even if 1.0 ships mock. |
| WHOOP production app approval | Developer app exists only in dev/mock form | WHOOP's production review timeline is external and uncontrolled; confirm requirements early so `USE_MOCK_WHOOP=false` is not blocked on paperwork. |
| Health-app review lead time on Play | Health apps declaration adds manual review | First submission of a health-declared app can take noticeably longer than a standard app review; submit to internal/closed tracks early so the listing review runs in parallel with beta. |
| No release `buildTypes`/`signingConfigs` in repo yet | Added locally per §2 | The repo intentionally carries no signing material; each release machine needs `~/.gradle/gradle.properties` set up. Document the holder of the upload keystore. |
| R8/minify untested | Enable per §2.2, fall back to unminified if keep rules bite | Unminified 1.0 is acceptable; revisit in 1.1 with device coverage. |
| `versionName` currently `0.1.0` | Bump to `1.0.0` in §1 | Upload will be rejected or mislabeled otherwise. |
| Support contact | `support@spartan.app` placeholder | Replace with a monitored mailbox before submission; Play requires a working support email. |
| No crash SDK | Deliberate (privacy posture) | Slower crash signal than Crashlytics-style tooling; mitigated by staged rollout + daily vitals checks (§7). |
