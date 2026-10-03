# Screenshots

Portfolio pictures, oldest first within each section. The rule for taking and naming
them is in `CLAUDE.md` §16 ("Screenshots for the portfolio").

`screenshot.png` is the one `README.md` shows.

## Game (`game/`)

| File | Date | Shows | Belongs to |
|---|---|---|---|
| `2026-10-03-lobby.png` | 2026-10-03 | Lobby: title, film-theme copy, top-10 ranking (local test data) | lobby, ranking |
| `2026-10-03-room-with-rival.png` | 2026-10-03 | A room on the torus world: you (△), a rival (▶), cabinets, bushes, doors, Game Boy controls | torus world `fbd7740`, input `43ed5e5` |
| `2026-10-03-phone-touch-zones-crate.png` | 2026-10-03 | Phone layout at 390 px: the controls strip fills the height below the board (touch zones); a floor crate | touch zones `43ed5e5`, crate `15d860e` |
| `2026-10-03-away-death.png` | 2026-10-03 | The game ending while the player was away: "자고 있는 사이에 야생 동물에 당해 끔찍한 시체가 되었다." | away screen `6c8ff64`, wording `c53fb09` |
| `2026-10-03-live-https-phone.png` | 2026-10-03 | The live site over HTTPS at phone width, as `~noriko` (unranked), with a floor crate. The URL line on top is an added caption, not the browser's address bar | HTTPS, `~` names `099fc07` |
| `2026-10-03-v2-lobby-google-sign-in.png` | 2026-10-03 | V2 lobby, signed out: guest name and START, and the new Google sign-in link (local, `v2` branch) | V2 step 1, accounts |
| `2026-10-03-v2-lobby-entry.png` | 2026-10-03 | V2 lobby reworked at the user's request: Google sign-in and guest are the first choice; the name comes after | V2 step 1 |
| `2026-10-03-v2-hideout-trader-hut.png` | 2026-10-03 | The hideout with the trader (a picked stash item's sell button, the stock with buy buttons greyed when unaffordable) and the ASCII hut-under-the-moon background. Staged: the hideout data was stubbed in the page, no Google sign-in | V2 step 4, trader; hideout art |
| `2026-10-03-v2-exit-compass.png` | 2026-10-03 | V2 compass above the board (`탈출구 ◎ 이 방 · ↓ 1`) and my own exit `◎` on the floor beside me | V2 step 3, exits and compass |
| `2026-10-03-v2-extracting.png` | 2026-10-03 | Standing on the exit holding B: [B 탈출], "탈출 중 — B를 떼거나 움직이거나 맞으면 취소", the gauge starting | V2 step 3, extraction |
| `2026-10-03-v2-extracted.png` | 2026-10-03 | The 탈출 성공 screen with the haul line and the record | V2 step 3, extraction |
| `2026-10-03-v2-crate-and-inventory.png` | 2026-10-03 | V2 bag window over the board: an opened crate on the left, three inventory slots on the right with `e` on the equipped one, and the [가방] button | V2 step 2, crates and inventory |

## AWS (`aws/`)

| File | Date | Shows | Belongs to |
|---|---|---|---|
| `2026-10-02-phase2-sg-db-inbound.png` | 2026-10-02 | Editing `battle-royal-db` inbound: MySQL 3306 with the source set to the `battle-royal-web` security group, not an IP | AWS Phase 2 |
| `2026-10-03-sg-web-inbound-no-ssh.png` | 2026-10-03 | `battle-royal-web` inbound: only HTTP 80 and HTTPS 443 from anywhere — SSH 22 is gone | SSH closed, Session Manager instead |
| `2026-10-03-sg-db-inbound.png` | 2026-10-03 | `battle-royal-db` inbound as saved: MySQL 3306 from the `battle-royal-web` group only | AWS Phase 2 |
| `2026-10-03-rds-private.png` | 2026-10-03 | RDS `battle-royal-db`: MySQL Community on db.t4g.micro, internet access gateway disabled | AWS Phase 3 (MySQL 8.4) |
| `2026-10-03-ec2-role-sg.png` | 2026-10-03 | The instance: t3.micro, 3/3 checks, Elastic IP; Security tab with IAM role `battle-royal-ec2` and only 80/443 inbound | AWS Phase 4, Phase 10 |
| `2026-10-03-ssm-fleet-online.png` | 2026-10-03 | Fleet Manager: the instance managed by SSM, ping Online — how deploys and shell access reach it | Phase 10, Session Manager |
| `2026-10-03-systemd-running.png` | 2026-10-03 | `systemctl status battle-royal`: active (running), `-Xmx512m`, over Session Manager (session bar cropped) | AWS Phase 7 |
| `2026-10-03-iam-deploy-trust.png` | 2026-10-03 | The `deploy` role's trust policy: GitHub OIDC, `sub` in the immutable `owner@id/repo@id` form, `main` only | Phase 10, the OIDC subject fix |
| `2026-10-03-actions-test-deploy.png` | 2026-10-03 | A GitHub Actions run: `test` (1m 57s) then `deploy` (38s), both green, from a push to `main` | Phase 10 `3638c1f`, run for `43ed5e5` |
| `2026-10-03-cloudwatch-alarms.png` | 2026-10-03 | The four CloudWatch alarms, all OK, with their conditions | CloudWatch alarms |
