import math, random, re, sys

W, H = 64, 30
random.seed(7)
ch = [[' '] * W for _ in range(H)]
cl = [[None] * W for _ in range(H)]

def put(x, y, c, k):
    if 0 <= x < W and 0 <= y < H:
        ch[y][x] = c; cl[y][x] = k

ASPECT = 1.85  # columns per row for a round shape

# --- sky: stars, and a faint band of moonlit haze over the ridges
for _ in range(34):
    x, y = random.randrange(W), random.randrange(0, 12)
    c = random.choice('·····+*')
    put(x, y, c, 'star-hi' if c in '+*' else 'star')

MX, MY, MR = 44, 5, 2.9
def moon_d(x, y):
    return math.hypot((x - MX) / ASPECT, y - MY)

# --- ridges, back to front. Each is a height (row of its top) per column.
def ridge(peaks, base):
    out = []
    for x in range(W):
        top = base
        for px, py, slope in peaks:
            top = min(top, py + abs(x - px) * slope)
        out.append(top)
    return out

far = ridge([(9, 9.0, 0.42), (22, 13.5, 0.5), (56, 8.0, 0.40), (38, 14.0, 0.6)], 16.0)
mid = ridge([(4, 15.5, 0.35), (17, 17.0, 0.3), (47, 15.0, 0.28), (62, 16.5, 0.4)], 19.0)
near_top = 23

def fill_ridge(tops, body, lit, edge_body, edge_lit, peaks, snow=0):
    for x in range(W):
        t = tops[x]
        r0 = math.floor(t)
        # which side of the nearest peak: lit if the peak lies between it and the moon
        px = min(peaks, key=lambda p: p[1] + abs(x - p[0]) * p[2])[0]
        lit_side = (x > px) == (MX > px) and x != px
        for y in range(r0, H):
            if y == r0:
                frac = t - r0
                if frac >= 0.5:
                    c = '▄'
                else:
                    c = '█'
            else:
                c = '█'
            k = lit if lit_side else body
            if snow and y - t < snow and t < 12.5:
                k = 'snow' if lit_side else 'snow-shade'
            elif snow and y - t < snow + 1.2 and t < 12.5:
                c = '▓' if c == '█' else c
            put(x, y, c, k)

fill_ridge(far, 'far', 'far-lit', None, None,
           [(9, 9.0, 0.42), (22, 13.5, 0.5), (56, 8.0, 0.40), (38, 14.0, 0.6)], snow=2.2)

# haze where the far ridge meets the mid hills
for x in range(W):
    y = math.floor(mid[x]) - 1
    if 0 <= y < H and cl[y][x] in ('far', 'far-lit'):
        ch[y][x] = '▓'

fill_ridge(mid, 'mid', 'mid-lit', None, None,
           [(4, 15.5, 0.35), (17, 17.0, 0.3), (47, 15.0, 0.28), (62, 16.5, 0.4)])

# --- the moon, drawn after the far ridge so a peak can sit in front of nothing
for y in range(H):
    for x in range(W):
        d = moon_d(x, y)
        if cl[y][x] not in (None, 'star', 'star-hi'):
            continue
        if d <= MR - 0.15:
            crater = (x - MX + 2) ** 2 / 4 + (y - MY + 1) ** 2 < 1.3 or \
                     (x - MX - 3) ** 2 / 3 + (y - MY - 1.2) ** 2 < 0.9 or \
                     (x - MX + 1) ** 2 / 2 + (y - MY - 2) ** 2 < 0.5
            put(x, y, '▓' if crater else '█', 'moon-shade' if crater else 'moon')
        elif d <= MR + 0.35:
            put(x, y, '▒', 'halo')
        elif d <= MR + 0.9:
            put(x, y, '░', 'halo')

# --- pines: a stack of widening rows, a split trunk at the foot
def pine(cx, top, h, k):
    for i in range(h):
        y = top + i
        half = int(i * 0.75) if i < h - 1 else int((i - 1) * 0.75)
        tier = i % 3 == 2  # notch each third row for the bough line
        for x in range(cx - half, cx + half + 1):
            if tier and abs(x - cx) == half and half > 1:
                put(x, y, '▄', k)
            else:
                put(x, y, '█' if i else '▄', k)
    put(cx, top + h, '█', k)

# mid-distance pines along the hills
for cx in (2, 6, 9, 14, 19, 50, 54, 58, 61):
    base = math.floor(mid[cx])
    h = random.choice((4, 5, 6))
    pine(cx, base - h + 2, h, 'pine-far')

