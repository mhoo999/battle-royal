/*
 * Server-side checks for the world contract: separate arrival rooms, doors that lead
 * somewhere stable, replication between players who share a room, item pickup, combat
 * events and who receives them, and a server that ignores anything the client asserts
 * about itself.
 *
 * Items spawn at random, so the pickup and combat checks run only when the room
 * happens to offer something. They print SKIP rather than PASS when it does not.
 *
 * Uses Node's built-in WebSocket (Node 18+), so it runs with no dependencies. The
 * browser-level suite lives in two-player.spec.ts; this one isolates the server.
 *
 *   node e2e/smoke-two-sockets.mjs
 */

const BASE = process.env.BASE_URL ?? 'http://localhost:8080';
const WS = BASE.replace(/^http/, 'ws') + '/ws/game';

const GRID = 15;
const WALKABLE = new Set(['.', '+', 'b']);

/** The server drops commands that arrive mid-cooldown, so pacing has to respect it. */
const MOVE_COOLDOWN_MS = 200;

let failures = 0;

const WEAPONS = new Set(['KNIFE', 'PISTOL', 'PAN']);

function skip(label, why) {
  console.log(`SKIP  ${label}  ${why}`);
}

function check(ok, label, detail = '') {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}${detail ? '  ' + detail : ''}`);
  if (!ok) failures++;
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function openSession(nickname) {
  const response = await fetch(`${BASE}/api/session`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nickname }),
  });
  if (!response.ok) throw new Error(`session failed: ${response.status}`);
  const session = await response.json();

  const socket = new WebSocket(`${WS}?token=${encodeURIComponent(session.token)}`);
  let latest = null;
  const events = [];
  socket.addEventListener('message', (event) => {
    const message = JSON.parse(event.data);
    if (message.type === 'SNAPSHOT') latest = message;
    else events.push(message);
  });
  await new Promise((resolve, reject) => {
    socket.addEventListener('open', resolve, { once: true });
    socket.addEventListener('error', reject, { once: true });
  });

  const player = {
    session,
    socket,
    latest: () => latest,
    events,
    send: (message) => socket.send(JSON.stringify(message)),
  };
  await until(player, (s) => s.self, 'first snapshot');
  return player;
}

/** Waits for a snapshot satisfying a predicate, rather than guessing at a delay. */
async function until(player, predicate, label, timeoutMs = 3000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const snapshot = player.latest();
    if (snapshot && predicate(snapshot)) return snapshot;
    await sleep(25);
  }
  throw new Error(`timed out waiting for ${label}`);
}

// --- Navigation ----------------------------------------------------------

/** Breadth-first path over the terrain the server sent us. */
function pathTo(snapshot, target) {
  const { self, terrain } = snapshot;
  const blocked = new Set(snapshot.players.map((p) => `${p.x},${p.y}`));
  const start = `${self.x},${self.y}`;
  const goal = `${target.x},${target.y}`;

  const cameFrom = new Map([[start, null]]);
  const queue = [[self.x, self.y]];

  while (queue.length) {
    const [x, y] = queue.shift();
    const key = `${x},${y}`;
    if (key === goal) break;
    for (const [dir, dx, dy] of [['UP', 0, -1], ['DOWN', 0, 1], ['LEFT', -1, 0], ['RIGHT', 1, 0]]) {
      const nx = x + dx;
      const ny = y + dy;
      if (nx < 0 || nx >= GRID || ny < 0 || ny >= GRID) continue;
      const nextKey = `${nx},${ny}`;
      if (cameFrom.has(nextKey)) continue;
      if (!WALKABLE.has(terrain[ny][nx])) continue;
      if (blocked.has(nextKey) && nextKey !== goal) continue;
      cameFrom.set(nextKey, { from: key, dir });
      queue.push([nx, ny]);
    }
  }

  if (!cameFrom.has(goal)) return null;
  const steps = [];
  for (let at = goal; cameFrom.get(at); at = cameFrom.get(at).from) {
    steps.unshift(cameFrom.get(at).dir);
  }
  return steps;
}

/** Walks to a tile, re-planning each step because other players move too. */
async function walkTo(player, target, budget = 60) {
  for (let i = 0; i < budget; i++) {
    const snapshot = player.latest();
    if (snapshot.self.x === target.x && snapshot.self.y === target.y) return true;
    const steps = pathTo(snapshot, target);
    if (!steps || steps.length === 0) return false;
    player.send({ type: 'MOVE', dir: steps[0] });
    await sleep(MOVE_COOLDOWN_MS);
  }
  return false;
}

const DOOR_POS = {
  UP: { x: 7, y: 0 }, DOWN: { x: 7, y: 14 },
  LEFT: { x: 0, y: 7 }, RIGHT: { x: 14, y: 7 },
};

/**
 * Which wall the player is standing against. Arrivals land just inside the door they
 * came through, which is normally the facing wall but not guaranteed: if that door was
 * already spoken for the server falls back to another one.
 */
function arrivalSide(snapshot) {
  const { x, y } = snapshot.self;
  for (const [side, door] of Object.entries(DOOR_POS)) {
    if (Math.abs(door.x - x) + Math.abs(door.y - y) <= 1) return side;
  }
  return null;
}

/** Walks to the door on the given wall and steps through it. */
async function takeDoor(player, side) {
  const doorPos = DOOR_POS[side];

  const roomBefore = player.latest().roomId;
  if (!(await walkTo(player, doorPos))) return false;
  player.send({ type: 'ACTION_B' });
  try {
    await until(player, (s) => s.roomId !== roomBefore, `transit ${side}`, 1500);
    return true;
  } catch {
    return false;
  }
}

/** Walks onto an item and presses B. Resolves to the snapshot once it is in hand. */
async function pickUp(player, item) {
  if (!(await walkTo(player, item))) return null;
  player.send({ type: 'ACTION_B' });
  try {
    return await until(player, (s) => !s.items.some((i) => i.id === item.id), 'pickup', 1500);
  } catch {
    return null;
  }
}

/** Stands beside a tile and turns to face it; stepping into an occupied tile only turns. */
async function faceFromBeside(player, target) {
  for (const [dir, dx, dy] of [['RIGHT', -1, 0], ['LEFT', 1, 0], ['DOWN', 0, -1], ['UP', 0, 1]]) {
    const spot = { x: target.x + dx, y: target.y + dy };
    const row = player.latest().terrain[spot.y];
    if (!row || !WALKABLE.has(row[spot.x])) continue;
    if (await walkTo(player, spot)) {
      player.send({ type: 'MOVE', dir });
      await sleep(MOVE_COOLDOWN_MS);
      return player.latest().self.direction === dir;
    }
  }
  return false;
}

// --- Checks --------------------------------------------------------------

async function main() {
  const a = await openSession('alpha');
  const b = await openSession('bravo');
  await sleep(200);

  // Arrival: separate rooms, and nobody is dropped into a fight on spawn.
  const aStart = a.latest();
  const bStart = b.latest();
  check(aStart.roomId !== bStart.roomId, 'a fresh login starts alone',
    `${aStart.roomId} vs ${bStart.roomId}`);
  check(aStart.players.length === 0 && bStart.players.length === 0,
    'a new arrival sees nobody');

  // Items: B picks up whatever its starting room offers.
  const offered = b.latest().items[0];
  if (!offered) {
    skip('B picks up an item', 'every spawn in the starting room rolled empty');
  } else {
    const scoreBefore = b.latest().self.score;
    const after = await pickUp(b, offered);
    check(after !== null && after.self.item === offered.kind,
      'B picks up the item it stands on', `${offered.kind} ${offered.id}`);
    if (after) {
      check(after.self.score === scoreBefore + 5, 'a first pickup scores +5',
        `${scoreBefore} -> ${after.self.score}`);
    }
  }

  // Doors lead somewhere, and back again.
  const origin = a.latest().roomId;
  check(await takeDoor(a, 'RIGHT'), 'A takes the RIGHT door');
  const moved = a.latest().roomId;
  check(moved !== origin, 'the room changes', `${origin} -> ${moved}`);

  // Arriving beside a door is the contract. Which wall it is is a preference: the
  // server aims for the facing one and settles for another when that is taken, so
  // asserting the wall would fail now and then by design.
  const cameInBy = arrivalSide(a.latest());
  check(cameInBy !== null, 'A arrives beside a doorway, not adrift in the room',
    `${cameInBy} wall${cameInBy === 'LEFT' ? '' : ' (facing door was taken)'}`);
  check(await takeDoor(a, cameInBy ?? 'LEFT'), 'A walks back');
  check(a.latest().roomId === origin, 'walking back lands in the same room', origin);

  // The world is capped by population, so wandering has to run into somebody. There
  // are no coordinates to steer by; A simply tries doors.
  const target = b.latest().roomId;
  const legs = ['UP', 'RIGHT', 'DOWN', 'LEFT'];
  let transits = 0;
  while (transits < 20 && a.latest().roomId !== target) {
    if (!(await takeDoor(a, legs[transits % legs.length]))) break;
    transits++;
  }
  const met = a.latest().roomId === target;
  check(met, `A wanders into B after ${transits} door transits`,
    `${a.latest().roomId} vs ${target}`);

  if (met) {
    check(a.latest().roomId === b.latest().roomId, 'both are now in one room');
    await until(a, (s) => s.players.length === 1, 'A to see B');
    await until(b, (s) => s.players.length === 1, 'B to see A');

    const other = a.latest().players[0];
    const exposed = Object.keys(other).sort();
    check(
      JSON.stringify(exposed) === JSON.stringify(['alive', 'direction', 'id', 'x', 'y']),
      'opponent view exposes position and facing only',
      exposed.join(','),
    );
    check(a.latest().self.hp === 100, 'own hp is visible to self');

    // B moves; A's view must agree with where B actually is.
    //
    // Walking into a bush is a legitimate outcome and makes B vanish from A entirely,
    // so the check is agreement rather than "something changed": either A sees B on the
    // tile B reports, or A sees nobody and B says they are concealed.
    const before = { x: b.latest().self.x, y: b.latest().self.y };
    let moved = false;
    for (const dir of ['RIGHT', 'LEFT', 'UP', 'DOWN']) {
      b.send({ type: 'MOVE', dir });
      await sleep(MOVE_COOLDOWN_MS + 100);
      const now = b.latest().self;
      if (now.x !== before.x || now.y !== before.y) {
        moved = true;
        break;
      }
    }
    check(moved, 'B is able to move at all');

    if (moved) {
      const truth = a.latest().players[0];
      const self = b.latest().self;
      const concealed = self.concealment !== 'NONE';
      const agrees = concealed
        ? truth === undefined
        : truth !== undefined && truth.x === self.x && truth.y === self.y;
      check(agrees, "A's view of B matches where B actually is",
        concealed
          ? `B concealed in ${self.concealment}, A sees ${truth ? 'them anyway' : 'nobody'}`
          : `B at (${self.x},${self.y}), A sees ${truth ? `(${truth.x},${truth.y})` : 'nobody'}`);
    }
  }

  // Combat: A arms itself from whatever the shared room offers and strikes B.
  if (met) {
    await combat(a, b);
  }

  // Server authority: forged frames must change nothing.
  const hpBefore = a.latest().self.hp;
  const posBefore = { x: a.latest().self.x, y: a.latest().self.y };
  a.send({ type: 'MOVE', x: 999, y: 999 });
  a.send({ type: 'SET_POSITION', x: 999, y: 999 });
  a.send({ type: 'SET_HP', hp: 9999 });
  a.socket.send('not even json');
  await sleep(300);
  const now = a.latest().self;
  check(now.x === posBefore.x && now.y === posBefore.y,
    'forged coordinate frames are ignored', `(${now.x},${now.y})`);
  check(now.hp === hpBefore, 'forged hp frames are ignored');

  // An unknown token must be refused.
  const rogue = new WebSocket(`${WS}?token=not-a-real-token`);
  const refused = await new Promise((resolve) => {
    rogue.addEventListener('close', () => resolve(true), { once: true });
    rogue.addEventListener('error', () => resolve(true), { once: true });
    setTimeout(() => resolve(false), 2000);
  });
  check(refused, 'a socket with an unknown token is refused');

  a.socket.close();
  b.socket.close();
  console.log(failures === 0 ? '\nAll checks passed.' : `\n${failures} check(s) failed.`);
  process.exit(failures === 0 ? 0 : 1);
}

async function combat(a, b) {
  const label = 'A strikes B';
  let armed = WEAPONS.has(a.latest().self.item);
  if (!armed) {
    const weapon = a.latest().items.find((i) => WEAPONS.has(i.kind));
    if (!weapon) {
      skip(label, 'no weapon in the room they met in');
      return;
    }
    armed = (await pickUp(a, weapon)) !== null && WEAPONS.has(a.latest().self.item);
    if (!armed) {
      const self = a.latest().self;
      skip(label, `could not take the ${weapon.kind} at (${weapon.x},${weapon.y}); ` +
        `A at (${self.x},${self.y}), B offers ${self.actionB}`);
      return;
    }
  }

  const bAt = { x: b.latest().self.x, y: b.latest().self.y };
  if (!(await faceFromBeside(a, bAt))) {
    skip(label, `could not get beside B at (${bAt.x},${bAt.y})`);
    return;
  }

  const weapon = a.latest().self.item;
  const hpBefore = b.latest().self.hp;
  a.events.length = 0;
  b.events.length = 0;
  a.send({ type: 'ACTION_A' });
  try {
    await until(b, (s) => s.self.hp < hpBefore, 'B to lose hp', 1500);
  } catch {
    check(false, `${label} with ${weapon}`, `B hp stayed ${hpBefore}`);
    return;
  }
  await sleep(100);
  check(true, `${label} with ${weapon}`, `hp ${hpBefore} -> ${b.latest().self.hp}`);

  const hit = a.events.find((e) => e.event === 'HIT');
  check(hit !== undefined, 'the attacker is told it hit');
  if (hit) {
    check(JSON.stringify(Object.keys(hit).sort()) === JSON.stringify(['event', 'type']),
      'HIT carries nothing but the fact', JSON.stringify(hit));
  }
  check(!b.events.some((e) => e.event === 'HIT'), 'the victim is not sent the HIT');

  const aShot = a.events.some((e) => e.event === 'SHOT');
  const bShot = b.events.find((e) => e.event === 'SHOT');
  if (weapon === 'PISTOL') {
    check(aShot && bShot !== undefined, 'a pistol shot is shown to the whole room');
    if (bShot) {
      const [sx, sy] = bShot.path[0];
      const self = a.latest().self;
      check(sx === self.x && sy === self.y, 'the shot path starts at the shooter');
    }
  } else {
    check(!aShot && bShot === undefined, `a ${weapon} blow leaves no shot trail`);
    const bSwing = b.events.find((e) => e.event === 'SWING');
    check(a.events.some((e) => e.event === 'SWING') && bSwing !== undefined,
      `a ${weapon} swing is shown to the whole room`);
    if (bSwing) {
      const self = a.latest().self;
      check(bSwing.from[0] === self.x && bSwing.from[1] === self.y
          && bSwing.to[0] === bAt.x && bSwing.to[1] === bAt.y,
        'the swing runs from the attacker to the tile struck', JSON.stringify(bSwing));
    }
  }
}

main().catch((e) => {
  console.error('FAIL  ' + e.message);
  process.exit(1);
});
