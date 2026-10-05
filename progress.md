# Progress

## Current Milestone

V1 done (2026-10-03). V2.0, V2.1 and V2.2 (everything but a player market, which is
postponed) live on https://battleroyale.site. V2.3 live 2026-10-05: trader reputation
(skeleton, no unlocks yet), finished errands folding, and bots on the island. Tags `v1.0`, `v2.0`, `v2.1`, `v2.2`. Plan:
`docs/V2_PLAN.md`. Numbers: `docs/GAME_RULES.md`. This file was condensed on
2026-10-04; the history before that is in git (`git log -- progress.md`).

## Current Task

None in flight. Deployed 2026-10-05: `3ad9791` (Actions run 37307045066), the first deploy
that found the server by its `Deploy` tag and uploaded `current/app.jar`. Checked from
outside: page 200, hideout 401 without sign-in, bot sign-in 404 (absent in production),
season 1 intact, new client served. Bots in production are untested by a person yet —
they appear only once someone is on the island. **Season 1 ends 2026-11-01 00:00 KST: the
user takes the snapshot in `docs/AWS_DEPLOYMENT.md` §12 at 10-31 23:50.** Exact next step:
Next, item 1.

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

**V2.2 (live 2026-10-04/05).** Six rooms from the film's island (14 layouts). Military
outposts: about one new room in nine, two soldiers (군인; `Guard`, its own entity, not a
`Player`) that turn every 3s and fire down their line after 0.5s of sight; 60 health,
drop 6 rounds, stand again after 2 min empty; military loot table; no exits in
outposts. The trader's errands are three ladders by kind, easy to hard, one of each
under way at once (`Quests.LADDERS`): 납품 (6, from the stash), 장소 (3, private marks ✦
some doors away, stand on them all and get out), 군인 처치 (3, soldiers counted when a
trip ends). Plus three daily errands a day per account, new at midnight Seoul
(`DailyQuests`). Errands live on the hideout's 의뢰 page. The compass shows needles only:
exits mint, errand marks gold, no labels and no distance.

**V2.3 trader reputation, skeleton (live 2026-10-05).** Every errand done,
ladder or daily, adds standing by difficulty (1 / 2 / 4); levels at 0 / 10 / 25 / 50
(`Reputation`, `account.reputation`). The 의뢰 page shows 평판 Lv.N · n/next, each reward
line 평판 +N; the hideout view carries `standing`. Wiped with the season. Levels unlock
nothing yet.

**Cloud hardening, first pass (2026-10-05).** `docs/ROADMAP.md` holds the plan, roadmap,
marketing and BM options. Terraform (`infra/`, how-to in `infra/README.md`): `shared`
(release bucket, GitHub OIDC and deploy role, instance role, alert topic, production's
Elastic IP) and `prod` (module `game-stack`: security groups, EC2, RDS, alarms), both
imported from the console with plan "No changes". The module builds a whole stack from
nothing: user_data installs Java, Nginx, the jar from `current/app.jar`, systemd and the
certificate; secrets come from Parameter Store `/battle-royal/prod/*` at every start (put
by the user with `scripts/put-secrets.ps1`; all three are in); the database starts from a
snapshot. `infra/rehearsal` is the same stack, unprotected, for a rebuild drill. CI
(live since 3ad9791) finds the server by tag and uploads `current/app.jar`. RDS
deletion protection on; backups stay at 1 day (the Free plan refuses more). Manual
snapshots work on the Free plan (about 1 minute).

**Load-test bots (S2, 2026-10-05).** `e2e/bots.mjs`: N signed-in bots live the whole
loop (hideout, sortie, crates, fights, exit or death, errands, selling). Bot sign-in
`POST /api/loadtest/login/{name}` exists only under the `loadtest` profile, refuses to run
beside `prod`, and leaves the same OIDC session as Google. First local run: 200 bots,
move→snapshot p99 56ms, no errors (`docs/LOAD_TEST.md`, with the measurement pitfalls).

**Bots on the island (V2.3, live 2026-10-05).** While at least one person is
out, server-run classmates arrive every 3–12s until people and bots make
`game.bots.target` (prod 4, else 0); they leave when the last person does. Unmarked (user's
call): ordinary `p-N` ids, guest `~names`, no account, so no ranking, stash or results.
`BotBrain` (bare hands; loot and hold the best; a knife or better → fight; else run from
anyone within 5 tiles and leave; trips of 5–10 rooms and 1–2.5 min) issues the same
`Command`s as a person at a person's pace and sees only what its snapshot would.
`BotDirector` is a `TickDriver` on the loop thread; the world is sized for people only
(`RoomRegistry.people()`). Rules: `docs/GAME_RULES.md` §10c.

**Fixes worth remembering.** A sortie whose socket never attached no longer blocks the
account (retired atomically with the handshake, gear refunded). Stepping off an open
crate closes the bag window. `stash_item.kind` is a VARCHAR, not an ENUM.

Tests: `./gradlew test` all green (327). Socket smoke passes against a local server.

## In Progress

Nothing.

## Next

The wider plan is `docs/ROADMAP.md` (cloud hardening §3, the scale-out track S1–S5).

1. **Play production with the bots on (user).** As a guest or signed in: do bots arrive
   (up to 4 with you), do they loot, fight with a knife or better, run otherwise, get out?
   Locally an idle newcomer died to a bot in 21–28s. If it feels harsh, options (ask before
   changing the user's rule): no fighting for the bot's first N seconds, fight only within
   N tiles, fewer bots arming up. Also: the production play check left from V2.2 (a trip
   with a bag, the 20-slot stash, an errand of each kind, a daily).
2. **Season 1 wipe, 2026-11-01.** User: snapshot at 10-31 23:50 (`AWS_DEPLOYMENT.md` §12),
   check after midnight. Decide season 2's length before then (`SEASON_DAYS` 28; about 91
   days suggested).
