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

const A_LABEL = { ATTACK: '공격', FIRE: '발사', RELOAD: '재장전', HEAL: '치료' };
const ITEM_LABEL = { KNIFE: '칼', PISTOL: '권총', MEDKIT: '메디킷', PAN: '프라이팬', SPOON: '숟가락' };
const B_LABEL = { PICKUP: '줍기', SWAP: '교체', DOOR: '이동', HIDE: '숨기', UNHIDE: '나오기' };

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

/* The arc drawn on the tile a knife or pan swings at, curving the way it was swung. */
const SWING_GLYPH = { UP: '⌒', DOWN: '⌣', LEFT: '(', RIGHT: ')' };

const el = (id) => document.getElementById(id);

const ui = {
  lobby: el('lobby'), lobbyForm: el('lobby-form'), nickname: el('nickname'),
  lobbyError: el('lobby-error'),
  game: el('game'), score: el('score'), board: el('board'),
  hpFill: el('hp-fill'), hpText: el('hp-text'), item: el('item'), state: el('state'),
  btnA: el('btn-a'), btnB: el('btn-b'),
  dead: el('dead'), deadScore: el('dead-score'), deadKills: el('dead-kills'),
  deadTime: el('dead-time'), restart: el('restart'),
  loot: el('loot'), lootFill: el('loot-fill'),
};

const cells = [];
let socket = null;
let lastSnapshot = null;
let flourishes = [];   // [{ marks: [{x, y, glyph, cls}], until }] shots and swings on screen
let lastHp = null;
let nickname = '';
let startedAt = 0;

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
    cell.classList.add('has-item');
    cell.textContent = '$';
    cell.title = ITEM_LABEL[item.kind] || item.kind;
  }

  for (const other of snapshot.players) {
    if (!other.alive) continue;
    const cell = cells[other.y * GRID + other.x];
    cell.classList.add('has-enemy');
    cell.textContent = ENEMY_GLYPH[other.direction] || '▲';
  }

  const self = snapshot.self;
  if (self.concealment !== 'CABINET') {
    const cell = cells[self.y * GRID + self.x];
    cell.classList.add('has-self');
    cell.textContent = SELF_GLYPH[self.direction] || '△';
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

  setAction(ui.btnA, 'A', A_LABEL[self.actionA]);
  setAction(ui.btnB, 'B', B_LABEL[self.actionB]);

  ui.state.className = 'state';
  if (self.concealment === 'CABINET') {
    ui.state.textContent = '캐비닛에 숨어 있음 — 이동·공격 불가';
    ui.state.classList.add('hidden-cabinet');
  } else if (self.lootMsLeft !== null) {
    ui.state.textContent = '줍는 중 — B를 떼거나 움직이면 취소';
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
  const left = self.lootMsLeft;
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

function setAction(button, letter, label) {
  button.querySelector('b').textContent = letter;
  button.querySelector('small').textContent = label || '-';
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

function connect(token) {
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
  socket = new WebSocket(`${scheme}://${location.host}/ws/game?token=${encodeURIComponent(token)}`);

  socket.addEventListener('message', (event) => {
    const message = JSON.parse(event.data);
    if (message.type === 'SNAPSHOT') {
      lastSnapshot = message;
      paint(message);
      paintHud(message);
      paintLoot(message.self);
    } else if (message.type === 'EVENT') {
      if (message.event === 'SHOT') showShot(message.path);
      else if (message.event === 'SWING') showSwing(message.from, message.to);
      else if (message.event === 'HIT') blink(ui.board, 'hit', HIT_MS);
    } else if (message.type === 'YOU_DIED') {
      showDeath(message);
    }
  });

  socket.addEventListener('close', () => {
    if (ui.dead.hidden) {
      ui.state.className = 'state';
      ui.state.textContent = '연결이 끊어졌습니다';
    }
  });
}

// --- Screens -------------------------------------------------------------

async function beginSession(typed) {
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
    nickname = session.nickname;
    startedAt = Date.now();
    lastSnapshot = null;
    lastHp = null;
    flourishes = [];
    ui.lobby.hidden = true;
    ui.dead.hidden = true;
    ui.game.hidden = false;
    connect(session.token);
  } catch (e) {
    ui.lobbyError.textContent = '서버에 연결할 수 없습니다';
  }
}

function showDeath(message) {
  ui.deadScore.textContent = message.score;
  ui.deadKills.textContent = message.kills;
  ui.deadTime.textContent = (message.survivedSeconds ?? Math.round((Date.now() - startedAt) / 1000)) + 's';
  ui.dead.hidden = false;
}

/** Back to the lobby, with the last name filled in and selected so typing replaces it. */
function restart() {
  if (socket) socket.close();
  socket = null;
  ui.dead.hidden = true;
  ui.game.hidden = true;
  ui.lobby.hidden = false;
  ui.nickname.value = nickname;
  ui.nickname.focus();
  ui.nickname.select();
}

// --- Input ---------------------------------------------------------------

function holdToRepeat(button, fire) {
  let timer = null;

  const stop = () => {
    if (timer !== null) {
      clearInterval(timer);
      timer = null;
    }
  };

  button.addEventListener('pointerdown', (event) => {
    event.preventDefault();
    fire();
    stop();
    timer = setInterval(fire, REPEAT_MS);
  });

  for (const type of ['pointerup', 'pointercancel', 'pointerleave']) {
    button.addEventListener(type, stop);
  }
}

function wireInput() {
  for (const pad of document.querySelectorAll('.pad')) {
    holdToRepeat(pad, () => move(pad.dataset.dir));
  }
  ui.btnA.addEventListener('click', actionA);
  // B is held, not clicked: a loot lasts only while it stays down. Doors and cabinets
  // act on the press and ignore the release.
  let bDown = false;
  const pressB = () => { if (!bDown) { bDown = true; actionB(); } };
  const letGoB = () => { if (bDown) { bDown = false; releaseB(); } };
  ui.btnB.addEventListener('pointerdown', (event) => { event.preventDefault(); pressB(); });
  for (const type of ['pointerup', 'pointercancel', 'pointerleave']) {
    ui.btnB.addEventListener(type, letGoB);
  }

  // Browser auto-repeat fires far faster than the server accepts moves, so held keys
  // are throttled to the same cadence as the on-screen pad.
  let lastKeyMove = 0;

  window.addEventListener('keydown', (event) => {
    if (!ui.dead.hidden || ui.game.hidden) return;

    const dir = KEY_DIR[event.key];
    if (dir) {
      event.preventDefault();
      const now = Date.now();
      if (now - lastKeyMove >= REPEAT_MS) {
        lastKeyMove = now;
        move(dir);
      }
      return;
    }
    if (event.key === 'j' || event.key === 'J') actionA();
    if ((event.key === 'k' || event.key === 'K') && !event.repeat) pressB();
  });
  window.addEventListener('keyup', (event) => {
    if (event.key === 'k' || event.key === 'K') letGoB();
  });
}

// --- Boot ----------------------------------------------------------------

buildBoard();
wireInput();
ui.lobbyForm.addEventListener('submit', (event) => {
  event.preventDefault();
  beginSession(ui.nickname.value.trim());
});
ui.restart.addEventListener('click', restart);
ui.nickname.focus();
