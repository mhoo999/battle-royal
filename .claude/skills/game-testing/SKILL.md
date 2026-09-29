---
name: game-testing
description: Run the two-player end-to-end game test for this project. Use when asked to test the game, verify multiplayer sync, check combat or hiding behavior in a real browser, or confirm a feature works beyond unit tests.
---

# Game Testing

Verifies the V1 success criterion: **two clients share the same game world and the
server makes every ruling.**

Unit tests (`./gradlew test`) cover game rules. This skill covers what they cannot:
real WebSocket traffic, independent clients, and rendering. There are two layers: a
socket smoke suite that runs in seconds, and a browser walk for anything visual.
There is no Playwright suite.

## 1. Start the server

```bash
./gradlew bootRun          # background: it does not exit on its own
until curl -sf http://localhost:8080/ >/dev/null; do sleep 1; done
```

Never wait on a fixed sleep. `bootRun` copies static resources at build time, so a
change under `src/main/resources/static` needs a restart. On Windows, stop the server
before `./gradlew test` — a running `bootRun` can hold build files.

## 2. Socket smoke suite

```bash
node e2e/smoke-two-sockets.mjs
```

Node 18+, no dependencies. Two players over raw WebSockets. It checks separate arrival
rooms, doors that lead somewhere and back, meeting by wandering, the opponent view
exposing only position and facing, replication, pickup, a bare-hand strike and who
receives `HIT`/`SWING`, forged frames being ignored, an unknown token refused, and a
reconnect inside the grace period landing on the same tile.

It prints `PASS`, `FAIL` or `SKIP`. `SKIP` is not a failure: pickup is skipped when
B's starting room rolled nothing. Exit code is non-zero on any `FAIL`.

Back-to-back runs share the world with the previous run's players for their 15s
disconnect grace. Wait ~16s between runs when a clean world matters.

## 3. Browser walk

Two tabs (or one tab plus a socket script) on `http://localhost:8080`. Walk what the
change touched; the full list:

1. Lobby shows the top-10 ranking; enter a name and start
2. Both players move; each sees the other on the right tile and facing
3. B picks up a `$` (hold B; releasing or moving cancels) — the item's kind is only
   known once in hand
4. Swap: the old item stays on the floor as `$`
5. A attacks (bare hands work) → B's board flashes red, A's flashes white
6. A kills B → B's death screen names killer and weapon ("주먹에 맞고" for fists)
7. 처음으로 → lobby with the name prefilled; the result is highlighted in the top
   10, or shown under "⋮" with its rank
8. Walking into a bush hides you from outside it; you still see them
9. Walking into an empty cabinet hides you; attacking the cabinet still wounds you
10. Close the socket (`socket.close()` in the console) → "재접속 중 (n/15)" → back on
    the same tile

Check the console for errors at the end.

### Automation notes

- Chrome throttles timers in background tabs. A scripted two-tab test must send one
  step per call rather than loop on `setTimeout` in the hidden tab.
- The client's top-level bindings (`socket`, `lastSnapshot`, `ui`, `restart()`,
  `deathCause()`) are reachable from the page console, which makes state checks cheap.
- To get a browser player killed without a second human, drive a second player over a
  socket from a scratch script. Restrict it to the target's `roomId`: the world is
  shared, and anyone else connected — including the user playing — is fair game to it.

## Also verify by hand

Server authority cannot be fully tested from a well-behaved client. From the console:

```js
socket.send(JSON.stringify({ type: "MOVE", x: 999, y: 999 }))
socket.send(JSON.stringify({ type: "SET_HP", hp: 9999 }))
```

The player must not move and HP must not change. (The smoke suite does this too.)

## Do not

- Do not trigger `alert`/`confirm` — modal dialogs freeze browser automation.
- Do not weaken an assertion to make a run pass. A failing check is the finding.
