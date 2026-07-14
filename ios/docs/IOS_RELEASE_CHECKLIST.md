# Spartan — iOS App Store release checklist

The engineer's step-by-step path from this repo to a live App Store listing.

Scope: **Spartan iOS 1.0.0** (bundle id `com.spartan`, build 1 — keep the bundle id identical to
whatever `ios/SpartanApp/project.yml` declares; they must match App Store Connect exactly).
Companion documents: [APP_STORE_LISTING.md](APP_STORE_LISTING.md) (store copy, counts verified),
[APP_STORE_PRIVACY_LABELS.md](APP_STORE_PRIVACY_LABELS.md) (App Privacy answers + privacy
manifest), [docs/PRIVACY_POLICY.md](../../docs/PRIVACY_POLICY.md) (must be iOS-adapted and hosted
at a public URL), and the Android counterpart
[docs/RELEASE_CHECKLIST.md](../../docs/RELEASE_CHECKLIST.md) (shared decisions: sample-data
default, OAuth production lead times, no-crash-SDK posture).

Contact email used throughout: `support@spartan.app` — **placeholder; replace with a monitored
mailbox before submission.**

---

## 0. Honest "launch tomorrow" feasibility

**Submit tomorrow: achievable. Live on the store tomorrow: not in your control.**

- **Submittable in a day** if all of the following are already true: an Apple Developer Program
  membership is **active** (enrollment itself can take 24–48 hours or more — see §1; if it has
  not started, tomorrow is off), a Mac with full Xcode can archive (`SpartanKit` builds and tests
  on Command Line Tools alone, but archiving `SpartanApp` needs Xcode + the iOS SDK), the privacy
  policy is hosted at a real URL, the support URL and email are real, and the screenshots exist.
- **Live on the store** depends on App Review: typically 24–48 hours for most submissions, and
  health-category first submissions sometimes take longer (§9). Any rejection adds a full round
  trip. Plan "submitted tomorrow, live within the week" and treat anything faster as a gift.
- The sample-data build helps here: reviewers can exercise the entire app with no credentials,
  no demo account, and no network — one less rejection class.

## 1. Apple Developer Program enrollment

- [ ] Enroll at developer.apple.com/programs (USD 99/year). **Individual** enrollment usually
      activates in 24–48 hours. **Organization** enrollment requires a D-U-N-S number and legal
      entity verification — budget days to weeks. Start this before anything else; nothing below
      works without it.
