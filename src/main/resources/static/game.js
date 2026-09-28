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

/* How long a shot path and the hit blink stay on screen. Presentation only. */
const SHOT_MS = 100;
const HIT_MS = 150;

const el = (id) => document.getElementById(id);

const ui = {
  lobby: el('lobby'), lobbyForm: el('lobby-form'), nickname: el('nickname'),
  lobbyError: el('lobby-error'),
  game: el('game'), score: el('score'), board: el('board'),
  hpFill: el('hp-fill'), hpText: el('hp-text'), item: el('item'), state: el('state'),
  btnA: el('btn-a'), btnB: el('btn-b'),
  dead: el('dead'), deadScore: el('dead-score'), deadKills: el('dead-kills'),
  deadTime: el('dead-time'), restart: el('restart'),
};

const cells = [];
let socket = null;
let lastSnapshot = null;
let shot = null;       // { path, until } while a shot is on screen
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

  paintShot();
}

/*
 * Drawn over whatever the snapshot put there, except players: the path starts at the
 * shooter, and a shooter you can see is already marked. A shooter you cannot see, in
 * a bush, is given away by exactly this dot on their tile.
 */
function paintShot() {
  if (!shot || Date.now() >= shot.until) return;
  for (const [x, y] of shot.path) {
    const cell = cells[y * GRID + x];
    if (!cell || cell.classList.contains('has-self') || cell.classList.contains('has-enemy')) continue;
    cell.classList.add('shot');
    cell.textContent = '•';
  }
}

function showShot(path) {
  shot = { path, until: Date.now() + SHOT_MS };
  paintShot();
  setTimeout(() => {
    if (shot && Date.now() >= shot.until) {
      shot = null;
      if (lastSnapshot) paint(lastSnapshot);
    }
  }, SHOT_MS);
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
  } else if (self.looting) {
    ui.state.textContent = '줍는 중… 움직이면 처음부터';
    ui.state.classList.add('looting');
  } else if (self.concealment === 'BUSH') {
    ui.state.textContent = '부시에 은폐 중 — 밖에서 보이지 않음';
    ui.state.classList.add('hidden-bush');
  } else {
    ui.state.textContent = '';
  }
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

function connect(token) {
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
  socket = new WebSocket(`${scheme}://${location.host}/ws/game?token=${encodeURIComponent(token)}`);

  socket.addEventListener('message', (event) => {
    const message = JSON.parse(event.data);
    if (message.type === 'SNAPSHOT') {
      lastSnapshot = message;
      paint(message);
      paintHud(message);
    } else if (message.type === 'EVENT') {
      if (message.event === 'SHOT') showShot(message.path);
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
    shot = null;
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

/** Restart reuses the nickname, so death costs one tap rather than retyping an id. */
function restart() {
  if (socket) socket.close();
  ui.dead.hidden = true;
  beginSession(nickname);
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
  ui.btnB.addEventListener('click', actionB);

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
    if (event.key === 'k' || event.key === 'K') actionB();
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
