/*
 * Client. Renders snapshots and sends intent. It predicts nothing and simulates
 * nothing: every position on screen came from the server.
 *
 * The only game knowledge here is how to draw a glyph and which Korean word an action
 * token maps to. Rules stay on the server.
 */

const GRID = 15;

const TERRAIN = {
  '#': { cls: 't-wall',    glyph: ''  },
  '.': { cls: 't-floor',   glyph: ''  },
  '+': { cls: 't-door',    glyph: '+' },
  'C': { cls: 't-cabinet', glyph: '■' },
  'b': { cls: 't-bush',    glyph: '▒' },
};

const SELF_GLYPH  = { UP: '△', DOWN: '▽', LEFT: '◁', RIGHT: '▷' };
const ENEMY_GLYPH = { UP: '▲', DOWN: '▼', LEFT: '◀', RIGHT: '▶' };

const A_LABEL = { ATTACK: '공격', FIRE: '발사', HEAL: '치료', RELOAD: '장전' };
const ITEM_LABEL = {
  KNIFE: '칼', BAT: '야구배트', PISTOL: '권총', CROSSBOW: '석궁', MEDKIT: '메디킷',
  PAN: '프라이팬', SPOON: '숟가락', CUP: '컵', DOLL: '솜 빠진 인형', RECORDER: '리코더',
  REGISTER: '출석부', ROUNDS: '권총탄', BOLTS: '화살',
};
/*
 * How a weapon killed you, glued after its name. Optional per weapon: anything not
 * listed falls back to DEATH_VERB_DEFAULT, so a new weapon needs only an ITEM_LABEL.
 */
const DEATH_VERB = {
  PISTOL: '에 맞고', CROSSBOW: '에 맞고', KNIFE: '에 찔려', BAT: '에 두들겨 맞고',
  PAN: '에 얻어맞고', SPOON: '에 얻어맞고', CUP: '에 얻어맞고', DOLL: '에 얻어맞고',
  RECORDER: '에 얻어맞고', REGISTER: '에 얻어맞고',
};
const DEATH_VERB_DEFAULT = '에 당해';

const B_LABEL = { OPEN: '열기', CLOSE: '닫기', DOOR: '이동', EXTRACT: '탈출' };

const KEY_DIR = {
  ArrowUp: 'UP', ArrowDown: 'DOWN', ArrowLeft: 'LEFT', ArrowRight: 'RIGHT',
  w: 'UP', s: 'DOWN', a: 'LEFT', d: 'RIGHT',
  W: 'UP', S: 'DOWN', A: 'LEFT', D: 'RIGHT',
};

/*
 * A touch faster than the server's 150ms move cooldown, so the server-side input
 * buffer is already primed when each cooldown clears and a held direction advances
 * without gaps. Sending slower than the cooldown is what makes movement stutter;
 * sending much faster only wastes frames, since the buffer holds one move at most.
 */
const REPEAT_MS = 140;

/* How long a shot path, a swing and the hit blink stay on screen. Presentation only. */
const SHOT_MS = 100;
const SWING_MS = 150;
const HIT_MS = 150;
/* A fall: the ✕ where somebody went down, and the line under the board saying so. */
const FELL_MS = 1000;
const NOTICE_MS = 2000;

/* The arc drawn on the tile a knife or pan swings at, curving the way it was swung. */
const SWING_GLYPH = { UP: '⌒', DOWN: '⌣', LEFT: '(', RIGHT: ')' };

const el = (id) => document.getElementById(id);

const ui = {
  lobby: el('lobby'), lobbyForm: el('lobby-form'), nickname: el('nickname'),
  lobbyError: el('lobby-error'), start: el('start'), entry: el('entry'),
  guestEntry: el('guest-entry'), back: el('back'), logoutForm: el('logout-form'),
  game: el('game'), hudName: el('hud-name'), clock: el('clock'), score: el('score'),
  board: el('board'),
  hpFill: el('hp-fill'), hpText: el('hp-text'), item: el('item'), state: el('state'),
  bag: el('bag'), inv: el('inv'), invClose: el('inv-close'), invCrate: el('inv-crate'),
  crateList: el('crate-list'), invList: el('inv-list'), invHint: el('inv-hint'),
  hideout: el('hideout'), hideoutName: el('hideout-name'), stashList: el('stash-list'),
  stashCount: el('stash-count'), loadoutList: el('loadout-list'), hideoutHint: el('hideout-hint'),
  hideoutError: el('hideout-error'), setOut: el('set-out'), hideoutBack: el('hideout-back'),
  money: el('money'), haul: el('haul'), sell: el('sell'), traderList: el('trader-list'),
  btnA: el('btn-a'), btnB: el('btn-b'), controls: el('controls'), dpad: el('dpad'),
  dead: el('dead'), deadScore: el('dead-score'), deadKills: el('dead-kills'),
  deadTime: el('dead-time'), deadCause: el('dead-cause'), deadName: el('dead-name'),
  deadRecord: el('dead-record'),
  restart: el('restart'), deadTitle: el('dead-title'), compass: el('compass'),
  loot: el('loot'), lootFill: el('loot-fill'),
  ranking: el('ranking'), rankingList: el('ranking-list'),
};

const cells = [];
let socket = null;
let lastSnapshot = null;
let notice = null;     // { text, until } a passing line that outranks the status line
let flourishes = [];   // [{ marks: [{x, y, glyph, cls}], until }] shots and swings on screen
let lastHp = null;
let nickname = '';
let startedAt = 0;
let clockTimer = null;

// --- Board ---------------------------------------------------------------

