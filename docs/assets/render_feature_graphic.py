#!/usr/bin/env python3
"""Spartan Play feature graphic (1024x500), rendered at 2x and downsampled.
Design language: 'Measured Fire' — dark instrument panel, one luminous readiness arc,
a calibrated 45-day field of recovery bars (the user's real series shape)."""
import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

FONTS = "/Users/bohueilin/Library/Application Support/Claude/local-agent-mode-sessions/skills-plugin/20cf01a3-d15d-4470-8305-834f51396222/f136448a-5ba4-4863-a576-87ca01367254/skills/canvas-design/canvas-fonts"
OUT = "/private/tmp/claude-501/-Users-bohueilin-hackathons-Spartan--claude-worktrees-spartan-whoop-integration-029bda/a63e765c-dba7-4179-96e3-ed3802d3db15/scratchpad/store-assets/feature-graphic-1024x500.png"

S = 2  # supersample
W, H = 1024 * S, 500 * S

# Palette (brand tokens, dark theme)
BG      = (10, 15, 14)        # #0A0F0E
BG_LIFT = (14, 21, 19)
INK     = (234, 241, 239)     # #EAF1EF
MUTED   = (157, 176, 171)     # #9DB0AB
FAINT   = (86, 102, 97)
LINE    = (41, 54, 48)        # #293630
TEAL    = (63, 224, 200)      # #3FE0C8  balanced / accent
GREEN   = (56, 208, 126)      # #38D07E  primed
AMBER   = (231, 178, 90)      # #E7B25A  easy
EMBER   = (230, 122, 90)      # #E67A5A  rest

# The real 45-day recovery series (None = no reading) — the quiet reference.
REC = [None, 48, 62, 58, 37, 37, 60, 34, 70, 58, 68, 1, 47, 58, 71, 39, 62, 60, 87, 61,
       37, 45, 14, 59, 51, 74, 65, 54, 37, 85, 72, 52, 72, 37, None, 81, 95, 85, 38, 58,
       72, 58, 2, 77, 92]

def band(v):
    if v is None: return FAINT
    if v >= 67: return GREEN
    if v >= 50: return TEAL
    if v >= 34: return AMBER
    return EMBER

def font(name, size):
    return ImageFont.truetype(f"{FONTS}/{name}", size)

img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)

# --- ground: barely-there vertical lift behind the instrument, vignette calm elsewhere
for x in range(W):
    t = x / W
    lift = max(0.0, 1.0 - abs(t - 0.22) * 3.2)
    if lift > 0:
        c = tuple(int(BG[i] + (BG_LIFT[i] - BG[i]) * lift) for i in range(3))
        d.line([(x, 0), (x, H)], fill=c)

MARGIN = 60 * S

# ---------------- Readiness ring (left) ----------------
cx, cy, R = int(W * 0.225), int(H * 0.54), int(H * 0.30)
ring_w = 13 * S

# recessed track
d.arc([cx - R, cy - R, cx + R, cy + R], 0, 360, fill=LINE, width=ring_w)

# luminous 92% arc: gradient teal→green drawn as segments, on a glow layer
glow = Image.new("RGB", (W, H), (0, 0, 0))
dg = ImageDraw.Draw(glow)
start, sweep = -90, 331  # 92% of 360
steps = 240
for i in range(steps):
    t0 = start + sweep * i / steps
    t1 = start + sweep * (i + 1) / steps + 0.6
    t = i / steps
    col = tuple(int(TEAL[j] + (GREEN[j] - TEAL[j]) * t) for j in range(3))
    dg.arc([cx - R, cy - R, cx + R, cy + R], t0, t1, fill=col, width=ring_w)
# rounded end-cap dot at the arc tip
tip_a = math.radians(start + sweep)
tx, ty = cx + R * math.cos(tip_a), cy + R * math.sin(tip_a)
dg.ellipse([tx - ring_w / 2, ty - ring_w / 2, tx + ring_w / 2, ty + ring_w / 2], fill=GREEN)
from PIL import ImageChops
halo = glow.filter(ImageFilter.GaussianBlur(9 * S)).point(lambda p: int(p * 0.5))
img = ImageChops.add(img, halo)                      # luminous halo, ground untouched
img = ImageChops.add(img, glow)                      # crisp arc on top
d = ImageDraw.Draw(img)

