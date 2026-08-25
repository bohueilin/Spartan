# Spartan — Visual & Video Guide

How Spartan looks, what to shoot, and exactly what to type into a video model to get assets that
match. Written to be executed by someone who is not a designer.

Everything here inherits three hard rules from `docs/AGENTS.md` and `docs/Spartan_Decisions.md`:

1. **No medical claims, ever.** Not in a voiceover, not in a caption, not in a chyron. Spartan gives
   wellness and fitness guidance. Never "diagnose", "treat", "cure", "fix your heart", "lower your
   blood pressure", "medical-grade".
2. **No fabricated data presented as real.** Any number in a screenshot or video is sample data and
   must be visibly labelled `SAMPLE DATA` — the app already enforces this on every surface. Do not
   retouch a screenshot to show a nicer recovery score.
3. **No before/after bodies, no scales, no calorie counts, no shame.** This is a readiness product,
   not a weight-loss product. The research file `docs/research/HABIT_RETENTION_UX_2026.md` is
   explicit that loss-framing and streak-anxiety are what churn users.

---

## 1. The visual system

The palette is already defined in code (`ui/theme/Theme.kt`, `Tokens.kt`) and duplicated in
`ios/SpartanApp/Sources/SpartanApp.swift`. Use these exact values in every external asset.

| Token | Dark | Light | Use |
|---|---|---|---|
| Background | `#0A0F0E` | `#F6F8F8` | Canvas. Dark is the hero look. |
| Surface | `#121817` | `#FFFFFF` | Cards |
| Outline | `#3D4F48` | `#9BADAA` | Borders, ring track |
| Accent (teal) | `#3FE0C8` | `#0B685C` | The one accent. Primary actions, brand. |
| Primed (green) | `#38D07E` | `#0E7B43` | High readiness |
| Easy (amber) | `#E7B25A` | `#7C570E` | Medium readiness |
| Rest (ember) | `#E67A5A` | `#A0381D` | Low readiness |
| Ink | `#EAF1EF` | `#11201E` | Primary text |
| Muted | `#9DB0AB` | `#4A5654` | Secondary text |

**Geometry:** 18dp card radius, 8dp chip radius, 10dp skeleton radius. Spacing scale
4/8/12/16/20/24. Ring stroke 9dp, round caps.

**Type:** the app ships system fonts (Roboto on Android, SF on iOS) on purpose. For marketing art
only, a geometric grotesque is acceptable — **Inter Tight**, **Space Grotesk**, or **General Sans**
are the closest free matches. Wordmark tracking is **3** everywhere (`SPARTAN`, all caps).

**Motion:** 140ms micro, 220ms state change, 420ms reveal. Everything eases out. There is exactly
one continuous animation in the whole product (the loading pulse) — do not invent more.

### The look in one sentence
> Near-black canvas, one teal accent, generous negative space, a single confident number, and no
> decoration that isn't data.

### Anti-patterns (what makes it look generic)
- Purple/blue SaaS gradients, glassmorphism, floating 3D blobs
- Stock "fitness people smiling at a phone"
- Confetti, trophies, flames, streak counters — deliberately rejected by the product
- Fake dashboards with dense charts Spartan does not have
- Neon glow on everything; the accent earns attention by being rare

---

## 2. Asset inventory (what to produce, exact specs)

### Already in the repo
`docs/assets/play-icon-512.png` · `play-feature-graphic-1024x500.png` · `spartan-github.png` ·
`spartan-poster.png`, plus the generator `render_feature_graphic.py`.

### Still needed

| Asset | Spec | Where it goes |
|---|---|---|
| Phone screenshots ×6–8 | 1080×2404 PNG (this device) | Play listing, App Store, landing |
| App preview video | 1080×1920 or 886×1920, ≤30s, H.264/HEVC, ≤500MB | Play "promo video", App Store preview |
| Landing hero loop | 1920×1080, 8–12s, silent, seamless loop, ≤3MB webm + mp4 | Website hero |
| Social launch card | 1200×630 | X / LinkedIn / OG image |
| Onboarding hero still | 1200×1200 | README, press kit |

### Screenshot shot list (in order, with captions)

Capture on the real device, dark theme, at 1080×2404. The captions are the marketing copy — the
screenshot itself must be unretouched.

1. **Today / readiness** — ring at 42 "Take it easy". Caption: *"One number. One plan."*
2. **Today / plan list** — REQUIRED card with accent border. Caption: *"Today, decided for you."*
3. **Expanded activity + follow-along card** — the new video card. Caption: *"Every action explains itself."*
4. **Metric detail + trend** — the new gradient sparkline. Caption: *"Your trend, not a population average."*
5. **Coach** — goal + healthy ranges. Caption: *"A goal that bends the week."*
6. **Connections** — consent copy + SAMPLE DATA chip. Caption: *"Honest about what's real."*
7. **Privacy** — export + delete. Caption: *"Local-first. Delete it all, anytime."*
8. *(optional)* **Reflection sheet** — Caption: *"An ending, not a scoreboard."*

