"""
The hideout illustration: a hut on a hill under a full moon, a student with the
issued day pack walking the lit path up to it. Painted in grayscale at the art's true proportions, then
dithered into block characters of one colour, five glyphs x brightness tiers.

Run from a scratch directory: python tools/hideout-art.py
Writes illus_art.txt (the <pre> body for #hideout .hideout-art in index.html),
illus_gray.png (the painting) and illus_preview.png (roughly how the page shows it).
Needs numpy, Pillow, and Consolas for the preview.
"""
import math, random, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

C, R = 120, 144          # columns, rows
CW, CH = 440 / 120, 960 / 144         # one cell in canvas pixels (Consolas is ~0.55em wide)
W, H = 440, 960
rng = random.Random(11)

def layer():
    return Image.new('L', (W, H), 0)

def blur(img, r):
    return img.filter(ImageFilter.GaussianBlur(r))

# --- sky: dark at the top, a little lighter toward the horizon
y = np.linspace(0, 1, H)[:, None]
sky = 10 + 26 * (y ** 1.6) * np.ones((1, W))
img = sky.astype(np.float32)

def add(a, k=1.0):
    global img
    img = img + k * np.asarray(a, np.float32)

def over(mask_img, value):
    """Paint value where mask (0..255) is set, blending by mask."""
    global img
    m = np.asarray(mask_img, np.float32) / 255.0
    v = value if np.isscalar(value) else np.asarray(value, np.float32)
    img = img * (1 - m) + v * m

# moon glow and moon
MX, MY, MRAD = 300, 215, 62
glow = layer(); d = ImageDraw.Draw(glow)
d.ellipse([MX - 150, MY - 150, MX + 150, MY + 150], fill=60)
add(blur(glow, 60))
moon = layer(); d = ImageDraw.Draw(moon)
d.ellipse([MX - MRAD, MY - MRAD, MX + MRAD, MY + MRAD], fill=255)
over(moon, 238)
craters = layer(); d = ImageDraw.Draw(craters)
for cx, cy, r in ((-22, -18, 16), (18, 10, 12), (-6, 26, 9), (28, -24, 7), (-30, 14, 6)):
    d.ellipse([MX + cx - r, MY + cy - r, MX + cx + r, MY + cy + r], fill=255)
craters = Image.composite(blur(craters, 3), layer(), moon)
add(craters, -0.22)

# stars
stars = layer(); d = ImageDraw.Draw(stars)
for _ in range(70):
    sx, sy = rng.randrange(W), rng.randrange(0, 420)
    if math.hypot(sx - MX, sy - MY) < MRAD + 30:
        continue
    b = rng.choice((90, 120, 160, 220))
    d.point((sx, sy), fill=b)
    if b > 200:
        d.point([(sx - 1, sy), (sx + 1, sy), (sx, sy - 1), (sx, sy + 1)], fill=110)
add(stars)

# clouds: thin wisps, the moon side of each lit
def cloud(cx, cy, w, h, val):
    c = layer(); d = ImageDraw.Draw(c)
    for i in range(7):
        ox = rng.uniform(-w / 2, w / 2); oy = rng.uniform(-h / 3, h / 3)
        d.ellipse([cx + ox - w / 3, cy + oy - h / 2, cx + ox + w / 3, cy + oy + h / 2], fill=255)
    c = blur(c, 6)
    over(c.point(lambda p: p * 0.85), val)
cloud(140, 300, 180, 18, 44)
cloud(360, 286, 150, 12, 70)
cloud(70, 150, 120, 10, 32)

# far mountains, snow lit on the moon's side
def ridge(points, base, val, lit=None, snow=None):
    m = layer(); d = ImageDraw.Draw(m)
    d.polygon(points + [(W, base), (0, base)], fill=255)
    over(m, val)
    if snow:
        s = layer(); d = ImageDraw.Draw(s)
        for (px, py) in snow:
            d.polygon([(px, py), (px + 36, py + 34), (px + 14, py + 30), (px + 2, py + 40), (px - 10, py + 26)], fill=255)
        over(Image.composite(s, layer(), m), 175)
    if lit:
        # a lit rim along the ridge line
        e = layer(); d = ImageDraw.Draw(e)
        d.line(points, fill=255, width=3)
        over(Image.composite(blur(e, 1), layer(), m), lit)

