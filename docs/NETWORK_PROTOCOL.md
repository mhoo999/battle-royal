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

**로그인한 계정**: 요청 본문은 무시하고 계정의 닉네임으로 플레이한다. 닉네임을 아직
정하지 않았으면 409. (V2 브랜치)

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
    "equipped": 0,
    "crate": null,
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

`self.item`: `KNIFE | BAT | PISTOL | CROSSBOW | MEDKIT | PAN | SPOON | CUP | DOLL |
RECORDER | REGISTER`, or null for empty hands. `YOU_DIED.weapon` is null for a
bare-hand kill.

**V2: 인벤토리 3칸.** `self.inventory`는 칸 순서대로 `{kind, ammo}` 또는 빈 칸 `null`,
`self.equipped`는 A가 쓰는 칸, `self.item`/`self.ammo`는 장착 칸의 것이다.
`self.crate`는 **내가 열어 둔 상자의 내용**(순서대로)이고, 열린 상자가 없으면 `null`이다.

**바닥에는 상자(`items[]`)만 보낸다. 내용은 연 사람에게만, 그것도 `self.crate`로만
보낸다(결정).** 상자를 열기 전에는 무엇이 들었는지 아무도 모른다. 클라가 그리지 않더라도
전송하면 개발자 도구로 보이므로 필드 자체를 두지 않는다 — `SnapshotFilterTest`가
`FloorItem`을 `id`/`x`/`y`로 고정하고, 상자 내용이 연 사람 말고는 가지 않는 것도 확인한다.
`items[].id`는 상자의 ID다. 들고 있는 것은 본인만 안다.

`actionA`/`actionB`는 서버가 계산한 현재 유효 행동 **토큰**이다. 표시 문구가 아니다.

```
actionA   ATTACK | FIRE | HEAL | null
actionB   OPEN | CLOSE | DOOR | null      (V1: PICKUP | SWAP | DOOR)
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
| `FELL` | 사망 순간 사망자를 볼 수 있었던 같은 방 사람 (사망자 제외) |

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