3. **Server-side tick metrics (C9):** tick time p50/p99, people, bots, sockets (Micrometer,
   or a log line every N ticks first). Bots now run on the loop thread, so this also shows
   what they cost on the t3.micro.
4. **Rebuild rehearsal (asks first).** `current/app.jar` now exists (CI keeps it). Snapshot
   production as `rehearsal-source`, plan `infra/rehearsal` with `-var
   snapshot=rehearsal-source`, the user applies (15–20 min), check the lobby by IP, the
   ranking and the socket smoke, then `terraform destroy` and delete the snapshot. Watch
   for the Free plan refusing a second RDS instance. Then write "take production down /
   bring it back" in `infra/README.md`.
5. **Load test on the production-like stack** (`docs/LOAD_TEST.md` Next): rehearsal +
   `loadtest` profile, bots from another machine — "one server's limit", the basis for
   S3–S5.
6. **Tune numbers** from play: prices, loot odds, soldier strength, errand and daily
   rewards, bot behaviour.
7. Remaining hardening (ROADMAP §3): S3 backend for Terraform state (one new bucket),
   Flyway (C5), logs to CloudWatch (C10), maintenance notice before deploys (C11).
   Tidy-up: the GitHub variable `INSTANCE_ID` is no longer used by CI.
8. Hideout on a real phone; jar rollback drill; `sudo reboot` check (both drop everyone).
9. Deferred: reputation unlocks (user, 2026-10-05); player market (postponed 2026-10-04;
   fixed-price only if it returns).
10. **Before 2027-02: paid plan or close (C15).** The Free plan closes the account when
   credits run out or on 2027-04-02; resources are deleted 90 days after unless upgraded.
   Credits $137.40 on 2026-10-05 (no refills); bots add CPU while people play. Check real
   monthly spend in the Billing console (free).

## Known Issues

- **`stash_item.location` and `sortie.outcome` are MySQL ENUM columns.** `ddl-auto=update`
  never alters them, so a new Location or Outcome value needs a manual `ALTER TABLE` on
  RDS first. Seasons reuse REFUNDED for that reason.
- **An item passed between two accounts in a crate can be duplicated by a restart.** If
  account A's stash item ends up extracted by B and the server restarts while A is still
  out, D6 refunds A's copy too. Needs a restart mid-trip and two accounts.
- **V1 `game_result` rows stay in the table**; nothing reads them since v2.0.
- **Errand progress made before 2026-10-05 was reinterpreted**: `quest_step` became the
  delivery ladder's step and `quest_kills` the soldier count. An account part-way through
  the old single list may sit a step off; season 1's wipe resets it.
- `bootRun` serves static files copied at build time: after editing
  `src/main/resources/static`, run `./gradlew processResources` (a running server picks
  them up) and fetch `game.js`/`game.css` with `cache: 'reload'`. Background tabs throttle
  timers, so held-input cadence cannot be measured from an automated tab.
- A second local server for checks needs its own DB: `--server.port=8081
  --spring.datasource.url=jdbc:h2:mem:check` (the shared H2 file's AUTO_SERVER times out).
- **RDS backups are 1 day** (Free plan ceiling); a problem found later than that needs a
  manual snapshot to recover from.
- **EC2 CPU credits are `unlimited`**: bursts are billed from the credits rather than
  throttled.
- Infra gotchas (provider dropping `disable_api_termination` alongside a tag change; Git
  Bash rewriting `/battle-royal/...` paths): `infra/README.md`, Known quirks.
- Bots fill the island only while a person is on it; with nobody on, none run. Their
  behaviour in production is untested by a person yet (Next 1).
- Production's server was built by hand; its user_data is empty and ignored. A rebuilt
  server gets the bootstrap (`infra/modules/game-stack/user-data.sh.tftpl`), untested
  until the rehearsal.

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
- **Guards (군인) are not players**: no score, kills, results or population; bringing one
  down counts only for the soldier errands. They see by the players' rules.
- **Errands are three ladders by kind**, one of each under way, plus three dailies a
  day (2026-10-05); no player-kill errands. Rewards grow with difficulty.
- **The compass gives the way, never the distance** (2026-10-05).
- **Trader reputation: system first, unlocks later** (2026-10-05). Standing comes from
  errands only (ladders and dailies), never from selling; it is wiped with the season
  like errand progress (agent's call, change if wanted).
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
- **Infra as code** (2026-10-05): every AWS change goes through `infra/` and a plan the
  user has seen; **the user runs `terraform apply`** (the agent's is blocked by the
  permission classifier, which is fine). Production is protected on the AWS side
  (termination, deletion), not by `prevent_destroy`, so the same module serves
  rehearsals. Production must be rebuildable: snapshot → apply → same IP and domain.
- **Scale-out is an experiment, not production** (2026-10-05): production stays one
  server; ALB, two servers and Redis are built in a separate stack, brought up to measure
  and torn down (ROADMAP S2–S5), justified by bot load numbers first.
- **Bots are not marked** (2026-10-05, user's call against the agent's advice to label
  them): same ids and guest-style names as people; kept out of ranking and results. They
  fill the island only while a person is on it, and never size the world.
- **Portfolio framing** (2026-10-05): with no real users, measure, break and rebuild
  (load tests, drills) rather than claim operating experience.

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
- Errand ladders and dailies: whether players climb too fast or stall; daily rewards
  against the economy.
