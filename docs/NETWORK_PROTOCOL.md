# NETWORK_PROTOCOL

## 전송 분리

**HTTP** — 페이지 로딩, 세션 발급, 랭킹 조회.
**WebSocket** — 게임 중 모든 실시간 상태.

WebRTC는 도입하지 않는다. STOMP도 쓰지 않는다 — 메시지 타입이 6개뿐이고
per-player 필터링에는 raw `TextWebSocketHandler` + Jackson이 더 단순하다.

---

## HTTP

### `POST /api/session`

```json
요청   { "nickname": "kang" }                          게스트
응답   { "token": "<uuid>", "playerId": "p-17", "nickname": "~kang" }
```

**게스트**(로그인 안 함): 닉네임은 trim 후 1~12자, 공백만이면 400. 서버가 앞에 `~`를
붙여 **랭킹에 남지 않게** 한다(이미 붙어 있으면 다시 붙이지 않는다). 중복은 허용한다.

**로그인한 계정**은 여기로 오지 않는다: 409 "거점에서 출발하세요". 거점에서 출발한다(아래).
(V2 브랜치)

### `GET /api/hideout`, `POST /api/hideout/sortie` (V2 브랜치, 로그인 필요 — 아니면 401)

```json
GET   → { "stash": [ { "id": 11, "kind": "PISTOL", "ammo": 4, "price": 110 } ],
          "capacity": 10, "upgrade": { "capacity": 20, "price": 500 },
          "out": false, "money": 135, "haul": 165,
          "trader": [ { "kind": "KNIFE", "ammo": 0, "price": 120 }, … ],
          "quests": [ { "category": "DELIVERY", "difficulty": "EASY", "step": 1, "total": 6,
                        "title": "첫 납품",
                        "deliver": [ { "kind": "SPOON", "count": 1, "have": 0 } ],
                        "visits": 0, "soldiers": 0, "soldiersInOneTrip": false,
                        "soldiersDone": 0, "money": 30, "reward": null,
                        "rewardAmmo": null, "ready": false }, … ],
          "dailies": [ { "slot": 0, "title": "잡동사니", "difficulty": "EASY",
                         "goal": "DELIVERY", "deliver": [ { "kind": "CUP", "count": 1, "have": 0 } ],
                         "count": 0, "progress": 0, "money": 40, "done": false,
                         "ready": false }, … ],
          "dailiesResetAt": "2026-10-05T15:00:00Z" }
POST  { "loadout": [ 11, null, 12 ], "bag": 13 }  → { "token": "<uuid>", "playerId": "p-3", "nickname": "shuya" }
```

`loadout`은 칸 순서대로 창고 아이템 ID(빈 칸은 `null`), 최대 3칸 — `bag`(V2.1, 창고의 가방 ID
또는 생략)을 메면 3 + 가방 칸까지. 가방이 아닌 것을 `bag`으로 고르면 400. 응답 토큰으로 WebSocket에
붙으면 그 아이템을 들고 섬에 들어가고, 첫 칸이 장착된다. 거부: 닉네임이 없으면 409, 이미
섬에 나가 있으면 409 "이미 섬에 나가 있습니다" — 단 이전 출발의 소켓이 한 번도 붙지 않았다면
그 토큰을 폐기하고 장비를 창고로 돌린 뒤 새로 출발한다, 남의 아이템·이미 나간 아이템·같은 아이템
두 번·4칸 이상이면 400. 한국어 사유를 본문에 담는다.

들고 나간 아이템은 섬에서 평범한 아이템이고 ID가 `s-<창고 ID>`다. 죽으면 잃는다(창고에서
삭제). 서버가 재시작되면 들고 나간 것만 창고로 돌아온다(D6). 탈출하면 가져온 것이 창고에
들어간다(`EXTRACTED` 참고).

### `POST /api/hideout/sell`, `POST /api/hideout/buy` (V2 브랜치, 로그인 필요)

```json
sell  { "itemId": 11 }     → 위의 GET과 같은 거점 화면
buy   { "kind": "KNIFE" }  → 위의 GET과 같은 거점 화면
```