function buildBoard() {
  const frag = document.createDocumentFragment();
  for (let i = 0; i < GRID * GRID; i++) {
    const cell = document.createElement('div');
    cell.className = 'cell';
    frag.appendChild(cell);
    cells.push(cell);
  }
  ui.board.appendChild(frag);
}

function paint(snapshot) {
  const terrain = snapshot.terrain;

  for (let y = 0; y < GRID; y++) {
    const row = terrain[y] || '';
    for (let x = 0; x < GRID; x++) {
      const tile = TERRAIN[row[x]] || TERRAIN['.'];
      const cell = cells[y * GRID + x];
      cell.className = 'cell ' + tile.cls;
      cell.textContent = tile.glyph;
      cell.title = '';
    }
  }

  for (const item of snapshot.items) {
    const cell = cells[item.y * GRID + item.x];
    // Only that something lies here: a crate, drawn by CSS. What is in it, you find
    // out by looting it.
    cell.classList.add('has-item');
    cell.textContent = '';
  }

  // My own exits, under anything standing on them. Only ever in my snapshot.
  for (const exit of snapshot.self.exits) {
    if (exit.x === null) continue;
    const cell = cells[exit.y * GRID + exit.x];
    cell.classList.add('exit');
    cell.textContent = '◎';
  }

  for (const other of snapshot.players) {
    if (!other.alive) continue;
    const cell = cells[other.y * GRID + other.x];
    cell.classList.add('has-enemy');
    cell.textContent = ENEMY_GLYPH[other.direction] || '▲';
  }

  const self = snapshot.self;
  const selfCell = cells[self.y * GRID + self.x];
  if (self.concealment === 'CABINET') {
    // Keep the ■ so the board reads the same as everyone else's; just mark it yours.
    selfCell.classList.add('self-cabinet');
  } else {
    selfCell.classList.add('has-self');
    selfCell.textContent = SELF_GLYPH[self.direction] || '△';
  }

  paintFlourishes();
}

/*
 * Shots and swings, drawn over whatever the snapshot put there except players. Both
 * start on the attacker's tile: an attacker you can see is already marked, and one
 * you cannot, in a bush, is given away by exactly this mark on their tile.
 */
function paintFlourishes() {
  const now = Date.now();
  flourishes = flourishes.filter((f) => f.until > now);
  for (const { marks } of flourishes) {
    for (const { x, y, glyph, cls } of marks) {
      const cell = cells[y * GRID + x];
      if (!cell || cell.classList.contains('has-self') || cell.classList.contains('has-enemy')) continue;
      cell.classList.add(cls);
      cell.textContent = glyph;
    }
  }
}

function showFlourish(marks, ms) {
  flourishes.push({ marks, until: Date.now() + ms });
  paintFlourishes();
  setTimeout(() => { if (lastSnapshot) paint(lastSnapshot); }, ms);
}

function showShot(path) {
  showFlourish(path.map(([x, y]) => ({ x, y, glyph: '•', cls: 'shot' })), SHOT_MS);
}

function showSwing(from, to) {
  const [fx, fy] = from;
  const [tx, ty] = to;
  const dir = tx > fx ? 'RIGHT' : tx < fx ? 'LEFT' : ty > fy ? 'DOWN' : 'UP';
  showFlourish([
    { x: fx, y: fy, glyph: '•', cls: 'shot' },
    { x: tx, y: ty, glyph: SWING_GLYPH[dir], cls: 'swing' },
  ], SWING_MS);
}

function showFell(at) {
  const [x, y] = at;
  showFlourish([{ x, y, glyph: '✕', cls: 'fell' }], FELL_MS);
  notice = { text: '누군가 쓰러졌다', until: Date.now() + NOTICE_MS };
  if (lastSnapshot) paintHud(lastSnapshot);
  setTimeout(() => { if (lastSnapshot) paintHud(lastSnapshot); }, NOTICE_MS);
}

function blink(element, cls, ms) {
  element.classList.add(cls);
  setTimeout(() => element.classList.remove(cls), ms);
}

function paintHud(snapshot) {
  const self = snapshot.self;

  ui.score.textContent = 'SCORE ' + self.score;

  if (lastHp !== null && self.hp < lastHp) {
    blink(ui.hpFill, 'hurt', HIT_MS);
    blink(ui.board, 'hurt', HIT_MS);
  }
  lastHp = self.hp;
  ui.hpFill.style.width = Math.max(0, Math.min(100, self.hp)) + '%';
  ui.hpFill.classList.toggle('low', self.hp <= 30);
  ui.hpText.textContent = self.hp;

  ui.item.textContent = self.item
    ? (ITEM_LABEL[self.item] || self.item) + (self.ammo === null ? '' : ' ' + self.ammo)
    : '-';

  // A medkit at full health is not offered. Keep its name on the button, greyed, so
  // the player sees why A is off rather than a bare dash.
  // An empty gun with nothing to load it says so rather than going blank.
  const idleA = self.item === 'MEDKIT' ? A_LABEL.HEAL
    : (self.item === 'PISTOL' || self.item === 'CROSSBOW') ? '탄 없음' : null;
  setAction(ui.btnA, 'A', A_LABEL[self.actionA], idleA);
  setAction(ui.btnB, 'B', B_LABEL[self.actionB]);

  ui.state.className = 'state';
  if (notice !== null && notice.until > Date.now()) {
    ui.state.textContent = notice.text;
    ui.state.classList.add('notice');
  } else if (self.concealment === 'CABINET') {
    ui.state.textContent = '캐비닛에 숨어 있음 — 옆이나 뒤로 움직이면 나감';
    ui.state.classList.add('hidden-cabinet');
  } else if (self.extractMsLeft !== null) {
    ui.state.textContent = '탈출 중 — B를 떼거나 움직이거나 맞으면 취소';
    ui.state.classList.add('extracting');
  } else if (self.lootMsLeft !== null) {
    ui.state.textContent = '여는 중 — B를 떼거나 움직이면 취소';
    ui.state.classList.add('looting');
  } else if (self.concealment === 'BUSH') {
    ui.state.textContent = '부시에 은폐 중 — 밖에서 보이지 않음';
    ui.state.classList.add('hidden-bush');
  } else {
    ui.state.textContent = '';
  }
}

