# Spartan — Google Play Store listing package

Launch collateral for `com.spartan` 1.0.0 (minSdk 26, targetSdk 35). All copy follows the product
voice: disciplined, calm, premium, honest. Sentence case, no hype words, no exclamation marks, no
invented claims. Contact email `support@spartan.app` is a **placeholder — replace before submission**.

---

## 1. App title

> Max 30 characters.

**Exact string:**

```
Spartan: daily recovery coach
```

**Character count: 29 / 30.**

Notes:
- Avoids "WHOOP" in the title. Play metadata policy discourages third-party trademarks in titles;
  WHOOP is referenced descriptively in the descriptions instead.
- Alternate (shorter, if a plain title is preferred): `Spartan` (7 / 30).

---

## 2. Short description

> Max 80 characters.

**Exact string:**

```
Turns your WHOOP recovery into a simple, scheduled daily plan. On-device data.
```

**Character count: 78 / 80.**

---

## 3. Full description

> Max 4,000 characters. The copy below is ~2,300 characters. Paste verbatim.

```
Spartan reads your WHOOP recovery and turns it into a short daily plan you can actually complete.

Every morning you get 2–4 concrete activities — mobility, zone 2 cardio, breathwork, sleep hygiene, hydration — chosen to match how recovered you are. Each card explains why it matters, what to do step by step, how long it takes, and how important it is today. Check activities off in one tap, or snooze, skip, and reschedule without guilt.

If you connect Google Calendar, Spartan looks only at your free/busy times — never event titles or contents — and suggests open gaps that fit each activity. Local reminders nudge at the right moment and respect your quiet hours.

What Spartan reads from WHOOP (read-only, with your consent): recovery, sleep, strain, HRV, resting heart rate, and respiratory rate — through WHOOP's official sign-in.

A note on this version: Spartan 1.0 runs on clearly labeled sample data, so you can explore the full experience with nothing connected and no account. Live WHOOP and Google Calendar sign-in is coming in an update and will always require your explicit consent through their official sign-in flows.

Features
• Recovery-adjusted daily plan (2–4 activities), rebuilt each morning
• Plain-language "why it matters" and step-by-step instructions on every card
• One-tap done, snooze, skip, or reschedule — your progress is saved
• Calendar-aware scheduling using free/busy gaps only
• Local reminders with quiet hours — nudges, never nagging
• Trends for recovery, sleep, HRV, and resting heart rate over time

Private by design. Your data stays on your device: no cloud backend, no account, no analytics, no ads, no trackers. Sign-in tokens are protected with Android Keystore encryption and excluded from backups and device transfers. You can disconnect any integration or delete all app data at any time, from inside the app.

Spartan provides wellness and fitness guidance only. It is not a medical device, does not provide medical advice, and does not diagnose, treat, or prevent any condition. If a reading looks concerning, Spartan suggests speaking with a qualified clinician. Always consult a clinician for health concerns.

A WHOOP device and account are required for live data. WHOOP is a trademark of its owner; Spartan is an independent app and is not affiliated with or endorsed by WHOOP.

Questions: support@spartan.app
```

Copy rules honored: leads with the daily-plan value prop, sample-data honesty note included,
not-medical-advice disclaimer included, privacy paragraph included, no user counts, no
"doctor recommended", no compliance-certification claims, no exclamation marks.

---

## 4. Category and tags

- **Application type:** App
- **Category:** Health & Fitness
- **Tags** (Play Console lets you pick up to 5 from its fixed list — choose the closest matches):
  1. Fitness
  2. Health
  3. Sleep
  4. Workout & exercise
  5. Wellness (if unavailable in the picker, substitute "Training")

Do not tag "Medical" — Spartan is deliberately positioned as consumer wellness, not medical.

---

## 5. Content rating questionnaire (IARC) — expected answers

Complete in Play Console → App content → Content ratings. Expected answers for Spartan 1.0.0:

| Question area | Expected answer |
|---|---|
| App category in questionnaire | Utility / productivity / other (health & fitness app) |
| Violence | None |
| Sexual content / nudity | None |
| Profanity or crude humor | None |
| Controlled substances (drugs, alcohol, tobacco) | None (no references; hydration/sleep guidance only) |
| Gambling (simulated or real) | None |
| Fear / horror themes | None |
| Users can interact or exchange content | No (no chat, no social features, no user-generated content sharing) |
| Shares user's physical location with others | No |
| Allows purchase of digital goods | No (nothing in 1.0.0) |
| Contains ads | No |
| Miscellaneous: health/medical information | It presents fitness/wellness information; it does not provide medical advice or diagnosis |

**Expected result:** Everyone (ESRB), PEGI 3, USK 0, and equivalents. There is no content basis for
a higher rating; if the certificate comes back higher, re-check the interaction and health answers.

---

## 6. Health apps / Health Connect policy notes

Google Play requires a **Health apps declaration** (Play Console → App content → Health apps) for
apps in Health & Fitness.

- **Declared health feature category:** Health and fitness → wellness / fitness coaching
  (activity planning driven by wearable recovery metrics).
