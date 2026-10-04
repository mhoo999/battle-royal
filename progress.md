# Progress

## Current Milestone

V1 done (2026-10-03). V2.0, V2.1 and V2.2 (without the player market) live since
2026-10-04 on https://battleroyale.site. Tags `v1.0`, `v2.0`, `v2.1`. Plan:
`docs/V2_PLAN.md`. Numbers: `docs/GAME_RULES.md`. This file was condensed on
2026-10-04; the history before that is in git (`git log -- progress.md`).

## Current Task

None in flight. Everything on `main` is deployed (last code deploy `60f2d7e`, Actions
run 37192660410). **Season 1 ends 2026-11-01 00:00 KST** — the first wipe in
production. Exact next step: see Next, item 1.

## Completed

**V1 (2026-09 – 10-03).** Server-authoritative 20Hz loop, one loop thread; torus world
sized to the population; 15x15 authored rooms; instant line hits; bushes (one-way) and
cabinets; fists, knife, bat, pan, pistol, crossbow, medkit, junk; loot crates with
regrowth; score, results, ranking; reconnect grace 15s and reload resume; touch zones
and Game Boy controls; CSS Grid renderer. AWS: EC2 t3.micro (jar + systemd behind
Nginx) and RDS MySQL 8.4, battleroyale.site with Let's Encrypt, SSH closed (Session
Manager), four CloudWatch alarms, CD via GitHub Actions + OIDC + S3 + SSM with rollback
to `app.jar.prev`; docs-only pushes do not deploy. Details: `docs/AWS_DEPLOYMENT.md`.

**V2.0 extraction loop (live 2026-10-04).** Google sign-in (`openid` only) and a unique
nickname (blocklist filter); guests play unranked as `~name`. Crates opened by holding
B; three-slot inventory with an equipped slot; stash in the DB (`stash_item`, `sortie`);
two private exits with a compass, 5s held extraction; death loses what was carried;
start-up refunds sorties a stopped server left open (D6). Trader with values
(`ItemValues`), money, ranking by haul (finds only). Empty guns reload from
ROUNDS/BOLTS bundles. Hideout like the title screen with a short ASCII banner
(`tools/hideout-art.py`); every inventory is a grid, looting window, 상점 and 창고 in
4x4 pages; bag window closes on any step.

**V2.1 (live 2026-10-04).** Stash 10 → 20 (500원) → 40 (2,000원) from the 창고 page.
Bags in their own slot: small +2 (150원), big +4 (450원), lost on death, refused while
the slots they would remove hold anything; island 2% / 0.5%. Seasons: 28 days, ending
at midnight Seoul (`SeasonService`, checked each minute); at the deadline account
players on the island get `SEASON_OVER`, trophies by final rank (CHAMPION / TOP10 /
PARTICIPANT), then ranking, stash items, money, stash size and quests are wiped.

**V2.2 (live 2026-10-04).** Six rooms from the film's island (14 layouts). The trader's
ten errands (`Quests.ALL`): deliveries from the stash and kills counted when a trip
ends; money and sometimes an item; wiped each season. Military outposts: about one new
room in nine, two `Guard`s (own entity, not a `Player`) that turn every 3s and fire
down their line after 0.5s of sight; 60 health, drop 6 rounds, stand again after 2 min
empty; military loot table; no exits in outposts.

**Fixes worth remembering.** A sortie whose socket never attached no longer blocks the
account (retired atomically with the handshake, gear refunded). Stepping off an open
crate closes the bag window. `stash_item.kind` is a VARCHAR, not an ENUM.

Tests: `./gradlew test` all green (~300). Socket smoke passes against a local server.

## In Progress

Nothing.

## Next

1. **Production play check (user).** Signed in on https://battleroyale.site: a trip
   (sortie, extract, stash), a small bag worn out (5 slots), the 20-slot stash once the
   money is there, the first errand (a spoon), an outpost if one turns up, and
   시즌 1 · n일 남음 in the lobby and hideout. Then
   `BASE_URL=https://battleroyale.site node e2e/smoke-two-sockets.mjs` (the agent was
   not permitted to run it against production; with outposts on, a rare failure on the
   walk can be a guard).