상인(D11). 창고 항목의 `price`가 상인이 사 주는 값이자 그 아이템의 가치이고, `trader`는
상인이 파는 목록과 값이다. 값은 서버만 정한다(`ItemValues`). 팔면 그 아이템은 사라지고 돈이
는다. 사면 돈이 줄고 새 아이템이 창고에 들어온다 — 총은 빈 채로(`ammo` 0), 탄약 묶음(`ROUNDS`,
`BOLTS`)은 가득 차서. 거부는 409와 한국어 사유:
창고에 없는(나가 있거나 남의) 아이템, 상인이 팔지 않는 물건, 돈 부족, 창고가 가득 참(사기만 —
탈출은 가득 차도 다 들어온다).

### `POST /api/hideout/quest/deliver` (V2.2, 로그인 필요)

```json
(본문 없음)  → 위의 GET과 같은 거점 화면
```

진행 중인 납품 의뢰를 위해 창고에서 필요한 것을(오래된 것부터) 상인에게 넘기고 보상을 받는다.
GET의 `quests`는 종류(`DELIVERY` 납품, `VISIT` 장소, `SOLDIER` 군인 처치)마다 진행 중인 의뢰 하나씩,
사다리를 다 오른 종류는 빠진다. `ready`는 납품을 지금 넘길 수 있는지다. 장소·군인 의뢰는 판이
끝날 때(사망·탈출) 서버가 센다 — 따로 부를 것이 없다. 거부는 409: 납품할 의뢰가 없음, 창고에 모자람.

### `POST /api/hideout/daily/{slot}/deliver` (V2.2, 로그인 필요)

```json
(본문 없음)  → 위의 GET과 같은 거점 화면
```

오늘의 일일 의뢰 중 납품(`goal: DELIVERY`)인 `slot`을 창고에서 넘기고 보상을 받는다. `dailies`는
오늘의 세 개(`goal`: `DELIVERY`, `EXTRACT` 탈출, `SOLDIER` 군인), `dailiesResetAt`은 서울 자정.
탈출·군인은 판이 끝날 때 서버가 센다. 거부는 409: 납품이 아닌 칸, 이미 끝남, 창고에 모자람.

### `POST /api/hideout/stash-upgrade` (V2.1, 로그인 필요)

```json
(본문 없음)  → 위의 GET과 같은 거점 화면
```

창고를 다음 크기로 늘린다: 일반 상자 10칸 → 큰 상자 20칸(500원) → 고급 상자 40칸(2,000원).
계정에 영구히 남는다. GET의 `upgrade`가 다음 크기와 값이고, 가장 큰 창고면 `null`. 거부는
409와 한국어 사유: 돈 부족, 더 큰 창고 없음.

### `GET /api/season` (V2.1, 누구나)

```json
→ { "number": 1, "endsAt": "2026-10-31T15:00:00Z",
    "trophies": [ { "season": 1, "tier": "CHAMPION", "placing": 1 } ] }
```

지금 시즌과 마감 시각(서울 자정). `trophies`는 로그인한 사람 자신의 것, 오래된 시즌부터
(`CHAMPION` 1위 · `TOP10` 2~10위 · `PARTICIPANT` 그 밖에 탈출 1번 이상, 참가는 `placing`이
순위이거나 `null`). 게스트는 빈 배열.

### `GET /api/me`, `POST /api/me/nickname` (V2 브랜치)

```json
GET   → { "signedIn": false, "nickname": null }       누구나
POST  { "nickname": "shuya" } → { "signedIn": true, "nickname": "shuya" }   로그인 필요(아니면 401)
```

닉네임은 게스트와 계정 모두 `NicknamePolicy`를 거친다: 1~12자, 비속어·음란어·운영자 사칭 금지
(400, 본문은 플레이어에게 그대로 보여 줄 한국어 사유). 구글 로그인은 `/oauth2/authorization/google`로 시작하고 `openid` 범위만 요청한다. 계정에는
구글의 `sub`와 닉네임만 저장한다(이메일·이름 없음). 계정 닉네임은 1~12자, 계정끼리
중복 불가(409), `~`로 시작할 수 없고(400), 한 번 정하면 바꾸지 않는다(409). 세션 쿠키는
`SameSite=Lax`, 운영에서는 `Secure`. CSRF 토큰은 쓰지 않는다(`SecurityConfig` 주석).

### `GET /api/ranking?limit=20` (V2: 가져온 가치)

