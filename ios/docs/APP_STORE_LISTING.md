# Spartan — Apple App Store listing package

Launch collateral for the iOS app (bundle id `com.spartan`, version 1.0.0, build 1). All copy
follows the product voice: disciplined, calm, premium, honest. Sentence case, no hype words, no
exclamation marks, no invented claims. Contact email `support@spartan.app` is a **placeholder —
replace before submission**.

Companion documents: [APP_STORE_PRIVACY_LABELS.md](APP_STORE_PRIVACY_LABELS.md) (App Privacy
answers), [IOS_RELEASE_CHECKLIST.md](IOS_RELEASE_CHECKLIST.md) (path to submission), and the
Android originals in `docs/` (this file adapts
[docs/PLAY_STORE_LISTING.md](../../docs/PLAY_STORE_LISTING.md) — same voice, same claims,
Apple's field limits).

Apple indexes the app name, subtitle, and keyword field for search. Do not waste characters
repeating a word across those three fields.

---

## 1. App name

> Max 30 characters.

**Exact string:**

```
Spartan: daily recovery coach
```

**Character count: 29 / 30.**

Notes:
- Avoids "WHOOP" in the name. App Review guideline 2.3.7 and Apple's trademark rules discourage
  third-party marks in metadata; WHOOP is referenced descriptively in the description instead.
- Alternate (shorter, if a plain name is preferred): `Spartan` (7 / 30).

---

## 2. Subtitle

> Max 30 characters. Shown under the name on the product page; indexed for search.

**Exact string:**

```
Simple plans, on-device data
```

**Character count: 28 / 30.**

Notes:
- No word repeated from the app name (Apple gives no ranking credit for repeats).
- Makes only claims the default build literally satisfies: the plan is simple, the data is
  on-device.

---

## 3. Promotional text

> Max 170 characters. Sits above the description; **editable any time without a new build or
> review** — use it for the sample-data honesty note now, release news later.

**Exact string:**

```
Ships with clearly labeled sample data, so you can explore the full experience before connecting anything. Your data stays on your device — no account, no analytics.
```

**Character count: 165 / 170.**

---

## 4. Description

> Max 4,000 characters. The copy below is ~2,290 characters. Adapted from the Play full
> description: identical claims and voice; the token-protection sentence now names the iOS
> Keychain instead of Android Keystore. Paste verbatim.

```
Spartan reads your WHOOP recovery and turns it into a short daily plan you can actually complete.

Every morning you get 2–4 concrete activities — mobility, zone 2 cardio, breathwork, sleep hygiene, hydration — chosen to match how recovered you are. Each card explains why it matters, what to do step by step, how long it takes, and how important it is today. Check activities off in one tap, or snooze, skip, and reschedule without guilt.

If you connect Google Calendar, Spartan looks only at your free/busy times — never event titles or contents — and suggests open gaps that fit each activity. Local reminders nudge at the right moment and respect your quiet hours.

What Spartan reads from WHOOP (read-only, with your consent): recovery, sleep, strain, HRV, resting heart rate, and respiratory rate — through WHOOP's official sign-in.

A note on this version: Spartan ships with clearly labeled sample data, so you can explore the full experience before connecting anything. Live WHOOP and Google Calendar connections require your explicit consent through their official sign-in flows.

Features
• Recovery-adjusted daily plan (2–4 activities), rebuilt each morning
• Plain-language "why it matters" and step-by-step instructions on every card
• One-tap done, snooze, skip, or reschedule — your progress is saved
• Calendar-aware scheduling using free/busy gaps only
• Local reminders with quiet hours — nudges, never nagging
• Trends for recovery, sleep, HRV, and resting heart rate over time

Private by design. Your data stays on your device: no cloud backend, no account, no analytics, no ads, no trackers. Sign-in tokens are protected in the iOS Keychain and never leave your device. You can disconnect any integration or delete all app data at any time, from inside the app.

Spartan provides wellness and fitness guidance only. It is not a medical device, does not provide medical advice, and does not diagnose, treat, or prevent any condition. If a reading looks concerning, Spartan suggests speaking with a qualified clinician. Always consult a clinician for health concerns.

A WHOOP device and account are required for live data. WHOOP is a trademark of its owner; Spartan is an independent app and is not affiliated with or endorsed by WHOOP.

Questions: support@spartan.app
```

Copy rules honored: leads with the daily-plan value prop, sample-data honesty note included,
not-medical-advice disclaimer included, privacy paragraph included, no user counts, no
"doctor recommended", no compliance-certification claims, no exclamation marks.

---

## 5. Keywords field

> Max 100 characters, comma-separated, no spaces after commas (spaces count against the limit).
> Not shown to users; search index only. Do not repeat words already in the name or subtitle
> (spartan, daily, recovery, coach, simple, plans, on-device, data) or the category name
> (health, fitness) — Apple already indexes those.

**Exact string:**

```
whoop,hrv,sleep,strain,readiness,mobility,breathwork,zone 2,wellness,habit,routine,rest
```

**Character count: 87 / 100.**

Notes:
- `whoop` is descriptive compatibility use (the app genuinely reads the user's own WHOOP data,
  and the description carries the non-affiliation line). Trademarked terms in the keyword field
  are a known 2.3.7 rejection risk. If App Review objects, resubmit with this fallback:

  ```
  hrv,sleep,strain,readiness,mobility,breathwork,zone 2,wellness,habit,routine,rest,wearable
  ```

  **Fallback character count: 90 / 100.**

---

## 6. Category

- **Primary category:** Health & Fitness
- **Secondary category:** Lifestyle (optional; leave empty if in doubt — primary drives ranking)

Do not choose Medical — Spartan is deliberately positioned as consumer wellness, not medical.
This must stay consistent with the description's disclaimer and the App Review notes.

---

## 7. Age rating questionnaire — expected answers

Complete in App Store Connect → App Information → Age Rating. Expected answers for Spartan 1.0.0:

| Question area | Expected answer |
|---|---|
| Cartoon or fantasy violence | None |
| Realistic violence | None |
| Prolonged graphic or sadistic violence | None |
| Profanity or crude humor | None |
| Mature or suggestive themes | None |
| Horror or fear themes | None |
| Medical or treatment information | None (wellness/fitness guidance only; the app deliberately provides no medical or treatment information — concerning readings produce only a suggestion to consult a clinician, enforced in code by the safety filter) |
| Alcohol, tobacco, or drug use or references | None (hydration/sleep guidance only) |
| Simulated gambling | None |
| Sexual content or nudity | None |
| Contests | None |
| Gambling (real money) | No |
| Unrestricted web access | No (no in-app browser) |
| User-generated content / user interaction | No (no chat, no social features, no sharing) |

**Expected result: 4+** (and the lowest tier under Apple's updated global age rating system).
If the calculated rating comes back higher, the most likely cause is the medical/treatment
question — "Infrequent/Mild" there forces 12+. The honest answer for Spartan is **None**: it
presents fitness/wellness information and explicitly does not provide medical advice, diagnosis,
or treatment. Keep the answer and the in-app copy aligned.

---

## 8. Screenshot plan

Same six shots and captions as the Play listing (dark theme, sample-data state — keep the
"Sample data" label visible where it appears; it is a trust asset, not a defect). Two required
device sets for an iPhone-only app:

| Set | Devices represented | Pixel size (portrait) |
|---|---|---|
| 6.7-inch | iPhone Pro Max / Plus class | 1290 × 2796 |
| 6.1-inch | iPhone / iPhone Pro class | 1179 × 2556 |

App Store Connect scales the largest set down for smaller devices if only one set is uploaded,
but shipping both sets keeps text crisp. Capture from the iOS Simulator at exact device sizes
(`Cmd+S` saves at native resolution). No iPad set: the target declares iPhone only.

| # | Screen | Caption (overlay, one line, sentence case) |
|---|---|---|
| 1 | Check-in hero — Today screen with 2–4 activity cards, per-day total time, progress indicator | A short daily plan, matched to your recovery |
| 2 | Readiness ring — recovery band with sleep, HRV, and resting heart rate summary | Your recovery, read at a glance |
| 3 | Expanded activity card — why it matters, steps, duration, priority, snooze/skip/reschedule | Every activity explains why it matters |
| 4 | Connections/consent screen — WHOOP and Google Calendar with plain-language scope explanations | You decide exactly what Spartan can read |
| 5 | Privacy screen — on-device storage, disconnect, delete all data | Your data stays on your device |
| 6 | Onboarding — sample-data welcome state with "Connect WHOOP" call to action | Explore with sample data before connecting anything |

App icon: 1024 × 1024 px, no alpha, no rounded corners (Apple applies the mask); matches the
launcher concept — dark, minimal, teal ring motif. No App Store feature-graphic equivalent exists;
the icon and screenshot 1 do that job.

---

## 9. What's New — 1.0.0

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

## 10. Store-presence checklist

- [ ] **App icon** — 1024 × 1024 PNG, no alpha, consistent with the in-app icon asset catalog.
- [ ] **Screenshots** — both sets in §8 (minimum 1 per required size; ship all 6 per set).
- [ ] **Privacy policy URL** — **required** for every app, and load-bearing for a Health & Fitness
      app. Host the iOS-adapted privacy policy (see APP_STORE_PRIVACY_LABELS.md §5) at a stable
      public URL. Do not submit with a placeholder URL.
- [ ] **Support URL** — required by App Store Connect (Apple wants a URL, not just an email).
      A simple public page with the support contact is enough. Placeholder until it exists.
- [ ] **Contact email** — `support@spartan.app` (**placeholder — register/replace with a
      monitored address before submission**).
- [ ] **App Privacy labels** completed per [APP_STORE_PRIVACY_LABELS.md](APP_STORE_PRIVACY_LABELS.md)
      (Data Not Collected for the sample-data build; tracking: No).
- [ ] **Category + age rating** set per §6 and §7; certificate shows 4+.
- [ ] **Name/subtitle/keywords** pasted exactly from §1, §2, §5 — counts verified above; do not
      improvise copy, it is written to the same safety constraints as the app.
- [ ] **Trademark hygiene** — WHOOP used descriptively only (not in name, subtitle, or icon);
      the non-affiliation line in §4 retained verbatim; keyword fallback ready per §5.