- [ ] Decide who owns the account (it owns the app's identity forever) and enable two-factor
      authentication on the Apple ID.
- [ ] Accept the latest Apple Developer Program License Agreement in App Store Connect (uploads
      silently fail on an unaccepted agreement).

## 2. Bundle id, signing, capabilities

- [ ] **Register the bundle id**: developer.apple.com → Certificates, Identifiers & Profiles →
      Identifiers → new App ID, explicit (not wildcard), `com.spartan`, platform iOS.
- [ ] **Capabilities: none beyond defaults.** Spartan 1.0 uses local notifications only (no push
      entitlement needed), no HealthKit, no App Groups, no iCloud. Do not add HealthKit "for
      later" — an unused HealthKit entitlement invites review questions the app cannot answer.
- [ ] **Signing: let Xcode do it.** In the generated project, Signing & Capabilities → check
      "Automatically manage signing", select the team. Xcode creates and rotates the development
      and distribution certificates and provisioning profiles. Do not hand-manage certificates
      for a one-app, one-team setup.
- [ ] Confirm `ITSAppUsesNonExemptEncryption` is set to `NO` in the Info.plist (the app uses only
      standard TLS, which is exempt) — this skips the export-compliance question on every upload.

## 3. Pre-flight code checklist

- [ ] **SpartanKit green.** From `ios/SpartanKit`: `swift build && swift test` — includes the
      ported coaching eval harness (generated plans checked against safety/correctness
      invariants). This is the gate on everything the app tells a user to do.
- [ ] **Generate and build the app.** Requires a Mac with full Xcode (not just Command Line
      Tools):
  ```bash
  brew install xcodegen
  cd ios/SpartanApp
  xcodegen generate
  open SpartanApp.xcodeproj
  ```
  Build for a simulator, then for "Any iOS Device (arm64)".
- [ ] **Data sources confirmed.** 1.0 ships on the labeled mock WHOOP source and stub
      calendar — the same honest decision as Android 1.0 — plus the on-device WHOOP CSV import
      (Connections → "Import WHOOP export (.csv)"), which replaces the sample series with the
      user's own exported data, parsed and stored entirely on-device. The UI must label sample
      data plainly (and drop the label once real data is imported), and the app must make
      **zero network requests** (verify with Xcode's network instrument or a proxy, and include
      one CSV import in that airplane-mode/zero-network verification). Do not enable live OAuth
      integrations until §6 completes.
- [ ] **No secrets tracked.** Same rule as the repo root: only `.env.example` placeholders may be
      committed. `git ls-files | grep -iE '\.env$|\.env\.local|\.p8|\.p12|\.mobileprovision'`
      must come back empty.
- [ ] **Version/build set.** Marketing version `1.0.0`, build `1`. Every subsequent upload to the
      same version needs a strictly higher build number.
- [ ] **Privacy manifest present** (`PrivacyInfo.xcprivacy`) and consistent with
      [APP_STORE_PRIVACY_LABELS.md](APP_STORE_PRIVACY_LABELS.md) §4 — missing required-reason API
      declarations are an automatic upload rejection.
- [ ] **SafetyEngine copy pass.** Spot-check generated plan text on a device or simulator:
      wellness framing only, no diagnosis, the clinician-referral nudge renders as "talk to a
      clinician", never a diagnosis. Same rule ids, thresholds, and copy as Android — if the two
      platforms disagree, stop and fix before shipping either.
- [ ] **Manual device smoke list** (mirror of the Android §6.3 list, adapted): fresh install →
      onboarding → Today screen renders a labeled sample plan with no network; check off an
      activity → force-quit → relaunch → state persisted; notification tap opens Today; snooze
      fires at the snoozed time and respects quiet hours; skip/reschedule proposes a free/busy
      gap; delete-all-data returns to first-run state; dark/light both readable; VoiceOver can
      complete onboarding → Today → check-off with sensible labels and 44pt targets; airplane
      mode end-to-end (the local-first claim must be literally true).

## 4. Archive and upload

- [ ] In Xcode: select the "Any iOS Device (arm64)" destination → Product → **Archive**.
- [ ] Xcode **Organizer** opens with the archive → **Validate App** first (catches signing,
      icon, and privacy-manifest problems in minutes instead of after review) → then
      **Distribute App** → App Store Connect → Upload. Automatic signing handles the
      distribution certificate and profile.
- [ ] Wait for processing in App Store Connect (minutes to ~an hour). The build then appears
      under TestFlight and becomes selectable for the release.
- [ ] CLI alternative for repeatability (optional):
  ```bash
  xcodebuild -project SpartanApp.xcodeproj -scheme SpartanApp \
    -destination 'generic/platform=iOS' archive -archivePath build/SpartanApp.xcarchive
  xcodebuild -exportArchive -archivePath build/SpartanApp.xcarchive \
    -exportOptionsPlist ExportOptions.plist -exportPath build/export
  ```

## 5. App Store Connect setup

- [ ] **Create the app record.** App Store Connect → My Apps → "+" → New App: platform iOS,
      name "Spartan: daily recovery coach" (29/30 — reserved at creation; if taken, fall back per
      APP_STORE_LISTING.md §1), primary language en-US, bundle id `com.spartan`, SKU
      `spartan-ios-001`, price **free**.
- [ ] **Store listing.** Copy every field verbatim from
      [APP_STORE_LISTING.md](APP_STORE_LISTING.md) — name, subtitle, promotional text,
      description, keywords, screenshots (6.7" + 6.1" sets), What's New. Do not improvise copy;
      it is written to the same safety constraints as the app.
- [ ] **App Privacy.** Answer per [APP_STORE_PRIVACY_LABELS.md](APP_STORE_PRIVACY_LABELS.md):
      Data Not Collected; tracking No; privacy policy URL live and iOS-accurate.
- [ ] **Age rating** per APP_STORE_LISTING.md §7 (expected 4+).
- [ ] **Category** Health & Fitness; support URL and marketing URL fields filled (support URL is
      required).
- [ ] **App Review information**: contact name/phone/email; **no demo account needed** — state it
      (§8 notes); attachments none.

## 6. OAuth production readiness (before any live-integration release — not 1.0)

Skip for the 1.0 sample-data release, but start the clocks now; both lead times are external.

- [ ] **WHOOP developer app (production).** Redirect URI `spartan://oauth/whoop` (must match the
      iOS URL scheme registered in Info.plist and the Android build verbatim). Read-only scopes:
      `read:recovery read:sleep read:workout read:cycles read:profile offline`. Confirm WHOOP's
      production review requirements and turnaround early.
- [ ] **Google Cloud OAuth.** iOS OAuth client type (registered by **bundle id** — iOS has no
      SHA-1 fingerprint step, unlike Android). Scope `calendar.freebusy` (add `calendar.events`
      only if opt-in event creation ships). Sensitive-scope verification requires a homepage,
      hosted privacy policy, scope justification, often a demo video — **budget weeks, not
      days**; until verified, consent is capped at 100 test users with a warning screen.
- [ ] Redirect URI `spartan://oauth/google` via AppAuth-iOS (ASWebAuthenticationSession under the
      hood); tokens land in the Keychain, never in logs or files.
- [ ] Re-verify the App Privacy answers per APP_STORE_PRIVACY_LABELS.md §3 before shipping the
      live build.

## 7. TestFlight

- [ ] **Internal testing** (up to 100 members of your App Store Connect team): add testers to an
      internal group, enable the build — available on their devices within minutes of processing,
      **no Beta App Review needed**. Run the §3 smoke list on TestFlight-delivered builds (they
      are App Store-signed, closest to what users get).
- [ ] **External testing** (optional for 1.0, up to 10,000 testers): the first build in an
      external group goes through **Beta App Review** (usually about a day — health apps can get
      the same extra scrutiny as §9). Because the product is a daily loop, hold at least a few
      days of real daily use here or internally: one session does not exercise snooze, missed,
      and reschedule paths.
- [ ] TestFlight builds expire after 90 days — irrelevant for a fast launch, relevant if the
      submission slips.

## 8. Submission and App Review notes

- [ ] Select the processed build on the version page, complete every red-badge section, and
      **Submit for Review**.
- [ ] **Paste into the App Review notes** (Guideline 5.1.3 is the health-data guideline —
      wellness framing, no diagnosis, health data never used for advertising or data mining;
      answer it before it is asked):

  ```
  Review notes — Spartan 1.0.0

  • No login, no demo account needed. The app ships in a clearly labeled SAMPLE DATA mode:
    a built-in mock data source provides realistic recovery/sleep/HRV values so every screen
    and flow (daily plan, check-off, snooze, reschedule, privacy controls) can be exercised
    with zero credentials and zero network access.
  • Optionally, the user can import their own WHOOP data using WHOOP's official CSV Data
    Export (Connections → "Import WHOOP export (.csv)"). The import is parsed entirely
    on-device, stored with iOS complete file protection, excluded from iCloud backup,
    deletable in-app, and never transmitted anywhere — the app still makes zero network
    requests, so App Privacy remains "Data Not Collected" (nothing leaves the device).
    Live WHOOP and Google Calendar OAuth connections remain disabled in this build; the
    connections screen demonstrates the consent UX.
  • Health posture (Guideline 5.1.3): Spartan is a consumer wellness app. It provides
    fitness/wellness guidance only — no medical advice, no diagnosis, no treatment claims;
    a rules-based safety filter over all generated coaching copy enforces this. Concerning
    readings produce only a suggestion to consult a qualified clinician. The app does not use
    HealthKit. Health data is stored on-device only and is never used for advertising, data
    mining, or disclosure to third parties; the developer operates no servers and collects
    no data (App Privacy: Data Not Collected).
  • WHOOP is referenced descriptively for compatibility; the description carries an explicit
    non-affiliation statement.
  ```

- [ ] Guidelines worth pre-reading, in likely order of reviewer interest: **5.1.3** (health data),
      **5.1.1/5.1.2** (privacy, permissions — only notification permission is requested, in
      context), **2.3.7** (metadata/keywords — WHOOP fallback ready per APP_STORE_LISTING.md §5),
      **4.2** (minimum functionality — the sample-data app is a complete, working daily planner,
      not a demo shell; the listing says sample data plainly, so expectations match).

## 9. Review timelines and release options

- [ ] **Typical review: 24–48 hours** (Apple states most submissions are reviewed within a day;
      plan on two). **Health & Fitness apps and first submissions sometimes take longer** — the
      category gets manual attention, and a privacy-label-vs-policy mismatch is the classic slow
      path. A rejection restarts the clock; respond in the Resolution Center same-day.
- [ ] **Release option:** set **"Manually release this version"** at submission — approval then
      parks the app until you press Release, which decouples "Apple approved it" from "users see
      it" and lets launch happen on your clock.
- [ ] **Phased release** (7-day gradual rollout) only affects **automatic updates to existing
      users** — for a 1.0 with zero installed base it does nothing. Enable it anyway so it is
      already on for 1.0.1; unlike Play's staged rollout percentages, new users always get the
      new version immediately.
- [ ] If a critical issue demands speed later, an **expedited review** can be requested (sparingly)
      via App Store Connect support.

## 10. Post-launch

- [ ] **No crash SDK — by design**, same privacy posture as Android. The monitoring surface is
      App Store Connect → Analytics/Metrics plus Xcode Organizer crash reports (opt-in users
      only, so the signal is thinner than Play vitals — check daily for the first two weeks).
      Never add logging of metric values, plan contents, or tokens in a hotfix.
- [ ] **No rollback exists on the App Store.** You cannot revert to a previous build; the only fix
      is a new version with a higher build number (expedited review if severe). Keep every
      shipped archive (`.xcarchive`) so any prior build can be re-issued as a new version fast.
- [ ] **Update cadence:** patch as metrics demand; otherwise a small monthly release keeps
      dependencies, Xcode/SDK requirements, and the review relationship warm. Every release
      repeats §3, §4, and the smoke list, and bumps the build number.
- [ ] Keep iOS and Android in lockstep on rule ids, thresholds, and coaching copy — a divergence
      between platforms is a correctness bug, not a style difference.