```json
{ "top": [ { "rank": 1, "nickname": "shuya", "value": 1240 } ],
  "me":  { "rank": 25, "nickname": "kawada", "value": 90 } }
```

계정별 **가져온 가치**(`haul`) 순위다(D5). 탈출할 때 섬에서 **주워 온 것**의 가치를 더한
값이고, 창고에서 들고 나갔다 다시 들고 온 장비는 세지 않는다(같은 권총으로 들락날락하며
쌓는 것을 막는다). 0인 계정은 없다. 같은 값은 같은 순위, 그다음 먼저 가입한 쪽이 위.
`limit`은 1~100. `me`는 로그인한 사람 자신의 줄(어디에 있든), 아니면 `null`. 로비는 상위
10명을 보이고, 내가 그 밖이면 `⋮` 아래에 내 줄을 붙인다. 게스트는 순위가 없다.
V1의 점수 순위(`/api/ranking/rank`)는 없어졌다. 목숨마다의 기록(`GameResult`)은 계속
저장한다.

---

## WebSocket 핸드셰이크

```
GET /ws/game?token=<uuid>
```

서버가 토큰으로 플레이어를 해석한다. **클라이언트가 playerId를 주장하는 경로를
만들지 않는다.** 토큰이 없거나 유효하지 않으면 연결을 거부한다(close 1003).

토큰은 **그 목숨이 끝날 때까지** 유효하다. 소켓이 끊겨도 남아 있어서, 15초 유예 안에
같은 토큰으로 다시 연결하면 같은 플레이어로 복귀한다(같은 방, 같은 칸, 즉시 스냅샷).
사망하면 폐기되므로 그 뒤의 재접속은 거부된다. 클라는 끊기면 1초 간격으로 15번
재시도한다. 1003을 받거나 15번을 다 쓰면 판이 끝난 것이다: 기록 없이 "자고 있는 사이에 야생
동물에 당해 끔찍한 시체가 되었다." 화면과 [처음으로]를 띄운다. 서버가 재시작된 경우에도 같은 거부가 오고
그때는 결과가 저장되지 않으므로, 화면은 기록이 저장됐다고 말하지 않는다. 휴대폰은 숨은
페이지의 타이머를 멈추므로, 페이지가 다시 보이거나(`visibilitychange`) 온라인으로
돌아오면(`online`) 소켓이 끊겨 있을 때 타이머를 기다리지 않고 바로 다시 붙는다.

클라는 토큰을 `sessionStorage`(`battle-royal.session`: token, nickname, startedAt)에
둔다. 새로고침해도 유예 안이면 로비를 거치지 않고 같은 플레이어로 복귀한다. 탭마다
따로라서 두 탭은 두 플레이어다. 복귀 시도가 1003으로 거부되면(그사이 죽음) 저장을
지우고 "이전 게임은 끝났습니다"와 함께 로비로 간다. 사망 화면이 뜨면 바로 지운다.
저장소를 못 쓰는 환경(시크릿 창 등)에서는 예전처럼 새로고침이 곧 끝이다.

새 소켓이 옛 소켓보다 먼저 붙는 경우가 있으므로, 닫힌 소켓이 현재 등록된 소켓일
때만 끊김으로 처리한다.

---

## 클라 → 서버

```json
{ "type": "MOVE", "dir": "UP" }        // UP | DOWN | LEFT | RIGHT
{ "type": "ACTION_A" }
{ "type": "ACTION_B" }                 // B를 누름
{ "type": "RELEASE_B" }                // B를 뗌 — 진행 중인 루팅 취소
{ "type": "EQUIP", "slot": 1 }         // A가 쓸 칸 (V2)
{ "type": "TAKE", "index": 0, "slot": 2 } // 열린 상자의 index번째를 slot으로; 차 있으면 맞바꿈 (V2)
{ "type": "PUT", "slot": 2 }           // slot의 아이템을 열린 상자로 (V2)
                                        // slot -1은 가방 칸 (V2.1): TAKE는 가방만, 늘어난 칸이 비어야
{ "type": "CLOSE" }                    // 열린 상자 닫기 (V2)
```