ridge([(0, 470), (60, 420), (110, 440), (170, 380), (235, 452), (290, 430), (350, 370), (400, 410), (440, 395)],
      H, 52, lit=110, snow=[(170, 380), (350, 370)])
ridge([(0, 520), (70, 500), (140, 515), (230, 490), (320, 505), (440, 480)], H, 30)

# pine forest on the far hill
pines = layer(); d = ImageDraw.Draw(pines)
for px in range(-5, W + 10, 13):
    base = 520 + rng.randint(-8, 8)
    h = rng.randint(30, 55)
    d.polygon([(px, base - h), (px - 9, base), (px + 9, base)], fill=255)
over(pines, 13)

# the hill the hut sits on, rim-lit
hill = layer(); d = ImageDraw.Draw(hill)
d.ellipse([-60, 545, 520, 900], fill=255)
d.rectangle([0, 700, W, H], fill=255)
over(hill, 26)
rim = layer(); d = ImageDraw.Draw(rim)
d.arc([-60, 545, 520, 900], 200, 340, fill=255, width=3)
over(blur(rim, 1.2), 120)

# the hut
HX, HY = 175, 520          # left of the walls, top of the walls
HW, HH = 140, 62
hut = layer(); d = ImageDraw.Draw(hut)
d.rectangle([HX, HY, HX + HW, HY + HH], fill=255)
over(hut, 72)
planks = layer(); d = ImageDraw.Draw(planks)
for x in range(HX + 6, HX + HW, 9):
    d.line([(x, HY), (x, HY + HH)], fill=255, width=1)
over(planks, 40)
# roof: lit on the moon side
roof = layer(); d = ImageDraw.Draw(roof)
d.polygon([(HX - 18, HY + 4), (HX + 40, HY - 52), (HX + HW - 30, HY - 52), (HX + HW + 18, HY + 4)], fill=255)
over(roof, 115)
roofl = layer(); d = ImageDraw.Draw(roofl)
d.polygon([(HX + HW - 30, HY - 52), (HX + HW + 18, HY + 4), (HX + HW - 40, HY + 4), (HX + HW - 60, HY - 52)], fill=255)
over(roofl, 170)
eave = layer(); d = ImageDraw.Draw(eave)
d.line([(HX - 18, HY + 5), (HX + HW + 18, HY + 5)], fill=255, width=4)
over(eave, 12)
for x in range(HX - 6, HX + HW + 10, 10):   # shingle rows
    sh = layer(); d = ImageDraw.Draw(sh)
    d.line([(x, HY + 2), (x + 26, HY - 50)], fill=255, width=1)
    over(sh, 80)
# chimney and smoke
ch = layer(); d = ImageDraw.Draw(ch)
d.rectangle([HX + HW - 42, HY - 78, HX + HW - 28, HY - 40], fill=255)
over(ch, 90)
smoke = layer(); d = ImageDraw.Draw(smoke)
sx, sy = HX + HW - 35, HY - 84
for i in range(9):
    r = 3 + i * 1.5
    d.ellipse([sx - r, sy - r, sx + r, sy + r], fill=255)
    sx -= 7 + i * 1.5; sy -= 13
over(blur(smoke, 4).point(lambda p: p * 0.45), 80)
# door, and the lit window with its glow
door = layer(); d = ImageDraw.Draw(door)
d.rectangle([HX + 22, HY + 18, HX + 44, HY + HH], fill=255)
over(door, 6)
wglow = layer(); d = ImageDraw.Draw(wglow)
d.ellipse([HX + 60, HY - 10, HX + 140, HY + 70], fill=255)
add(blur(wglow, 18), 0.18)
win = layer(); d = ImageDraw.Draw(win)
d.rectangle([HX + 82, HY + 16, HX + 116, HY + 42], fill=255)
over(win, 245)
bars = layer(); d = ImageDraw.Draw(bars)
d.line([(HX + 99, HY + 16), (HX + 99, HY + 42)], fill=255, width=2)
d.line([(HX + 82, HY + 29), (HX + 116, HY + 29)], fill=255, width=2)
over(bars, 70)
# light spilling onto the grass
spill = layer(); d = ImageDraw.Draw(spill)
d.polygon([(HX + 80, HY + HH), (HX + 118, HY + HH), (HX + 150, HY + HH + 60), (HX + 50, HY + HH + 60)], fill=255)
add(blur(spill, 10), 0.22)

