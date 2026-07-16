# Spartan — App Privacy ("nutrition label") answers for App Store Connect

How to fill in App Store Connect → App Privacy for Spartan iOS 1.0.0, mapped from what the app
actually does. Grounded in [docs/PRIVACY_POLICY.md](../../docs/PRIVACY_POLICY.md) (the policy the
label must agree with) and the app's real data flows: local-first, no Spartan backend, no
analytics/ads/crash SDKs, sample data by default.

**The one-line answer for the 1.0.0 sample-data build: "Data Not Collected", tracking: No.**
The rest of this document is the reasoning, so the answer survives review and future audits.

---

## 1. Apple's definition of "collect" (and how it differs from Play's)

Apple asks what data **you (the developer) and your third-party partners** (SDKs you embed,
analytics providers, ad networks) collect. Apple defines *collect* as transmitting data off the
device in a way that allows you and/or your third-party partners to access it for longer than
needed to service the request in real time. Data processed **only on the device** is not
collected. Data the developer never receives is not collected by the developer.

This is close to, but not identical to, Google Play's Data safety definition (Play asks about
data transmitted off-device generally; Apple asks specifically about access by the developer and
the developer's partners). Spartan's honest answer is the same under both: **the developer
collects nothing.** There is no Spartan server, no account system, no analytics, no telemetry,
no crash-reporting SDK, no advertising SDK. Nothing the app handles is ever transmitted to the
developer.

---

## 2. The 1.0.0 sample-data build: Data Not Collected

The shipped 1.0.0 build runs on a clearly labeled mock WHOOP source and a stub calendar, and can
import the user's own WHOOP CSV export, parsed and stored on-device only. It makes
**no network requests at all**. Everything (plan history, check-ins, imported WHOOP data,
optional name/height, preferences) lives in local storage on the device.

App Store Connect answers:

