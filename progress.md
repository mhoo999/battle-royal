# Progress

## Current Milestone

V1 Multiplayer Core

## Current Task

None in flight. Bare hands, new weapons and junk done; next is the browser checks in Next.

## Completed

- [x] Docs: `CLAUDE.md`, `docs/PRD.md`, `docs/GAME_RULES.md`,
      `docs/NETWORK_PROTOCOL.md`, `docs/ARCHITECTURE.md`,
      `.claude/skills/game-testing/SKILL.md`
- [x] Spring Boot 4.1.1 + Gradle Wrapper, Java 21
- [x] `game/core` — Pos, Direction, TileType, GridMap, Player, Room, Item, ItemKind,
      Command, Concealment, ActionA, ActionB
- [x] `game/map` — MapTemplate (parse + structural validation), 8 hand-authored layouts
- [x] `game/rule` — GameConstants, MovementRules, RoomSimulator, VisibilityRules,
      ActionResolver
- [x] `game/loop` — GameLoopService (20Hz thread), RoomRegistry, RoomBroadcaster
- [x] `ws` — Snapshot, SnapshotFilter, GameWebSocketHandler, SessionRegistry,
      WebSocketSnapshotBroadcaster
- [x] `web` — GuestSessionService, SessionController
- [x] Client — lobby, CSS Grid 15x15 renderer, D-pad + A/B, death overlay
- [x] Movement: 150ms cooldown with a one-slot input buffer
- [x] Rooms: linked graph, population-capped, always one connected piece
- [x] Door transit, arriving just inside the door you came through
- [x] Bush and cabinet visibility filtering
- [x] `e2e/smoke-two-sockets.mjs` — dependency-free two-socket checks
- [x] Step 4 combat: `CombatRules.trace` (instant raycast), A wired in `RoomSimulator`
      (Knife, Pistol fire / reload at zero, Medkit), damage, death, hit/kill score,
      `GameEvent` (Shot/Hit/Died) queued on `Room` and routed by audience,
      `ws/Outbound` wire records, dead reaped after broadcast, client draws `•` path
      for 100ms and blinks on HIT / HP loss
- [x] Step 5 items: `ItemSpawns` (roll on room creation, 20s re-roll after emptied or
      after an empty roll), B `PICKUP`/`SWAP` (old item stays on the tile), +5 once per
      item id, Pan (weak melee) and Spoon (junk), Korean item names in the client,
      spawn-in-door-reach validation, death drops kept out of door reach
- [x] Loot time: B starts a 10-tick loot, leaving the tile resets it; `self.looting`
      drives a "줍는 중" status line
- [x] Hit feedback split: the victim's board flashes red on HP loss, the attacker's
      HIT is a neutral white flash
