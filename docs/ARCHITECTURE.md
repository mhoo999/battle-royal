# ARCHITECTURE

## 원칙

**게임 규칙을 Spring에서 완전히 분리한다.** `game/core`와 `game/rule`은 Spring을
import하지 않는다. 그래야 20Hz 루프를 띄우지 않고 결정론적 단위 테스트가 가능하다.

```
Game State  ->  Game Logic  ->  Network State  ->  Client Renderer
```

서버는 시각적 표현에 의존하지 않는다. 렌더러는 게임 규칙을 건드리지 않고 교체할 수
있어야 한다.

---

## 패키지

```
com.example.battleroyal
├── game/core/     순수 도메인 — 아무것도 import하지 않는다
│   Pos, Direction, TileType, GridMap, Player, Room, Item, ItemKind,
│   Command, Concealment, ActionA, ActionB, GameEvent
├── game/rule/     상수와 판정 — 전부 tick 단위. core만 의존한다
│   GameConstants, MovementRules, VisibilityRules, ActionResolver,
│   RoomSimulator, CombatRules(raycast 포함), ScoreRules, GuardRules (V2.2 초소 군인),
│   Bags, Quests, ItemValues
├── game/map/      MapTemplate + 템플릿 문자열 상수
├── game/loop/     GameLoopService, RoomRegistry (토러스 월드), RoomBroadcaster, DeathListener,
│                  TickDriver (루프 스레드에서 매 틱 행동하는 쪽의 자리)
├── bot/           BotDirector (TickDriver: 사람이 있을 때 봇을 채움), BotBrain (봇 하나의 판단)
├── ws/            GameWebSocketHandler, SessionRegistry, SnapshotFilter, Outbound(이벤트 wire)
├── web/           SessionController, MeController, RankingController, GameSessionService,
│                  AccountService (V2: Google sign-in), HideoutService/Controller (V2),
│                  SeasonService/Controller (V2.1: 마감·트로피·와이프, 1분마다 @Scheduled)
├── config/        WebSocketConfig, SecurityConfig (V2)
├── persistence/   GameResult, Account, StashItem, Sortie, Season, Trophy 엔티티 + repository
└── config/        WebSocketConfig, JacksonConfig
```

`RoomSimulator`는 `(방 상태, 커맨드, tick) -> 새 상태` 형태의 순수 함수다. `core`가
아니라 `rule`에 있다 — 규칙을 조율하므로 `core`에 두면 `core -> rule` 의존이 생기고
두 순수 패키지 사이에 순환이 만들어진다. **`core`는 아무것도 import하지 않는다.**

`VisibilityRules`는 별도 클래스로 분리한다. 정보 누출은 가장 테스트하기 쉬우면서
가장 치명적인 버그이므로 한 곳에 모아 둔다.

원래 PRD 초안은 `RoomManager`/`PlayerManager`/`ItemManager`/`GameLoop`/
`CombatService`/`MovementService`/`GameStateService`/`WebSocketHandler` 8개
서비스를 제안했으나, V1 규모에 과하고 "과설계 금지" 규칙과 충돌하므로 위 구조로
축소했다. 필요해질 때 쪼갠다.

---

## 동시성

**JVM당 게임 루프 스레드 1개가 모든 활성 방을 처리한다.**

```
WS inbound thread              Game loop thread (20Hz)
       |                              |
  토큰 -> playerId                 tick 시작
  커맨드 파싱                          |
       |                          join/leave 반영
  RoomRegistry 큐에 제출  ----->  커맨드 drain -> 방으로 라우팅 -> 적용
  ConcurrentLinkedQueue               |
                                 raycast / 쿨다운 / 타이머 -> 이벤트는 Room에 적재
                                      |
                                 dirty·이벤트 있는 방만 스냅샷 -> per-player 필터 -> send
                                 이벤트 drain -> 대상별 send (SHOT 방 전체, HIT 공격자, YOU_DIED 사망자)
                                      |
                                 시체 제거 -> 월드 크기 맞춤 (fitWorld)
```