/*
 * The gauge fills over whatever time the server says is left. The server alone decides
 * when the item lands; this only shows the wait, and restarts only when a new loot
 * begins rather than on every snapshot that arrives during one.
 */
function paintLoot(self) {
  // Getting out fills the same gauge as opening a crate, only for longer.
  const left = self.lootMsLeft ?? self.extractMsLeft;
  if (left === null || left === undefined) {
    ui.loot.hidden = true;
    ui.lootFill.style.transition = 'none';
    ui.lootFill.style.width = '0%';
    return;
  }
  if (!ui.loot.hidden) return;
  ui.loot.hidden = false;
  ui.lootFill.style.transition = 'none';
  ui.lootFill.style.width = '0%';
  void ui.lootFill.offsetWidth;
  ui.lootFill.style.transition = `width ${left}ms linear`;
  ui.lootFill.style.width = '100%';
}

/*
 * A needle per exit: eight ways, from how many rooms east and south it lies, and how
 * many doors away. In its own room an exit is the ◎ on the board instead.
 */
function needle(dx, dy) {
  const col = Math.sign(dx) + 1;
  const row = Math.sign(dy) + 1;
  return [['↖', '↑', '↗'], ['←', '◎', '→'], ['↙', '↓', '↘']][row][col];
}

function paintCompass(self) {
  if (self.exits.length === 0) {
    ui.compass.textContent = '';
    return;
  }
  const parts = self.exits.map((exit) => {
    const doors = Math.abs(exit.dx) + Math.abs(exit.dy);
    return doors === 0 ? '◎ 이 방' : `${needle(exit.dx, exit.dy)} ${doors}`;
  });
  const label = document.createElement('b');
  label.textContent = '탈출구 ';
  ui.compass.replaceChildren(label, parts.join('  ·  '));
}

function setAction(button, letter, label, idleLabel = null) {
  button.querySelector('b').textContent = letter;
  button.querySelector('small').textContent = label || idleLabel || '-';
  button.disabled = !label;
}

// --- Socket --------------------------------------------------------------

function send(message) {
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify(message));
  }
}

const move = (dir) => send({ type: 'MOVE', dir });
const actionA = () => send({ type: 'ACTION_A' });
const actionB = () => send({ type: 'ACTION_B' });
const releaseB = () => send({ type: 'RELEASE_B' });

/*
 * A dropped socket leaves the player standing in the world for 15 seconds (the server's
 * grace period). Retrying once a second for that long brings them back if the network
 * does. The server refuses the token once the player is gone, which ends the retries.
 *
 * Running out of tries means the grace period has passed too, so the game is over
 * either way and the player is told so rather than left on a frozen board. A phone
 * may freeze timers while the page is hidden, so coming back to the page retries at
 * once instead of waiting out a timer that may not have run.
 */
const RECONNECT_MS = 1000;
const RECONNECT_TRIES = 15;
const CLOSE_REFUSED = 1003;
let reconnectTries = 0;
let sessionToken = null;

function connect(token) {
  sessionToken = token;
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
  const ws = new WebSocket(`${scheme}://${location.host}/ws/game?token=${encodeURIComponent(token)}`);
  socket = ws;

  ws.addEventListener('open', () => { reconnectTries = 0; });

  ws.addEventListener('message', (event) => {
    const message = JSON.parse(event.data);
    if (message.type === 'SNAPSHOT') {
      lastSnapshot = message;
      paint(message);
      paintHud(message);
      paintLoot(message.self);
      paintCompass(message.self);
      paintBag(message.self);
    } else if (message.type === 'EVENT') {
      if (message.event === 'SHOT') showShot(message.path);
      else if (message.event === 'SWING') showSwing(message.from, message.to);
      else if (message.event === 'HIT') blink(ui.board, 'hit', HIT_MS);
      else if (message.event === 'FELL') showFell(message.at);
    } else if (message.type === 'YOU_DIED') {
      showDeath(message);
    } else if (message.type === 'EXTRACTED') {
      showExtracted(message);
    }
  });

  ws.addEventListener('close', (event) => {
    // Replaced by a newer socket, or closed on purpose by going back to the lobby.
    if (socket !== ws) return;
    // Dead: the result screen is up and there is nothing to come back to.
    if (!ui.dead.hidden) return;

    if (event.code === CLOSE_REFUSED) {
      forgetSession();
      // A page reload tried to resume a player who died while it was away.
      if (lastSnapshot === null) {
        restart();
        ui.lobbyError.textContent = '이전 게임은 끝났습니다';
        return;
      }
    }
    ui.state.className = 'state';
    if (event.code === CLOSE_REFUSED || reconnectTries >= RECONNECT_TRIES) {
      showDisconnected();
      return;
    }
    reconnectTries++;
    ui.state.textContent = `연결 끊김 — 재접속 중 (${reconnectTries}/${RECONNECT_TRIES})`;
    setTimeout(() => { if (socket === ws) connect(token); }, RECONNECT_MS);
  });
}

