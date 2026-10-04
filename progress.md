# Progress

## Current Milestone

V1 done (2026-10-03, user's call). V2 planning: `docs/V2_PLAN.md`.

## Current Task

**On branch `v2`.** V2.0 (extraction loop, `docs/V2_PLAN.md` §7) is built here and merged
to `main` in one go; `main` stays the live V1 (fixes tagged v1.1, v1.2 …). Steps 2–4 are
done: crates, three-slot inventory, bag window, stash in the DB, sorties, two private
exits and a compass, extraction, trader, money, ranking by haul; empty guns stay and
reload from ammo bundles bought apart. The hideout (2026-10-04) opens like the title
screen: a short ASCII banner on top (rows 9-33 of `tools/hideout-art.py`, a ruin in the
woods cut out of a big moon), block-letter HIDEOUT, a red 隠れ家 rule, one column of
equal buttons; 창고 and 상점 are pages of their own. Every inventory is a grid. MySQL 8.4 rehearsal
passed. Stash upgrades wait for V2.1. Exact next step: see Next, item 1.

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
- [x] `web` — GameSessionService, SessionController
- [x] Client — lobby, CSS Grid 15x15 renderer, D-pad + A/B, death overlay
- [x] Movement: 150ms cooldown with a one-slot input buffer
- [x] Rooms: torus grid sized to the population (replaced the capped linked graph)
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
      Client auto-retries 15× at 1s. When that runs out or the token is refused, a
      "자고 있는 사이에 야생 동물에 당해 끔찍한 시체가 되었다." overlay (user's
      wording) with 처음으로 replaces the frozen board;
      coming back to the page (or online) retries at once (2026-10-03).
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
- [x] AWS deployment Phases 1–8 (2026-10-03): EC2 t3.micro (jar + systemd behind
      Nginx), RDS MySQL 8.4, Elastic IP. Remote smoke all passed; browser play
      confirmed by the user. Values and steps in `docs/AWS_DEPLOYMENT.md`.
- [x] V2 step 1, accounts (branch `v2`, 2026-10-03): Spring Security OAuth2 client,
      Google sign-in with the `openid` scope only; `Account` stores Google's `sub` and a
      unique nickname, nothing else. Lobby: guest form + Google link; signed in, pick a
      nickname once, then START plays under it. Guests get the `~` prefix (unranked).
      `GuestSessionService` renamed `GameSessionService`. 202 tests (AccountServiceTest,
      SignInWebTest with oidcLogin). Not yet done: a real Google round trip (needs the
      client ID and secret as GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET; the ID is in
      V2_PLAN §5, the secret stays with the user).
- [x] Grid inventories (2026-10-04, user): bag, crate (6 cells), stash (5x4: 10 usable, the
      next 10 locked as dashed `…`), loadout and the trader are square-cell grids. 상점 is
      the stock left, the stash right; picking a cell shows 구매 (가격) / 판매 (가격) in a
      bar below. Hideout picture moved from a backdrop to a short banner above the title;
      menu buttons one width, counts pinned right. Fix: stepping off an open crate now
      closes the bag window too (the server already shut the crate; the client kept the
      window). Emptying a crate in place keeps the bag open.
      Then (user): the looting window (crate | bag) and 상점 (stock | stash) show 4x4 pages
      on both sides with a ‹ n/m › pager, so the two grids are one size; cells past what a
      grid holds are drawn disabled (dashed `…`), never left out. The 창고 page keeps 5x4.
- [x] Production Google secrets (2026-10-04, user): GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET in
      `/etc/battle-royal/env` on EC2, service not restarted (V1 ignores them; the v2.0
      deploy picks them up). Redirect URIs for apex, www and localhost checked registered
      against Google's auth endpoint. www is registered because it serves 200 rather than
      redirecting to the apex.
- [x] Ammo system (branch `v2`, 2026-10-03, user's call): an empty pistol/crossbow stays in
      hand (A does nothing); ROUNDS/BOLTS bundles take a slot, A=RELOAD fills the empty gun
      (RELOAD_TICKS 30); bundles spawn on the island; trader sells guns empty and ammo apart.
      Hideout art (2026-10-04, user: "too unlike the title, go mono, buttons in front"):
      one mint like the title, depth by glyph density and five opacity tiers (`.t1`–`.t5`),
      the title's red offset only on the moon and lit window; 64 columns across the foot of
      the screen with sky above, so 섬으로/뒤로 (solid background) stand in front of the hut
      and the moon sits in the trader list's empty middle. Screenshot
      `2026-10-04-v2-hideout-mono-behind-buttons.png`.
      Then (user: "a menu over the picture") the hideout became a front and two pages:
      the front is 창고 (stash count) / 상점 / 섬으로 (what is carried) / 뒤로 over the sky
      with the hut and moon in view; 창고 is stash + three slots, 상점 is 사기 plus a 팔기
      list of every stash item (selling one that was in a slot takes it out of the slot);
      each page has 거점으로, the picture dims behind it. Client only. Screenshot
      `2026-10-04-v2-hideout-menu.png`.
      Then (user: "behind the buttons, mono, like a Princess Maker ending picture") the
      picture became a framed illustration: painted in grayscale by `tools/hideout-art.py`
      (hut, full moon, a student with a day pack walking the lit path), dithered into
      120x144 mint block cells, fitted whole to the screen in a double-line frame; the
      title sits over its sky and the menu is a double-bordered window over its foot,
      sized from the frame height so the hut stays in view on short screens. Screenshot
      `2026-10-04-v2-hideout-ending-picture.png`.
      Then (user: "look at ASCII art on Pinterest") redrawn as classic ASCII art after
      looking at Pinterest and asciiart.eu: the dithered blocks read as pixel art, real
      ASCII art is line drawing with a lot of black. `tools/hideout-art.py` now draws
      64x78: 45-degree mountains with snow, tiered pines, hut/smoke/path/student from
      hand-made pieces, only the moon shaded with a character ramp; tiers t1-t5 retuned
      for thin lines. Screenshot `2026-10-04-v2-hideout-ascii-art.png`.
      Then (user: "a lived-in hut is not Battle Royale; a ruin, a makeshift shelter, and
      in the trees, not out in the open") the hut became a ruin: broken concrete walls, a
      tarp strung over a bedroll and a pack, no chimney smoke or lit window, the moon
      the only light; a dim treeline behind and pines in front half over its walls, a
      winding trail, barbed wire cut where the trail passes. Screenshot
      `2026-10-04-v2-hideout-ruin-in-woods.png`.
      Then (user: "better quality, the title's mood, big buttons in the middle") the
      hideout front copies the title screen: block-letter HIDEOUT in the same font and
      shadow (`.logo`), a red 隠れ家 rule (`.logo-rule`), big centred buttons, no frame.
      The picture runs along the foot of the screen (64x36, under half the height): a big
      moon with the ruin, pines and treeline cut out of it in black, the moon's face with
      the title's red offset. Pages hide the heading. Screenshot
      `2026-10-04-v2-hideout-title-style.png`. GAME_RULES/NETWORK_PROTOCOL/CLAUDE.md now
      describe reload and the new prices. 256 tests.
- [x] V2 trader and value ranking (branch `v2`, 2026-10-03): `ItemValues` price table
      (a gun's value falls with each shot; the trader sells gear at 3x, no junk);
      `account.money` and `account.haul` (default 0 so `ddl-auto=update` can add them to
      a table with rows: without it H2 refused the column, and so might prod);
      `/api/hideout/sell`, `/buy` under the account row lock; `/api/ranking` is now
      `{top, me}` by haul. Hideout art pins Consolas: a Korean font drew `\` as `₩`.
      251 tests.
- [x] V2 exits, compass, extraction (branch `v2`, 2026-10-03): each new player gets two
      exits 2 and 3 doors from the start room (capped by the island), on plain floor out of
      door reach, private to them; `self.exits` carries torus bearings and the tile in its
      own room (`◎`). B on your exit is EXTRACT, held 100 ticks, broken by moving, letting go
      or a hit. `EXTRACTED` to that player; listeners (renamed `DepartureListener`) retire
      the token, save the result, and settle the sortie: own gear home with its ammo, finds
      added, gear left on the island deleted. Exits re-roll if a shrink drops their room.
      Smoke walks a guest to an exit by compass and out (5.1s). 243 tests.
- [x] V2 stash and hideout (branch `v2`, 2026-10-03): `stash_item` (STASH or OUT with a
      sortie) and `sortie` (OUT, DIED, EXTRACTED, REFUNDED). Setting out locks the account
      row and refuses a second open sortie; death deletes what was carried on its own
      writer thread; start-up refunds sorties a stopped server left open (D6). Signed-in
      START opens the hideout: pick from a 10-slot stash into 3 slots, then 섬으로.
      `/api/session` is guests only now. 226 tests.
- [x] V2 crates and inventory (branch `v2`, 2026-10-03): the floor holds crates; B held
      opens one for the opener alone; a bag window over the board (button, `I`) shows
      the crate left and three inventory slots right; pick, then pick where it goes
      (TAKE swaps into an occupied slot, PUT returns one, the same slot again equips);
      `e` marks the equipped slot (`1` `2` `3`). A death drops everything in one crate.
      216 tests, smoke opens and takes; browser checked.
- [x] Lobby: Google and guest first, the name after; a named account starts in one tap.
- [x] Nickname filter (branch `v2`, 2026-10-03, user's request): swearing, sexual
      words and posing as staff, for guests and accounts. List in
      `nickname-blocklist.txt`; names are normalized (spaces, symbols, digit swaps,
      full-width) before matching. Refusals are Korean text the lobby shows as is.
- [x] Ranking cleanup (2026-10-03): a `~name` plays unranked (`UNRANKED_PREFIX`) and
      smoke uses `~alpha`/`~bravo`/`~reconnect`, so production smoke leaves no rows
      (checked). Deleted 20 test rows from the live DB at the user's word, leaving only
      유승훈's two games. `scripts/db-query.ps1` runs SQL on RDS over SSM.
- [x] A death is seen (2026-10-03): `FELL` event with the tile only, to whoever could
      see the victim at the moment of death (worked out before the cabinet flag is
      cleared, since the dead are invisible to `canSee`). Client: ✕ on the tile for 1s,
      "누군가 쓰러졌다" for 2s. Smoke now finishes B off with fists (~10s) to check it.
- [x] Medkit not offered at full HP, so it cannot be wasted; A shows 치료 greyed.
      User's call (2026-10-03). No first-play controls guide: the buttons already
      say what they do (user).
- [x] Floor items drawn as a crate (2026-10-03), not `$`: a player said `$` read as
      money. CSS-drawn outline and strap, so it cannot pass for a cabinet's ■ and looks
      the same in every font; it dims under a player standing on it.
- [x] Input (2026-10-03): touch zones instead of buttons — the left half of the
      controls strip steers by the thumb's side of the drawn cross (slide to turn),
      the right half is whichever of A/B is nearer; on a phone the strip fills the
      height below the board. A fires on press. Held keys repeat on our own 140ms
      timer (OS key repeat paused ~0.5s first); overlapping keys fall back to the one
      still held. Short vibration on Android. GAME_RULES §10 "입력".
- [x] Ops (2026-10-03): SSH port closed (Session Manager instead), four CloudWatch
      alarms to email, domain battleroyale.site with Let's Encrypt HTTPS; http and
      the bare IP no longer serve the game. Remote smoke over wss all passed.
- [x] Continuous deployment (2026-10-03): `deploy` job after the tests in `ci.yml`.
      OIDC role, jar to S3, `deploy/install.sh` run over SSM, rollback to
      `app.jar.prev` if the new jar does not answer in 2 minutes. Docs-only pushes do
      not deploy. First run deployed `4e331af`; remote smoke all passed.
- [x] Torus world (2026-10-03): rooms on a wrapping grid, 3x3 for 1–2 players and about
      7 rooms per other player beyond (`WorldSize`). Doors always reach the facing wall;
      no neighbours share a layout; a fresh login starts with nobody next door. Grows at
      once, shrinks only when a newcomer would not regrow it and the dropped rooms are
      empty. Replaced the encounter bias, one-way links, island bridging and room GC.
      Fixes the reported "stuck in one room" loop.

Torus: `./gradlew test` 182 passing (two wanderers on 3x3 meet after 5.3 doors on
average). Smoke 3/3 in a row, with a row sweep instead of a fixed UP/RIGHT/DOWN/LEFT
cycle (that only circles four rooms on a torus); the world grew to 6x5 with lingering
players and shrank back. Browser: RIGHT x3 and UP x3 each came back to the start room,
arriving beside the facing door every time; no console errors.

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
room-score cases, GameSessionServiceTest, GameResultRepositoryTest 2). Smoke all
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

1. **Google sign-in, for real (user, local).** Run locally with GOOGLE_CLIENT_ID /
   GOOGLE_CLIENT_SECRET set and do one real round trip: sign in, pick a nickname, land in
   the hideout, set out, extract, see the haul in the stash. Nothing has exercised the
   real Google hop yet (tests use `oidcLogin`).
2. **Merge `v2` into `main` as v2.0 — ask first, it deploys.** Then on production:
   `account` / `stash_item` / `sortie` created on RDS, smoke, a browser trip, a
   screenshot. The lobby ranking starts empty (Known Issues).
3. Hideout look, on a real phone: the art is sized from the viewport, never checked off
   a desktop browser. Tweak `tools/hideout-art.py` if the user wants more.
4. Left over from V1, whenever convenient: real-phone check of touch input; rollback
   drill; `sudo reboot` comes back on its own. Both of the last two drop everyone.
5. V2.1: stash upgrades (`docs/V2_PLAN.md` §7).
6. Later: two EC2 instances with deploys that keep players (world handoff or rooms
   pinned to servers, CLAUDE.md §10). V2 makes this more pressing: a restart costs
   players their gear.

## Known Issues

- **The lobby ranking starts empty after v2.0.** It now ranks accounts by haul; the V1
  `game_result` rows stay in the table but nothing reads them.
- **`stash_item.location` and `sortie.outcome` are MySQL ENUM columns.** `ddl-auto=update`
  never alters them, so adding a Location or Outcome value needs a manual `ALTER TABLE`
  first. `kind`, the one that grows, is a VARCHAR (see Recent Decisions).

- `bootRun` copies static resources at build time; editing `src/main/resources/static`
  needs a restart, and Chrome caches `game.js`/`game.css`: fetch them with
  `cache: 'reload'` (or hard-reload) before testing a client change. Background tabs
  also throttle timers, so held-input cadence cannot be measured from an automated
  tab; check it on a real phone.

## Recent Decisions

- **MySQL 8.4 rehearsal for v2.0 (2026-10-04).** Docker MySQL 8.4 with the V1 schema and
  rows (V1 prod jar from `main`), then the V2 prod jar on the same DB: `account`,
  `stash_item`, `sortie` created, `game_result` and its rows untouched, `account.money`/
  `haul` default 0, smoke 100% on MySQL. It found `stash_item.kind` made an ENUM, which
  `update` would freeze at today's kinds; `kind` is now stored as a plain String (an
  AttributeConverter still got a CHECK listing the names).

- **Only finds count towards the ranking.** Haul = value of extracted items that did
  not leave the stash on that trip; otherwise a stash pistol walked in and out would farm
  it. Prices are a first draft (Q12).
- **Stash upgrades wait for V2.1**, as V2_PLAN §7 says, though step 4's line lists them.

- **A full stash still takes the whole haul (V2 step 3, temporary).** Losing loot at the
  hideout door would punish the best trips, and there is no way to make room until step
  4's traders. The count shows e.g. 11/10. Revisit with selling.
- **Guests get exits and can extract**; they just have no stash. The portfolio visitor
  should see the whole loop without a Google account.
- **Known hole (V2 step 3):** an item one account put in a crate and another account
  extracted with is a new row for the second; if the server restarts while the first is
  still out, D6 refunds the first one's copy too. Needs a restart and two accounts.

- **A game that ended while the socket was down says so, without a record.** The
  client cannot tell an expired grace (result saved) from a server restart (nothing
  saved), so the overlay claims neither. Grace stays 15s: the user was offered 30–60s
  for phones and did not take it.
- **Deploys go through OIDC + S3 + SSM, not SSH.** Runner IPs change, so SSH would have
  meant opening port 22 to the world; this way the repo holds no keys and the server
  opens no port. CD before a second server: the hard part of two servers is the
  in-memory world, and only the last pipeline step changes. Every deploy drops all
  players and resets the world, hence no deploy for docs-only pushes.
- **RDS runs MySQL 8.4, not 8.0.** 8.0 left RDS standard support on 2026-07-31 and a
  new 8.0 instance forces paid Extended Support. No code change; local rehearsal was
  on 8.0.
- **AWS: one EC2 (jar + systemd behind Nginx) and RDS for MySQL.** No ALB, Redis
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
  a medkit, not dropped. **V2 replaces it** (user, 2026-10-03): an empty gun stays,
  ROUNDS/BOLTS bundles reload it, the trader sells guns empty and ammunition apart.
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
- **The world is a torus sized to the population.** User's call (2026-10-03), after a
  player reported being stuck in a loop: the capped linked graph (2 rooms a player,
  minimum 4) folded back into 2–4 room cycles with no geography, and twin layouts made
  them look like one room. Now rooms sit on a wrapping grid; doors are fixed, so a map
  can be learned and a chase followed. Size: 3x3 for 1–2, then about 7 rooms per other
  player (user asked for 1–2 → 3x3, 3–5 → 4x4 and left the rest to me; 4x4 for five
  meets after 2.2 doors, too soon). User's target: meet after 4–5 doors, time to loot.
  Simulated table in `GameConstants.ROOMS_PER_OTHER_PLAYER`.
- **Resizes never move anyone.** Grow at once (before placing a newcomer); shrink only
  when one more join would not regrow it and the rooms being dropped are empty. Edge
  doors may lead somewhere new afterwards; rooms re-added later are new rooms.
- **The unbounded coordinate grid stays rejected.** It was a 2D random walk: a third of
  simulated pairs never met. What makes the torus work is that it wraps and is sized.
- **Neighbours never share a layout**, so walking through a door never looks like
  walking back into the room you left. Only checked on creation; a shrink can make
  two old rooms neighbours.
- **Door order no longer depends on a per-JVM hash salt.** `GridMap` stores doors in an
  `EnumMap`; `Map.copyOf` iterated in a per-run salted order and changed the world.
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
- The socket suite asserts arrival at the facing wall, and finds B with a row sweep:
  east until the room id comes round, then south. It also treats B walking into a bush as a correct outcome rather than a missing
  update, because concealment removes B from A's view entirely.

## Tuning Candidates

- 150ms movement once combat exists — Knife's 500ms cooldown needs melee to stay viable.
- `ROOMS_PER_OTHER_PLAYER` 7: about 4–5 doors to meet in simulation. Real players loot,
  fight and stand still, so watch whether meetings feel too rare or too frequent.
- Loot weights and `LOOT_REGROW_TICKS` 600: a real weapon about one room in eight,
  a pistol one in a hundred. Watch whether fights are mostly fists and junk.
- Fist 5 (20 blows). Whether bare-hand brawls drag on too long.
- Whether door camping becomes dominant without an entry shield.
