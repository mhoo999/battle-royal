"""
The hideout picture, drawn as ASCII art: a hut on a hill under a full moon, a student
with the issued day pack walking the path up to it.

Line art in the classic manner: mountains at 45 degrees as / and \\, pines as stacked
boughs, the hut, smoke and path drawn from hand-made pieces, stars as . * +, and a lot
of black left alone. Only the moon is shaded, with a density ramp of characters. One
colour; depth is five brightness tiers, t1 darkest, t5 the moon and the lit window.

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
near = range_([(4, 32), (17, 31), (28, 34), (40, 32), (56, 30)], 3, clear_below=40)


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
                put(x, y0 + i, "'", t - 1)
    stamp(cx, base, ['||'], t, solid=True)


pine(3, 54, 6, 3)                   # the tall pair framing the picture
pine(60, 52, 7, 3)

# --- the hut on its hill
HUT = [
    r"            ___[]___           ",
    r"         .-'   ||   '-.        ",
    r"      .-' /  /  /  /  \'-.     ",
    r"   .-'   /  /  /  /  /  \ '-.  ",
    r"  '--------------------------' ",
    r"   |  _____        ______   |  ",
    r"   | |     |      |######|  |  ",
    r"   | |     |      |######|  |  ",
    r"   | |   o |      '------'  |  ",
    r"___|_|_____|________________|__",
]
HX, HY = 16, 36
stamp(HX, HY, HUT, 4, solid=True)
for y, row in enumerate(HUT):
    for x, c in enumerate(row):
        if c == '#':
            put(HX + x, HY + y, '#', 5)
GROUND = HY + len(HUT) - 1
for x in range(0, W):
    if ch[GROUND][x] == ' ':
        put(x, GROUND, '_' if 8 < x < 56 else '.', 2)
stamp(HX + 13, HY - 6, [r"  (  )", r" (   )", r"   ( )", r"    ()", r"    ()"], 2)

# --- the path down from the door, widening toward us
DOOR = HX + 6
for i, y in enumerate(range(GROUND + 1, H)):
    put(DOOR - i, y, '/', 3)
    put(DOOR + 5 + i, y, '\\', 3)
    for x in range(DOOR - i + 1, DOOR + 5 + i):
        if rng.random() < 0.05:
            put(x, y, rng.choice(".'"), 2)

# --- the student, back to us, the day pack on, walking up
stamp(DOOR + 1, GROUND + 4, [" _ ", "(_)", "[#]", "/ \\"], 4, solid=True)

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
