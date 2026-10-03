# 서버 명령어 모음

운영 중에 자주 쓰는 명령. 배경과 설치 과정은 `docs/AWS_DEPLOYMENT.md`.

| 무엇 | 값 |
|---|---|
| 접속 주소 | https://battleroyale.site |
| 인스턴스 | `i-02d65fab4965cb3c4` (t3.micro, Amazon Linux 2023), 탄력적 IP `54.116.237.112` |
| 앱 | systemd `battle-royal`, `/opt/battle-royal/app.jar`, 127.0.0.1:8080 |
| 앱 설정(비밀 값) | `/etc/battle-royal/env` (root만 읽음) |
| Nginx 설정 | `/etc/nginx/conf.d/battle-royal.conf` |
| DB | `battle-royal-db.c1caasea602e.ap-northeast-2.rds.amazonaws.com` / DB·사용자 `battleroyal` |

---

## 0. 접속

**서버 셸:** EC2 콘솔 → 인스턴스 체크 → **Connect → Session Manager → Connect**.
SSH(22)는 닫혀 있다. 처음 뜨는 `sh-5.2$`는 `ssm-user`이고 `sudo`가 비밀번호 없이 된다.

```bash
bash                          # 화살표·탭 완성이 되는 셸로
sudo su - ec2-user            # ec2-user로 (홈 디렉터리가 필요할 때)
```

**DB (내 PC에서):** `scripts\db-tunnel.cmd` 더블클릭 → Workbench로 `127.0.0.1:13306`.

---

## 1. 앱 (battle-royal)

```bash
systemctl status battle-royal --no-pager          # 떠 있나 (active (running))
sudo systemctl restart battle-royal               # 재시작 (접속자 전원 끊김, 월드 초기화)
sudo systemctl stop battle-royal                  # 멈춤
sudo systemctl start battle-royal                 # 시작

sudo journalctl -u battle-royal -n 100 --no-pager # 최근 로그 100줄
sudo journalctl -u battle-royal -f                # 실시간 로그 (Ctrl+C로 빠져나옴)
sudo journalctl -u battle-royal --since "1 hour ago" --no-pager
sudo journalctl -u battle-royal --since today | grep -E "ERROR|WARN"

curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8080/   # 앱이 직접 답하나 (200)
```

로그에서 자주 보는 줄:

| 로그 | 뜻 |
|---|---|
| `Started BattleRoyalApplication` | 기동 완료 |
| `World 3x3 -> 4x4 for 2 players` | 인원에 맞춰 월드 크기 변경 |
| `p-... joined room-5 (3x3 world)` | 입장 |
| `p-... did not come back in time` | 15초 안에 재접속 안 해서 탈락 |
| `Game loop stopped after N ticks` | 종료 중 |

---

## 2. 배포 상태

배포는 `main`에 push하면 GitHub Actions가 한다. 손으로 할 일은 거의 없다.

```bash
ls -l /opt/battle-royal/                  # app.jar(현재), app.jar.prev(직전)
sha256sum /opt/battle-royal/app.jar*      # 두 jar가 같은지
```

**손으로 되돌리기** (자동 롤백이 실패했거나 배포 후 문제를 발견했을 때):

```bash
sudo install -o battleroyal -g battleroyal -m 644 /opt/battle-royal/app.jar.prev /opt/battle-royal/app.jar
sudo systemctl restart battle-royal
```

배포 기록은 PC에서 `gh run list --limit 5`, 자세히는 GitHub → Actions.

---

## 3. Nginx · HTTPS

```bash
sudo nginx -t                                   # 설정 문법 검사 (바꾼 뒤 항상)
sudo systemctl reload nginx                     # 끊김 없이 설정 반영
systemctl status nginx --no-pager

sudo tail -n 50 /var/log/nginx/access.log       # 요청 기록
sudo tail -n 50 /var/log/nginx/error.log        # 에러
sudo grep "/ws/game" /var/log/nginx/access.log | tail -n 20   # WebSocket 응답 코드 (101 정상, 403 Origin 문제)

sudo certbot certificates                       # 인증서 만료일
sudo certbot renew --dry-run                    # 갱신 연습
systemctl list-timers | grep certbot            # 자동 갱신 타이머
```

---

## 4. DB

서버에서 바로 (앱 계정, 비밀번호는 `/etc/battle-royal/env`의 DB_PASSWORD):

```bash
mysql --ssl -h battle-royal-db.c1caasea602e.ap-northeast-2.rds.amazonaws.com -u battleroyal -p battleroyal
```

```sql
-- 랭킹 상위 20
SELECT nickname, score, kills, survived_seconds, ended_at
FROM game_result ORDER BY score DESC LIMIT 20;

-- 최근 10판
SELECT id, nickname, score, kills, ended_at FROM game_result ORDER BY id DESC LIMIT 10;

-- 판 수, 최고 점수, 오늘 판 수
SELECT COUNT(*) AS games, MAX(score) AS best,
       SUM(ended_at >= CURDATE()) AS today
FROM game_result;

-- 지우기 전에는 항상 SELECT로 대상을 먼저 본다. 되돌릴 수 없다.
-- SELECT * FROM game_result WHERE nickname = 'first';
-- DELETE FROM game_result WHERE nickname = 'first';
```

`ended_at`은 UTC다(한국 시간 = +9시간).

---

## 5. 서버 상태

```bash
uptime                       # 켜진 시간, 부하
free -m                      # 메모리 (t3.micro 1 GiB, JVM 최대 512 MB)
df -h /                      # 디스크 (10 GiB)
top                          # 프로세스 (q로 종료)
sudo ss -ltnp                # 열려 있는 포트: 80/443 nginx, 127.0.0.1:8080 java
sudo journalctl --disk-usage # 로그가 디스크를 얼마나 쓰나
sudo journalctl --vacuum-time=14d   # 14일보다 오래된 로그 삭제
systemctl status amazon-ssm-agent --no-pager   # Session Manager·자동 배포가 이걸로 들어온다
sudo reboot                  # 재부팅 (앱·Nginx는 자동으로 다시 뜬다)
```

---

## 6. 증상별로 먼저 볼 것

| 증상 | 먼저 |
|---|---|
| 사이트가 안 열림 | `systemctl status nginx battle-royal --no-pager` |
| `502 Bad Gateway` | 앱이 죽었거나 기동 중. `sudo journalctl -u battle-royal -n 100 --no-pager` |
| 페이지는 뜨는데 "재접속 중" 반복 | `grep "/ws/game"` 로 Nginx 응답 코드. 403이면 Origin/전달 헤더 |
| 느림, 버벅임 | `top`, `free -m`. CloudWatch 경보 `br-ec2-cpu-credits` 상태 |
| 배포가 실패로 끝남 | GitHub Actions 로그의 install.sh 출력, 서버 `journalctl -u battle-royal` |
| 랭킹이 비어 있음 | 위 4번 쿼리로 DB 확인, 로그에서 `ERROR` |
| 인증서 경고 | `sudo certbot certificates`, `sudo certbot renew` |
| 디스크 부족 | `df -h /`, `sudo journalctl --vacuum-time=7d` |
| Session Manager 접속 불가 | EC2 콘솔에서 인스턴스 상태 검사, 필요하면 sg-web에 SSH 22를 My IP로 잠깐 열고 `.pem`으로 |
