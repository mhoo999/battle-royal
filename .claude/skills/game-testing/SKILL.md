---
name: game-testing
description: Run the two-player end-to-end game test for this project. Use when asked to test the game, verify multiplayer sync, check combat or hiding behavior in a real browser, or confirm a feature works beyond unit tests.
---

# Game Testing

Verifies the V1 success criterion: **two browsers share the same game world and the
server makes every ruling.**

Unit tests (`./gradlew test`) cover game rules. This skill covers what they cannot:
real WebSocket traffic, two independent clients, and rendering.

## Run it

```bash
# 1. Start the server (background — it does not exit on its own)
./gradlew bootRun

# 2. Wait until it is actually listening, then run the suite
cd e2e && npx playwright test
```

Never use a fixed sleep to wait for startup. Poll the port:

```bash
until curl -sf http://localhost:8080/ >/dev/null; do sleep 1; done
```

Useful flags: `--headed` to watch it, `--debug` to step, `-g "<name>"` for one test.

## What the suite asserts

Two `BrowserContext`s (independent sessions, separate cookies/storage) drive two
players in one room.

1. Both players enter and see each other
2. A moves → B's view updates
3. B moves → A's view updates
4. A picks up an item
5. A swaps the item → the old one stays on the floor and B can take it
6. A attacks → the server rules → B's HP drops
7. A kills B → B gets the game-over overlay → A's score rises
8. The result is persisted and appears in `GET /api/ranking`
9. Room transition → the old room's player count changes
10. A walks into a bush → A vanishes from B's view → A still sees B
11. A hides in a cabinet → A vanishes → B attacks the cabinet → A takes damage

## Adding a case

Assert on **rendered client state**, never on server internals. The point is to
catch desync between what the server knows and what a player sees.

Keep each case independent — create fresh contexts per test. Do not chain state
across tests.

## Also verify manually

Server authority cannot be fully tested from a well-behaved client. In the browser
console, send a forged payload directly over the socket and confirm the server
ignores it:

```js
ws.send(JSON.stringify({ type: "MOVE", x: 999, y: 999 }))
ws.send(JSON.stringify({ type: "SET_HP", hp: 9999 }))
```

The player must not move and HP must not change.

## Do not

- Do not trigger `alert`/`confirm` — modal dialogs freeze browser automation.
- Do not weaken an assertion to make a run pass. A failing test is the finding.
