/*
 * Load-test bots (docs/ROADMAP.md S2): N signed-in players who live the whole loop —
 * hideout, out to the island, crates, fights, an exit or a death, back to the hideout
 * to hand in errands and sell — and report what the server felt like meanwhile.
 *
 * Needs a server with the loadtest profile, which has a bot sign-in and nothing else
 * that production lacks:
 *
 *   ./gradlew bootRun --args='--spring.profiles.active=loadtest'
 *   BOTS=20 SECONDS=120 node e2e/bots.mjs
 *
 * Production has no bot sign-in, so this cannot run against it by mistake.
 *
 * What it measures, from the bots' side:
 *   move→snapshot  ms from sending MOVE to the snapshot that shows the step. Includes a
 *                  wait for the next 50ms tick, so ~25ms average is an idle server.
 *   http           ms per hideout call (each ends in database work)
 *   trips          how trips ended: extracted, died, timeout, error
 *
 * Settings (environment): BASE_URL, BOTS (10), SECONDS (120), PREFIX (bot), REPORT_S (10).
 * Bots are named <PREFIX>-<n> and keep their accounts between runs.
 */

const BASE = process.env.BASE_URL ?? 'http://localhost:8080';
const WS = BASE.replace(/^http/, 'ws') + '/ws/game';
const BOTS = Number(process.env.BOTS ?? 10);
const SECONDS = Number(process.env.SECONDS ?? 120);
const PREFIX = process.env.PREFIX ?? 'bot';
const REPORT_S = Number(process.env.REPORT_S ?? 10);

const GRID = 15;
const WALKABLE = new Set(['.', '+', 'b']);
// Above the server's 150ms move cooldown with room to spare, so a timed step is never
// held back by the cooldown and the measurement is the tick and the network alone.
const MOVE_COOLDOWN_MS = 200;
const TRIP_TIMEOUT_MS = 180_000;
const DOOR_POS = {
  UP: { x: 7, y: 0 }, DOWN: { x: 7, y: 14 },
  LEFT: { x: 0, y: 7 }, RIGHT: { x: 14, y: 7 },
};
const STEP = { UP: [0, -1], DOWN: [0, 1], LEFT: [-1, 0], RIGHT: [1, 0] };
const JUNK = new Set(['SPOON', 'CUP', 'DOLL', 'RECORDER', 'REGISTER', 'PAN']);
const WEAPON_RANK = ['PISTOL', 'CROSSBOW', 'BAT', 'KNIFE'];

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const deadline = Date.now() + SECONDS * 1000;

// --- Measurements ----------------------------------------------------------------

const samples = { move: [], http: new Map() };
const counts = { extracted: 0, died: 0, timeout: 0, error: 0, crates: 0, delivered: 0,
  sold: 0, httpErrors: 0, socketErrors: 0 };
let liveBots = 0;
let onIsland = 0;

function record(list, value) {
  list.push(value);
}

function pct(list, p) {
  if (list.length === 0) return '-';
  const sorted = [...list].sort((a, b) => a - b);
  return Math.round(sorted[Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length))]);
}

function report(final = false) {
  const elapsed = Math.round((Date.now() - (deadline - SECONDS * 1000)) / 1000);
  const m = samples.move;
  console.log(`${final ? '== final' : '--'} ${elapsed}s  bots ${liveBots} (island ${onIsland})`
    + `  trips ext ${counts.extracted} died ${counts.died} timeout ${counts.timeout}`
    + ` err ${counts.error}  crates ${counts.crates} delivered ${counts.delivered}`
    + ` sold ${counts.sold}`);
  console.log(`   move→snapshot ms  n ${m.length}  p50 ${pct(m, 50)}  p95 ${pct(m, 95)}`
    + `  p99 ${pct(m, 99)}  max ${pct(m, 100)}`
    + `   errors http ${counts.httpErrors} socket ${counts.socketErrors}`);
  for (const [path, list] of samples.http) {
    console.log(`   http ${path.padEnd(22)} n ${String(list.length).padStart(4)}`
      + `  p50 ${pct(list, 50)}  p95 ${pct(list, 95)}`);
  }
  if (final) {
    // One line to paste into a results table.
    console.log(JSON.stringify({ bots: BOTS, seconds: SECONDS, moveP50: pct(m, 50),
      moveP95: pct(m, 95), moveP99: pct(m, 99), moves: m.length, ...counts }));
  }
}

