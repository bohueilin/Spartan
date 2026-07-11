# Spartan — hero poster generation prompt

Generate the image, export as PNG, save it to `docs/assets/spartan-poster.png`, and the README
picks it up automatically. Primary target is the **landscape cinematic banner** (GitHub renders it
full-width); the portrait variant is for social/store use.

---

## Primary prompt — cinematic banner (use 21:9 or 2:1, e.g. 2560×1097 or 1280×640)

> A premium minimalist cinematic movie-poster-style banner for a health technology brand called
> "SPARTAN". Pure near-black background (#0A0F0E) with a subtle dark-teal atmospheric gradient and
> very fine film grain. Centered slightly left of frame: a single luminous ring of light — an
> incomplete circle, open at the upper right about 30 degrees, drawn in glowing teal-cyan (#3FE0C8)
> with soft volumetric bloom, like a ring of dawn light photographed in fog. Inside the ring, a
> minimal off-white check mark (#EAF1EF) glows faintly, rendered as light, not an icon. The ring
> casts a soft teal reflection downward onto a polished black floor, like a still lake at night.
> Thin wisps of atmospheric haze drift through the light beam. To the right of the ring, elegant
> movie-title typography: the word "SPARTAN" in a tall, wide-tracked, all-caps geometric sans-serif,
> off-white (#EAF1EF), letterspacing very wide, understated and confident. Beneath it in much
> smaller teal type the tagline: "YOUR DAILY READINESS, DECIDED." At the very bottom edge, a single
> row of tiny movie-poster "billing block" style condensed text in dim gray-green (#9DB0AB), barely
> legible, evoking a film credits line. Overall mood: disciplined, calm, monolithic, A24-film
> aesthetic — one light source, immense negative space, museum-grade restraint. Photorealistic
> light behavior, 8k render quality, sharp focus on the ring, no people, no bodies, no armor, no
> helmets, no shields, no swords, no fitness equipment, no smartphone mockups, no charts, no extra
> text beyond the title, tagline, and credits line.

**Negative prompt (if the tool supports one):** warriors, spartan helmet, armor, weapons, muscles,
human figures, gym equipment, smartwatch, phone screen, UI elements, graphs, lens flare clichés,
purple-blue gradients, clutter, extra words, watermark, low contrast.

## Portrait variant — one-sheet (2:3, e.g. 1600×2400) for social/store

Same prompt, with these composition swaps: ring centered in the upper two-thirds, floor reflection
in the lower third; "SPARTAN" set below the ring, centered; tagline beneath; billing block across
the bottom margin like a theatrical one-sheet.

## Art direction notes (why these choices)

- **The ring is the brand.** It is the app's readiness ring and launcher icon (330° teal arc +
  check) — the poster renders the interface's core symbol as a physical object of light.
- **No warrior imagery** — a hard brand rule. The name carries the discipline; the image carries
  the calm.
- **Palette is the app's**: `#0A0F0E` canvas, `#3FE0C8` accent, `#EAF1EF` text, `#9DB0AB` muted —
  the poster and the product are one visual system.
- **A24-poster restraint** photographs well at README width and thumbnails cleanly at social size.

## After generating

1. Save as `docs/assets/spartan-poster.png` (keep under ~1.5 MB; `sips -Z 2560` + PNG-8 or a
   quality-85 JPEG renamed `.png` is fine for GitHub).
2. Commit and push — the README references it already.
3. Optional: crop a 1280×640 version and set it as the repo **social preview** in GitHub →
   Settings → General.