**큐는 `RoomRegistry`에 하나뿐이다. 방별 큐가 아니다.** 루프 스레드가 하나라 방별
큐는 처리량 이득이 없고, 커맨드를 어느 방에 넣을지 두 스레드가 합의해야 하는 문제만
생긴다. 스레드 경계를 한 곳으로 모아 라우팅을 루프 쪽으로 넘겼다.

방 상태는 루프 스레드만 변경한다. **락을 추가하지 않는다. WebSocket 스레드에서 방
상태를 변경하지 않는다.** 이 결정을 깨면 전면 재작업이 된다.

**봇(`bot`)도 같은 길로만 세상을 바꾼다.** `BotDirector`는 루프 스레드에서 커맨드 적용
직전에 불리고(`TickDriver`), 방을 읽어 봇마다 `BotBrain`으로 다음 행동을 고른 뒤 사람과 똑같은
`Command`를 `RoomRegistry`에 넣는다. 봇은 소켓도 계정도 없는 `Player`일 뿐이라 브로드캐스터는
보낼 곳이 없어 건너뛰고, 결과 기록은 `~` 이름이라 남기지 않는다. `game/loop`는 `bot`을 모른다.

**쿨다운 중 도착한 커맨드는 버려진다. 큐에 쌓이지 않는다.** 따라서 클라이언트는
쿨다운보다 긴 주기로 입력을 보내야 한다.

---

## 스택

| 영역 | 선택 | 이유 |
|---|---|---|
| 런타임 | Java 21 | — |
| 빌드 | Gradle Wrapper | 로컬에 Gradle CLI 없음 |
| 서버 | Spring Boot 4.1.1 | start.spring.io가 3.x를 더 이상 제공하지 않음 |
| WebSocket | raw `TextWebSocketHandler` + Jackson | STOMP는 per-player 필터링에 오버헤드 |
| DB | H2 (file) → Postgres/RDS | 로컬 개발 마찰 최소화 |
| 프론트 | `resources/static` 순수 HTML/CSS/JS | 빌드 스텝 없음. CSS Grid 15x15 |
| e2e | Node 내장 WebSocket (`e2e/smoke-two-sockets.mjs`) | 의존성 없음. 화면은 브라우저 수동 확인 |

프론트엔드에 빌드 스텝을 도입하지 않는다. 게임 보드는 `<pre>` 하나가 아니라
**CSS Grid + 개별 셀**로 렌더한다.

---

## 영속성

실시간 상태(Room, Player, Item, Bullet, Map)는 **서버 메모리에만** 둔다.

DB에 저장하는 것은 `GameResult`뿐이다. 실시간 이동 이벤트를 DB에 쓰지 않는다.
랭킹은 `GameResult`에 대한 top-N 쿼리다 — 별도 테이블을 두지 않는다. V2 브랜치부터
`Account`(구글 `sub`, 닉네임)가 생겼다. WebSocket은 여전히 게임 세션 토큰으로 인증한다:
로그인은 토큰을 받기 전 단계(`POST /api/session`)에서만 쓰인다.

사망은 루프 스레드에서 `DeathListener`로 알린다. `game/loop`는 듣는 쪽을 import하지
않는다. `GameSessionService`는 토큰을 폐기하고, `ResultRecorder`는 단일 writer
스레드에 저장을 넘긴다. **루프 스레드에서 DB를 쓰지 않는다** — 쓰기 하나가 tick보다
길 수 있다.

---

## AWS 단계

```
Phase 1   Browser -> Spring Boot -> EC2 -> RDS
Phase 2   Browser -> ALB -> EC2 x N
Phase 3   + Redis (방->서버 디렉터리, pub/sub)
```

Phase 3에서 Redis는 **20Hz 게임 상태 공유에 쓰지 않는다.** 지연과 경합으로
무너진다. 방을 단일 서버에 고정(sticky routing)하고 Redis는 디렉터리와 서버 간
메시징에만 쓴다.

서비스는 구체적 요구가 생긴 시점에만 추가한다.