이것이 전부다. 좌표, HP, 데미지, 아이템을 담은 메시지는 **존재하지 않아야 한다.**
`slot`/`index`는 **내 인벤토리와 내가 연 상자 안의 자리**일 뿐이고, 서버가 범위와 상자가
열려 있는지(그 칸에 서 있는지)를 확인한다. 아이템 ID나 종류를 보내는 명령은 없다.
`SET_POSITION`, `SET_HP`, `DEAL_DAMAGE` 같은 타입을 추가하지 않는다.

알 수 없는 타입이나 형식 오류는 조용히 무시한다(연결을 끊지 않는다).

메시지는 `{type, dir, slot, index}` 형태로만 역직렬화한다. 따라서 `{"type":"MOVE","x":999,"y":999}`
같은 프레임은 `x`/`y`가 **들어갈 자리가 없어서** 자동으로 버려진다.

**쿨다운 중 도착한 커맨드는 큐잉되지 않고 버려진다.** 클라이언트는 이동 쿨다운(200ms)
보다 긴 주기로 입력을 보내야 한다. 짧으면 입력이 조용히 유실된다.

---

## 서버 → 클라

### `SNAPSHOT`

상태가 변한 tick에만 전송한다. 방 단위 전체 상태를 보낸다 — 15x15 타일 게임에서
델타 전송은 복잡도만 늘린다.

```json
{
  "type": "SNAPSHOT",
  "tick": 12840,
  "roomId": "room-07",
  "map": { "template": "T2", "tiles": "…" },
  "self": {
    "id": "p1", "x": 10, "y": 7, "direction": "UP",
    "hp": 80, "item": "MEDKIT", "ammo": null,
    "inventory": [ { "kind": "MEDKIT", "ammo": null }, null, { "kind": "PISTOL", "ammo": 4 } ],
    "bag": null,
    "equipped": 0,
    "crate": null,
    "concealment": "CABINET", "lootMsLeft": null, "invulnerable": false,
    "exits": [ { "dx": 0, "dy": 0, "x": 2, "y": 5 }, { "dx": -1, "dy": 2, "x": null, "y": null } ],
    "extractMsLeft": null,
    "score": 420, "kills": 1,
    "actionA": "HEAL", "actionB": null
  },
  "players": [
    { "id": "p2", "x": 15, "y": 5, "direction": "LEFT", "alive": true }
  ],
  "items":   [ { "id": "i-31", "x": 4, "y": 9 } ],
  "guards":  [ { "x": 5, "y": 5, "direction": "DOWN" } ],
  "score": 420
}
```

`self`만 `hp`/`item`/`ammo`/`cooldown`/`hidden`/`lootMsLeft`/`invulnerable`을 가진다.
`lootMsLeft`는 루팅 중일 때 남은 ms, 아니면 `null`이다. 클라는 루팅이 새로 시작될
때만 게이지를 0에서 이 시간에 걸쳐 채운다. 완료 판정은 서버가 한다.
다른 플레이어가 루팅 중인지는 보내지 않는다.

**V2: 탈출구와 나침반(D3, D9).** `self.exits`는 내 탈출구마다 하나씩, 지금 방에서 그
탈출구가 있는 방까지 **토러스 최단 변위**(`dx` 동쪽+, `dy` 남쪽+, 방 단위)다. 그 방에
들어와 있을 때만(`dx`=`dy`=0) `x`/`y`에 타일을 준다 — 클라는 거기에 `◎`를 그린다.
`extractMsLeft`는 탈출 중일 때 남은 ms(`lootMsLeft`와 같은 방식). **남의 탈출구는 어떤
형태로도 보내지 않는다** — `Self`에만 있고 `Other`에는 필드가 없다(`SnapshotFilterTest`).
게스트도 탈출구를 받는다. 창고가 없을 뿐이다.
`players[]`의 각 항목은 `id`/`x`/`y`/`direction`/`alive`만 가진다.

`self.item`: `KNIFE | BAT | PISTOL | CROSSBOW | MEDKIT | PAN | SPOON | CUP | DOLL |
RECORDER | REGISTER | ROUNDS | BOLTS` (V2: the last two are ammunition bundles), or null for empty hands. `YOU_DIED.weapon` is null for a
bare-hand kill.