Capture command (already proven on this device):

```bash
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png ./shot.png && adb shell rm /sdcard/s.png
```

Frame them in a device bezel for the store with any mockup tool, or keep them bare — bare
screenshots read as more confident and are allowed on both stores.

---

## 3. Video strategy

Four videos, in priority order. Only the first is required to ship.

### V1 — App preview (store), 20–25s
The one that converts. Structure, one beat per screen, no voiceover (stores autoplay muted):

| Time | Beat | On screen |
|---|---|---|
| 0–3s | Hook | Black. Ring sweeps 0→42. Text: *"Your daily readiness, decided."* |
| 3–8s | The number means something | Band label "Take it easy" resolves under the ring |
| 8–13s | The plan | Plan list scrolls; one card checks off with the spring + haptic beat |
| 13–18s | The why | Card expands to WHY THIS MATTERS + follow-along video card |
| 18–22s | Trust | SAMPLE DATA chip and Privacy screen; text: *"Local-first. Yours to delete."* |
| 22–25s | Wordmark | `SPARTAN` on black, teal underline sweep |

**Make it from real screen recordings, not AI.** Store rules (Apple especially) require app previews
to be captured from the actual app. Use:

```bash
adb shell screenrecord --size 1080x2404 --bit-rate 12000000 /sdcard/demo.mp4
# ...drive the app...
adb pull /sdcard/demo.mp4
```

Then cut with ffmpeg (see §5). AI video is for the *landing page*, not the store preview.

### V2 — Landing hero loop, 8–12s, silent, seamless
Abstract, generated. No UI, no people. This is where AI video earns its place. Prompts in §4.

### V3 — Feature micro-loops, 3–5s each, silent
One per landing-page section: the ring sweeping, a card checking off, the trend line drawing in.
Screen-recorded and cropped, or re-created as Lottie/CSS if you want them under 100KB.

### V4 — Founder/《why》 video, 45–60s
Only if you want it. Talking head, no B-roll of gyms. The honest positioning — local-first, no
servers, no ads, rules you can read — is the story. Do not claim medical benefit.

---

## 4. Video generation prompts

For **Sora 2 / Veo 3 / Runway Gen-4 / Kling 2.x / Pika**. Each is self-contained: paste as-is.
Always append the negative list. Generate 3–4 takes per prompt and pick.

Shared **style suffix** (append to every prompt):

> Style: minimal, premium, near-black background #0A0F0E, single teal accent #3FE0C8, high contrast,
> soft volumetric depth, no text, no logos, no people, no UI elements, cinematic, shallow depth of
> field, 24fps, slow deliberate motion, seamless loop.

Shared **negative prompt**:

> people, faces, hands, gym equipment, weights, food, pills, medical imagery, hospital, stethoscope,
> heartbeat EKG line, text, watermark, logo, UI, charts, purple, blue, orange glow, confetti,
> lens flare, fast cuts, shaky camera, stock footage look

---

**P1 — Hero loop: "the ring breathes"** *(primary landing hero)*
> A single thin luminous teal ring floating in deep black space, slowly rotating and pulsing once
> like a calm breath. Fine particles of light drift around it. The ring's stroke has soft bloom.
> Camera pushes in almost imperceptibly. Absolute stillness and control. 10 seconds, seamless loop.

**P2 — Hero loop: "readiness resolves"**
> Three concentric thin rings in teal, green and amber, each sweeping open from the top at different
> speeds, then settling into perfect stillness. Deep black background. The arcs have soft glow and
> rounded caps. Meditative, precise, like an instrument coming into focus. 8 seconds, seamless loop.

**P3 — Texture loop: "night into morning"**
> Extremely slow gradient shift across a near-black field, a faint teal horizon glow rising like the
> first minute of dawn, fine grain texture. No objects. Feels like waking up rested. 12 seconds,
> seamless loop.

**P4 — Section loop: "the line finds its shape"**
> A single thin teal line draws itself left to right across black, gently rising and falling like a
> trend, with a soft gradient fading beneath it. A small dot lands on the final point and glows once.
> 5 seconds.

**P5 — Section loop: "the check"**
> A soft-cornered square outline in teal on black. A checkmark strokes in from left to right with a
> single confident motion, and the square fills with a translucent teal wash that settles. Tactile,
> physical, satisfying. 3 seconds.