# center numeral + label
f_num = font("BigShoulders-Bold.ttf", 128 * S)
f_lab = font("GeistMono-Regular.ttf", 15 * S)
num = "92"
nb = d.textbbox((0, 0), num, font=f_num)
d.text((cx - (nb[2] - nb[0]) / 2 - nb[0], cy - (nb[3] - nb[1]) / 2 - nb[1] - 14 * S), num, font=f_num, fill=INK)
lab = "R E C O V E R Y"
lb = d.textbbox((0, 0), lab, font=f_lab)
d.text((cx - (lb[2] - lb[0]) / 2, cy + 66 * S), lab, font=f_lab, fill=MUTED)
chip = "P R I M E D"
f_chip = font("GeistMono-Bold.ttf", 15 * S)
cb = d.textbbox((0, 0), chip, font=f_chip)
d.text((cx - (cb[2] - cb[0]) / 2, cy + 94 * S), chip, font=f_chip, fill=GREEN)

# ---------------- Wordmark + tagline (top right zone) ----------------
f_mark = font("BigShoulders-Bold.ttf", 66 * S)
mark = "S P A R T A N"
mx = int(W * 0.435)
my = int(H * 0.16)
d.text((mx, my), mark, font=f_mark, fill=INK)
f_tag = font("GeistMono-Regular.ttf", 19 * S)
d.text((mx + 3 * S, my + 84 * S), "YOUR DAILY READINESS, DECIDED.", font=f_tag, fill=MUTED)

# ---------------- 45-day calibrated bar field (right) ----------------
fx0, fx1 = int(W * 0.435), W - MARGIN
fy1 = int(H * 0.87)          # baseline
fh = int(H * 0.36)           # field height
n = len(REC)
gap = 4 * S
bw = (fx1 - fx0 - gap * (n - 1)) / n

# threshold strata at 34 / 67 (dashed hairlines)
for thr in (34, 67):
    y = fy1 - fh * thr / 100
    x = fx0
    while x < fx1:
        d.line([(x, y), (min(x + 7 * S, fx1), y)], fill=LINE, width=1 * S)
        x += 13 * S
# baseline
d.line([(fx0, fy1), (fx1, fy1)], fill=(60, 76, 70), width=1 * S)

for i, v in enumerate(REC):
    x0 = fx0 + i * (bw + gap)
    x1 = x0 + bw
    if v is None:
        d.rounded_rectangle([x0, fy1 - 3 * S, x1, fy1], radius=1 * S, fill=FAINT)
        continue
    h = max(3 * S, fh * v / 100)
    col = band(v)
    d.rounded_rectangle([x0, fy1 - h, x1, fy1], radius=2 * S, fill=col)
# emphasize the final (today) bar with a subtle cap glow
x0 = fx0 + (n - 1) * (bw + gap)
dot_r = 3 * S
d.ellipse([x0 + bw / 2 - dot_r, fy1 - fh * 0.92 - 14 * S - dot_r,
           x0 + bw / 2 + dot_r, fy1 - fh * 0.92 - 14 * S + dot_r], fill=INK)

# field micro-labels
f_micro = font("GeistMono-Regular.ttf", 13 * S)
d.text((fx0, fy1 + 10 * S), "45 DAYS · MAY 30 \u2014 JUL 13", font=f_micro, fill=FAINT)
lab2 = "REST · EASY · BALANCED · PRIMED"
b2 = d.textbbox((0, 0), lab2, font=f_micro)
d.text((fx1 - (b2[2] - b2[0]), fy1 + 10 * S), lab2, font=f_micro, fill=FAINT)
# tiny band swatches inline with the right label
sw = 8 * S
swx = fx1 - (b2[2] - b2[0]) - 4 * sw - 18 * S
for j, c in enumerate((EMBER, AMBER, TEAL, GREEN)):
    d.rounded_rectangle([swx + j * (sw + 3 * S), fy1 + 12 * S, swx + j * (sw + 3 * S) + sw, fy1 + 12 * S + sw],
                        radius=2 * S, fill=c)

# threshold value tags
for thr in (34, 67):
    y = fy1 - fh * thr / 100
    t = str(thr)
    tb = d.textbbox((0, 0), t, font=f_micro)
    d.text((fx0 - (tb[2] - tb[0]) - 8 * S, y - (tb[3] - tb[1]) / 2 - tb[1]), t, font=f_micro, fill=FAINT)

# ---------------- downsample & save ----------------
final = img.resize((1024, 500), Image.LANCZOS)
final.save(OUT, "PNG")
print("saved", OUT)