/** Back on the page, or back online, with the socket down: try now. */
function retryIfDropped() {
  if (document.visibilityState !== 'visible') return;
  if (ui.game.hidden || !ui.dead.hidden || sessionToken === null) return;
  if (socket && socket.readyState !== WebSocket.CLOSED) return;
  // Any retry still waiting on a timer sees a newer socket and stands down.
  connect(sessionToken);
}

document.addEventListener('visibilitychange', retryIfDropped);
window.addEventListener('online', retryIfDropped);

// --- Survival clock --------------------------------------------------------

/*
 * Time alive, counted from the browser's own start time. Presentation only: the death
 * screen shows the server's figure, which can differ from this by about a second.
 */
function paintClock() {
  const seconds = Math.floor((Date.now() - startedAt) / 1000);
  const mm = String(Math.floor(seconds / 60)).padStart(2, '0');
  const ss = String(seconds % 60).padStart(2, '0');
  ui.clock.textContent = `${mm}:${ss}`;
}

function startClock() {
  stopClock();
  paintClock();
  clockTimer = setInterval(paintClock, 1000);
}

function stopClock() {
  if (clockTimer !== null) {
    clearInterval(clockTimer);
    clockTimer = null;
  }
}

// --- Ranking -------------------------------------------------------------

const RANKING_SIZE = 10;

/* Nicknames are typed by other players, so rows are built with textContent only. */
function rankingRow(rank, name, score, mine) {
  const li = document.createElement('li');
  if (mine) li.classList.add('mine');
  for (const [cls, text] of [['rank', rank], ['name', name], ['score', score]]) {
    const span = document.createElement('span');
    span.className = cls;
    span.textContent = text;
    li.appendChild(span);
  }
  return li;
}

/*
 * The ten biggest hauls, and below them, for a signed-in account outside the ten, "⋮"
 * and its own row. The server works out that row from the session. A failed fetch
 * leaves the ranking hidden; the lobby works without it.
 */
async function loadRanking() {
  try {
    const response = await fetch(`/api/ranking?limit=${RANKING_SIZE}`);
    if (!response.ok) throw new Error(String(response.status));
    const { top, me: mine } = await response.json();
    const isMine = (row) => mine !== null && row.nickname === mine.nickname;
    const items = top.map((row) => rankingRow(row.rank, row.nickname, row.value, isMine(row)));
    if (mine !== null && !top.some(isMine)) {
      if (mine.rank > top.length + 1) {
        const gap = document.createElement('li');
        gap.className = 'gap';
        gap.textContent = '⋮';
        items.push(gap);
      }
      items.push(rankingRow(mine.rank, mine.nickname, mine.value, true));
    }
    ui.rankingList.replaceChildren(...items);
    ui.ranking.hidden = items.length === 0;
  } catch (e) {
    ui.ranking.hidden = true;
  }
}

// --- Surviving a page reload -------------------------------------------------

/*
 * The session token is kept in sessionStorage so a reload inside the server's 15s
 * grace period picks the same player back up. sessionStorage is per tab, so two tabs
 * stay two players. Storage can be missing or throw (private windows, blocked site
 * data); the game then simply behaves as it did before: a reload ends the life.
 */
const SESSION_KEY = 'battle-royal.session';

function saveSession(saved) {
  try { sessionStorage.setItem(SESSION_KEY, JSON.stringify(saved)); } catch (e) { /* none */ }
}

function savedSession() {
  try {
    const saved = JSON.parse(sessionStorage.getItem(SESSION_KEY));
    return saved && saved.token && saved.nickname ? saved : null;
  } catch (e) {
    return null;
  }
}

function forgetSession() {
  try { sessionStorage.removeItem(SESSION_KEY); } catch (e) { /* none */ }
}

// --- Account -------------------------------------------------------------

/*
 * Who the browser is signed in as, and what the lobby shows for it:
 *   signed out          Google and guest buttons; guest leads to the name form
 *   signed in, no name  the name form picks the account's nickname, once
 *   signed in, named    one button that starts as that nickname, no questions
 */
let me = { signedIn: false, nickname: null };
let guestChosen = false;

async function loadMe() {
  try {
    const response = await fetch('/api/me');
    if (response.ok) me = await response.json();
  } catch (e) { /* offline: stay a guest */ }
  paintAccount();
}

function choosingNickname() {
  return me.signedIn && !me.nickname;
}

function paintAccount() {
  const named = me.signedIn && !!me.nickname;
  const showForm = me.signedIn || guestChosen;
  ui.entry.hidden = showForm;
  ui.lobbyForm.hidden = !showForm;
  ui.nickname.hidden = named;
  ui.nickname.placeholder = choosingNickname() ? '닉네임 정하기' : '이름';
  ui.nickname.setAttribute('aria-label', ui.nickname.placeholder);
  ui.start.textContent = choosingNickname() ? '확인' : named ? `${me.nickname} · START` : 'START';
  ui.back.hidden = !(guestChosen && !me.signedIn);
  ui.logoutForm.hidden = !me.signedIn;
  if (showForm && !ui.nickname.hidden && !ui.lobby.hidden) ui.nickname.focus();
}