# ground under the hut
for y in range(near_top, H):
    for x in range(W):
        put(x, y, '█', 'near')
for x in range(W):
    put(x, near_top - 1, '▄', 'near')

# foreground pines framing the hut, tall enough to stand against the hills
pine(4, 13, 13, 'pine')
pine(11, 17, 9, 'pine')
pine(58, 12, 14, 'pine')
pine(51, 18, 8, 'pine')

# --- the hut
HX, HW, ROOF, EAVE, FLOOR = 22, 20, 17, 20, 26   # left, width, ridge row, eave row, sill row
# chimney and smoke
for y in range(ROOF - 1, EAVE - 1):
    put(HX + 15, y, '█', 'hut-shade'); put(HX + 16, y, '▌', 'hut-shade')
smoke = [(HX + 15, ROOF - 2, '▒'), (HX + 14, ROOF - 3, '░'), (HX + 14, ROOF - 4, '▒'),
         (HX + 13, ROOF - 5, '░'), (HX + 11, ROOF - 6, '░'), (HX + 12, ROOF - 6, '░'),
         (HX + 9, ROOF - 7, '░'), (HX + 10, ROOF - 7, '░')]
for x, y, c in smoke:
    put(x, y, c, 'smoke')
# roof: a gable that widens row by row, shingles alternating, an overhang at the eave
for i, y in enumerate(range(ROOF, EAVE + 1)):
    l = HX + HW // 2 - 3 - i * 4
    r = HX + HW // 2 + 2 + i * 4
    l = max(l, HX - 2); r = min(r, HX + HW + 1)
    for x in range(l, r + 1):
        edge = x in (l, r)
        if y == EAVE:
            put(x, y, '▀', 'roof-edge')
        elif edge:
            put(x, y, '▄', 'roof')
        else:
            put(x, y, '▓' if (x + i) % 3 == 0 else '█', 'roof-shade' if (x + i) % 3 == 0 else 'roof')
# walls: vertical planks
for y in range(EAVE + 1, FLOOR + 1):
    for x in range(HX, HX + HW):
        put(x, y, '█', 'hut' if (x - HX) % 3 else 'hut-shade')
# window, lit, with a cross bar
WX, WY = HX + 12, EAVE + 2
for y in (WY, WY + 1, WY + 2):
    for x in range(WX, WX + 5):
        bar = x == WX + 2 or y == WY + 1
        put(x, y, '█' if not bar else '▓', 'lit' if not bar else 'lit-bar')
# door, ajar: dark with a sliver of light
for y in range(EAVE + 2, FLOOR + 1):
    for x in range(HX + 3, HX + 7):
        put(x, y, '█', 'dark')
    put(HX + 6, y, '▌', 'lit-bar')
# porch step
for x in range(HX + 2, HX + 8):
    put(x, FLOOR + 1, '▀', 'hut-shade')
# warm spill on the ground in front of the window
for y in range(FLOOR + 1, H):
    spread = (y - FLOOR) * 2
    for x in range(WX - spread, WX + 5 + spread):
        if cl[y][x] == 'near':
            d = abs(x - (WX + 2)) / (3 + spread)
            put(x, y, '▒' if d < 0.45 and y == FLOOR + 1 else '░', 'glow')

# grass tufts and a few fireflies
for x in range(0, W, 1):
    if cl[near_top][x] == 'near' and random.random() < 0.28:
        put(x, near_top, random.choice(',\'"'), 'grass')
for x, y in ((16, 21), (45, 22), (47, 20), (19, 24), (60, 27), (8, 26)):
    if cl[y][x] in ('near', 'mid', 'mid-lit', 'pine'):
        put(x, y, '·', 'firefly')

# --- emit
def emit():
    lines = []
    for y in range(H):
        out, cur, buf = [], None, ''
        row = ''.join(ch[y]).rstrip()
        for x in range(len(row)):
            k = cl[y][x] if ch[y][x] != ' ' else None
            if k != cur:
                if buf:
                    out.append(buf if cur is None else f'<span class="{cur}">{buf}</span>')
                cur, buf = k, ''
            buf += ch[y][x]
        if buf:
            out.append(buf if cur is None else f'<span class="{cur}">{buf}</span>')
        lines.append(''.join(out))
    return '\n'.join(lines)

art = emit()
if len(sys.argv) > 1 and sys.argv[1] == 'plain':
    print('\n'.join(''.join(r).rstrip() for r in ch))
else:
    sys.stdout.reconfigure(encoding='utf-8')
    print(art)
