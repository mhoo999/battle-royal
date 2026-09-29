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
요청   { "nickname": "kang" }
응답   { "token": "<uuid>", "playerId": "p-17" }
```

닉네임은 trim 후 1~12자. 공백만인 값은 400. 중복은 허용한다.

### `GET /api/ranking?limit=20`

```json
[ { "nickname": "kang", "score": 1270, "kills": 3, "survivedSeconds": 412 } ]
```

`GameResult`에 대한 top-N 쿼리다. 별도 Ranking 테이블은 두지 않는다.
정렬은 점수 내림차순, 같으면 생존 시간이 긴 쪽, 그다음 먼저 끝난 쪽. `limit`은
1~100으로 잘린다. 로비는 상위 10명을 보여준다.

### `GET /api/ranking/rank?score=37&survivedSeconds=28`

```json
{ "rank": 25 }
```

이 수치의 기록이 몇 위인지. 1 + (더 나은 기록 수)다 — 점수가 높거나, 같은 점수에
더 오래 살았으면 더 나은 기록이다. 완전히 같은 기록은 같은 순위다. 이름이 아니라
수치로 묻는 이유는 이름이 중복되기 때문이다. 클라는 방금 끝난 목숨(`YOU_DIED`)의
수치로 묻고, 그 기록이 10위 밖이면 목록 아래에 `⋮`와 함께 붙인다. 저장이 끝나기
전에 물어도 답이 같다.

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
재시도하고, 1003을 받으면 멈춘다. 토큰은 페이지 메모리에만 있어서 새로고침하면
복귀할 수 없다.

새 소켓이 옛 소켓보다 먼저 붙는 경우가 있으므로, 닫힌 소켓이 현재 등록된 소켓일
때만 끊김으로 처리한다.

---

## 클라 → 서버

```json
{ "type": "MOVE", "dir": "UP" }        // UP | DOWN | LEFT | RIGHT
{ "type": "ACTION_A" }
{ "type": "ACTION_B" }                 // B를 누름
{ "type": "RELEASE_B" }                // B를 뗌 — 진행 중인 루팅 취소
```

이것이 전부다. 좌표, HP, 데미지, 인벤토리를 담은 메시지는 **존재하지 않아야 한다.**
`SET_POSITION`, `SET_HP`, `DEAL_DAMAGE` 같은 타입을 추가하지 않는다.

알 수 없는 타입이나 형식 오류는 조용히 무시한다(연결을 끊지 않는다).

메시지는 `{type, dir}` 형태로만 역직렬화한다. 따라서 `{"type":"MOVE","x":999,"y":999}`
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
    "concealment": "CABINET", "lootMsLeft": null, "invulnerable": false,
    "score": 420, "kills": 1,
    "actionA": "HEAL", "actionB": null
  },
  "players": [
    { "id": "p2", "x": 15, "y": 5, "direction": "LEFT", "alive": true }
  ],
  "items":   [ { "id": "i-31", "x": 4, "y": 9 } ],
  "score": 420
}
```

`self`만 `hp`/`item`/`ammo`/`cooldown`/`hidden`/`lootMsLeft`/`invulnerable`을 가진다.
`lootMsLeft`는 루팅 중일 때 남은 ms, 아니면 `null`이다. 클라는 루팅이 새로 시작될
때만 게이지를 0에서 이 시간에 걸쳐 채운다. 완료 판정은 서버가 한다.
다른 플레이어가 루팅 중인지는 보내지 않는다.
`players[]`의 각 항목은 `id`/`x`/`y`/`direction`/`alive`만 가진다.

`self.item`: `KNIFE | PISTOL | MEDKIT | PAN | SPOON`.
**바닥 아이템은 위치만 보낸다. 종류는 누구에게도 보내지 않는다(결정).** 무엇인지는
루팅이 끝나 손에 들어왔을 때 `self.item`으로 처음 안다. 클라가 그리지 않더라도
전송하면 개발자 도구로 보이므로 필드 자체를 두지 않는다 — `SnapshotFilterTest`가
`FloorItem`을 `id`/`x`/`y`로 고정한다. 들고 있는 아이템은 본인만 안다.

`actionA`/`actionB`는 서버가 계산한 현재 유효 행동 **토큰**이다. 표시 문구가 아니다.

```
actionA   ATTACK | FIRE | HEAL | null
actionB   PICKUP | SWAP | DOOR | null
```

캐비닛은 `MOVE`로 들어가고 나온다. B 토큰이 없다.

서버는 의미를 정하고 클라는 단어를 정한다. 클라가 자체적으로 상황을 판단해 어떤
행동이 가능한지 계산하지 않는다 — 그러면 버튼 라벨과 실제 동작이 갈릴 수 있다.

### `EVENT`

```json
{ "type": "EVENT", "event": "SHOT", "path": [[10,7],[10,6],[10,5]] }
{ "type": "EVENT", "event": "HIT" }
{ "type": "EVENT", "event": "DEAD",        "playerId": "p2" }
{ "type": "EVENT", "event": "PICKUP",      "itemId": "i-31" }
{ "type": "EVENT", "event": "ROOM_CHANGE", "roomId": "room-08" }
```

`SHOT`의 `path`는 **발사자 타일부터** 시작한다. 부시 안에서 쏘면 이 때문에 위치가
드러난다 — 의도된 동작이다.

`SHOT`의 `path`는 명중 시 **피격자 타일에서 끝난다**(부시 안이어도). 벽·캐비닛
타일은 경로에 넣지 않는다. 결정 배경은 GAME_RULES §5.

`HIT`은 **명중 사실만** 담는다. 대상 ID, HP, 생사 여부를 넣지 않는다.
`OutboundTest`가 이 형태를 고정한다.

| 이벤트 | 받는 사람 |
|---|---|
| `SHOT` | 방 안의 모든 플레이어 (사수를 못 보는 사람 포함 — 그것이 노출이다) |
| `SWING` | 방 안의 모든 플레이어 (`from` 공격자 타일, `to` 휘두른 타일) |
| `HIT` | 공격자만 |
| `YOU_DIED` | 사망자만 |

이벤트는 같은 tick의 `SNAPSHOT` **뒤에** 보낸다. `PICKUP`·`ROOM_CHANGE` 이벤트는
아직 보내지 않는다 — 스냅샷 변화로 충분해서 필요해질 때 추가한다. `DEAD` 이벤트는 아직 없다 —
다른 플레이어는 다음 스냅샷에서 시체가 사라지는 것으로 안다.

`CHAT` 이벤트는 V1에 없다.

### `YOU_DIED`

```json
{ "type": "YOU_DIED", "score": 1270, "kills": 3, "survivedSeconds": 412,
  "killer": "kang", "weapon": "PISTOL" }
```

`killer`/`weapon`은 **사망자에게만** 간다. 살아 있는 동안 숨겨지는 정보(상대 무기)지만
이 시점에 받는 사람은 이미 탈락했다. 처치자가 없는 사망(향후 끊김 타임아웃)이면 둘 다
`null`. 문장은 클라가 만든다 — 서버는 무기 코드만 보낸다.

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