**V2: 인벤토리 3칸.** `self.inventory`는 칸 순서대로 `{kind, ammo}` 또는 빈 칸 `null`,
`self.equipped`는 A가 쓰는 칸, `self.item`/`self.ammo`는 장착 칸의 것이다.
**V2.2:** `self.marks`는 들고 나간 장소 의뢰의 표식들로 `exits`와 같은 모양(`{dx, dy, x, y}`,
그 방에 있을 때만 `x`/`y`)이다. 밟은 표식은 빠진다. 장소 의뢰가 아니면 빈 배열. 거점 `quests`의 `VISIT`
항목에 `visits`(밟을 표식 수)가 있다.
**V2.1:** `self.bag`은 가방 칸에 멘 가방(`{kind}`, `SMALL_BAG` | `BIG_BAG`) 또는 `null`이고,
`self.inventory`의 길이는 3 + 가방이 더하는 칸(작은 2, 큰 4)이다. 가방은 다른 사람에게
보내지 않는다.
`self.crate`는 **내가 열어 둔 상자의 내용**(순서대로)이고, 열린 상자가 없으면 `null`이다.

**바닥에는 상자(`items[]`)만 보낸다. 내용은 연 사람에게만, 그것도 `self.crate`로만
보낸다(결정).** 상자를 열기 전에는 무엇이 들었는지 아무도 모른다. 클라가 그리지 않더라도
전송하면 개발자 도구로 보이므로 필드 자체를 두지 않는다 — `SnapshotFilterTest`가
`FloorItem`을 `id`/`x`/`y`로 고정하고, 상자 내용이 연 사람 말고는 가지 않는 것도 확인한다.
`items[].id`는 상자의 ID다. 들고 있는 것은 본인만 안다.

`actionA`/`actionB`는 서버가 계산한 현재 유효 행동 **토큰**이다. 표시 문구가 아니다.

```
actionA   ATTACK | FIRE | HEAL | RELOAD | null      (RELOAD: V2, an empty gun and its bundle carried)
actionB   EXTRACT | OPEN | CLOSE | DOOR | null      (V1: PICKUP | SWAP | DOOR)
```

캐비닛은 `MOVE`로 들어가고 나온다. B 토큰이 없다.

서버는 의미를 정하고 클라는 단어를 정한다. 클라가 자체적으로 상황을 판단해 어떤
행동이 가능한지 계산하지 않는다 — 그러면 버튼 라벨과 실제 동작이 갈릴 수 있다.

### `EVENT`

```json
{ "type": "EVENT", "event": "SHOT", "path": [[10,7],[10,6],[10,5]] }
{ "type": "EVENT", "event": "HIT" }
{ "type": "EVENT", "event": "FELL",        "at": [8,1] }
{ "type": "EVENT", "event": "PICKUP",      "itemId": "i-31" }
{ "type": "EVENT", "event": "ROOM_CHANGE", "roomId": "room-08" }
```

`SHOT`의 `path`는 **발사자 타일부터** 시작한다. 부시 안에서 쏘면 이 때문에 위치가
드러난다 — 의도된 동작이다.

`SHOT`의 `path`는 명중 시 **피격자 타일에서 끝난다**(부시 안이어도). 벽·캐비닛
타일은 경로에 넣지 않는다. 결정 배경은 GAME_RULES §5.

`HIT`은 **명중 사실만** 담는다. 대상 ID, HP, 생사 여부를 넣지 않는다.
`OutboundTest`가 이 형태를 고정한다.

`FELL`은 **누군가 그 칸에서 쓰러졌다는 사실과 위치만** 담는다. 누구인지는 넣지 않는다.
받는 사람은 **죽기 직전 그 사람을 볼 수 있었던 같은 방 사람**이고, 서버가 사망 순간
(캐비닛 플래그를 지우기 전)에 정해 둔다. 부시나 캐비닛 안에서 죽으면 밖에서는 아무도
받지 않는다 — 그 죽음이 이전보다 더 드러나지 않는다. 클라는 그 칸에 ✕를 1초,
상태줄에 "누군가 쓰러졌다"를 2초 띄운다. (예전 초안의 `DEAD`+`playerId`는 누가 죽었는지
알려 줘서 쓰지 않는다.)

