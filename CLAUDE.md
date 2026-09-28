# Project: Real-time 2D Survival PvP

## 1. Project Goal

Build a mobile-first browser-based real-time 2D PvP survival game, titled
**BATTLE ROYALE** and themed on the Japanese film *Battle Royale*: stranded
classmates, random weapons, a real gun is a lucky find.

This project is also an AWS/cloud infrastructure portfolio project.

Priorities:

1. Real-time multiplayer synchronization
2. Server-authoritative game logic
3. Simple and maintainable architecture
4. AWS deployment
5. Mobile usability
6. Testability

Do not prioritize visual complexity over gameplay and architecture.

---

## 2. V1 Scope

V1 must include:

- Room creation and deletion
- Room-to-room movement
- Real-time player movement
- WebSocket communication
- Server-authoritative game state
- Player direction
- Player combat
- Instant hit resolution along a line
- Damage and death
- Item pickup
- One-item inventory
- Item swapping
- Knife, Pistol, Medkit, plus junk: Pan (weak melee) and Spoon (useless)
- Reload
- Cabinets and bushes
- Score
- Game result persistence
- Basic ranking

V1 must NOT include:

- Chat, voice chat, friends, matchmaking
- Complex progression, character upgrades, skins
- Monetization
- Bomb
- Complex animation
- Writing real-time world state to the database
- Unnecessary AWS services

Do not expand V1 scope without an explicit decision.

### Four decided exceptions

These override the wording an earlier draft of this document used. Each was decided
deliberately; do not reverse one without saying so first.

| Earlier wording | What V1 actually does | Why |
|---|---|---|
| "No persistent world" | V1 **is** a persistent world: no rounds, no winner, no shrinking zone | What is forbidden is writing world state to the DB, not the persistent-world model |
| "No FOV / fog of war" | Global visibility **except bushes**, which are one-way | Bushes are the only concealment that hides you while you can still act |
| "Fast projectiles" with speed, travel and collision | Shots resolve **instantly** along a line; `•` is a 100ms flourish | A moving entity adds tunnelling and interpolation for no gameplay gain at this tile size |
| "Knife, Pistol, Medkit" as the whole item set | Spawns also roll **nothing**, a **Pan** (weak melee) or a **Spoon** (does nothing) | A real weapon should be a lucky find, as in *Battle Royale*; junk still fills the one slot |

---

## 3. Game Rules

Numbers live in `docs/GAME_RULES.md`. Do not invent one; read it.

### Visibility

Entering a room reveals the whole room. Bushes are the single exception.

Visible: room layout, other players, their facing, where floor items lie, cabinets,
obstacles, doors.

Hidden: enemy HP, equipped item, ammunition, cooldown, next action, whether a cabinet
is occupied, anyone standing in a bush you are not also standing in, and **what a
floor item is** — every one is `$` until you loot it. That is what looting is for.

Never expose hidden enemy state to the client.

### Player representation

`△` player, `▲` enemy. The triangle's orientation is the facing direction. Do not add
separate direction indicators unless readability demands it.

### Items

A player carries exactly one item. Picking up another swaps them: the outgoing item
stays on the floor for anyone else to take. **Never destroy the previous item.**

### Concealment

Cabinets stop bullets, hide the occupant, and freeze them — no attacking, Medkit only,
and the only move is out, never straight through. Walk into an empty one to hide; an
occupied one blocks like a player. They can be attacked blind.

Bushes conceal but do not stop bullets. You can move and attack from inside one.

---

## 4. Controls

**A** performs the equipped item's primary action: Knife attacks, Pistol fires or
reloads when empty, Medkit heals.

**B** performs the contextual interaction: pick up, swap, take a door. Bushes and
cabinets are walked into and out of, never pressed.

The server resolves both and sends the answer in the snapshot as a token
(`ATTACK`, `FIRE`, `PICKUP`, …). The client maps tokens to words. The client never
works out for itself which action is available.

---

## 5. Combat

Combat is server-authoritative. The client sends commands, never state.

Good: `MOVE`, `ACTION_A`, `ACTION_B`

Bad: `SET_POSITION x=100 y=50`, `SET_HP 0`, `DEAL_DAMAGE 100`

The server validates: player state, position, movement cooldown, collision, facing,
attack cooldown, ammo, range, line of fire, and damage.

Never trust client-provided HP, position, damage, inventory, or score.

### Hit resolution

A shot scans tile by tile from the shooter's own tile in the facing direction. Bushes
pass, walls stop it, a cabinet stops it and wounds whoever is inside. The `SHOT` event
carries the path so the client can draw it; the path starts at the shooter, which is
what gives away a bush camper the moment they fire.

`HIT` tells the attacker only that something was hit. Never who, never how badly.

---

## 6. Networking

HTTP for: page load, session creation, game result, ranking.

WebSocket for: movement, attacks, item interaction, room entry and exit, damage, death,
real-time state.

V1 has no chat. Do not introduce WebRTC or STOMP without a specific requirement.

Schemas are in `docs/NETWORK_PROTOCOL.md`. Adding a field to the view other players
receive publishes it to every opponent — re-read the filter rules before you do.

---

## 7. Architecture

Keep game state independent of rendering.

```
game/core, game/rule   pure Java, zero Spring imports, deterministic
       |
game/loop              single 20Hz thread, owns all mutation
       |
ws                     per-player filtering, serialization
       |
client                 renders snapshots, predicts nothing
```

`game/core` imports nothing. `game/rule` may import `core`. Never the other way round.