# a tall pine on the right, framing the scene
def pine(cx, top, h, w, val):
    p = layer(); d = ImageDraw.Draw(p)
    tiers = 6
    for i in range(tiers):
        t0 = top + i * h / (tiers + 1)
        ww = w * (i + 1.5) / (tiers + 1)
        d.polygon([(cx, t0), (cx - ww, t0 + h / tiers * 1.4), (cx + ww, t0 + h / tiers * 1.4)], fill=255)
    d.rectangle([cx - 4, top + h - 8, cx + 4, top + h + 40], fill=255)
    over(p, val)
    return p
p = pine(395, 330, 300, 46, 8)
e = blur(p, 1).filter(ImageFilter.FIND_EDGES)
add(e.point(lambda v: min(255, v * 2)), 0.12)
pine(40, 400, 260, 40, 7)

# foreground meadow: grass blades against the dark, lit tips
grass = layer(); d = ImageDraw.Draw(grass)
for _ in range(420):
    gx = rng.uniform(0, W); gy = rng.uniform(680, H)
    h = rng.uniform(8, 26) * (0.6 + (gy - 680) / 400)
    d.line([(gx, gy), (gx + rng.uniform(-5, 5), gy - h)], fill=255, width=1)
over(grass, 58)
path = layer(); d = ImageDraw.Draw(path)
d.polygon([(HX + 18, HY + HH + 4), (HX + 48, HY + HH + 4), (250, H), (120, H)], fill=255)
over(blur(path, 5), 78)

# the student on the path, walking to the hut, back to us: a silhouette on the lit path
FX, FY, K = 250, 660, 0.85  # between the feet, ground line, scale
fig = layer(); d = ImageDraw.Draw(fig)
d.ellipse([FX - 9 * K, FY - 118 * K, FX + 9 * K, FY - 98 * K], fill=255)                  # head
d.polygon([(FX - 10 * K, FY - 112 * K), (FX + 10 * K, FY - 112 * K), (FX + 12 * K, FY - 92 * K), (FX - 12 * K, FY - 92 * K)], fill=255)  # bob of hair
d.polygon([(FX - 15 * K, FY - 94 * K), (FX + 15 * K, FY - 94 * K), (FX + 18 * K, FY - 52 * K), (FX - 18 * K, FY - 52 * K)], fill=255)   # sailor top
d.polygon([(FX - 20 * K, FY - 54 * K), (FX + 20 * K, FY - 54 * K), (FX + 26 * K, FY - 30 * K), (FX - 26 * K, FY - 30 * K)], fill=255)   # pleated skirt
d.rectangle([FX - 13 * K, FY - 32 * K, FX - 6 * K, FY], fill=255)                     # legs
d.rectangle([FX + 6 * K, FY - 32 * K, FX + 13 * K, FY - 4 * K], fill=255)
d.line([(FX - 15 * K, FY - 90 * K), (FX - 26 * K, FY - 58 * K)], fill=255, width=4)      # arm, with the bag
d.rectangle([FX - 34 * K, FY - 62 * K, FX - 18 * K, FY - 44 * K], fill=255)              # the issued day pack
d.line([(FX + 15 * K, FY - 90 * K), (FX + 22 * K, FY - 60 * K)], fill=255, width=4)
over(fig, 4)
# a thin moonlit edge on the moon's side
edge = blur(fig, 1).filter(ImageFilter.FIND_EDGES)
side = Image.new('L', (W, H), 0); ImageDraw.Draw(side).rectangle([FX + 2 * K, 0, W, FY - 40 * K], fill=255)
over(Image.composite(edge, layer(), side).point(lambda v: min(255, v * 2)), 120)
# her shadow, falling toward us
sh = layer(); d = ImageDraw.Draw(sh)
d.polygon([(FX - 14 * K, FY), (FX + 14 * K, FY), (FX + 30 * K, FY + 70 * K), (FX - 6 * K, FY + 70 * K)], fill=255)
over(blur(sh, 4).point(lambda v: v * 0.7), 10)