**P6 — Social card motion: "discipline"**
> Slow orbit around a matte black sphere with a single thin teal meridian line, sitting on an
> infinite black plane. Faint rim light. Restrained and expensive-looking. 6 seconds, seamless loop.

**Audio** (if you add any): no music on store previews. For the landing loop, silence is stronger.
If you must, a single low sub-bass swell at the ring's pulse — no drums, no EDM.

### Image models (Midjourney / Flux / Ideogram) for stills

> minimal product poster, near-black background #0A0F0E, one luminous thin teal ring #3FE0C8
> centred, vast negative space, soft bloom, fine film grain, Swiss typographic composition, premium
> health-technology brand, no people, no text --ar 16:9 --style raw

Swap `--ar 1:1` for the social card, `--ar 9:16` for phone wallpapers.

---

## 5. Production pipeline

Record on device, then cut locally. All ffmpeg, no editor needed.

```bash
# 1. Record (stop with ctrl-C, max 3 min per clip)
adb shell screenrecord --size 1080x2404 --bit-rate 12000000 /sdcard/demo.mp4
adb pull /sdcard/demo.mp4 raw.mp4

# 2. Trim a beat (start 4.0s, length 5.0s)
ffmpeg -ss 4.0 -i raw.mp4 -t 5.0 -c:v libx264 -crf 18 -preset slow -an beat1.mp4

# 3. Concatenate beats
printf "file 'beat1.mp4'\nfile 'beat2.mp4'\n" > list.txt
ffmpeg -f concat -safe 0 -i list.txt -c copy preview.mp4

# 4. Store-safe encode (H.264, yuv420p, no audio track)
ffmpeg -i preview.mp4 -vf "scale=1080:1920:force_original_aspect_ratio=decrease,pad=1080:1920:(ow-iw)/2:(oh-ih)/2:color=0x0A0F0E" \
  -c:v libx264 -profile:v high -pix_fmt yuv420p -crf 20 -movflags +faststart -an app-preview.mp4

# 5. Landing loop: webm + mp4, tiny
ffmpeg -i hero.mp4 -c:v libvpx-vp9 -crf 34 -b:v 0 -an -movflags +faststart hero.webm
ffmpeg -i hero.mp4 -c:v libx264 -crf 24 -pix_fmt yuv420p -an -movflags +faststart hero.mp4

# 6. Poster frame for the <video> tag
ffmpeg -i hero.mp4 -vframes 1 -q:v 2 hero-poster.jpg
```

Embed the landing loop as: `<video autoplay muted loop playsinline poster="hero-poster.jpg">` —
`muted` + `playsinline` are required or mobile Safari will not autoplay. Always provide the poster
so the hero has something on first paint, and respect `prefers-reduced-motion` by pausing it.

---

## 6. Store listing requirements (quick reference)

**Google Play:** icon 512×512 PNG · feature graphic 1024×500 · 2–8 phone screenshots, 16:9 or 9:16,
min 320px, max 3840px · promo video is a **YouTube URL**, not an upload.

**App Store:** icon 1024×1024, no alpha, no rounded corners · screenshots for 6.9" and 6.5" · app
previews 15–30s, captured from the app, up to 3 per size.

**Health-category note:** both stores review health apps harder. The listing must not imply
diagnosis or treatment. Spartan's existing disclaimer line ("wellness and fitness guidance, not
medical advice") should appear in the listing description, not only in-app.

---

## 7. In-app visual enhancements shipped alongside this guide

| Enhancement | Why it matters |
|---|---|
| **Follow-along video cards** | The video story was a plain text link. Now a real media card: generated teal tile, play glyph, title, channel, duration, and an explicit "Opens YouTube". Drawn locally — the app makes no network calls, so a generated tile is the honest option rather than faking a video still. |
| **Trend chart with gradient area fill** | The sparkline was a bare polyline with a dot on every point. Now a gradient area under the line, a single baseline, and a marker only on the newest reading — the eye lands on "where I am now". |
| **Onboarding hero mark** | First run opened on a form. Now three concentric arcs sweep in, in the readiness palette. Deliberately abstract — no number, because a ring with a score would read as a health reading the app cannot yet have. |

Each respects reduced motion and the existing token system.

## 8. Backlog — visual ideas not yet built

- **Lottie/Compose splash → first plan transition** (the ring persists across the boundary)
- **Share card**: render "Day complete" as a 1080×1080 image the user can share. Must carry the
  SAMPLE DATA marker when applicable.
- **Widget refresh**: the Glance widget is text-only; a mini ring would make it recognisable.
- **Metric detail hero**: promote the trend chart to a full-bleed hero with the current value overlaid.
- **Empty states with drawn character**: currently calm text; a light line illustration per tab.
- **App icon motion** for the store (Play supports an animated icon in some placements).