One game-loop thread mutates room state. Inbound commands cross threads once, at
`RoomRegistry`. Do not add locks. Do not mutate a room from a WebSocket thread.

The renderer must be replaceable without touching game rules.

Keep responsibilities understandable, but do not create abstractions for theoretical
future requirements. See `docs/ARCHITECTURE.md` for the current package layout.

---

## 8. Rendering

ASCII-inspired, not ASCII-implemented: a CSS Grid of individual cells, not one `<pre>`.

```
△   player      ▲   enemy       $   floor item
■   cabinet     ▒   bush        +   door       •   shot
```

The map is the primary UI. Keep it readable on mobile. Do not add unnecessary HUD.

---

## 9. Real-Time State

Rooms, players, items and the map live in server memory only.

Persistent data — player records, game results — belongs in the relational database.
Never write movement or per-tick state to it.

---

## 10. AWS Strategy

Start with one Spring Boot server on EC2, then RDS. Add a service only when there is a
concrete requirement.

- Redis: not until multiple game servers need shared state or inter-server messaging.
  When that day comes, pin each room to one server and use Redis for the room-to-server
  directory and pub/sub — never for 20Hz game state.
- ALB: not until more than one instance is required.

Do not add AWS services merely because they are available.

---

## 11. Testing

Every major mechanic needs tests. Priority: movement, collision, visibility filtering,
hit resolution, item pickup and swap, damage, death, room transition, scoring,
WebSocket synchronization.

Before calling a feature complete:

1. `./gradlew test`
2. Run the application
3. Verify real browser behaviour — see `.claude/skills/game-testing`
4. Update `progress.md`
5. Commit

Never delete or weaken an existing test to make a change pass.

**Tests must be deterministic.** `Room` does not override `hashCode`, so iterating a
`HashSet<Room>` varies per JVM run; the encounter measurements swung between ten and
sixteen door transits for identical code until that was pinned to insertion order. If a
number moves without a code change, suspect ordering before suspecting the number.

### Two-player browser test

Start the backend, open two sessions, and walk the scenario in
`.claude/skills/game-testing`. `node e2e/smoke-two-sockets.mjs` covers the same ground
at the socket level with no dependencies and runs in seconds; prefer it for a quick
check and the browser for anything visual.

---

## 12. Development Workflow

Before implementing a feature:

1. Read `progress.md`.
2. Inspect the relevant files.
3. Understand the current architecture.
4. Identify the smallest implementation required.
5. State the plan.
6. Implement only the requested scope.
7. Run tests.
8. Run the application when applicable.
9. Verify actual behaviour.
10. Update `progress.md`.
11. Commit.

Do not silently expand scope. Do not refactor unrelated code while implementing a
feature. Do not speculate about files you have not read.

---

## 13. Session Continuity

Development must survive across separate sessions. The project, not the conversation,
carries the state.

Do not preserve conversation history in project files. Keep `progress.md` compact: it
is a handoff document, not a log. Summaries over history.

Structure:

```md
# Progress

## Current Milestone
## Current Task
## Completed
## In Progress
## Next
## Known Issues
## Recent Decisions
```

### Before ending a significant task

Record in `progress.md`: what was completed, what was not, the architectural and
gameplay decisions made, known issues, and the exact next step. If the session ends
mid-change, write down the state the code is actually in.

The next session should be able to continue from:

```
CLAUDE.md   progress.md   git status   git diff
```

---

## 14. Git and Recovery

Git is the source of truth for code history, not the conversation.

```
Start task -> git status -> implement -> test -> update progress.md
           -> review diff -> commit
```

Commit messages describe the actual change:

```
feat: add websocket player movement
feat: implement item swapping
fix: prevent shots passing through cabinets
refactor: separate room state from renderer
```

Commit at every checkpoint that leaves the tests green. A commit that cannot build is
worse than no commit.

If a session ends unexpectedly: read `git status`, `git diff`, `progress.md`, then the
affected files, and continue from the actual code state. **Never assume an unfinished
conversational plan was implemented.**

---

## 15. Architecture Decisions

Recorded in `progress.md` under Recent Decisions. Current standing decisions:

- Server-authoritative; the client sends intent only
- Persistent world: no rounds, no winner
- 20Hz tick, 150ms move cooldown, one-slot input buffer, no client prediction
- Shots resolve instantly along a line
- Rooms are a linked graph, capped at `ROOMS_PER_PLAYER` per player, which is what
  makes players find each other
- The room graph is always one connected piece
- Full room visibility except bushes
- Enemy HP, item and ammo hidden
- One-item inventory; a swap drops the previous item
- No entry invulnerability
- Real-time state in memory, results in the relational DB
- One server; Redis only when distributed state requires it; ALB only when a second
  instance does
- ASCII-inspired CSS Grid renderer
- No chat in V1

Do not silently change an established rule. If a new requirement conflicts with one,
name the conflict before changing anything.

---

## 16. Documentation

```
docs/PRD.md               what the product does
docs/ARCHITECTURE.md      how the system is structured
docs/GAME_RULES.md        how gameplay works, and every number
docs/NETWORK_PROTOCOL.md  events, payloads, and the visibility filter
progress.md               what is done and what happens next
```

Avoid duplicating the same information across documents.

---

## 17. Important Rules

Uncertain about a game rule: do not invent a mechanic. Name the ambiguity and ask.

Uncertain about architecture: inspect the existing implementation first, then choose
the simplest thing that satisfies the current requirement. No infrastructure for
hypothetical scale.

Task complete: test it, verify it, update `progress.md`, commit it.

The project itself must contain enough to continue development.
