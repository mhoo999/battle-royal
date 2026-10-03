"""
The hideout picture, drawn as ASCII art: a ruin in the island's woods under a full
moon, a tarp strung from its broken wall over a bedroll, barbed wire across the foreground, a student
with the issued day pack walking up the path to spend the night there.

Line art in the classic manner: mountains at 45 degrees as / and \\, pines as stacked
boughs, the ruin, tarp, wire and path drawn from hand-made pieces, stars as . * +, and a lot
of black left alone. Only the moon is shaded, with a density ramp of characters. One
colour; depth is five brightness tiers, t1 darkest, t5 the moon, the only light.

Run from a scratch directory: python tools/hideout-art.py
Writes hideout-art.txt (the <pre> body for #hideout .hideout-art in index.html) and
prints the picture as plain text.
"""
import math
import random
import sys

W, H = 64, 78
rng = random.Random(4)
ch = [[' '] * W for _ in range(H)]
tier = [[0] * W for _ in range(H)]


def put(x, y, c, t):
    if 0 <= x < W and 0 <= y < H:
        ch[y][x] = c
        tier[y][x] = t if c != ' ' else 0


def stamp(x, y, lines, t, solid=False):
    """Text at (x, y). Spaces leave what is underneath unless solid, which clears the
    span between each line's first and last mark, so a shape hides what is behind it."""
    for dy, line in enumerate(lines):
        marks = [i for i, c in enumerate(line) if c != ' ']
        if not marks:
            continue
        for dx in range(marks[0], marks[-1] + 1):
            c = line[dx]
            if c != ' ' or solid:
                put(x + dx, y + dy, c if c != '`' or True else c, t)


# --- stars, kept off the moon
MX, MY, MR = 42, 16, 6.0
ASPECT = 1.82                      # columns per row for a round shape
for _ in range(38):
    x, y = rng.randrange(W), rng.randrange(0, 28)
    if math.hypot((x - MX) / ASPECT, y - MY) < MR + 2.5:
        continue
    c = rng.choice('......*+')
    put(x, y, c, 4 if c in '*+' else 3)
for x, y in ((8, 4), (24, 11), (5, 17)):
    stamp(x - 1, y - 1, [' . ', '-*-', " ' "], 4)

# --- the moon: a disc shaded by a density ramp, craters lighter
RAMP = ' .:-=+*#%@'
CRATERS = [(-2.6, -1.8, 1.7), (2.0, 1.4, 1.5), (-0.6, 3.2, 1.1), (3.4, -2.6, 0.9)]
for y in range(H):
    for x in range(W):
        dx, dy = (x - MX) / ASPECT, y - MY
        d = math.hypot(dx, dy)
        if d > MR:
            continue
        b = 1.0 - 0.35 * (d / MR) ** 4
        for cx, cy, r in CRATERS:
            k = math.hypot(dx - cx, dy - cy) / r
            if k < 1:
                b -= 0.42 * (1 - k * k)
        i = max(1, min(len(RAMP) - 1, round(b * (len(RAMP) - 1))))
        put(x, y, RAMP[i], 5 if i >= 6 else 4)
for a in range(0, 360, 9):           # a broken ring of light round it
    r = MR + 1.3
    x = round(MX + math.cos(math.radians(a)) * r * ASPECT)
    y = round(MY + math.sin(math.radians(a)) * r)
    if rng.random() < 0.5 and ch[y][x] == ' ':
        put(x, y, '.', 2)


# --- mountains: every slope 45 degrees, peaks /\, snow under the high ones
def range_(peaks, t, snow=0, clear_below=0):
    top = {}
    for x in range(W):
        best = None
        for px, py in peaks:
            if x <= px:
                y, c = py + (px - x), '/'
            else:
                y, c = py + (x - px - 1), '\\'
            if best is None or y < best[0]:
                best = (y, c)
        top[x] = best
    for x in range(W):
        y, c = top[x]
        for yy in range(y, min(H, y + clear_below + 1)):
            put(x, yy, ' ', 0)
        put(x, y, c, t)
    for px, py in peaks:
        for k in range(1, snow + 1):
            for x in range(px - k + 1, px + k + 1):
                if top[x][0] < py + k and rng.random() < 0.8:
                    put(x, py + k, ':' if (x + k) % 2 else '.', t + 2)
    return top


far = range_([(9, 21), (20, 26), (31, 25), (59, 22)], 2, snow=3, clear_below=30)
near = range_([(4, 32), (17, 31), (28, 34), (40, 32), (56, 30)], 2, clear_below=40)


# --- pines: tiers of boughs, each flaring out at its foot, needles as dots inside
BS = chr(92)  # a backslash


def pine(cx, base, tiers, t):
    rows, w = [], 0
    for k in range(tiers):
        w = max(0, w - 2)
        for r in range(3):
            if r < 2:
                rows.append('/' + ' ' * w + BS)
                w += 2
            else:
                rows.append('/_' + ' ' * max(0, w - 2) + '_' + BS)
    y0 = base - len(rows)
    for i, row in enumerate(rows):
        x0 = cx - len(row) // 2 + 1
        stamp(x0, y0 + i, [row], t, solid=True)
        for x in range(x0 + 2, x0 + len(row) - 2):
            if (x * 7 + i * 3) % 5 == 0:
                put(x, y0 + i, "'", max(1, t - 1))
    stamp(cx, base, ['||'], t, solid=True)