2. **Tune numbers from that play (user's impressions).** Prices, loot odds, guard
   strength (60 health = 3 pistol shots), quest rewards are all starting values.
3. **Season length after season 1 (user decides by 2026-11-01).** `SEASON_DAYS` is 28;
   the user leans towards about three months (91 days suggested). Applies from the next
   season; season 1's deadline is stored in `season.ends_at`.
4. Hideout look and touch input on a real phone; rollback drill; `sudo reboot` check
   (the last two drop everyone).
5. Player market: **postponed (user, 2026-10-04).** If it comes back: fixed-price listings
   only, no auction (the user does not want auctions yet).
6. Later: two EC2 instances with deploys that keep players (CLAUDE.md §10). V2 makes
   this more pressing: a restart costs players the trip they are on (gear is refunded).

## Known Issues

- **`stash_item.location` and `sortie.outcome` are MySQL ENUM columns.** `ddl-auto=update`
  never alters them, so a new Location or Outcome value needs a manual `ALTER TABLE` on
  RDS first. Seasons reuse REFUNDED for that reason.
- **An item passed between two accounts in a crate can be duplicated by a restart.** If
  account A's stash item ends up extracted by B and the server restarts while A is still
  out, D6 refunds A's copy too. Needs a restart mid-trip and two accounts.
- **V1 `game_result` rows stay in the table**; nothing reads them since v2.0.
- **The Google client secret was pasted into a chat once (2026-10-04).** Rotation was
  advised (local env and `/etc/battle-royal/env`); not confirmed done.
- `bootRun` serves static files copied at build time: after editing
  `src/main/resources/static`, run `./gradlew processResources` (a running server picks
  them up) and fetch `game.js`/`game.css` with `cache: 'reload'`. Background tabs throttle
  timers, so held-input cadence cannot be measured from an automated tab.
- A second local server for checks needs its own DB: `--server.port=8081
  --spring.datasource.url=jdbc:h2:mem:check` (the shared H2 file's AUTO_SERVER times out).

## Recent Decisions

Standing decisions; each was the user's call unless marked otherwise.

- **Server-authoritative, persistent world**, no rounds; instant line hits; global
  visibility except one-way bushes; cabinets hide, freeze and stop bullets; no entry
  invulnerability; 150ms moves with a one-slot buffer; no client prediction.
- **Torus world sized to the population** (3x3 for 1–2 players, about 7 rooms per other
  player); doors always reach the facing wall; neighbours never share a layout; arrival
  just inside the door. Target: meeting someone after 4–5 doors.
- **Floor items are anonymous crates**; what is inside is learned by opening it.
- **One A cooldown**; empty hands punch (5); junk never softer than a fist.
- **V2 is an extraction game on the same island** (`docs/V2_PLAN.md` D1–D15): stash,
  sorties, private exits, haul ranking (finds only), seasons with wipes and trophies.
- **A full stash still takes everything extracted**, and quest reward items too; only
  buying needs a free slot.
- **Guards are not players**: no score, kills, results or population; their kills do not
  count for errands. They see by the players' rules.
- **No player market for now, and no auction** (2026-10-04).
- **Seasons**: 4 weeks for season 1 so the first wipe runs in production within a month,
  probably about 3 months after (pending, Next 3).
- **Deploys drop every player** (one server, world in memory), so docs-only pushes do not
  deploy and code deploys are asked for first.
- **Architecture**: `game/core` imports nothing; `game/rule` pure; one loop thread mutates
  rooms, commands cross threads once at `RoomRegistry`; `Snapshot.Other` and
  `Snapshot.GuardView` are the contract for what opponents see; A/B are tokens.
- **Infra**: one EC2 + RDS, no ALB/Redis/containers until a second instance is needed;
  OIDC + S3 + SSM deploys, no SSH; RDS on MySQL 8.4 (8.0 forces paid Extended Support).

## Testing Notes

- Tests must be deterministic. Room iteration is insertion-ordered; if a number moves
  without a code change, suspect ordering first. Seeded `RoomRegistry` constructors make
  no outposts (`withOutposts(Random)` turns them on), and item, exit and outpost rolls
  each have their own `Random`, so the encounter measurements stay put.
- The socket smoke takes 40–110s a run; a shorter timeout looks like a hang. CI runs it
  against `--game.outposts=false` (guards on a random route fail walkers at random).
  Back-to-back runs share the world with the last run's players for 15s.
- Shipped templates must have exactly 2 bush regions (`MapTemplateTest`), besides what
  `MapTemplate.parse` checks at class load.

## Tuning Candidates

- Guard strength (60 health, 20 damage, 0.5s reaction, 1.5s fire) and outpost frequency
  (1 in 9).
- Economy: trader prices, stash and bag prices, quest rewards; whether money piles up or
  never comes.
- Loot weights (out of 200) and the military table; regrowth 30s.
- `ROOMS_PER_OTHER_PLAYER` 7: watch whether meetings feel too rare or too frequent.
- Fist 5 (20 blows): whether bare-hand brawls drag.
- Season length after season 1.