- "Do you or your third-party partners collect data from this app?" → **No** ("Data Not
  Collected" label).
- Tracking (data used to track users across apps/websites owned by other companies) → **No.**
  No AppTrackingTransparency prompt is needed or included; the app never touches the IDFA.

Per-category confirmation (every Apple category, all "not collected"):

| Apple data category | Does the app handle it? | Collected per Apple's definition? |
|---|---|---|
| Health & Fitness | Yes — sample or user's own WHOOP metrics via on-device CSV import (1.0.0); OAuth-fetched metrics (later); stored on device | **No** — never leaves the device toward the developer or any partner |
| Contact Info — Name | Optional, user-entered, on device | **No** |
| Contact Info — Email / Phone / Address | Not handled; no accounts | **No** |
| User Content (calendar-derived free/busy, check-ins) | On device only | **No** |
| Identifiers (user ID, device ID) | None exist — no accounts, no device ID reads | **No** |
| Usage Data (product interaction, advertising data) | Not handled — no analytics SDK | **No** |
| Diagnostics (crash data, performance) | Not handled — no crash/diagnostics SDK; Apple's own opt-in crash reporting goes to Apple, not the developer's SDKs, and does not require a label entry | **No** |
| Location, Financial Info, Sensitive Info, Contacts, Messages, Photos/Videos/Audio, Browsing History, Search History, Purchases, Other Data | Not handled at all | **No** |

---

## 3. What changes when real WHOOP / Google OAuth is enabled

When the live integrations ship (a later release), data starts moving — but only **between the
user's device and the provider the user signs into** (WHOOP's API, Google's Calendar API), over
TLS, under the user's own OAuth consent, using the user's own credentials:

- Health metrics flow **from** WHOOP **to** the device (read-only scopes).
- Free/busy intervals flow **from** Google **to** the device.
- OAuth tokens flow to WHOOP/Google for authentication, and are stored on device in the Keychain.
- If the user opts in to event creation, an activity title and time are written to **the user's
  own** Google calendar, per event, with per-event confirmation.

Nothing in that list is transmitted to the developer. There is still no Spartan server to receive
anything. WHOOP and Google here are **the user's own service providers**, reached with the user's
own account — not SDK partners collecting data on the developer's behalf. The data originates
from those services; Spartan reads it down to the device.

**Honest evaluation:** "Data Not Collected" remains the accurate answer under Apple's definition,
because no data is transmitted off the device *to the developer or to partners acting for the
developer* — the only off-device flows are user-initiated exchanges with the user's own accounts.
Before flipping the live build on, re-do this evaluation against Apple's then-current App Privacy
guidance (definitions have been refined over time), and:

- [ ] Re-read Apple's "collect" definition and optional-disclosure criteria at submission time.
- [ ] Confirm the OAuth libraries added for live sign-in (e.g. AppAuth-iOS) embed no collection
      of their own and are not on Apple's privacy-impacting SDK list requiring a signed privacy
      manifest.
- [ ] State the data-flow reasoning in the App Review notes (reviewers ask; a prepared, truthful
      paragraph beats an improvised one — see IOS_RELEASE_CHECKLIST.md §8).
- [ ] If the team prefers maximum caution, the conservative alternative is to declare
      Health & Fitness data as collected (linked to identity: No — there is no account; tracking:
      No). Do not do this casually: over-declaring is also inaccurate, scares users, and is hard
      to walk back. Prefer the truthful "Data Not Collected" while the flow stays strictly
      device↔provider and developer-blind.

Either way, **tracking remains No** — nothing is used to track users across other companies'
apps or websites, ever. That posture is a product commitment, not a build flag.

---

## 4. Privacy manifest (PrivacyInfo.xcprivacy) — engineering companion to the label

Apple requires a privacy manifest bundled in the app. It must agree with the label above:

- `NSPrivacyTracking` → `false`; `NSPrivacyTrackingDomains` → empty.
- `NSPrivacyCollectedDataTypes` → empty array (matches "Data Not Collected").
- `NSPrivacyAccessedAPITypes` → **empty array**: no required-reason APIs are used today
  (settings live in the JSON `SettingsStore`, not UserDefaults; the JSON stores read/write
  whole files and call no file-timestamp APIs). This matches the shipped
  `PrivacyInfo.xcprivacy`. If `@AppStorage`/UserDefaults is ever introduced, add
  `NSPrivacyAccessedAPICategoryUserDefaults` with reason `CA92.1`; if file-timestamp checks
  are added, add `NSPrivacyAccessedAPICategoryFileTimestamp` with reason `C617.1`. Audit the
  final source before every submission; an undeclared required-reason API is an upload
  rejection (ITMS-91053).

---

## 5. Privacy policy URL (required)

App Store Connect requires a privacy policy URL for every app; for a Health & Fitness app it is
also what App Review checks the label against. Before submission:

- [ ] Adapt [docs/PRIVACY_POLICY.md](../../docs/PRIVACY_POLICY.md) for iOS wording: "Android app"
      → "iOS app"; Android Keystore/Room/DataStore sentences → iOS Keychain and local app-container
      storage; "excluded from Android cloud backup" → the iOS equivalent actually implemented
      (Keychain items stored this-device-only; app data excluded from iCloud backup if that is
      what ships). **Do not host claims the iOS build does not literally satisfy.**
- [ ] Keep section 10's spirit but retitle for Apple (the Play data-safety mapping table becomes
      the App Privacy mapping — sections 1–3 of this file).
- [ ] Host at a stable public URL; paste into App Store Connect → App Privacy → Privacy Policy.
- [ ] Replace the `support@spartan.app` placeholder with a monitored address in the hosted copy.

---

## 6. Summary card (paste-ready for the reviewer conversation)

> Spartan is local-first. The developer operates no servers and collects no data — no analytics,
> ads, crash reporting, or telemetry SDKs are present. The 1.0.0 build runs on clearly labeled
> sample data or the user's own WHOOP CSV export, imported and stored on-device (complete file
> protection, excluded from iCloud backup, deletable in-app), and makes no network requests.
> When users later connect WHOOP or Google
> Calendar, data moves only between the user's device and the user's own accounts at those
> services, over TLS, with read-only health scopes and free/busy-only calendar reads; none of it
> is transmitted to the developer. App Privacy: Data Not Collected. Tracking: No.