async function chooseNickname(typed) {
  if (!typed) {
    ui.lobbyError.textContent = '닉네임을 입력하세요';
    return;
  }
  ui.lobbyError.textContent = '';
  try {
    const response = await fetch('/api/me/nickname', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nickname: typed }),
    });
    if (response.status === 409) {
      ui.lobbyError.textContent = '이미 쓰는 닉네임입니다';
      await loadMe();
      return;
    }
    if (!response.ok) {
      // The server says why: too long, a blocked word, or the ~ that marks guests.
      ui.lobbyError.textContent = (await response.text()) || '쓸 수 없는 닉네임입니다';
      return;
    }
    me = await response.json();
    paintAccount();
  } catch (e) {
    ui.lobbyError.textContent = '서버에 연결할 수 없습니다';
  }
}

// --- Screens -------------------------------------------------------------

async function beginSession(typed) {
  // A signed-in account sets out from the hideout instead.
  if (me.signedIn) {
    openHideout();
    return;
  }
  if (!typed) {
    ui.lobbyError.textContent = '아이디를 입력하세요';
    return;
  }

  ui.lobbyError.textContent = '';
  try {
    const response = await fetch('/api/session', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nickname: typed }),
    });
    if (!response.ok) {
      ui.lobbyError.textContent = (await response.text()) || '시작할 수 없습니다';
      return;
    }
    const session = await response.json();
    const saved = { token: session.token, nickname: session.nickname, startedAt: Date.now() };
    saveSession(saved);
    enterGame(saved);
  } catch (e) {
    ui.lobbyError.textContent = '서버에 연결할 수 없습니다';
  }
}

/** Into the game screen for a new session, or for one resumed after a reload. */
function enterGame(saved) {
  nickname = saved.nickname;
  ui.hudName.textContent = nickname;
  startedAt = saved.startedAt ?? Date.now();
  startClock();
  lastSnapshot = null;
  lastHp = null;
  flourishes = [];
  notice = null;
  bagOpen = false;
  selection = null;
  crateWasOpen = false;
  ui.lobby.hidden = true;
  ui.dead.hidden = true;
  ui.game.hidden = false;
  reconnectTries = 0;
  connect(saved.token);
}

function deathCause(killer, weapon) {
  if (!killer) return '당신은 사망했다.';
  // A killer with nothing in hand did it with their fists.
  if (!weapon) return `'${killer}'의 주먹에 맞고 당신은 사망했다.`;
  const name = ITEM_LABEL[weapon] || weapon;
  return `'${killer}'의 ${name}${DEATH_VERB[weapon] ?? DEATH_VERB_DEFAULT} 당신은 사망했다.`;
}

function showDeath(message) {
  stopClock();
  forgetSession();
  ui.deadTitle.textContent = 'GAME OVER';
  ui.deadCause.textContent = deathCause(message.killer, message.weapon);
  showRecord(message);
}

/* The record under either ending. */
function showRecord(message) {
  ui.deadName.textContent = nickname;
  ui.deadScore.textContent = message.score;
  ui.deadKills.textContent = message.kills;
  ui.deadTime.textContent = (message.survivedSeconds ?? Math.round((Date.now() - startedAt) / 1000)) + 's';
  ui.deadRecord.hidden = false;
  ui.restart.textContent = me.signedIn ? '거점으로' : '처음으로';
  ui.dead.hidden = false;
}

/* Out through an exit, with what was carried. An account's haul is in the stash now. */
function showExtracted(message) {
  stopClock();
  forgetSession();
  ui.deadTitle.textContent = '탈출 성공';
  const haul = message.carried.map(slotText).join(', ');
  const kept = me.signedIn ? ' 창고로 옮겼다.' : '';
  ui.deadCause.textContent = haul
    ? `섬을 빠져나왔다. 가져온 것: ${haul}.${kept}`
    : '섬을 빠져나왔다. 빈손이다.';
  showRecord(message);
}

/*
 * The game ended while the socket was down: the 15-second grace ran out, or the server
 * restarted. The final numbers never arrived, so there is no record to show, and no
 * claim that one was saved: a restart saves nothing.
 */
function showDisconnected() {
  stopClock();
  forgetSession();
  sessionToken = null;
  const corpse = document.createElement('em');
  corpse.textContent = '끔찍한 시체';
  ui.deadCause.replaceChildren(
    '자고 있는 사이에 야생 동물에 당해', document.createElement('br'),
    corpse, '가 되었다.');
  ui.deadTitle.textContent = 'GAME OVER';
  ui.deadRecord.hidden = true;
  ui.restart.textContent = me.signedIn ? '거점으로' : '처음으로';
  ui.dead.hidden = false;
}

/** Back to the lobby, with the last name filled in and selected so typing replaces it. */
function restart() {
  stopClock();
  if (socket) socket.close();
  socket = null;
  ui.dead.hidden = true;
  ui.game.hidden = true;
  ui.lobby.hidden = false;
  // A guest's name came back with its ~; offer it again without one.
  ui.nickname.value = me.signedIn ? '' : nickname.replace(/^~/, '');
  if (!ui.nickname.hidden) {
    ui.nickname.focus();
    ui.nickname.select();
  }
  loadRanking();
}

// --- Input ---------------------------------------------------------------

/*
 * One steering direction, fed by the keyboard and the touch pad alike. While it is
 * set, a move goes out at once and then every REPEAT_MS, so a held key or thumb walks
 * steadily. The browser's own key repeat is not used: it waits a few hundred
 * milliseconds before the first repeat, which made every held key step, pause, then
 * run.
 */
const keysHeld = [];        // directions in the order their keys went down
let touchDir = null;
let steering = null;
let steerTimer = null;

function playing() {
  return ui.dead.hidden && !ui.game.hidden;
}