// --- HTTP as one signed-in browser ------------------------------------------------

class Browser {
  constructor() {
    this.cookie = '';
  }

  async call(method, path, body) {
    const started = performance.now();
    const response = await fetch(BASE + path, {
      method,
      headers: { 'Content-Type': 'application/json', ...(this.cookie && { Cookie: this.cookie }) },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const key = path.replace(/\/\d+\//, '/{n}/').replace(/login\/.*/, 'login');
    if (!samples.http.has(key)) samples.http.set(key, []);
    record(samples.http.get(key), performance.now() - started);
    for (const set of response.headers.getSetCookie()) {
      if (set.startsWith('JSESSIONID=')) this.cookie = set.split(';')[0];
    }
    const text = await response.text();
    if (!response.ok) {
      counts.httpErrors++;
      const error = new Error(`${method} ${path} ${response.status} ${text}`);
      error.status = response.status;
      throw error;
    }
    return text ? JSON.parse(text) : null;
  }
}

// --- On the island -----------------------------------------------------------------

function openSocket(token) {
  return new Promise((resolve, reject) => {
    const socket = new WebSocket(`${WS}?token=${encodeURIComponent(token)}`);
    const state = { latest: null, events: [], closed: false, socket, watch: null };
    socket.addEventListener('message', (event) => {
      const message = JSON.parse(event.data);
      if (message.type === 'SNAPSHOT') {
        state.latest = message;
        // Timed on arrival, not on the next poll: timers are coarse on some systems.
        if (state.watch && state.watch.test(message)) state.watch.done(performance.now());
      } else {
        state.events.push(message);
      }
    });
    socket.addEventListener('close', () => { state.closed = true; });
    socket.addEventListener('error', () => { counts.socketErrors++; });
    socket.addEventListener('open', () => resolve(state), { once: true });
    setTimeout(() => reject(new Error('socket did not open')), 5000);
  });
}

function send(state, message) {
  state.lastCommand = performance.now();
  if (state.socket.readyState === WebSocket.OPEN) state.socket.send(JSON.stringify(message));
}

async function until(state, predicate, timeoutMs) {
  const end = Date.now() + timeoutMs;
  while (Date.now() < end) {
    if (state.latest && predicate(state.latest)) return state.latest;
    if (tripOver(state)) return null;
    await sleep(15);
  }
  return null;
}

function tripOver(state) {
  return state.closed || state.events.some((e) =>
    e.type === 'YOU_DIED' || e.type === 'EXTRACTED' || e.type === 'SEASON_OVER');
}

function others(s) {
  return [...s.players.filter((p) => p.alive), ...(s.guards || [])];
}

function pathTo(s, target) {
  const blocked = new Set(others(s).map((p) => `${p.x},${p.y}`));
  const start = `${s.self.x},${s.self.y}`;
  const goal = `${target.x},${target.y}`;
  const cameFrom = new Map([[start, null]]);
  const queue = [[s.self.x, s.self.y]];
  while (queue.length) {
    const [x, y] = queue.shift();
    if (`${x},${y}` === goal) break;
    for (const [dir, [dx, dy]] of Object.entries(STEP)) {
      const nx = x + dx;
      const ny = y + dy;
      const key = `${nx},${ny}`;
      if (nx < 0 || nx >= GRID || ny < 0 || ny >= GRID || cameFrom.has(key)) continue;
      if (!WALKABLE.has(s.terrain[ny][nx])) continue;
      if (blocked.has(key) && key !== goal) continue;
      cameFrom.set(key, { from: `${x},${y}`, dir });
      queue.push([nx, ny]);
    }
  }
  if (!cameFrom.has(goal)) return null;
  const steps = [];
  for (let at = goal; cameFrom.get(at); at = cameFrom.get(at).from) steps.unshift(cameFrom.get(at).dir);
  return steps;
}

/** One step, timed from the command to the snapshot that shows it. */
async function step(state, dir) {
  // Taking a door counts as a move, so the step after one would wait out the cooldown
  // and time the rule rather than the server. Let any earlier command's cooldown pass.
  const since = performance.now() - (state.lastCommand ?? 0);
  if (since < MOVE_COOLDOWN_MS) await sleep(MOVE_COOLDOWN_MS - since);
  const before = state.latest;
  const sent = performance.now();
  const shown = new Promise((resolve) => {
    state.watch = {
      test: (s) => s.self.x !== before.self.x || s.self.y !== before.self.y
        || s.self.direction !== before.self.direction || s.roomId !== before.roomId,
      done: resolve,
    };
    setTimeout(() => resolve(null), 1000);
  });
  send(state, { type: 'MOVE', dir });
  const at = await shown;
  state.watch = null;
  if (at !== null) record(samples.move, at - sent);
  const wait = MOVE_COOLDOWN_MS - (performance.now() - sent);
  if (wait > 0) await sleep(wait);
  return at !== null;
}

async function walkTo(state, target, budget = 60) {
  for (let i = 0; i < budget && !tripOver(state); i++) {
    const s = state.latest;
    if (s.self.x === target.x && s.self.y === target.y) return true;
    if (await fightIfCornered(state)) continue;
    const steps = pathTo(s, target);
    if (!steps || steps.length === 0) return false;
    await step(state, steps[0]);
  }
  return false;
}

/** Someone next to us: turn to them (a step into an occupied tile only turns) and swing. */
async function fightIfCornered(state) {
  const s = state.latest;
  const near = others(s).find((p) => Math.abs(p.x - s.self.x) + Math.abs(p.y - s.self.y) === 1);
  if (!near || !['ATTACK', 'FIRE'].includes(s.self.actionA)) return false;
  const dir = near.x > s.self.x ? 'RIGHT' : near.x < s.self.x ? 'LEFT' : near.y > s.self.y ? 'DOWN' : 'UP';
  if (s.self.direction !== dir) await step(state, dir);
  send(state, { type: 'ACTION_A' });
  await sleep(250);
  return true;
}

async function lootRoom(state) {
  const s = state.latest;
  const free = () => state.latest.self.inventory.findIndex((slot) => slot === null);
  if (s.items.length === 0 || free() < 0) return;
  const crate = s.items[Math.floor(Math.random() * s.items.length)];
  if (!(await walkTo(state, crate))) return;
  send(state, { type: 'ACTION_B' });
  const open = await until(state, (x) => x.self.crate !== null, 6000);
  send(state, { type: 'RELEASE_B' });
  if (!open) return;
  counts.crates++;
  for (let index = open.self.crate.length - 1; index >= 0; index--) {
    const slot = free();
    if (slot < 0) break;
    send(state, { type: 'TAKE', index, slot });
    await sleep(120);
  }
  send(state, { type: 'CLOSE' });
  await sleep(100);
}

function doorsOf(s) {
  return Object.entries(DOOR_POS).filter(([, p]) => s.terrain[p.y][p.x] === '+').map(([side]) => side);
}

async function takeDoor(state, side) {
  const room = state.latest.roomId;
  if (!(await walkTo(state, DOOR_POS[side]))) return false;
  send(state, { type: 'ACTION_B' });
  return (await until(state, (s) => s.roomId !== room, 2000)) !== null;
}

async function headForExit(state) {
  for (let doors = 0; doors < 10 && !tripOver(state); doors++) {
    const exit = [...state.latest.self.exits]
      .sort((p, q) => (Math.abs(p.dx) + Math.abs(p.dy)) - (Math.abs(q.dx) + Math.abs(q.dy)))[0];
    if (exit.dx === 0 && exit.dy === 0 && exit.x !== null) {
      if (!(await walkTo(state, exit))) return;
      send(state, { type: 'ACTION_B' });
      await until(state, () => false, 7000); // returns when EXTRACTED ends the trip
      return;
    }
    const side = exit.dx > 0 ? 'RIGHT' : exit.dx < 0 ? 'LEFT' : exit.dy > 0 ? 'DOWN' : 'UP';
    if (!(await takeDoor(state, side))) await takeDoor(state, randomOf(doorsOf(state.latest)));
  }
}

function randomOf(list) {
  return list[Math.floor(Math.random() * list.length)];
}

/** One trip: a few rooms of looting and fighting, then the nearest exit. */
async function trip(token) {
  const state = await openSocket(token);
  onIsland++;
  try {
    if (!(await until(state, (s) => s.self, 5000))) return 'error';
    const end = Date.now() + TRIP_TIMEOUT_MS;
    const rooms = 2 + Math.floor(Math.random() * 3);
    for (let i = 0; i < rooms && !tripOver(state) && Date.now() < end; i++) {
      await lootRoom(state);
      const doors = doorsOf(state.latest);
      if (doors.length) await takeDoor(state, randomOf(doors));
    }
    if (!tripOver(state) && Date.now() < end) await headForExit(state);
    if (state.events.some((e) => e.type === 'YOU_DIED')) return 'died';
    if (state.events.some((e) => e.type === 'EXTRACTED')) return 'extracted';
    return 'timeout';
  } finally {
    onIsland--;
    state.socket.close();
  }
}

// --- At the hideout ------------------------------------------------------------------

async function tidyUp(browser, view) {
  for (const quest of view.quests) {
    if (quest.category === 'DELIVERY' && quest.ready) {
      view = await browser.call('POST', '/api/hideout/quest/deliver', {});
      counts.delivered++;
    }
  }
  for (const daily of view.dailies) {
    if (daily.ready) {
      view = await browser.call('POST', `/api/hideout/daily/${daily.slot}/deliver`, {});
      counts.delivered++;
    }
  }
  // Keep the stash from running over: sell junk once it is more than half full.
  const junk = view.stash.filter((e) => JUNK.has(e.kind));
  for (const entry of junk.slice(0, Math.max(0, view.stash.length - view.capacity / 2))) {
    view = await browser.call('POST', '/api/hideout/sell', { itemId: entry.id });
    counts.sold++;
  }
  return view;
}

function loadoutFrom(view) {
  for (const kind of WEAPON_RANK) {
    const entry = view.stash.find((e) => e.kind === kind && (e.ammo === null || e.ammo > 0));
    if (entry) return [entry.id];
  }
  return [];
}

async function bot(n) {
  const name = `${PREFIX}-${String(n).padStart(3, '0')}`.slice(0, 12);
  const browser = new Browser();
  await browser.call('POST', `/api/loadtest/login/${name}`);
  const me = await browser.call('GET', '/api/me');
  if (!me.nickname) await browser.call('POST', '/api/me/nickname', { nickname: name });
  liveBots++;
  try {
    while (Date.now() < deadline) {
      try {
        let view = await browser.call('GET', '/api/hideout');
        if (view.out) {
          await sleep(2000); // a trip from an earlier run is still being settled
          continue;
        }
        view = await tidyUp(browser, view);
        const sortie = await browser.call('POST', '/api/hideout/sortie', { loadout: loadoutFrom(view) });
        counts[await trip(sortie.token)]++;
      } catch (error) {
        counts.error++;
        if (counts.error <= 5) console.log(`   ${name}: ${error.message.slice(0, 160)}`);
        await sleep(1000);
      }
    }
  } finally {
    liveBots--;
  }
}

// --- Run -------------------------------------------------------------------------------

console.log(`${BOTS} bots for ${SECONDS}s against ${BASE}`);
const ticker = setInterval(() => report(), REPORT_S * 1000);
// Stagger the arrivals over a few seconds, as people would.
await Promise.all(Array.from({ length: BOTS }, (_, i) =>
  sleep(i * 200).then(() => bot(i + 1)).catch((error) => {
    counts.error++;
    console.log(`   bot ${i + 1} could not start: ${error.message.slice(0, 200)}`);
  })));
clearInterval(ticker);
report(true);