| 이벤트 | 받는 사람 |
|---|---|
| `SHOT` | 방 안의 모든 플레이어 (사수를 못 보는 사람 포함 — 그것이 노출이다) |
| `SWING` | 방 안의 모든 플레이어 (`from` 공격자 타일, `to` 휘두른 타일) |
| `HIT` | 공격자만 |
| `YOU_DIED` | 사망자만 |
| `EXTRACTED` | 탈출한 사람만 (V2) |
| `SEASON_OVER` | 시즌 마감에 섬에서 내보내진 사람만 (V2.1) |
| `FELL` | 사망 순간 사망자를 볼 수 있었던 같은 방 사람 (사망자 제외) |

이벤트는 같은 tick의 `SNAPSHOT` **뒤에** 보낸다. `PICKUP`·`ROOM_CHANGE` 이벤트는
아직 보내지 않는다 — 스냅샷 변화로 충분해서 필요해질 때 추가한다. `DEAD` 이벤트는 아직 없다 —
다른 플레이어는 다음 스냅샷에서 시체가 사라지는 것으로 안다.

`CHAT` 이벤트는 V1에 없다.

### `YOU_DIED`

```json
{ "type": "YOU_DIED", "score": 1270, "kills": 3, "survivedSeconds": 412,
  "killer": "kang", "weapon": "PISTOL", "byGuard": false }
```

`byGuard`(V2.2)는 군 초소 군인에게 맞아 죽었을 때 `true`이고, 그때 `killer`/`weapon`은 `null`이다.

`guards`(스냅샷, V2.2)는 그 방에 서 있는 군 초소 군인의 위치와 방향이다. 초소가 아니면 빈 배열,
쓰러진 군인은 빠진다. 군인의 체력은 보내지 않는다(플레이어처럼).

`killer`/`weapon`은 **사망자에게만** 간다. 살아 있는 동안 숨겨지는 정보(상대 무기)지만
이 시점에 받는 사람은 이미 탈락했다. 처치자가 없는 사망(향후 끊김 타임아웃)이면 둘 다
`null`. 문장은 클라가 만든다 — 서버는 무기 코드만 보낸다.

### `EXTRACTED` (V2)

```json
{ "type": "EXTRACTED", "score": 15, "kills": 0, "survivedSeconds": 58,
  "carried": [ { "kind": "PISTOL", "ammo": 4 }, { "kind": "CUP", "ammo": null } ] }
```

탈출구에서 B를 5초 누른 사람에게만. `carried`는 가지고 나온 것(칸 순서, 빈 칸 제외)이다.
`YOU_DIED`처럼 그 목숨의 끝이다: 토큰은 폐기되고(다시 붙으면 거부), 결과가 저장되며
(`~` 이름 제외), 계정이면 가져온 것이 창고에 들어간다. 다른 사람은 다음 스냅샷에서
그 사람이 사라지는 것으로만 안다.

### `SEASON_OVER` (V2.1)

```json
{ "type": "SEASON_OVER" }
```

시즌 마감 시각에 계정으로 섬에 나가 있던 사람에게만. 그 사람은 섬에서 사라지고(들고 있던 것은
상자로 떨어지지 않고 와이프로 사라짐), 토큰은 폐기된다. 게스트는 영향받지 않는다.

---

## SnapshotFilter — 정보 비대칭의 단일 관문

시청자 `V`에게 대상 `T`를 포함할지 결정한다. `game/rule/VisibilityRules`에 순수
함수로 구현하고, `ws/SnapshotFilter`가 그것을 호출한다.

```
T == V                          -> self 로 전체 포함
T가 캐비닛 안                    -> 완전 제외
T가 부시 안 AND V가 같은 부시     -> players[] 에 포함 (id/x/y/direction/alive)
T가 부시 안 AND 그 외             -> 완전 제외
그 외                            -> players[] 에 포함 (id/x/y/direction/alive)
```

"같은 부시"는 **부시 region ID 일치**로 판정한다. 인접 타일이 아니다.

### 절대 규칙

타인의 `hp`, `item`, `ammo`, `cooldown`은 **어떤 경우에도 전송하지 않는다.**

캐비닛 점유 여부를 노출하지 않는다 — 캐비닛 타일은 비어 있을 때와 동일하게 보낸다.
스냅샷에 새 필드를 추가할 때마다 이 문서로 돌아와 규칙을 확인한다. 정보 누출은
가장 테스트하기 쉬우면서 가장 치명적인 버그다.
