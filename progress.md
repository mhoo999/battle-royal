# Progress

## Current Milestone

V1 Multiplayer Core

## Current Task

Step 4 — combat: hit resolution along a line, Knife/Pistol/Medkit, damage and death.
None of it is written yet.

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

Verified: `./gradlew test` 79 passing; `node e2e/smoke-two-sockets.mjs` 10 consecutive
clean runs with players meeting after 1–3 door transits. Two browser tabs confirmed
movement replication and one-way bush concealment.

Committed as `feat: V1 world, movement and real-time sync`.

## In Progress

Nothing. The tree is green.

## Next

1. `game/rule/CombatRules` and hit resolution: bushes pass, walls stop, a cabinet stops
   the shot and wounds its occupant.
2. Wire `ACTION_A` in `RoomSimulator` — Knife, Pistol (fire, reload at zero), Medkit.
3. Introduce `GameEvent` and an event path to the client. `SHOT` carries the tile path
   starting at the shooter; `HIT` carries nothing but the fact.
4. Client: draw the shot path, react to HP changes.
5. Tests: range edges, wall blocking, cabinet blocking plus occupant damage, bush
   pass-through hitting a concealed player, A-at-zero-ammo reloads, cooldown rejection,
   and that `HIT` names no target.

## Known Issues

- No combat, so nobody can die and score never moves.
- Items never spawn. `ActionResolver.actionB` only handles doors and cabinets.
- A dropped socket removes the player immediately. The 15-second grace period that stops
  players quitting to escape a fight is not implemented.
- `bootRun` copies static resources at build time; editing `src/main/resources/static`
  needs a restart.

## Recent Decisions

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
