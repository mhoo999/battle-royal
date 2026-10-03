"""
The hideout picture, drawn as ASCII art: a big moon rising behind the island's woods,
and in front of it, black against the light, the ruin someone is hiding in, a tarp
strung from its broken wall, pines on either side, barbed wire along the ground, a
student with the issued day pack standing at the edge of it.

Silhouette against the moon, in the manner of the boldest ASCII landscapes: the moon is
shaded with a density ramp of characters, and whatever stands in front of it is cut out
of it as black, outlined where it leaves the moon for the dark. One colour, five
brightness tiers, t1 darkest, t5 the moon's face.

Run from a scratch directory: python tools/hideout-art.py
Writes hideout-art.txt (the <pre> body for #hideout .hideout-art in index.html) and
prints the picture as plain text.
"""
import math
import random
import sys

W, H = 64, 36
BS = chr(92)  # a backslash
rng = random.Random(7)
ch = [[' '] * W for _ in range(H)]
tier = [[0] * W for _ in range(H)]
solid = [[False] * W for _ in range(H)]       # stands in front of the moon


def put(x, y, c, t):
    if 0 <= x < W and 0 <= y < H:
        ch[y][x] = c
        tier[y][x] = t if c != ' ' else 0


# --- the moon: big, low, shaded by a density ramp, craters lighter
RAMP = ' .:-=+*#%@'
MX, MY, MR = 31, 19, 11.5
ASPECT = 1.82                      # columns per row for a round shape
CRATERS = [(-4.5, -3.5, 2.6), (3.5, 2.0, 2.2), (-1.0, 5.0, 1.6), (6.0, -4.5, 1.4),
           (-7.0, 2.5, 1.5), (1.5, -6.5, 1.2)]


def moon_at(x, y):
    dx, dy = (x - MX) / ASPECT, y - MY
    d = math.hypot(dx, dy)
    if d > MR:
        return None
    b = 1.0 - 0.3 * (d / MR) ** 4
    for cx, cy, r in CRATERS:
        k = math.hypot(dx - cx, dy - cy) / r
        if k < 1:
            b -= 0.45 * (1 - k * k)
    b += rng.uniform(-0.04, 0.04)
    i = max(1, min(len(RAMP) - 1, round(b * (len(RAMP) - 1))))
    return RAMP[i], 5 if i >= 7 else 4 if i >= 4 else 3


# --- stars, kept off the moon
for _ in range(34):
    x, y = rng.randrange(W), rng.randrange(0, 20)
    if math.hypot((x - MX) / ASPECT, y - MY) < MR + 2:
        continue
    c = rng.choice('.....*+')
    put(x, y, c, 4 if c in '*+' else 2)
for x, y in ((6, 3), (57, 6)):
    for dx, dy, c in ((0, -1, '.'), (-1, 0, '-'), (0, 0, '*'), (1, 0, '-'), (0, 1, "'")):
        put(x + dx, y + dy, c, 4)

# --- what stands in front of the moon, as shapes: '#' solid, anything else a mark
edges = {}                          # (x, y) -> (char, tier): outlines and details


def shape(x0, y0, rows, t):
    for dy, row in enumerate(rows):
        for dx, c in enumerate(row):
            x, y = x0 + dx, y0 + dy
            if not (0 <= x < W and 0 <= y < H) or c == ' ':
                continue
            solid[y][x] = True
            if c == '#':
                edges.pop((x, y), None)     # in front of whatever was drawn before
            else:
                edges[(x, y)] = (c, t)


def pine(cx, base, tiers, t):
    """Tiers of boughs, each flaring at its foot; solid, its edges drawn."""
    rows, w = [], 0
    for k in range(tiers):
        w = max(0, w - 2)
        for r in range(3):
            if r < 2:
                rows.append('/' + '#' * w + BS)
                w += 2
            else:
                rows.append('/_' + '#' * max(0, w - 2) + '_' + BS)
    rows.append(' ' * (len(rows[-1]) // 2 - 1) + '||')
    for i, row in enumerate(rows):
        shape(cx - len(row) // 2 + 1, base - len(rows) + 1 + i, [row], t)


# the treeline along the far side of the clearing, small and dense
for cx in range(-2, W + 3, 4):
    pine(cx + rng.randint(-1, 1), 31, rng.choice((1, 2, 2, 3)), 2)

# the ruin: a broken two-storey wall whose window holes let the moon through, a stub
# of another wall, a tarp strung between them over where someone sleeps. A space
# inside the walls is a hole; '#' is wall.
RUIN = [
    r"      ,                                 ",
    r"     /|                                 ",
    r"    |#|  _                              ",
    r"   |##|_|#|  ,                          ",
    r"   |#########|                          ",
    r"   |##.--.###|                          ",
    r"   |##|  |###|                          ",
    r"   |##'--'###|           ,_             ",
    r"   |#########|           |#|_           ",
    r"   |#########|~-._       |###|          ",
    r"   |##.--.###|####`~-._  |###|-._       ",
    r"   |##|  |###|#########`-|###|###`-._   ",
    r" ,.|##|  |###|_.:,#######|###|_.,####`\ ",
]
RX, RY = 13, H - 4 - len(RUIN) + 1
shape(RX, RY, RUIN, 4)

# the big pines framing it, near and dark
pine(4, 32, 7, 3)
pine(58, 32, 8, 3)

# the ground: everything below is solid, a ragged edge of grass on top
GROUND = H - 4
for y in range(GROUND, H):
    for x in range(W):
        solid[y][x] = True
for x in range(W):
    edges[(x, GROUND)] = (rng.choice("_,'\"_.^_"), 2)

# --- paint: moon first, then cut out what stands before it, then the marks
for y in range(H):
    for x in range(W):
        m = moon_at(x, y)
        if m and not solid[y][x]:
            put(x, y, *m)
        elif solid[y][x]:
            put(x, y, ' ', 0)
for (x, y), (c, t) in edges.items():
    put(x, y, c, t)

# the student at the foot of the trail, back to us, the day pack on
for dy, row in enumerate([" _ ", "(_)", "[#]", "/ " + BS]):
    for dx, c in enumerate(row):
        if c != ' ':
            put(44 + dx, GROUND - 3 + dy, c, 5)

# barbed wire along the ground, on posts, cut and sagging where the trail goes in
WY = GROUND + 2
for x in range(W):
    if 42 <= x <= 48:
        continue
    put(x, WY, 'x' if x % 3 == 0 else '-', 3)
put(41, WY + 1, BS, 3)
put(49, WY + 1, '/', 3)
for x in range(3, W, 9):
    if 39 <= x <= 51:
        continue
    put(x, WY - 1, '+', 3)
    put(x, WY, '|', 3)
    put(x, WY + 1, '|', 3)


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