- [x] Melee `SWING` event (room-wide, from/to) drawn as an arc for 150ms
- [x] Hold-B looting: `RELEASE_B` cancels; `self.lootMsLeft` drives a gauge
- [x] Death screen names the killer and weapon ("'kang'의 권총에 맞고 당신은
      사망했다."); `YOU_DIED` carries `killer`/`weapon`, victim only; per-weapon verb
      table with a default so new weapons need only a label
- [x] Game Boy cross D-pad: one dark plus, dimpled centre, dim arrows
- [x] Lobby: block-letter ASCII title in `--self` with a red offset shadow, 生き残れ
      rule, school-trip story copy; scales with viewport (287px wide at 375px)
- [x] BATTLE ROYALE title and film theme copy, "탈락" overlay, "처음으로" returns to
      the lobby with the last name prefilled, Game Boy A/B layout
- [x] Step 6 cabinets: walked into (empty only) and out of (any way but straight on),
      8-tick toggle, no B; wood-background tile, own cabinet outlined
- [x] HUD bar: name | survival clock (client-side, centred) | score

- [x] Step 7 score: survival +1/10s outside cabinets, room entry +10 (first visit,
      30s cap) — `ScoreRules`
- [x] Step 7 disconnect grace: 15s in the world, hittable; same token reconnects to
      the same player; expiry kills with no killer. Token retired on death.
      Client auto-retries 15× at 1s.
- [x] Step 7 results: `GameResult` saved on every death via `DeathListener` →
      `ResultRecorder` (own writer thread). `GET /api/ranking`, top 10 on the lobby.
- [x] Own rank under the top 10 after a death ("⋮" then "25 kang 37"), asked by the
      numbers via `GET /api/ranking/rank`, since nicknames repeat
- [x] Item economy rework: no reload (last shot uses the pistol up), one floor item
      or none per room on one of the 4 `*` candidates, rarer weapons, loot regrows
      only while a room sits empty and bare (`LOOT_REGROW_TICKS`). Smoke combat now
      roams for a weapon when the meeting room has none.
- [x] Bare-hand attack (5 dmg), Bat (45, slow), Crossbow (range 7, 40, 3 bolts, used
      up like the pistol), junk Cup/Doll/Recorder/Register, Spoon now swings.
      `Weapons.strikeOf` maps held kind (null = fists) to range/damage/cooldown/shot.
      New loot table. Death screen: "주먹에 맞고" for a bare-hand kill.
- [x] README (screenshot, design points) and GitHub Actions CI: unit tests, a real
      server with the socket smoke suite, jar artifact. Repo on GitHub, branch `main`.
- [x] Deploy prep: `application-prod.properties` (env-var DB, H2 console off, bind
      127.0.0.1, forwarded headers, graceful shutdown), MySQL driver, `deploy/`
      (systemd unit, Nginx site, env template). Rehearsed locally: prod jar + Docker
      MySQL 8.0 + Docker Nginx + browser. `docs/AWS_DEPLOYMENT.md` written.
- [x] A page reload resumes the same player (token in `sessionStorage`); a refused
      resume returns to the lobby with "이전 게임은 끝났습니다"
- [x] Doors lead to the facing wall 98% of the time (was 90%): the encounter bias only
      picks occupied rooms whose facing door is free, and runs at 40%.

Reload resume: browser — moved, reloaded, came back on room-1 (4,11) with the name
and clock kept; a stored unknown token led to the lobby, name prefilled, message
shown, storage cleared. Smoke all passed. No console errors.

Step 7 browser walk (2026-09-29): lobby top 10 shown; `socket.close()` showed
"연결 끊김 — 재접속 중 (1/15)" and came back on the same room and tile; a socket player
killed the browser player with fists ("'hunter2'의 주먹에 맞고"); 처음으로 showed "⋮" then
"65 rankcheck 7" highlighted. Not exercised: a death landing inside the top 10.
`.claude/skills/game-testing` rewritten around the smoke suite and a browser walk.

Weapons and junk: `./gradlew test` 172 passing. Smoke 5/5; the combat check now
always runs, with bare hands (hp 100 -> 95). Browser: empty-handed A reads 공격, death
wording checked for fist/cup/doll/bat/crossbow/register, no console errors. Not seen
in play: bat or crossbow in hand (4% and 2% per roll; covered by CombatSimulationTest).

Item economy: `./gradlew test` 165 passing. Smoke 6/6 clean; combat ran in 5 (A armed
after 1–17 rooms) and SKIPped once after 60 rooms without a weapon. Browser: a room
renders with a single `$`, no console errors. Not seen in the browser: a pistol
vanishing on its last shot (4% per roll; covered by `CombatSimulationTest`).

Step 6 and HUD: `./gradlew test` green, smoke all passed, user verified cabinets, name
and clock in the browser.

Step 7: `./gradlew test` green (new: ScoreRulesTest 7, RoomRegistryTest grace and
room-score cases, GuestSessionServiceTest, GameResultRepositoryTest 2). Smoke all
passed including the new reconnect check (same room, same tile). Live server: the
three smoke players died 15s after their sockets closed ("did not come back in
time") and `/api/ranking` returned them best-first.

Verified: `./gradlew test` 79 passing; `node e2e/smoke-two-sockets.mjs` 10 consecutive
clean runs with players meeting after 1–3 door transits. Two browser tabs confirmed
movement replication and one-way bush concealment.

Swing, hold-B, reload fix, UI: `./gradlew test` 138 passing, smoke 8/8 including the
swing audience and coordinates. Browser: new title, Game Boy buttons, gauge fills over
500ms and hides, press-and-release leaves the item, swing arc drawn and cleared,
탈락 → 처음으로 → lobby → rejoin under a new name; no console errors.

Loot time and hit feedback: `./gradlew test` 133 passing, smoke 5/5. Browser: loot
completes 502ms after B, a move in the same tick cancels it with the item left on the
floor; a pan blow flashed the victim's board red (hp 100 → 85) and the attacker's
white. Note: Chrome throttles timers in hidden tabs, so scripted two-tab tests must
send one step per call rather than loop on `setTimeout`.

Steps 4–5: `./gradlew test` 128 passing. Smoke suite 8/8 clean, now also checking
pickup, HIT audience and payload, and SHOT path start; the combat check SKIPs when the
meeting room has no weapon (about a third of runs). Browser, two tabs: pickup, swap
leaving the pistol on the floor, no re-pickup score, `•` trail drawn and cleared,
four pistol shots killing a player hidden in a bush (190 = 10 + 4×20 + 100), GAME OVER
overlay, body gone from the killer's view, restart with the same nickname. No console
errors.

## In Progress

Nothing.

## Next

1. AWS deployment — Phase 1 onwards in `docs/AWS_DEPLOYMENT.md`, which carries its own
   state. Console work is the user's; the doc says what to click and what to record.

## Known Issues

- No `DEAD` event; others learn of a death when the body leaves the next snapshot.
- A Medkit at full HP is spent for nothing. An "only when hurt" rule was tried and
  reverted: not in the docs, and `SnapshotFilterTest` expects `HEAL` at full HP.
- `bootRun` copies static resources at build time; editing `src/main/resources/static`
  needs a restart.

## Recent Decisions

- **AWS: one EC2 (jar + systemd behind Nginx) and RDS for MySQL 8.0.** No ALB, Redis
  or containers. Rationale and rejected options in `docs/AWS_DEPLOYMENT.md` §6.
- **Nginx must forward `Host $http_host` and `X-Forwarded-Port`.** Found in the local
  rehearsal: without them the browser's WebSocket handshake gets 403 from Spring's
  same-origin check on any non-80 port, while Node clients (no Origin) pass.

- **Empty hands punch; junk never hits softer than a fist.** User's call (2026-09-29),
  replacing "Spoon does nothing" and "no item, no A". There is no drop action, so
  weaker junk would trap its holder. Fist 5, Spoon/Doll 5, Cup 6 (user: "+1"),
  Recorder 7, Register 8. Cabinets still forbid every attack.
- **Bat and Crossbow added**, each with its own role: bat slow and heavy against the
  knife's quick jabs; crossbow scarce, short and slow against the pistol. Loot
  nothing 45, junk 6 each, Pan 5, Medkit 8, Knife 5, Bat 4, Crossbow 2, Pistol 1 —
  my numbers, not playtested.
- **No reload.** User's call (2026-09-28). A pistol comes with 6 rounds; the last shot
  uses it up after the strike, so a kill with it still names the pistol. Consumed like
  a medkit, not dropped. Reload may return with an ammo system.
- **A room holds one floor item or none.** User's call: loot should mean travelling.
  One roll at creation; a hit lands on one of the map's 4 `*` tiles, chosen per roll.
  Weights are in `GameConstants.LOOT_WEIGHT_*` and GAME_RULES §8.
- **Loot regrows after 30s of the room standing empty and bare — the clock pauses,
  never resets.** No regrowth was tried first: the population cap keeps nearly every
  room alive as a neighbour, so the world dried up (smoke: 60 rooms, no weapon). A
  reset-on-visit clock failed the same way for anyone touring a small world. Pausing
  keeps camping useless while rewarding rooms you left behind.

- **Floor items do not say what they are.** User's call: "보이면 루팅을 왜 해?"
  Everything on the floor is `$`; you learn what it is when the loot lands in your
  hand. The kind is not sent at all (`FloorItem` is id/x/y), so devtools shows
  nothing either. Replaces the old "items are public" visibility rule in `CLAUDE.md`.

- **A token lives until death, not until the socket closes.** That is what lets a
  reconnect inside the 15s grace find the same player, and retiring it on death stops
  a late reconnect resurrecting them. The client keeps it in `sessionStorage`, so a
  page reload inside the grace resumes the same player; per tab, so two tabs stay two
  players. A refused resume clears it and returns to the lobby.
- **Results are written off the loop thread.** `ResultRecorder` hands each save to one
  writer thread; a DB write can outlast a tick. No `PlayerAccount` until accounts exist.
- **Survival score keeps accruing while disconnected.** At most +1 in the 15s grace;
  not worth a special case.

- **Cabinets are walked into, not pressed.** User's call, replacing B HIDE/UNHIDE.
  One occupant; an occupied cabinet blocks like a player, so bumping into it is how
  you learn someone is inside. Accepted: you only learn by trying.
- **Leave by moving any way but straight on; no walking through.** User chose
  direction-key exits. Refusing the entry direction is what stops a held key from
  carrying you out the far side 400ms later, since the server cannot tell held from
  pressed. Facing is frozen inside so the rule has something to go by.

- **The victim learns who killed them and with what.** Hidden while alive; told only
  to the dead player. Accepted that a restart then knows that name carries that
  weapon. Words stay on the client (`DEATH_VERB`, default `에 당해`).
- **A lower left, B upper right — mirrored from the Game Boy on purpose.** The original
  has A upper right; the user chose the swap after playing. It also lines up with the
  keyboard, where J (left) is A and K (right) is B.

- **Looting lasts only while B is held.** User's call. `RELEASE_B` is a new inbound
  intent, not state. Doors and cabinets act on press and ignore release.
- **A melee swing is visible to the whole room**, hit or miss, starting on the
  attacker's tile — swinging from a bush gives you away, same as firing.
- **Death returns to the lobby.** User's call, replacing the old instant same-name
  restart; the last name is prefilled and selected.
- **Theme is the film *Battle Royale*.** Title BATTLE ROYALE; copy only, no rules
  changed by it.

- **Looting takes 10 ticks and leaving the tile resets it.** User's call: grabbing
  under fire should be a commitment. You may move meanwhile; a position change
  cancels, turning, attacking or being hit does not. The loot is bound to the item id,
  so an item snatched first is not replaced by whatever lies there next.
- **Red border is the victim's, not the attacker's.** It is driven by the victim's own
  HP drop in the snapshot, so it needs no new event and leaks nothing. The attacker's
  HIT stays a board-wide neutral flash.

- **Pan and Spoon expand the V1 item set** so a real weapon is a lucky find, as in
  *Battle Royale*; recorded as an exception in `CLAUDE.md`. Pan is range 1, 15 damage,
  10-tick cooldown (starting values, not playtested).
- **Item rolls use their own `Random`.** Sharing the world's would shift every door
  choice whenever the spawn table changed, moving the encounter measurements.
- **New rooms are primed, not filled inline.** The roll is marked due and made in
  `tickRooms`, before the first broadcast, so `createRoom` needs no tick.
- **No item spawn or death drop within reach of a door.** B resolves DOOR before
  PICKUP, so such an item could never be taken. Kept the documented priority and fixed
  the placement instead; ARENA and GALLERY had one each.

- **A SHOT path ends on the victim's tile, even one hidden in a bush.** It reveals the
  victim's tile to the room and the distance to the shooter. Chosen by the user over
  drawing the path as if the hidden player were absent, and over per-viewer paths.
- **A dead player's item drops where they died**, ammo intact; on a neighbouring tile
  if one already lies there or they died in a cabinet.
- **One A cooldown**, set by the last action.
- **Events are queued on the Room and sent after that tick's snapshot**, each to its
  own audience. The dead are reaped after the broadcast so the victim sees HP 0.
- **Knife emits no SHOT.** Same scan at range 1; it can stab into a cabinet.
- **A hit that kills pays both** +20 hit and +100 kill.

- **Persistent world.** No rounds, no winner. What V1 forbids is writing world state to
  the database, not the model itself.
- **Shots resolve instantly along a line.** A travelling projectile would need tunnelling
  checks and interpolation for no gain at this tile size. `•` is a 100ms flourish.
- **Bushes are a deliberate FOV exception.** One-way visibility, per bush region. Every
  other visibility rule stays global.
- **Cabinets stop bullets and can be attacked blind.** The occupant is hidden and frozen,
  Medkit the one allowed action. No time limit, because being attackable is what keeps it
  honest.
- **Rooms are a linked graph capped by population**, not an infinite coordinate grid. The
  grid was tried: finding another player was a 2D random walk and a third of simulated
  pairs never met at all. A cap forces doors to fold back into the world.
- **The world is always one connected component.** Restored once per tick rather than
  patched wherever a link is chosen — a fresh login opens an unlinked room, and
  discarding rooms can cut a chain in half.
- **A saturated world opens a one-way passage instead of growing.** Four rooms of four
  doors saturate at eight links, after which every new door was creating a room and the
  cap stopped meaning anything. One-way links are only allowed to a room that already
  aims a door back here; chaining them otherwise strands the player with no way home and
  no doorway to arrive beside. `everyDoorHasAWayBack` guards it.
- **Newly opened doors lean 40% toward occupied rooms — only ones whose facing door
  is free.** User's call (2026-09-29, option B): leaving by the top wall and arriving
  at the top wall read as a bug. The old any-occupied-room bias made one link in ten
  sideways (90% facing). Facing-only at 30% gave 99% facing but a worst case of 18
  transits; 40% gives 98% facing, 2.3 average, 11 worst — better than the old rule on
  every count. Measurements in `GameConstants.ENCOUNTER_BIAS_PERCENT`; guarded by
  `mostDoorsLeadToTheFacingWall` (>= 95%).
- **Arrival is just inside the door you came through**, not a random tile. A pursuer has
  to appear where their quarry did or a chase stops reading as one.
- **No entry invulnerability.** A fresh login starts alone so it needs no shield, and on
  door transits the encounter is the game. Camping is tempered by there being four doors
  and only one of you.
- **Move cooldown 150ms plus a one-slot input buffer.** 200ms felt like wading. Lowering
  the cooldown alone was not enough: input arriving mid-cooldown was discarded, so a held
  direction skipped a beat.
- **`Snapshot.Other` is a record and that is the contract.** It carries id, position,
  facing, alive. Adding a field publishes it to every opponent and breaks a test.
- **A/B are tokens, not display text.** The server decides meaning, the client picks
  words.
- **`game/core` imports nothing.** `RoomSimulator` lives in `game/rule` because it
  orchestrates rules; putting it in `core` would invert the dependency.
- **One command queue at `RoomRegistry`, not one per room.** A single loop thread gains
  nothing from per-room queues, and routing would need two threads to agree.
- **Maps are authored, not generated**, and validated at class-load: row shape, door
  placement, furniture counts, cabinet reachability, no cabinet touching a bush, and full
  tile reachability. Eight layouts, because rooms are constantly discarded and rebuilt and
  three read as the same pair repeating.
- **Spring Boot 4.1.1 and Jackson 3** (`tools.jackson.*`). start.spring.io no longer
  serves 3.x and the starter names changed.

## Testing Notes

- Back-to-back smoke runs share the world with the previous run's players for their
  15s disconnect grace. The wander loop skips an unreachable door instead of giving
  up, since a lingering player can block the path. Six consecutive runs clean.
- The smoke combat check strikes with bare hands, so it no longer depends on loot.
  Gun SHOT paths are left to `CombatSimulationTest`.

- Tests must be deterministic. `Room` has no `hashCode`, so iterating a `HashSet<Room>`
  used identity-hash order and the encounter measurements swung between 10 and 16 door
  transits for identical code. Room iteration is now insertion-ordered throughout; if a
  number moves without a code change, suspect ordering first.
- The socket suite asserts contracts, not preferences. Arrival is *beside a doorway*
  (invariant); *which* wall is a preference the server may miss when the facing door is
  taken. It also treats B walking into a bush as a correct outcome rather than a missing
  update, because concealment removes B from A's view entirely.

## Tuning Candidates

- 150ms movement once combat exists — Knife's 500ms cooldown needs melee to stay viable.
- `ENCOUNTER_BIAS_PERCENT` 40 (facing-only): average 2.3 door transits, worst 11.
- `ROOMS_PER_PLAYER` 2, `MIN_ROOMS` 4.
- Loot weights and `LOOT_REGROW_TICKS` 600: a real weapon about one room in eight,
  a pistol one in a hundred. Watch whether fights are mostly fists and junk.
- Fist 5 (20 blows). Whether bare-hand brawls drag on too long.
- Whether door camping becomes dominant without an entry shield.