img = np.clip(img, 0, 255)
Image.fromarray(img.astype(np.uint8)).save('illus_gray.png')

# --- down to cells, then dithered onto the glyph ladder
cells = np.asarray(Image.fromarray(img.astype(np.uint8)).resize((C, R), Image.BOX), np.float32)
# Every glyph at every tier, by how bright it reads: coverage x tier alpha.
COVER = {'░': 0.25, '▒': 0.5, '▓': 0.75, '█': 1.0, '·': 0.15, '+': 0.3, '*': 0.35}
ALPHA = {1: 0.16, 2: 0.30, 3: 0.48, 4: 0.72, 5: 1.0}
combos = sorted({(round(COVER[g] * ALPHA[t], 3), g, t) for g in COVER for t in ALPHA})
LADDER, lv = [(' ', 0)], [0.0]
for b, g, t in combos:
    if g in '·+*':
        continue          # keep steps at least a little apart
    if b - lv[-1] >= 0.03:
        LADDER.append((g, t)); lv.append(b)
levels = np.array(lv, np.float32) * 255
# the night is painted dark; lift it so the ladder's low steps get used
cells = 255 * np.clip((cells - 22) / (235 - 22), 0, 1) ** 1.05
out = [[None] * C for _ in range(R)]
err = cells.copy()
for yy in range(R):
    for xx in range(C):
        v = err[yy, xx]
        i = int(np.argmin(np.abs(levels - v)))
        out[yy][xx] = LADDER[i]
        e = v - levels[i]
        if xx + 1 < C: err[yy, xx + 1] += e * 7 / 16
        if yy + 1 < R:
            if xx > 0: err[yy + 1, xx - 1] += e * 3 / 16
            err[yy + 1, xx] += e * 5 / 16
            if xx + 1 < C: err[yy + 1, xx + 1] += e * 1 / 16

# stars as glyphs on empty sky, like the title's punctuation
srng = random.Random(5)
for _ in range(60):
    xx, yy = srng.randrange(C), srng.randrange(0, 62)
    if out[yy][xx][0] == ' ':
        out[yy][xx] = srng.choice((('·', 3), ('·', 4), ('+', 4), ('*', 5), ('·', 5)))

# preview as it will look: one mint, tiers as alpha
ALPHA[0] = 0
font = ImageFont.truetype('C:/Windows/Fonts/consola.ttf', 12)
pw, ph = int(C * 6.6), R * 12
prev = Image.new('RGB', (pw, ph), (11, 14, 15))
d = ImageDraw.Draw(prev)
for yy in range(R):
    for xx in range(C):
        g, t = out[yy][xx]
        if g == ' ':
            continue
        a = ALPHA[t]
        col = tuple(int(bg + (fg - bg) * a) for fg, bg in zip((127, 212, 193), (11, 14, 15)))
        d.text((xx * 6.6, yy * 12), g, font=font, fill=col)
prev.save('illus_preview.png')

# html
lines = []
for row in out:
    while row and row[-1][0] == ' ':
        row = row[:-1]
    s, cur, buf = '', None, ''
    for g, t in row:
        t = t if g != ' ' else None
        if t != cur:
            if buf:
                s += buf if cur is None else f'<i class="t{cur}">{buf}</i>'
            cur, buf = t, ''
        buf += g
    if buf:
        s += buf if cur is None else f'<i class="t{cur}">{buf}</i>'
    lines.append(s)
open('illus_art.txt', 'w', encoding='utf-8').write('\n'.join(lines))
print('ok', W, H)