/** A short buzz so a thumb knows it registered without a glance. Android only. */
function buzz() {
  try { navigator.vibrate?.(8); } catch (e) { /* unsupported */ }
}

function steer() {
  // The touch pad wins while a thumb is on it; otherwise the last key still held.
  const dir = touchDir ?? keysHeld.at(-1) ?? null;
  if (dir === steering) return;
  steering = dir;
  clearInterval(steerTimer);
  steerTimer = null;
  for (const pad of document.querySelectorAll('.pad')) {
    pad.classList.toggle('pressed', pad.dataset.dir === dir);
  }
  if (dir === null) return;
  if (touchDir !== null) buzz();
  if (playing()) move(dir);
  steerTimer = setInterval(() => { if (playing()) move(steering); }, REPEAT_MS);
}

/*
 * Touch zones. The controls strip is split down the middle, and neither half has to be
 * hit precisely, so a thumb can stay put while the eyes stay on the board.
 *
 * Left half: steers by where the thumb is relative to the centre of the drawn cross,
 * whichever axis it is further along. Near the centre nothing happens. Sliding the
 * thumb round changes direction without lifting it.
 *
 * Right half: whichever of A and B is nearer. The tilted pair puts A low and B high,
 * so the line between them runs the way a thumb rocks.
 */
function padDirection(x, y) {
  const box = ui.dpad.getBoundingClientRect();
  const dx = x - (box.left + box.width / 2);
  const dy = y - (box.top + box.height / 2);
  // The middle square of the cross is the dead zone.
  if (Math.max(Math.abs(dx), Math.abs(dy)) < box.width / 6) return null;
  if (Math.abs(dx) > Math.abs(dy)) return dx > 0 ? 'RIGHT' : 'LEFT';
  return dy > 0 ? 'DOWN' : 'UP';
}

function nearerAction(x, y) {
  const distance = (button) => {
    const box = button.getBoundingClientRect();
    return Math.hypot(x - (box.left + box.width / 2), y - (box.top + box.height / 2));
  };
  return distance(ui.btnA) <= distance(ui.btnB) ? ui.btnA : ui.btnB;
}

function wireInput() {
  // B is held, not tapped: a loot lasts only while it stays down. Doors act on the
  // press and ignore the release.
  let bDown = false;
  const pressB = () => { if (!bDown) { bDown = true; actionB(); } };
  const letGoB = () => { if (bDown) { bDown = false; releaseB(); } };

  const fingers = new Map();  // pointerId -> 'pad' | the A or B button
  let padFinger = null;

  ui.controls.addEventListener('pointerdown', (event) => {
    event.preventDefault();
    // Keep receiving this finger's moves and lift even if it strays off the strip.
    try { ui.controls.setPointerCapture(event.pointerId); } catch (e) { /* not capturable */ }
    const box = ui.controls.getBoundingClientRect();
    if (event.clientX < box.left + box.width / 2) {
      fingers.set(event.pointerId, 'pad');
      padFinger = event.pointerId;
      touchDir = padDirection(event.clientX, event.clientY);
      steer();
      return;
    }
    const button = nearerAction(event.clientX, event.clientY);
    fingers.set(event.pointerId, button);
    if (button.disabled) return;
    button.classList.add('pressed');
    buzz();
    // A fires on the press, not the release: a tap's release comes a beat late.
    if (button === ui.btnA) actionA();
    else pressB();
  });

  ui.controls.addEventListener('pointermove', (event) => {
    if (event.pointerId !== padFinger) return;
    touchDir = padDirection(event.clientX, event.clientY);
    steer();
  });

  const lift = (event) => {
    const held = fingers.get(event.pointerId);
    if (held === undefined) return;
    fingers.delete(event.pointerId);
    if (held === 'pad') {
      if (event.pointerId === padFinger) {
        padFinger = null;
        touchDir = null;
        steer();
      }
      return;
    }
    held.classList.remove('pressed');
    if (held === ui.btnB) letGoB();
  };
  for (const type of ['pointerup', 'pointercancel', 'lostpointercapture']) {
    ui.controls.addEventListener(type, lift);
  }
  // A long press would otherwise open the phone's context menu over the controls.
  ui.controls.addEventListener('contextmenu', (event) => event.preventDefault());

  window.addEventListener('keydown', (event) => {
    const dir = KEY_DIR[event.key];
    if (dir) {
      if (!playing()) return;
      event.preventDefault();
      if (!keysHeld.includes(dir)) {
        keysHeld.push(dir);
        steer();
      }
      return;
    }
    if (!playing()) return;
    if (event.key === 'i' || event.key === 'I') setBag(!bagOpen);
    if (event.key === '1' || event.key === '2' || event.key === '3') {
      send({ type: 'EQUIP', slot: Number(event.key) - 1 });
    }
    if (event.key === 'j' || event.key === 'J') actionA();
    if ((event.key === 'k' || event.key === 'K') && !event.repeat) pressB();
  });
  window.addEventListener('keyup', (event) => {
    const dir = KEY_DIR[event.key];
    if (dir) {
      // Letting go of the newer key carries on in the direction still held.
      const at = keysHeld.indexOf(dir);
      if (at !== -1) keysHeld.splice(at, 1);
      steer();
      return;
    }
    if (event.key === 'k' || event.key === 'K') letGoB();
  });
  // Keys released while the window was not focused never send a keyup.
  window.addEventListener('blur', () => {
    keysHeld.length = 0;
    touchDir = null;
    steer();
    letGoB();
  });
}

// --- Bag: inventory and crate ----------------------------------------------

