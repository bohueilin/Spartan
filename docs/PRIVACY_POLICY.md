# Spartan privacy policy

**Effective date: July 9, 2026**

This policy explains what data Spartan handles, where it lives, and what control you have over it. Spartan is an Android app that turns your WHOOP data into a simple daily activity plan. We wrote this in plain language on purpose. If anything here is unclear, contact us (section 9).

## 1. Summary

Spartan is local-first. Your health data stays on your device, protected by Android's built-in device encryption; sign-in tokens get additional app-level encryption backed by the Android Keystore. There is no Spartan server, no account to create, no analytics, no advertising, and no data sale — we cannot sell or share what we never receive. The only network traffic the app makes is between your device and the services you explicitly connect (WHOOP and Google Calendar), using your own consent and your own credentials. You can disconnect those services and delete everything the app holds at any time, from inside the app.

## 2. Data we process

Spartan processes the following data, all of it to generate and manage your daily plan:

- **WHOOP-derived health metrics.** Recovery score, sleep performance and duration, day strain, heart rate variability (HRV), resting heart rate, and respiratory rate. Read from the WHOOP API with read-only scopes, only after you connect your WHOOP account.
- **WHOOP CSV export (optional import).** Instead of connecting the API, you can import the CSV files WHOOP's own Data Export feature gives you. The import is read entirely on your device with **no network access at all** and stores: the daily metrics above, per-sleep bed/wake times, recorded workouts (duration, strain, heart-rate zones), and your yes/no journal answers as three flags — caffeine, alcohol, and eating close to bedtime. These flags exist only to explain sleep/recovery patterns to you; they are never transmitted anywhere and are removed by the in-app full deletion.
- **Profile details (optional).** A name and height if you choose to enter them. Both are optional and stay on your device.
- **Activity check-ins.** Which planned activities you marked done, snoozed, skipped, or rescheduled, and when. Stored as statuses and timestamps, not free-form health narratives.
- **Calendar free/busy intervals only.** To find open time for activities, Spartan reads which time blocks are busy — never event titles, descriptions, attendees, or any other event content. Only the slot you choose for an activity is kept; the surrounding calendar data is not persisted.
- **OAuth tokens.** If you connect WHOOP or Google Calendar, the app holds the access tokens needed to talk to those services on your behalf.

**A note on the default build.** Spartan ships with clearly labeled sample data — a mock WHOOP source and a stub calendar — so the app works with no connections and no network access at all. Real WHOOP and Google Calendar integrations activate only when you connect them yourself and grant consent through each provider's own sign-in.

## 3. Where your data lives

- **On your device.** All app data is stored locally in the app's database and preferences storage (Room and DataStore). There is no Spartan cloud backend and no Spartan account.
- **Excluded from backup and transfer.** The app's data is excluded from Android cloud backup and device-to-device transfer, so your health data does not ride along when you back up or migrate your phone.
- **Tokens in encrypted storage.** OAuth tokens are kept in Android-Keystore-encrypted storage, separate from the rest of the app's data. They are never written to the database and never logged.
- **Nothing on our side.** We do not operate servers that receive, store, or process your data. We have no copy of it.

## 4. Third parties

Spartan talks to exactly two external services, and only if you connect them:

- **WHOOP API.** Read-only access to your recovery, sleep, strain, and related metrics, using WHOOP's official OAuth sign-in. Your use of WHOOP is governed by [WHOOP's privacy policy](https://www.whoop.com/privacy/).
- **Google Calendar API.** Free/busy availability reads, plus — only if you separately opt in — creating a single, clearly titled Spartan event in your calendar, confirmed by you each time. Your use of Google services is governed by [Google's privacy policy](https://policies.google.com/privacy).

In both cases, data flows directly between your device and the provider over TLS-encrypted connections. It does not pass through any Spartan server, because there isn't one.

There are no other third parties. Spartan contains no analytics SDK, no telemetry, no crash-reporting service, and no advertising SDK.

## 5. Your controls

- **Disconnect anytime.** You can disconnect WHOOP or Google Calendar independently at any time from the app's connections screen. Disconnecting stops data access immediately and clears the stored sign-in tokens; for a CSV import it also removes the imported raw cycle and workout tables so plans return to labeled sample data. Previously normalized readings remain on your device as your history until you delete them (per-source purge is planned).
- **Delete everything.** The app includes a full data deletion option that clears every table, all preferences, and all scheduled reminders, returning the app to its first-run state.
- **Export first.** Before deleting, you can produce a local, human-readable summary of your data. Export is user-directed — the app never sends it anywhere on its own.

Because all data is local, deletion is immediate and complete. There is no server-side copy to chase.

## 6. Notifications

Reminders (daily check-in, pre-activity nudges, missed-activity follow-ups) are generated and scheduled entirely on your device. No push service, no remote content, no data sent anywhere. Notifications respect the quiet hours you configure, and each kind can be turned off independently.

## 7. Children

Spartan is not directed at children under 13 (or the higher minimum age that applies in your country). We do not knowingly process data from children, and — since there are no accounts and no data collection on our side — we hold no data to remove. If you believe a child is using the app in your household, uninstalling it removes all of its data.

## 8. Health disclaimer

Spartan provides wellness and fitness guidance only. It is not medical advice, not a medical device, and does not diagnose, treat, cure, or prevent any condition. When readings look concerning, the app's response is deliberately limited: it suggests talking to a qualified clinician. For any health concern, consult a clinician — do not rely on this app.

## 9. Changes to this policy and contact

If this policy changes, we will update the effective date at the top and note material changes in the app's release notes before they take effect. Continued use after a change means the updated policy applies.

Questions or requests: **support@spartan.app** (placeholder address — replace with the real support contact before publishing this policy).

## 10. Google Play Data safety mapping

Google Play defines "collection" as data transmitted off the device. Under that definition, Spartan collects almost nothing: health data moves only between your device and WHOOP or Google, under your own OAuth consent, and is **never transmitted to the developer**. The table below maps Play's data categories to what Spartan actually does.

| Play data category | Collected (transmitted off device)? | Shared with third parties? | Encrypted in transit? | User-deletable? |
| --- | --- | --- | --- | --- |
| Health and fitness — health info | Stored on device only. Received *from* WHOOP over TLS; **not transmitted to the developer**; not sent anywhere else. | No | Yes (TLS for the device↔WHOOP connection) | Yes — full in-app deletion |
| Personal info — name | No. Optional, entered by you, stays on device. | No | n/a (never transmitted) | Yes — in-app deletion |
| Personal info — email address | No. There are no accounts; the app never asks for an email. | No | n/a | n/a |
| Calendar | Free/busy intervals are received *from* Google over TLS. If you opt in to event creation, a Spartan activity title and time are written to **your own** calendar, per-event, with your confirmation — never to the developer. | No | Yes (TLS for the device↔Google connection) | Yes — disconnect stops access and clears tokens; delete-all removes cached intervals; you control events in your own calendar |
| Credentials (OAuth tokens) | Transmitted only to WHOOP/Google for authentication, over TLS. Stored in Keystore-encrypted storage on device. | No | Yes | Yes — cleared on disconnect and on full deletion |
| App activity (check-ins, in-app interactions) | No. Check-ins and plan history stay on device. | No | n/a | Yes — in-app deletion |
| App info and performance (crash logs, diagnostics) | No. No crash-reporting or diagnostics SDK is included. | No | n/a | n/a |
| Device or other IDs | No | No | n/a | n/a |
| Location | No | No | n/a | n/a |
| Financial info | No | No | n/a | n/a |
| Messages, photos, videos, audio, files, contacts, web browsing | No | No | n/a | n/a |

In short: no data category is collected by or shared with the developer. The only off-device flows are the ones you authorize directly with WHOOP and Google, over encrypted connections, and everything the app stores is deletable in-app.
