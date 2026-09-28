# Progress

## Current Milestone

V1 Multiplayer Core

## Current Task

Step 5 — items: spawn points filled at room creation, B pickup and swap, 20s respawn,
+5 pickup score. Once weapons can be picked up, verify Step 4 combat in two browser
tabs (still owed).

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

Verified: `./gradlew test` 79 passing; `node e2e/smoke-two-sockets.mjs` 10 consecutive
clean runs with players meeting after 1–3 door transits. Two browser tabs confirmed
movement replication and one-way bush concealment.

Step 4: `./gradlew test` 112 passing; smoke suite 3/3 clean against the running
server with no loop errors. **Not browser-verified** — nobody can hold a weapon until
Step 5 spawns items.

## In Progress

Nothing. The tree is green.

## Next

1. Step 5: fill `itemSpawns` on room creation (weighted KNIFE/PISTOL/MEDKIT), B
   `PICKUP`/`SWAP` in `ActionResolver` + `RoomSimulator` (swap leaves the old item on
   the floor), 20s respawn per spawn point, +5 once per item id.
2. Browser: two tabs, pick up a pistol, shoot across a room and through a bush, knife
   into a cabinet, die and restart. Extend `e2e/smoke-two-sockets.mjs` with a
   shot/HIT/YOU_DIED check.
3. Step 6: cabinets (HIDE/UNHIDE, 8-tick toggle). `CombatRules` already assumes the
   occupant's `pos` is the cabinet tile — keep that convention.
4. Step 7: 15s disconnect grace, survival score, result persistence, ranking.

## Known Issues

- Items never spawn, so combat is unreachable in real play. `ActionResolver.actionB`
  only handles doors and cabinets.
- Combat not yet verified in a browser (see Next 2).
- No `DEAD` event; others learn of a death when the body leaves the next snapshot.
- `YOU_DIED` is sent but the result is not persisted.
- A Medkit at full HP is spent for nothing. An "only when hurt" rule was tried and
  reverted: not in the docs, and `SnapshotFilterTest` expects `HEAL` at full HP.
- A dropped socket removes the player immediately. The 15-second grace period that stops
  players quitting to escape a fight is not implemented.
- `bootRun` copies static resources at build time; editing `src/main/resources/static`
  needs a restart.

## Recent Decisions

- **A SHOT path ends on the victim's tile, even one hidden in a bush.** It reveals the
  victim's tile to the room and the distance to the shooter. Chosen by the user over
  drawing the path as if the hidden player were absent, and over per-viewer paths.
- **A dead player's item drops where they died**, ammo intact; on a neighbouring tile
  if one already lies there or they died in a cabinet.
- **One A cooldown**, set by the last action. A reload blocks A for 24 ticks and fills
  the magazine at the end, only if the same pistol is still in hand. A press landing on
  the completion tick fires rather than reloading again.
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
- **Newly opened doors lean 30% toward occupied rooms.** The cap alone guarantees a
  meeting but averaged six transits; 30% brings it to about three. See
  `GameConstants.ENCOUNTER_BIAS_PERCENT` for the measurements.
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
- `ENCOUNTER_BIAS_PERCENT` 30: average 3 door transits, worst measured 16.
- `ROOMS_PER_PLAYER` 2, `MIN_ROOMS` 4.
- Whether door camping becomes dominant without an entry shield.