/*
 * The bag window lies over the board. Three inventory slots, one of them equipped (the
 * "e"), and the crate beside them while one is open. Pick something, then pick where
 * it goes; the server checks every move and the next snapshot shows the result.
 *
 *   crate open, crate item picked   -> an inventory slot takes it (TAKE)
 *   crate open, inventory item      -> the crate takes it (PUT); the same slot again equips
 *   no crate                        -> tapping a slot equips it
 */
let bagOpen = false;
let selection = null;      // { from: 'crate' | 'inv', index }
let crateWasOpen = false;

function slotText(slot) {
  if (!slot) return '비어 있음';
  const name = ITEM_LABEL[slot.kind] || slot.kind;
  return slot.ammo === null || slot.ammo === undefined ? name : `${name} ${slot.ammo}`;
}

function slotButton(slot, from, index, equipped) {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'slot' + (slot ? '' : ' empty')
    + (selection && selection.from === from && selection.index === index ? ' selected' : '');
  button.dataset.from = from;
  button.dataset.index = String(index);
  button.textContent = slotText(slot);
  if (equipped) {
    const badge = document.createElement('span');
    badge.className = 'equip';
    badge.textContent = 'e';
    badge.setAttribute('aria-label', '장착');
    button.appendChild(badge);
  }
  const li = document.createElement('li');
  li.appendChild(button);
  return li;
}

function paintBag(self) {
  const crate = Array.isArray(self.crate) ? self.crate : null;
  // Opening a crate pops the bag open; a crate shutting (moving away) drops a pick from it.
  if (crate && !crateWasOpen) {
    bagOpen = true;
    selection = null;
  }
  if (!crate && selection && selection.from === 'crate') selection = null;
  crateWasOpen = !!crate;
  if (selection && selection.from === 'crate' && crate && selection.index >= crate.length) selection = null;

  ui.bag.classList.toggle('open', bagOpen);
  ui.inv.hidden = !bagOpen;
  if (!bagOpen) return;

  ui.invCrate.hidden = !crate;
  ui.crateList.replaceChildren(...(crate || []).map((slot, i) => slotButton(slot, 'crate', i, false)));
  ui.invList.replaceChildren(...self.inventory.map((slot, i) => slotButton(slot, 'inv', i, i === self.equipped)));

  if (!crate) ui.invHint.textContent = '누르면 장착 · 1 2 3';
  else if (!selection) ui.invHint.textContent = '옮길 아이템을 고르세요';
  else if (selection.from === 'crate') ui.invHint.textContent = '넣을 칸을 고르세요';
  else ui.invHint.textContent = '상자를 누르면 넣기 · 한 번 더 누르면 장착';
}

function repaintBag() {
  if (lastSnapshot) paintBag(lastSnapshot.self);
}

function setBag(open) {
  bagOpen = open;
  selection = null;
  // Closing the window over an open crate closes the crate too.
  if (!open && lastSnapshot && Array.isArray(lastSnapshot.self.crate)) send({ type: 'CLOSE' });
  repaintBag();
}

function pickInBag(from, index) {
  const crateOpen = lastSnapshot && Array.isArray(lastSnapshot.self.crate);
  if (from === 'crate') {
    if (selection && selection.from === 'inv') {
      send({ type: 'PUT', slot: selection.index });
      selection = null;
    } else {
      selection = { from: 'crate', index };
    }
  } else if (selection && selection.from === 'crate') {
    send({ type: 'TAKE', index: selection.index, slot: index });
    selection = null;
  } else if (!crateOpen || (selection && selection.from === 'inv' && selection.index === index)) {
    send({ type: 'EQUIP', slot: index });
    selection = null;
  } else {
    selection = { from: 'inv', index };
  }
  repaintBag();
}

function wireBag() {
  ui.bag.addEventListener('click', () => setBag(!bagOpen));
  ui.invClose.addEventListener('click', () => setBag(false));
  ui.inv.addEventListener('click', (event) => {
    const button = event.target.closest('.slot');
    if (button) {
      pickInBag(button.dataset.from, Number(button.dataset.index));
      return;
    }
    // Anywhere in the crate column takes a picked inventory item, not only its rows.
    if (event.target.closest('.crate-col') && selection && selection.from === 'inv') {
      send({ type: 'PUT', slot: selection.index });
      selection = null;
      repaintBag();
    }
  });
}

// --- Hideout ---------------------------------------------------------------

/*
 * A signed-in account's way onto the island. The stash on the left, three slots to
 * carry out on the right: pick a stash item, then the slot it goes in; tap a filled
 * slot to leave it at home. "섬으로" sets out with exactly that. Nothing taken out
 * comes back except by extraction or a server restart; dying loses it.
 */
let stash = [];             // [{ id, kind, ammo, price }]
let hideoutView = null;     // the last /api/hideout answer
let loadout = [null, null, null];   // stash item ids
let stashPick = null;       // a stash item id

async function openHideout() {
  ui.hideoutError.textContent = '';
  ui.hideoutName.textContent = me.nickname ?? '';
  stashPick = null;
  loadout = [null, null, null];
  try {
    const response = await fetch('/api/hideout');
    if (!response.ok) {
      ui.lobbyError.textContent = (await response.text()) || '거점을 열 수 없습니다';
      return;
    }
    applyHideout(await response.json());
    ui.lobby.hidden = true;
    ui.hideout.hidden = false;
  } catch (e) {
    ui.lobbyError.textContent = '서버에 연결할 수 없습니다';
  }
}