# --- the ruin: what is left of a concrete house, a tarp strung from its broken wall
# to a stub of another, a bedroll and a pack under it, in the woods where it is hard
# to see. Somewhere to hide for a night.
RUIN = [
    r"     _    ,                           ",
    r"    | |  /|_                          ",
    r"    | |_/   |,                        ",
    r"    |       | \                       ",
    r"    |  .--. |                         ",
    r"    |  |  | |         _               ",
    r"    |  '--' |--..__  | |_             ",
    r"    |       |      ``|   |            ",
    r"    |  .--. |~~--..__|   |`-._        ",
    r"    |  |  | |        |   |    `-._    ",
    r"    |  |  | |  _____ |   |  [#]   `\  ",
    r" .,;|__|__|_|_(_____)|___|_________\;.",
]
HX, HY = 10, 34
# a treeline behind it, so it stands in the woods, not out on open ground
for cx in range(1, W, 5):
    pine(cx + rng.randint(-1, 1), HY + len(RUIN) - 2, rng.choice((2, 3, 3, 4)), 1)
stamp(HX, HY, RUIN, 4, solid=True)
for y, row in enumerate(RUIN):        # the tarp and what lies under it, a shade dimmer
    for x, c in enumerate(row):
        if c in '~`-._' and y >= 6 and HX + x > HX + 12:
            put(HX + x, HY + y, c, 3)
GROUND = HY + len(RUIN) - 1
for x in range(0, W):
    if ch[GROUND][x] == ' ':
        put(x, GROUND, '_' if 8 < x < 56 else '.', 2)
for x, y, c in ((7, GROUND, ':'), (8, GROUND - 1, '.'), (49, GROUND, ';'), (50, GROUND - 1, ','),
                (51, GROUND, '.'), (6, GROUND - 1, ',')):          # rubble at the feet of it
    put(x, y, c, 3)

DOOR = HX + 15                      # the gap under the tarp

# --- a trail through the undergrowth, winding up to the ruin, footprints of earlier nights
TRAIL = {}
for i, y in enumerate(range(GROUND + 1, H)):
    mid = DOOR + 2 + 3 * math.sin(i / 3.5)
    half = 1 + i // 5
    TRAIL[y] = (round(mid - half), round(mid + half))
    for x in range(TRAIL[y][0], TRAIL[y][1] + 1):
        put(x, y, ' ', 0)
    put(TRAIL[y][0] - 1, y, ',' if i % 2 else '.', 2)
    put(TRAIL[y][1] + 1, y, '.' if i % 2 else ',', 2)
    if i % 3 == 1:
        put(round(mid) + (1 if i % 2 else -1), y, "'", 3)

# --- the student, back to us, the issued day pack on, walking up
stamp(DOOR + 1, GROUND + 1, [" _ ", "(_)", "[#]", "/ " + BS], 4, solid=True)

# --- the woods it hides in: pines in front, half over its walls, and the tall pair
pine(11, GROUND + 4, 4, 3)
pine(51, GROUND + 5, 5, 3)
pine(2, GROUND + 12, 7, 3)
pine(61, GROUND + 10, 7, 3)
for x in range(W):                     # undergrowth at the foot of the trees
    for y in range(GROUND - 1, GROUND + 9):
        if ch[y][x] == ' ' and not (TRAIL.get(y, (0, -1))[0] - 1 <= x <= TRAIL.get(y, (0, -1))[1] + 1):
            if rng.random() < 0.06:
                put(x, y, rng.choice("^v\"'"), 2)

# --- barbed wire across the foreground, cut where the trail goes through
FY = GROUND + 8
for y in (FY, FY + 2):
    gap = TRAIL[y]
    for x in range(W):
        if gap[0] - 2 <= x <= gap[1] + 2:
            continue
        put(x, y, 'x' if x % 3 == 0 else '-', 3)
    put(gap[0] - 2, y + 1, BS, 3)           # the cut ends hang down
    put(gap[1] + 2, y + 1, '/', 3)
for x in range(6, W, 9):
    if TRAIL[FY][0] - 3 <= x <= TRAIL[FY][1] + 3:
        continue
    for y in range(FY - 1, FY + 4):
        put(x, y, '|' if y > FY - 1 else '+', 3)

# --- grass, sparse
for _ in range(80):
    x, y = rng.randrange(W), rng.randrange(GROUND + 1, H)
    if ch[y][x] == ' ':
        put(x, y, rng.choice(",'\"`"), 2 if y < GROUND + 12 else 1)


# --- emit
def escape(s):
    return s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')


def emit():
    out = []
    for y in range(H):
        row = list(zip(ch[y], tier[y]))
        while row and row[-1][0] == ' ':
            row.pop()
        s, cur, buf = '', None, ''
        for c, t in row:
            t = t if c != ' ' else None
            if t != cur:
                if buf:
                    s += buf if cur is None else f'<i class="t{cur}">{escape(buf)}</i>'
                cur, buf = t, ''
            buf += c
        if buf:
            s += buf if cur is None else f'<i class="t{cur}">{escape(buf)}</i>'
        out.append(s)
    return '\n'.join(out)


open('hideout-art.txt', 'w', encoding='utf-8').write(emit())
sys.stdout.reconfigure(encoding='utf-8')
print('\n'.join(''.join(r).rstrip() for r in ch))