- **Health Connect:** Spartan 1.0 does **not** use Health Connect — no Health Connect permissions
  in the manifest, no `androidx.health.connect` dependency. Declare "does not integrate with
  Health Connect". If Health Connect is added later, the declaration, data safety form, and
  privacy policy must be updated before release.
- **Medical positioning:** Spartan is wellness-only. No diagnosis, treatment, or prevention claims
  anywhere in the app or listing (enforced in code by a safety filter over all generated coaching
  copy).

**Positioning statement to paste into the declaration free-text field:**

```
Spartan is a consumer wellness and fitness app. With the user's explicit consent, it reads
recovery, sleep, strain, HRV, resting heart rate, and respiratory rate from the user's own WHOOP
account via WHOOP's official read-only OAuth API, and generates a rules-based daily activity plan
(mobility, zone 2 cardio, breathwork, sleep hygiene, hydration). Spartan is not a medical device
and provides no medical advice, diagnosis, or treatment; concerning readings produce only a
suggestion to consult a qualified clinician. The default build uses clearly labeled sample data;
live integrations are opt-in. Spartan does not use Health Connect. All data is stored on the
user's device: there is no cloud backend, no account system, no analytics or advertising SDKs,
and no data sharing with third parties. Users can disconnect integrations and delete all app data
in-app at any time.
```

**Data safety form (related, same App content section):** declare **no data collected and no data
shared** — under Play's definitions, data processed on-device and never transmitted off-device by
the developer is not "collected". Calendar access is free/busy only; document that in the privacy
policy the form links to. Re-verify this answer before enabling any Phase-2 live integration.

---

## 7. Screenshot plan (6 shots) and feature graphic

Phone screenshots, 1080 × 2400 portrait, dark theme, sample-data state (the honest default build).
Keep the "Sample data" label visible where it appears — it is a trust asset, not a defect.

| # | Screen | Caption (overlay, one line, sentence case) |
|---|---|---|
| 1 | Check-in hero — Today screen with 2–4 activity cards, per-day total time, progress indicator | A short daily plan, matched to your recovery |
| 2 | Readiness ring — recovery band with sleep, HRV, and resting heart rate summary | Your recovery, read at a glance |
| 3 | Expanded activity card — why it matters, steps, duration, priority, snooze/skip/reschedule | Every activity explains why it matters |
| 4 | Connections/consent screen — WHOOP and Google Calendar with plain-language scope explanations | You decide exactly what Spartan can read |
| 5 | Privacy screen — on-device storage, disconnect, delete all data | Your data stays on your device |
| 6 | Onboarding — sample-data welcome state with "Connect WHOOP" call to action | Explore with sample data before connecting anything |

**Feature graphic (1024 × 500):** OLED-dark background (near-black), a single teal readiness-ring
motif offset to one side, the Spartan wordmark set in the app typeface on the other. No screenshots
embedded, no device frames, no claim text, no badges. Calm and premium; the ring is the only accent.

---

## 8. "What's new" — 1.0.0

```
Initial release.
• Daily plan matched to your WHOOP recovery, with why-it-matters on every activity
• One-tap done, snooze, skip, or reschedule
• Calendar-aware scheduling using free/busy gaps only
• Local reminders with quiet hours
• On-device data, no accounts, no analytics, no ads
Ships with labeled sample data — connect WHOOP and Google Calendar when you are ready.
```

---

## 9. Store-presence checklist

- [ ] **App icon** — 512 × 512 px, 32-bit PNG, matches the launcher icon; dark, minimal, teal ring motif.
- [ ] **Feature graphic** — 1024 × 500 px (concept in §7); required for listing prominence.
- [ ] **Screenshots** — the 6 shots in §7 (minimum 2 required; ship all 6), portrait, 1080 × 2400.
- [ ] **Privacy policy URL** — **required** by Play for all apps, and doubly so for Health & Fitness.
      Must be a live, publicly reachable page covering: what is read from WHOOP/Calendar, on-device
      storage, no collection/sharing, token encryption, deletion and disconnect rights. Do not
      submit with a placeholder URL.
- [ ] **Contact email** — `support@spartan.app` (**placeholder — register/replace with a monitored
      address before submission**; Play shows it publicly).
- [ ] **Category + tags** set per §4.
- [ ] **Content rating** questionnaire completed per §5; certificate shows Everyone / PEGI 3.
- [ ] **Health apps declaration** completed per §6 (wellness positioning statement pasted; Health
      Connect declared unused).
- [ ] **Data safety form** completed per §6 (no data collected, no data shared).
- [ ] **App content** — no ads declared; target audience adults (not designed for children; do not
      opt into Families).
- [ ] **Release artifact** — signed AAB, versionName 1.0.0, targetSdk 35 (meets current Play target
      API requirement); `allowBackup=false` and backup exclusions verified in the merged manifest.
- [ ] **Pre-release gate** from `docs/SECURITY_PRIVACY_CHECKLIST.md` run: no secrets in repo, no
      PHI in logs, delete/disconnect flows clear all stores, build/test/lint green.
- [ ] **Trademark hygiene** — WHOOP used descriptively only (not in title/icon/graphic); the
      non-affiliation line in §3 retained verbatim.