function applyHideout(view) {
  hideoutView = view;
  stash = view.stash;
  ui.stashCount.textContent = `${stash.length}/${view.capacity}`;
  ui.money.textContent = view.money;
  ui.haul.textContent = view.haul;
  if (stashPick !== null && !stashEntry(stashPick)) stashPick = null;
  paintHideout();
}

/*
 * The trader's stock. A line is greyed when it cannot be bought now; the server is the
 * one that says no, this only saves a pointless tap.
 */
function paintTrader() {
  const full = stash.length >= hideoutView.capacity;
  ui.traderList.replaceChildren(...hideoutView.trader.map((offer) => {
    const li = document.createElement('li');
    const name = document.createElement('span');
    // Guns are sold empty, so only a bundle's count is worth showing.
    name.textContent = slotText({ kind: offer.kind, ammo: offer.ammo > 0 ? offer.ammo : null });
    const price = document.createElement('span');
    price.className = 'price';
    price.textContent = `${offer.price}원`;
    const buy = document.createElement('button');
    buy.type = 'button';
    buy.dataset.kind = offer.kind;
    buy.textContent = '사기';
    buy.disabled = full || hideoutView.money < offer.price;
    li.append(name, price, buy);
    return li;
  }));
}

async function trade(path, body) {
  ui.hideoutError.textContent = '';
  try {
    const response = await fetch(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      ui.hideoutError.textContent = (await response.text()) || '거래할 수 없습니다';
      return;
    }
    applyHideout(await response.json());
  } catch (e) {
    ui.hideoutError.textContent = '서버에 연결할 수 없습니다';
  }
}

function stashEntry(id) {
  return stash.find((entry) => entry.id === id) || null;
}

function paintHideout() {
  const carried = new Set(loadout.filter((id) => id !== null));
  ui.stashList.replaceChildren(...stash.map((entry) => {
    const li = slotButton(entry, 'stash', entry.id, false);
    const button = li.firstChild;
    button.classList.toggle('selected', stashPick === entry.id);
    button.disabled = carried.has(entry.id);
    return li;
  }));
  if (stash.length === 0) {
    const li = document.createElement('li');
    li.className = 'inv-hint';
    li.textContent = '비어 있다';
    ui.stashList.replaceChildren(li);
  }
  ui.loadoutList.replaceChildren(...loadout.map((id, i) =>
    slotButton(id === null ? null : stashEntry(id), 'loadout', i, i === 0)));
  const picked = stashPick === null ? null : stashEntry(stashPick);
  ui.sell.hidden = picked === null;
  if (picked) ui.sell.textContent = `${slotText(picked)} 팔기 · ${picked.price}원`;
  paintTrader();
  ui.hideoutHint.textContent = stash.length === 0
    ? '첫 출발은 빈손이다. 탈출하면 가져온 것이 여기 쌓인다.'
    : stashPick !== null ? '넣을 칸을 고르거나 상인에게 파세요' : '창고에서 고른 뒤 칸을 고르세요 · 채운 칸을 누르면 빼기';
}

function pickInHideout(from, index) {
  if (from === 'stash') {
    stashPick = stashPick === index ? null : index;
  } else if (stashPick !== null) {
    loadout = loadout.map((id) => (id === stashPick ? null : id));
    loadout[index] = stashPick;
    stashPick = null;
  } else {
    loadout[index] = null;
  }
  paintHideout();
}

async function setOut() {
  ui.hideoutError.textContent = '';
  ui.setOut.disabled = true;
  try {
    const response = await fetch('/api/hideout/sortie', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ loadout }),
    });
    if (!response.ok) {
      ui.hideoutError.textContent = (await response.text()) || '출발할 수 없습니다';
      return;
    }
    const session = await response.json();
    const saved = { token: session.token, nickname: session.nickname, startedAt: Date.now() };
    saveSession(saved);
    ui.hideout.hidden = true;
    enterGame(saved);
  } catch (e) {
    ui.hideoutError.textContent = '서버에 연결할 수 없습니다';
  } finally {
    ui.setOut.disabled = false;
  }
}

function wireHideout() {
  ui.hideout.addEventListener('click', (event) => {
    const button = event.target.closest('.slot');
    if (!button || button.disabled) return;
    pickInHideout(button.dataset.from, Number(button.dataset.index));
  });
  ui.setOut.addEventListener('click', setOut);
  ui.sell.addEventListener('click', () => {
    if (stashPick !== null) trade('/api/hideout/sell', { itemId: stashPick });
  });
  ui.traderList.addEventListener('click', (event) => {
    const button = event.target.closest('button[data-kind]');
    if (button && !button.disabled) trade('/api/hideout/buy', { kind: button.dataset.kind });
  });
  ui.hideoutBack.addEventListener('click', () => {
    ui.hideout.hidden = true;
    ui.lobby.hidden = false;
  });
}

// --- Boot ----------------------------------------------------------------

buildBoard();
wireInput();
wireBag();
wireHideout();
ui.lobbyForm.addEventListener('submit', (event) => {
  event.preventDefault();
  if (choosingNickname()) chooseNickname(ui.nickname.value.trim());
  else beginSession(ui.nickname.value.trim());
});
ui.restart.addEventListener('click', () => {
  restart();
  // An account's next trip starts from the hideout, which shows what came home.
  if (me.signedIn) openHideout();
});
ui.guestEntry.addEventListener('click', () => { guestChosen = true; ui.lobbyError.textContent = ''; paintAccount(); });
ui.back.addEventListener('click', () => { guestChosen = false; ui.lobbyError.textContent = ''; paintAccount(); });
loadRanking();
loadMe();
const resumable = savedSession();
if (resumable) {
  enterGame(resumable);
}
