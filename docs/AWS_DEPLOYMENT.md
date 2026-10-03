# AWS 배포 진행 문서

`progress.md`가 게임 개발의 인수인계 문서라면, 이 문서는 **AWS 배포**의 인수인계
문서다. 콘솔 작업이 대부분이라 코드와 git만으로는 상태를 알 수 없다. 그래서 여기에
**무엇을 만들었고, 어떤 값이 나왔고, 다음에 무엇을 할지**를 남긴다.

- 체크박스는 끝낼 때마다 `[x]`로 바꾼다.
- 콘솔에서 나온 값(인스턴스 ID, 엔드포인트 등)은 §2 기록란에 적는다.
  **비밀번호·키 파일 내용은 절대 적지 않는다.** 이 파일은 git에 올라간다.
- 막혔던 문제와 해결은 §9 문제 기록에 한 줄씩 남긴다. 포트폴리오에서 가장 값진
  부분이 이것이다.

---

## 0. 현재 상태

**현재 단계:** Phase 10 (자동 배포) 진행 중. 저장소 쪽(`deploy/install.sh`, `ci.yml`의
`deploy` 잡)은 작성됨, 콘솔 ①~⑤는 아직. Phase 9 수동 재배포는 건너뜀: 서버에는 토러스 이전
버전(`bdacabd` 코드)이 떠 있고, 첫 자동 배포가 토러스를 올린다.

**완료:** 로컬 리허설 (§4 Phase 0). Phase 1 (2026-10-02): 루트 MFA, 관리자 IAM
사용자 `admin-myeonghoon`(MFA), 예산 `battle-royal-monthly` $5. 계정은 크레딧 방식
Free plan이다. Phase 2 (2026-10-02): 보안 그룹 두 개, sg-db 소스는 sg-web 참조.
Phase 3: RDS MySQL **8.4** 생성(8.0은 Extended Support 과금, §9). Phase 4: EC2와 탄력적
IP, SSH 접속. Phase 5 (2026-10-03): Corretto 21.0.12.1, Nginx 기본 페이지, `battleroyal`
사용자, EC2 → RDS 3306 OPEN. Phase 6: 앱 사용자 `battleroyal`(8개 권한, MySQL 8.4.11),
`mariadb105` 클라이언트가 `--ssl`로 8.4에 정상 접속. Phase 7: systemd + Nginx, 첫 배포.
Phase 8 일부: 로컬에서 smoke 전부 통과(공인 IP, Nginx 경유), `/h2-console` 404,
`/api/ranking`이 RDS의 결과 행을 읽음. 사용자가 브라우저로 플레이 확인(2026-10-03).

**다음 한 걸음:** Phase 10 콘솔 ①~⑤ → `main` push → Actions의 deploy 잡 확인.

> **이 PC의 SSH:** Windows OpenSSH 클라이언트가 설치되어 있지 않다. Git의 것을 쓴다:
> `& "C:\Program Files\Git\usr\bin\ssh.exe" -i "$env:USERPROFILE\.ssh\battle-royal.pem" ec2-user@54.116.237.112`
> (`scp.exe`도 같은 폴더). Git Bash에서는 `ssh`가 바로 된다.

> **크레딧 주의:** EC2 + RDS + IPv4를 24시간 켜 두면 월 $25 안팎이 크레딧에서
> 빠진다. $120이면 약 4~5개월로, 183일보다 먼저 바닥날 수 있다. Free plan은 크레딧이
> 다 떨어지거나 기간이 끝나면 유료 플랜으로 올리기 전까지 계정이 닫힌다. 안 쓰는
> 기간에는 §7 순서로 RDS를 스냅샷 후 삭제한다.

---

## 1. 목표와 구성

`CLAUDE.md` §10의 원칙을 따른다: **서버 1대 + RDS로 시작하고, 구체적인 필요가 생길
때만 서비스를 더한다.** ALB, Redis, 컨테이너 오케스트레이션은 지금 없다.

```
 브라우저 / 모바일
      │  HTTP :80  (나중에 HTTPS :443)
      ▼
┌──────────────────────── EC2 (Amazon Linux 2023, t3.micro) ────────────────────────┐
│  Nginx :80  ──proxy──▶  Spring Boot 127.0.0.1:8080   (systemd: battle-royal)     │
│  (WebSocket 업그레이드)   게임 월드는 이 JVM 메모리에만 있다                      │
└────────────────────────────────────────────┬─────────────────────────────────────┘
                                             │ MySQL :3306 (sg-web에서만 허용)
                                             ▼
                               RDS for MySQL 8.4 (db.t4g.micro)
                               퍼블릭 액세스 없음. game_result 테이블 하나
```

| 구성 요소 | 선택 | 이유 |
|---|---|---|
| 리전 | 서울 `ap-northeast-2` | 플레이어와 가까울수록 RTT가 줄어든다. 이 게임은 클라이언트 예측이 없어서 RTT가 곧 조작감이다 |
| 서버 | EC2 1대, Amazon Linux 2023 | 게임 월드가 JVM 메모리에 있으므로 서버는 1대여야 한다(§CLAUDE.md 9) |
| 실행 방식 | jar + systemd | Docker 없이도 재시작·부팅 시 자동 실행·로그가 해결된다. 한 대에는 이것으로 충분하다 |
| 앞단 | Nginx | 앱을 127.0.0.1에만 열어 두고 인터넷은 Nginx만 마주한다. 나중에 HTTPS를 붙일 자리 |
| DB | RDS for MySQL 8.4 | 결과·랭킹만 저장한다. 한국 Spring 채용에서 가장 흔한 조합. 8.0은 표준 지원이 끝나 Extended Support 요금이 붙는다(§9) |
| 네트워크 | 기본 VPC | 서브넷 설계는 서버가 여러 대가 될 때 한다. 대신 RDS는 퍼블릭 액세스를 끈다 |
| 접속 | SSH (내 IP만 허용) | 가장 단순하다. 익숙해지면 SSM Session Manager로 바꿔 22번 포트를 닫는다(§8) |

### 비용 — 먼저 읽을 것

- **2025-07-15 이후 만든 계정**은 크레딧 기반 프리 티어(가입 크레딧 + 활동 크레딧,
  최대 6개월 무료 플랜)다. 그 전 계정은 12개월 프리 티어(EC2 750시간, RDS 750시간,
  스토리지 20GB)다. **어느 쪽인지 Billing 콘솔 → Free Tier에서 먼저 확인한다.**
- **퍼블릭 IPv4는 무료가 아니다.** 2024-02부터 시간당 약 $0.005(월 약 $3.6)가
  붙는다. 탄력적 IP도 같다. 옛 12개월 프리 티어에는 월 750시간이 포함되어 있다.
- RDS는 **중지해도 7일 뒤 자동으로 다시 켜진다.** 오래 안 쓸 거면 스냅샷을 찍고
  삭제한다(§7).
- 예상: 프리 티어 안이면 월 $0~4, 밖이면 EC2 t3.micro + RDS db.t4g.micro + IPv4로
  월 약 $25 안팎. 정확한 값은 [AWS Pricing Calculator](https://calculator.aws)로.
- **예산 알림(Phase 1)을 만들기 전에는 아무것도 띄우지 않는다.**

---

## 2. 기록란

콘솔에서 값이 나오면 바로 채운다. (비밀번호는 여기 말고 비밀번호 관리자에.)

| 항목 | 값 |
|---|---|
| AWS 계정 ID | 495791792486 |
| 관리자 사용자 | admin-myeonghoon (IAM 사용자, AdministratorAccess, MFA) |
| 프리 티어 종류 (크레딧 / 12개월) | 크레딧 방식 Free plan. 2026-10-02 기준 $120, 183일 남음 (약 2027-04-03 종료) |
| 리전 | ap-northeast-2 |
| 예산 알림 이름 / 금액 | battle-royal-monthly / $5 (월간 비용 예산) |
| 보안 그룹 sg-web ID | sg-0494364a03526622b (`battle-royal-web`) |
| 보안 그룹 sg-db ID | sg-0a8511e0c3c9f0f86 (`battle-royal-db`) |
| RDS 식별자 | battle-royal-db |
| RDS 엔드포인트 | battle-royal-db.c1caasea602e.ap-northeast-2.rds.amazonaws.com (MySQL 8.4) |
| RDS 초기 DB 이름 | battleroyal |
| EC2 인스턴스 ID | i-02d65fab4965cb3c4 (`battle-royal-server`) |
| EC2 인스턴스 유형 | t3.micro, Amazon Linux 2023 x86_64, gp3 10 GiB |
| 키 페어 이름 (파일은 로컬 어디에) | battle-royal (`%USERPROFILE%\.ssh\battle-royal.pem`, 저장소 밖) |
| 탄력적 IP | 54.116.237.112 |
| 배포 버킷 | battle-royal-deploy-495791792486 (lifecycle `expire-releases`, 14일) |
| GitHub 배포 역할 | `deploy` (arn:aws:iam::495791792486:role/deploy, 인라인 정책 `deploy`) |
| EC2 인스턴스 역할 | battle-royal-ec2 (AmazonSSMManagedInstanceCore + `read-releases`) |
| 접속 URL | http://54.116.237.112/ |
| 최초 배포 일시 / 커밋 | 2026-10-03 / 게임 코드 `bdacabd` (그 뒤 커밋은 문서뿐) |

---

## 3. 전체 체크리스트

- [x] **Phase 0** 로컬 리허설 (운영 프로필 + MySQL + Nginx + 브라우저)
- [x] **Phase 1** 계정 준비: 루트 MFA, 관리자 사용자, 예산 알림 (2026-10-02)
- [x] **Phase 2** 보안 그룹 두 개 (2026-10-02)
- [x] **Phase 3** RDS MySQL 생성 (2026-10-02, 8.4)
- [x] **Phase 4** EC2 생성 + 탄력적 IP (2026-10-02)
- [x] **Phase 5** 서버 준비 (Java, Nginx, MySQL 클라이언트, 사용자·디렉터리) (2026-10-03)
- [x] **Phase 6** DB 사용자 만들기 (2026-10-03)
- [x] **Phase 7** 첫 배포 (jar, env, systemd, Nginx) (2026-10-03)
- [x] **Phase 8** 동작 확인 (브라우저 두 대, smoke, DB) (2026-10-03)
- [ ] **Phase 9** 재배포 절차 한 번 연습
- [ ] (선택) 도메인 + HTTPS
- [ ] **Phase 10** 자동 배포 (GitHub Actions + OIDC + S3 + SSM)
- [ ] (선택) SSH 포트 닫기 (SSM Session Manager로 접속)

---

## 4. 단계별 작업

### Phase 0 — 로컬 리허설 ✅ (2026-09-29)

AWS에 올리기 전에, 운영과 같은 조합을 로컬에서 먼저 돌려 봤다. 콘솔 작업 중에 코드
문제까지 섞이면 원인을 가르기 어렵다.

| 확인한 것 | 결과 |
|---|---|
| `SPRING_PROFILES_ACTIVE=prod`로 jar 실행, Docker의 MySQL 8.0에 연결 | 성공. `game_result` 테이블 자동 생성, utf8mb4 |
| smoke 스위트를 운영 jar에 직접 | 전부 통과. 결과 행이 MySQL에 저장되고 `/api/ranking`이 읽음 |
| H2 콘솔 `/h2-console` | 404 (운영에서 꺼짐) |
| Docker의 Nginx(`deploy/nginx-battle-royal.conf`)를 앞에 두고 smoke | 전부 통과 |
| 같은 구성에서 **브라우저** 접속 | 처음엔 **403** → 고침 → 접속 성공 (§9 첫 줄) |
| 다른 Origin에서 WebSocket | 403 (거부 유지) / 같은 Origin 101 |
| 한글 닉네임 저장 | `한글닉` 정상 (UTF-8 바이트 확인) |

저장소에 추가된 배포 파일:

| 파일 | 역할 |
|---|---|
| `src/main/resources/application-prod.properties` | 운영 프로필. DB 접속 정보는 환경 변수로, H2 콘솔 끔, 127.0.0.1에만 바인드 |
| `deploy/battle-royal.service` | systemd 유닛. 부팅 시 자동 실행, 죽으면 재시작 |
| `deploy/nginx-battle-royal.conf` | Nginx 사이트. WebSocket 업그레이드와 전달 헤더 |
| `deploy/env.example` | `/etc/battle-royal/env` 템플릿 (DB_URL, DB_USERNAME, DB_PASSWORD) |

로컬에서 다시 리허설하려면:

```bash
docker run -d --name br-mysql -e MYSQL_ROOT_PASSWORD=rootpw -e MYSQL_DATABASE=battleroyal \
  -e MYSQL_USER=battleroyal -e MYSQL_PASSWORD=localtest -p 3307:3306 mysql:8.0
./gradlew bootJar
SPRING_PROFILES_ACTIVE=prod SERVER_PORT=8081 \
  DB_URL='jdbc:mysql://localhost:3307/battleroyal?sslMode=REQUIRED&serverTimezone=UTC' \
  DB_USERNAME=battleroyal DB_PASSWORD=localtest \
  java -jar build/libs/battle-royal-0.0.1-SNAPSHOT.jar
BASE_URL=http://127.0.0.1:8081 node e2e/smoke-two-sockets.mjs
docker rm -f br-mysql     # 끝나면
```

---

### Phase 1 — 계정 준비

**왜:** 루트 계정은 모든 권한을 가진다. 유출되면 끝이다. 일상 작업은 별도 사용자로
하고, 돈이 새는 것은 알림으로 막는다.

1. **루트 MFA**
   - 루트로 로그인 → 오른쪽 위 계정 이름 → **Security credentials**
   - **Multi-factor authentication (MFA)** → **Assign MFA device** → 인증 앱(Google
     Authenticator 등) 등록
2. **관리자 사용자** (둘 중 하나)
   - 간단: **IAM** → Users → Create user → 이름 `admin-<이름>` → *Provide user access to
     the AWS Management Console* 체크 → 권한: *Attach policies directly* →
     `AdministratorAccess` → 만든 뒤 이 사용자에도 MFA
   - 권장(요즘 방식): **IAM Identity Center**로 사용자를 만들고 AdministratorAccess
     권한 세트 할당. 로그인 URL이 따로 생긴다.
   - 이후 모든 작업은 이 사용자로. 루트는 결제 설정 말고는 쓰지 않는다.
3. **예산 알림**
   - **Billing and Cost Management** → **Budgets** → Create budget
   - 템플릿 **Monthly cost budget** (또는 *Zero spend budget*으로 1센트라도 나가면 알림)
   - 금액 예: $5, 알림 이메일 입력
   - 크레딧 계정이면 크레딧 잔액 알림도 켠다(Billing → Free Tier)
4. **리전**: 콘솔 오른쪽 위를 **Asia Pacific (Seoul) ap-northeast-2**로. 이후 모든
   리소스는 여기에. 다른 리전에 만든 리소스는 목록에 안 보여서 잊고 과금된다.

완료 기준: 루트 MFA ✔, 관리자 사용자로 로그인 ✔, 예산 알림 이메일 수신 확인 ✔.
→ §2에 계정 ID, 프리 티어 종류, 예산 기록.

---

### Phase 2 — 보안 그룹 두 개

**왜:** 보안 그룹은 인스턴스 앞의 방화벽이다. **DB는 웹 서버에서만** 들어올 수 있게
하고, 그 규칙을 IP가 아니라 **"sg-web에 속한 것"**으로 건다. EC2의 IP가 바뀌어도
규칙이 유지된다.

**EC2 콘솔 → Network & Security → Security Groups → Create security group**

**sg-web** (EC2용)
- 이름 `battle-royal-web`, VPC: 기본 VPC
- Inbound:

  | Type | Port | Source | 설명 |
  |---|---|---|---|
  | HTTP | 80 | 0.0.0.0/0 | 게임 접속 |
  | SSH | 22 | **My IP** | 나만 SSH. 절대 0.0.0.0/0 금지 |
  | (나중에) HTTPS | 443 | 0.0.0.0/0 | 도메인 붙일 때 |

- Outbound: 기본값(전체 허용) 그대로. 패키지 설치와 RDS 접속에 필요하다.
- 8080은 **열지 않는다.** 앱은 127.0.0.1에만 붙어 있고 Nginx가 대신 받는다.

**sg-db** (RDS용)
- 이름 `battle-royal-db`, VPC: 기본 VPC
- Inbound:

  | Type | Port | Source |
  |---|---|---|
  | MySQL/Aurora | 3306 | **sg-web의 보안 그룹 ID** (sg-로 시작하는 것을 검색해서 선택) |

완료 기준: 두 그룹 생성, sg-db의 소스가 IP가 아니라 sg-web ID. → §2에 ID 기록.

> 집/카페 IP가 바뀌면 SSH가 타임아웃난다. 그때는 sg-web의 22번 규칙을 **My IP**로
> 다시 저장한다.

---

### Phase 3 — RDS MySQL

**RDS 콘솔 → Create database**

| 항목 | 값 | 메모 |
|---|---|---|
| 생성 방식 | **Standard create** | Easy create는 설정이 숨겨진다 |
| Engine | **MySQL**, 버전 **8.4.x** 최신, **RDS Extended Support 체크 해제** | 8.0은 2026-07-31 RDS 표준 지원 종료. 고르면 Extended Support 요금이 붙는다 |
| Templates | **Free tier** (보이면) / 아니면 Dev/Test | Free tier는 단일 AZ, 소형 인스턴스로 제한된다 |
| DB instance identifier | `battle-royal-db` | |
| Master username | `admin` | 앱은 이걸 쓰지 않는다(Phase 6) |
| Credentials | **Self managed**, 강한 비밀번호 | 비밀번호 관리자에 저장 |
| Instance class | `db.t4g.micro` (또는 `db.t3.micro`) | 프리 티어 대상 표시 확인 |
| Storage | gp3 **20 GiB**, **Storage autoscaling 끔** | 결과 몇 행뿐. 자동 확장은 요금만 늘린다 |
| Compute resource | **Don't connect to an EC2 compute resource** | 보안 그룹을 직접 만들었으므로 |
| VPC | 기본 VPC | |
| Public access | **No** | 인터넷에서 DB로 직접 오는 길을 없앤다 |
| VPC security group | **Choose existing → sg-db** (default는 제거) | |
| Additional configuration → Initial database name | **`battleroyal`** | 비워 두면 DB가 안 만들어진다 |
| Backup retention | 1일 | 연습용 |
| Encryption | 기본값(켜짐) 그대로 | |
| Performance Insights / Enhanced monitoring | 끔 | 비용·복잡도 |

**Create database** → 상태가 **Available**이 될 때까지 10분 안팎.

완료 기준: Available, **Connectivity & security** 탭에서 **Endpoint** 확인, Publicly
accessible = No, 보안 그룹 = sg-db. → §2에 엔드포인트 기록.

> 문자 집합: MySQL 8.0/8.4의 기본은 `utf8mb4`라 한글 닉네임이 그대로 저장된다(리허설로
> 확인). 파라미터 그룹을 따로 만들지 않는다.

---

### Phase 4 — EC2 + 탄력적 IP

**EC2 콘솔 → Instances → Launch instances**

| 항목 | 값 | 메모 |
|---|---|---|
| Name | `battle-royal-server` | |
| AMI | **Amazon Linux 2023** (x86_64) | |
| Instance type | **t3.micro** (콘솔이 *Free tier eligible*로 표시하는 것) | 1 GiB 메모리. JVM 힙을 512MB로 제한해 둠 |
| Key pair | **Create new key pair** → RSA, `.pem` | 다운로드는 한 번뿐이다. 잃어버리면 새로 만들어야 한다 |
| Network settings → Edit | 기본 VPC, Auto-assign public IP **Enable**, **Select existing security group → sg-web** | |
| Storage | gp3 **10 GiB** | |

**Launch instance** → 상태 **Running**, 상태 검사 **2/2 passed**까지 기다린다.

**탄력적 IP (고정 주소)**
- **EC2 → Network & Security → Elastic IPs → Allocate Elastic IP address** → Allocate
- 방금 만든 IP 선택 → **Actions → Associate Elastic IP address** → 인스턴스 선택
- **왜:** 인스턴스를 중지했다 켜면 자동 할당 퍼블릭 IP는 바뀐다. 접속 주소가 계속
  바뀌면 곤란하다. (탄력적 IP도 퍼블릭 IPv4 요금이 붙는다. 인스턴스를 지울 때 같이
  Release해야 과금이 멈춘다.)

**키 파일 권한 (Windows)**: OpenSSH는 권한이 넓은 키를 거부한다.
```powershell
icacls .\battle-royal.pem /inheritance:r
icacls .\battle-royal.pem /grant:r "$($env:USERNAME):(R)"
```

**접속 확인**
```bash
ssh -i battle-royal.pem ec2-user@<탄력적 IP>
```

완료 기준: SSH 접속 성공. → §2에 인스턴스 ID, 유형, 키 페어, 탄력적 IP 기록.

---

### Phase 5 — 서버 준비 (SSH 안에서)

```bash
# 패키지
sudo dnf update -y
sudo dnf install -y java-21-amazon-corretto-headless nginx mariadb105
java -version            # 21.x 확인

# 앱 전용 사용자: 로그인 불가, 앱 파일만 소유. root로 앱을 돌리지 않는다.
sudo useradd --system --no-create-home --shell /sbin/nologin battleroyal
sudo mkdir -p /opt/battle-royal /etc/battle-royal
sudo chown battleroyal:battleroyal /opt/battle-royal

# Nginx 켜기
sudo systemctl enable --now nginx
curl -s -o /dev/null -w "%{http_code}\n" http://localhost/   # 200 (아직 Nginx 기본 페이지)
```

- `mariadb105`는 MySQL 서버가 아니라 **접속용 클라이언트**다. RDS에 붙어 사용자를
  만들 때만 쓴다.
- 브라우저로 `http://<탄력적 IP>/`에 들어가 Nginx 기본 페이지가 보이면 sg-web의 80번
  규칙까지 확인된 것이다.

완료 기준: `java -version` 21, 브라우저에 Nginx 기본 페이지.

---

### Phase 6 — DB 사용자 만들기

**왜:** 앱이 마스터 계정(`admin`)을 쓰면, 앱이 뚫렸을 때 DB 전체가 넘어간다. 앱에는
`battleroyal` DB에 필요한 권한만 준다.

EC2에서:
```bash
mysql -h <RDS 엔드포인트> -u admin -p        # 마스터 비밀번호 입력
```
```sql
CREATE USER 'battleroyal'@'%' IDENTIFIED BY '<앱용 비밀번호>';
-- 조회·저장 + 첫 부팅 때 테이블/인덱스를 만드는 권한 (ddl-auto=update)
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON battleroyal.* TO 'battleroyal'@'%';
FLUSH PRIVILEGES;
SHOW GRANTS FOR 'battleroyal'@'%';
```

- `'%'`여도 괜찮은 이유: DB에 들어올 수 있는 길 자체가 sg-db(= sg-web에서만)로 막혀
  있다. 네트워크와 계정, 두 겹이다.
- 접속이 **멈춘 채 타임아웃**나면 네트워크(보안 그룹), **Access denied**면 계정/비밀번호
  문제다. §9 참고.

완료 기준: `mysql -h <엔드포인트> -u battleroyal -p battleroyal`로 접속 성공.

---

### Phase 7 — 첫 배포

**① 로컬에서 jar 빌드 후 업로드** (t3.micro에서 Gradle 빌드는 메모리가 빠듯하다)
```bash
./gradlew test bootJar
scp -i battle-royal.pem build/libs/battle-royal-0.0.1-SNAPSHOT.jar ec2-user@<IP>:/tmp/app.jar
scp -i battle-royal.pem deploy/battle-royal.service deploy/nginx-battle-royal.conf ec2-user@<IP>:/tmp/
```

**② 서버에서 배치**
```bash
sudo mv /tmp/app.jar /opt/battle-royal/app.jar
sudo chown battleroyal:battleroyal /opt/battle-royal/app.jar

# 비밀 값: root만 읽을 수 있게 만들고 편집 (deploy/env.example 참고)
sudo install -m 600 -o root -g root /dev/null /etc/battle-royal/env
sudo nano /etc/battle-royal/env
#   DB_URL=jdbc:mysql://<RDS 엔드포인트>:3306/battleroyal?sslMode=REQUIRED&serverTimezone=UTC
#   DB_USERNAME=battleroyal
#   DB_PASSWORD=<앱용 비밀번호>

# systemd
sudo mv /tmp/battle-royal.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now battle-royal
sudo journalctl -u battle-royal -f      # "Started BattleRoyalApplication" 나올 때까지. Ctrl+C
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8080/   # 200
```

**③ Nginx 연결**
```bash
sudo mv /tmp/nginx-battle-royal.conf /etc/nginx/conf.d/battle-royal.conf
sudo nginx -T 2>/dev/null | grep -n "listen\|server_name"   # 80을 듣는 server 블록이 몇 개인지
```
- `/etc/nginx/nginx.conf` 안에도 80번을 듣는 기본 `server { ... }` 블록이 있으면,
  그쪽이 요청을 가져가 **Nginx 기본 페이지가 계속 뜬다.** 그 블록을 주석 처리하거나
  지운다(`sudo nano /etc/nginx/nginx.conf`).
```bash
sudo nginx -t && sudo systemctl reload nginx
curl -s http://localhost/ | grep -o "<title>.*</title>"   # <title>BATTLE ROYALE</title>
```

완료 기준: 브라우저에서 `http://<탄력적 IP>/`에 BATTLE ROYALE 로비. → §2에 접속 URL,
배포 일시, 커밋 해시 기록.

---

### Phase 8 — 동작 확인

1. **브라우저 두 개** (PC + 휴대폰 LTE가 가장 좋다): 둘 다 입장 → 문을 돌다 만남 →
   서로 움직임이 보임 → 맨손 공격으로 HP가 줄어듦
2. **로컬에서 smoke**:
   ```bash
   BASE_URL=http://<탄력적 IP> node e2e/smoke-two-sockets.mjs
   ```
3. **새로고침 복귀**: 게임 중 새로고침 → 같은 칸으로 돌아옴
4. **DB 확인**: 누군가 죽은 뒤
   ```bash
   mysql -h <엔드포인트> -u battleroyal -p battleroyal -e "SELECT * FROM game_result ORDER BY id DESC LIMIT 5;"
   ```
   로비 랭킹에도 보여야 한다.
5. **재부팅 내성**: `sudo reboot` → 1~2분 뒤 접속 → 자동으로 다시 떠 있어야 한다
   (systemd `enable` 확인). 게임 월드는 초기화되지만 랭킹은 남는다 — 그게 설계다.
6. **체감 지연**: 휴대폰에서 이동이 끈적하지 않은지. 이동 쿨다운 150ms가 RTT를 흡수하도록
   되어 있다(GAME_RULES §1).

완료 기준: 1~5 전부. 스크린샷을 남긴다(포트폴리오용).

---

### Phase 9 — 재배포 절차

코드를 고친 뒤 올리는 방법. **재시작하면 메모리의 게임 월드가 사라지고 접속자 전원이
끊긴다.** V1은 그걸 받아들인다(월드를 DB에 쓰지 않는다는 결정의 대가).

```bash
# 로컬
./gradlew test bootJar
scp -i battle-royal.pem build/libs/battle-royal-0.0.1-SNAPSHOT.jar ec2-user@<IP>:/tmp/app.jar
# 서버
sudo install -o battleroyal -g battleroyal -m 644 /tmp/app.jar /opt/battle-royal/app.jar
sudo systemctl restart battle-royal
sudo journalctl -u battle-royal -n 50 --no-pager
```

- `server.shutdown=graceful`이라 진행 중인 HTTP 요청은 마무리하고 내려간다.
- 되돌리기: 올리기 전에 `sudo cp /opt/battle-royal/app.jar /opt/battle-royal/app.jar.prev`
  해 두면, 문제 시 되돌려 놓고 restart.
- Nginx 설정이나 서비스 파일을 바꾼 경우에만 각각 `nginx -t && reload`,
  `daemon-reload && restart`.

완료 기준: 작은 변경(예: 로비 문구)을 한 번 재배포해 보고 반영 확인.

---

### Phase 10 — 자동 배포 (GitHub Actions + OIDC + S3 + SSM)

**목표:** `main`에 push → 테스트 통과 → 자동으로 서버에 배포. Phase 9의 수동 절차를
그대로 기계가 하게 만든다.

**왜 이 방식인가 (SSH 배포가 아니라):**
- GitHub Actions 러너의 IP는 매번 바뀐다. SSH로 배포하려면 22번을 전 세계에 열어야
  하는데, 이는 "SSH는 내 IP만"이라는 원칙(Phase 2)과 충돌한다.
- OIDC를 쓰면 GitHub에 **장기 AWS 키도, `.pem` 키도 저장하지 않는다.** 워크플로가 실행될
  때마다 AWS가 이 저장소의 `main` 브랜치에만 몇 분짜리 임시 권한을 준다.
- 서버는 SSM Agent(AL2023 기본 설치)로 명령을 받는다. 들어오는 포트를 하나도 열지
  않는다. 나중에 22번을 닫을 수 있다(§8 개선 후보 2).

```
 git push main
     │
     ▼
 GitHub Actions ── test 잡 (기존 CI: 단위 테스트, smoke, jar)
     │   needs: test, main push일 때만, 문서만 바뀐 커밋은 건너뜀
     ▼
 deploy 잡 ── OIDC로 임시 자격 증명 (역할: battle-royal-github-deploy)
     │  ① jar + deploy/install.sh 를 S3 releases/<커밋>/ 에 업로드
     │  ② SSM send-command (AWS-RunShellScript) → EC2
     ▼
 EC2 (인스턴스 역할: battle-royal-ec2)
     S3에서 받기 → app.jar.prev 백업 → 교체 → restart → 127.0.0.1:8080 확인
     실패하면 app.jar.prev로 되돌리고 실패로 끝냄
     │
     ▼
 deploy 잡 ── 결과 확인 → 공인 IP로 smoke
```

**배포하지 않는 경우:** 문서만 바뀐 커밋(`docs/**`, `*.md`). 배포는 서버를 재시작해
**접속자 전원을 끊고 월드를 초기화한다.** `progress.md`만 고쳤는데 플레이어가 튕기면
안 된다.

#### 콘솔에서 할 일 (사용자)

**① S3 버킷**
- **S3 → Create bucket**, 이름 `battle-royal-deploy-495791792486`(전역 고유해야 해서
  계정 ID를 붙임), 리전 서울
- **Block all public access 켬**(기본값), 나머지 기본값
- 만든 뒤 **Management → Create lifecycle rule**: 이름 `expire-releases`, prefix
  `releases/`, *Expire current versions of objects* **14일**. 옛 jar가 쌓여 요금이 늘지
  않게 한다.

**② GitHub OIDC 공급자** (계정에 한 번만)
- **IAM → Identity providers → Add provider** → **OpenID Connect**
- Provider URL `https://token.actions.githubusercontent.com`, Audience `sts.amazonaws.com`

**③ GitHub용 역할 `battle-royal-github-deploy`** (실제로는 `deploy`로 만듦, §2)
- **IAM → Roles → Create role** → **Web identity** → 위 공급자, Audience
  `sts.amazonaws.com`, GitHub organization `mhoo999`, repository `battle-royal`,
  branch `main`
- 권한은 정책을 바로 붙이지 않고, 만든 뒤 **Add permissions → Create inline policy →
  JSON**으로 아래를 넣는다. 이름 `deploy`.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    { "Effect": "Allow", "Action": "s3:PutObject",
      "Resource": "arn:aws:s3:::battle-royal-deploy-495791792486/releases/*" },
    { "Effect": "Allow", "Action": "ssm:SendCommand",
      "Resource": [
        "arn:aws:ec2:ap-northeast-2:495791792486:instance/i-02d65fab4965cb3c4",
        "arn:aws:ssm:ap-northeast-2::document/AWS-RunShellScript" ] },
    { "Effect": "Allow", "Action": "ssm:GetCommandInvocation", "Resource": "*" }
  ]
}
```

- **Trust relationships** 탭에서 `sub` 조건이 `repo:mhoo999/battle-royal:ref:refs/heads/main`
  인지 확인한다. 다른 저장소나 브랜치, PR은 이 역할을 쓸 수 없어야 한다.

**④ EC2용 역할 `battle-royal-ec2`**
- **IAM → Roles → Create role** → **AWS service → EC2** → 권한
  `AmazonSSMManagedInstanceCore`
- 만든 뒤 인라인 정책 `read-releases`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    { "Effect": "Allow", "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::battle-royal-deploy-495791792486/releases/*" }
  ]
}
```

- **EC2 → 인스턴스 선택 → Actions → Security → Modify IAM role** → `battle-royal-ec2`
- 몇 분 뒤 **Systems Manager → Fleet Manager**에 인스턴스가 **Online**으로 보이면 된다.
  안 보이면 서버에서 `sudo systemctl restart amazon-ssm-agent`.

**⑤ GitHub 저장소 변수** (비밀이 아니라서 Secrets가 아니라 Variables)
- GitHub 저장소 → **Settings → Secrets and variables → Actions → Variables**

  | 이름 | 값 |
  |---|---|
  | `AWS_ROLE_ARN` | ③ 역할의 ARN |
  | `DEPLOY_BUCKET` | `battle-royal-deploy-495791792486` |
  | `INSTANCE_ID` | `i-02d65fab4965cb3c4` |
  | `PUBLIC_URL` | `http://54.116.237.112` |

#### 저장소에서 할 일 (Claude)
- `deploy/install.sh`: 서버에서 실행되는 배포 스크립트. 백업, 교체, 재시작, 헬스 체크,
  실패 시 되돌리기.
- `.github/workflows/ci.yml`에 `deploy` 잡 추가.

완료 기준: 작은 변경을 `main`에 push → Actions에서 deploy 잡 성공 → 브라우저에 반영.
문서만 바꾼 push에서는 deploy 잡이 건너뛰어짐. 일부러 깨진 배포(헬스 체크 실패)에서
`app.jar.prev`로 되돌아감.

---

## 5. (선택) 도메인 + HTTPS

지금은 필수가 아니다. 휴대폰 브라우저 일부가 HTTP 페이지에 경고를 띄우는 정도.
클라이언트는 이미 `https:` 페이지에서 `wss:`를 쓰도록 되어 있다.

1. 도메인: Route 53에서 구입하거나 외부 등록 기관 → A 레코드를 탄력적 IP로
2. sg-web에 443 추가
3. `deploy/nginx-battle-royal.conf`의 `server_name _;`을 도메인으로
4. Let's Encrypt 인증서: certbot을 설치해 `certbot --nginx -d <도메인>`
   (Amazon Linux 2023에서 certbot 설치 방법은 그때 공식 문서로 확인한다)
5. 확인: `https://<도메인>/`에서 게임, 브라우저 개발자 도구 Network에 `wss://`

주의: Nginx 설정의 `X-Forwarded-Proto`, `X-Forwarded-Port`가 HTTPS에서도 앱의 same-origin
검사를 맞춰 준다. 지우지 않는다(§9 첫 줄).

---

## 6. 결정 기록

| 날짜 | 결정 | 이유 | 버린 대안 |
|---|---|---|---|
| 2026-09-29 | EC2 1대 + RDS MySQL, ALB·Redis 없음 | 게임 월드가 한 JVM 메모리에 있다. 두 번째 서버가 필요할 때 다시 본다 | ECS/Fargate, Elastic Beanstalk |
| 2026-09-29 | RDS for MySQL 8.0 | 결과·랭킹만 저장. 채용 시장에서 흔함 | PostgreSQL (드라이버와 URL만 바꾸면 된다) |
| 2026-10-02 | MySQL 8.0 → **8.4** | 8.0은 RDS 표준 지원 종료(2026-07-31), 새로 만들면 Extended Support 과금. 8.4가 현재 LTS. Connector/J는 Boot가 관리하는 버전이라 코드 변경 없음. 로컬 리허설은 8.0으로만 했다 | 8.0 + Extended Support |
| 2026-10-03 | 자동 배포는 OIDC + S3 + SSM | 러너 IP가 매번 바뀌어 SSH 배포는 22번을 전 세계에 열어야 한다. OIDC는 장기 키가 없고 SSM은 들어오는 포트가 없다. 서버 두 대보다 먼저: 두 대의 진짜 과제는 메모리 속 월드(방 고정·핸드오프)이고 파이프라인은 마지막 배포 단계만 바뀐다 | GitHub Actions + SSH, CodeDeploy |
| 2026-09-29 | jar + systemd, Docker 안 씀 | 한 대에 컨테이너 런타임은 옮길 것만 늘린다 | Docker Compose |
| 2026-09-29 | Nginx 앞단, 앱은 127.0.0.1 | 8080을 인터넷에 열지 않는다. HTTPS 붙일 자리 | 앱을 80에 직접 |
| 2026-09-29 | 기본 VPC, RDS 퍼블릭 액세스 끔 | 서버 한 대에 서브넷 설계는 과하다. DB는 여전히 인터넷에서 못 닿는다 | 커스텀 VPC + 프라이빗 서브넷 |
| 2026-09-29 | `ddl-auto=update` | 테이블 하나. 스키마 변경이 생기면 Flyway 도입 | Flyway 지금 도입 |
| 2026-09-29 | 로컬에서 빌드해 scp | t3.micro 1 GiB에서 Gradle 빌드는 빠듯하다 | 서버에서 git pull + 빌드 |
| 2026-09-29 | DB 연결 `sslMode=REQUIRED` | 전송 암호화. 인증서 검증(VERIFY_IDENTITY)은 RDS CA 번들을 받아야 해서 나중에 | 평문 |

---

## 7. 정리(삭제) 순서 — 과금 멈추기

실습을 끝내거나 오래 쉴 때. 순서가 틀리면 "사용 중"이라 안 지워진다.

1. RDS: **Actions → Take snapshot**(랭킹 보존용, 스냅샷도 용량만큼 과금) → **Delete**
   (final snapshot 여부 선택)
2. EC2: 인스턴스 **Terminate**
3. 탄력적 IP: **Release** ← 잊기 쉽다. 연결 안 된 EIP도 과금된다
4. 보안 그룹 두 개 삭제 (sg-db 먼저, 참조 관계 때문)
5. 키 페어 삭제(선택)
6. 다음 날 Billing → Bills에서 0으로 수렴하는지 확인

---

## 8. 이후 개선 후보 (필요할 때만)

`CLAUDE.md` §10: 서비스는 구체적인 필요가 생길 때만 더한다. 포트폴리오 가치가 큰
순서로:

1. **GitHub Actions 배포 자동화**: main에 push → 테스트 → jar 빌드 → EC2에 올리고
   재시작. 수동 재배포(Phase 9)를 두세 번 해 본 뒤에.
2. **SSM Session Manager**: EC2에 IAM 역할(`AmazonSSMManagedInstanceCore`)을 붙이고
   22번 포트를 닫는다. "SSH 포트를 열지 않는 운영"은 면접에서 좋은 이야깃거리다.
3. **HTTPS** (§5).
4. **CloudWatch**: 인스턴스 기본 지표 + 경보(CPU, 상태 검사 실패). 로그는 CloudWatch
   Agent로 journal을 보낼 수 있다.
5. **RDS 인증서 검증**: `sslMode=VERIFY_IDENTITY` + RDS CA 번들.
6. 서버 두 대 이상이 필요해질 때: ALB, 방→서버 고정, Redis 디렉터리/pub-sub
   (`CLAUDE.md` §10). 20Hz 게임 상태를 Redis에 쓰지 않는다.

---

## 9. 문제 기록

막힌 것, 원인, 해결을 한 줄씩. 날짜 순.

| 날짜 | 증상 | 원인 | 해결 |
|---|---|---|---|
| 2026-10-02 | RDS 생성 화면: `mysql-8.0.46 reached RDS end of standard support on Jul 31, 2026 and requires engine lifecycle support` | 문서를 쓸 때 8.0을 골랐는데 그사이 표준 지원이 끝났다. EOL 버전을 새로 만들면 Extended Support(vCPU 시간당 과금)가 강제된다 | 8.4로 생성. Phase 6에서 `caching_sha2_password`와 `mariadb105` 클라이언트 호환을 확인할 것 |
| 2026-10-02 | SSH `Connection timed out`. 같은 IP에서 80번은 즉시 거부(인스턴스까지 닿음), github.com:22는 열림 | sg-web의 "my ssh" 규칙 Type이 SSH가 아니라 **HTTPS(443)**로 저장됨. Source IP는 맞았다 | Type을 SSH로 수정. 포트별로 시험해 회선 문제와 규칙 문제를 갈랐다. 콘솔 목록보다 인스턴스 **Security 탭**의 실제 규칙을 볼 것 |
| 2026-09-29 | (로컬 리허설) Nginx 뒤에서 **브라우저만** WebSocket 403. Node smoke는 통과 | Spring의 same-origin 검사는 Origin과 "앱이 보기에 자기 주소"를 비교한다. Tomcat은 `X-Forwarded-Proto`만 있으면 포트를 80으로 가정한다. Node는 Origin을 안 보내서 검사를 안 탔다 | Nginx에서 `Host $http_host`, `X-Forwarded-Port $server_port` 전달 |

### 자주 만날 문제

| 증상 | 먼저 볼 것 |
|---|---|
| PowerShell에서 `ssh`를 찾을 수 없음 | Windows OpenSSH 클라이언트 미설치. Git의 `C:\Program Files\Git\usr\bin\ssh.exe`를 쓰거나, 관리자 PowerShell에서 `Add-WindowsCapability -Online -Name OpenSSH.Client~~~~0.0.1.0` |
| SSH 타임아웃 | sg-web 22번이 **현재** 내 IP인지. 인스턴스가 Running인지 |
| SSH `Permission denied (publickey)` | 사용자 이름 `ec2-user`, 키 파일, Windows 키 권한(icacls) |
| RDS 접속이 멈춤 → 타임아웃 | sg-db 소스가 sg-web인지, EC2가 sg-web에 속해 있는지, 같은 VPC인지 |
| `Access denied for user` | 사용자/비밀번호, `GRANT`, DB 이름 `battleroyal` 존재 여부 |
| `Unknown database 'battleroyal'` | RDS 생성 때 Initial database name을 비웠음 → `CREATE DATABASE battleroyal;` |
| 앱이 안 뜸 | `sudo journalctl -u battle-royal -n 100 --no-pager`. env 파일 경로/권한, DB_URL 오타 |
| 브라우저에 Nginx 기본 페이지 | nginx.conf의 기본 server 블록이 먼저 잡음 (Phase 7 ③) |
| `502 Bad Gateway` | 앱이 죽었거나 아직 부팅 중. `systemctl status battle-royal` |
| 게임 화면은 뜨는데 "재접속 중"만 반복 | WebSocket 업그레이드 헤더나 403. Nginx access log에서 `/ws/game` 응답 코드 확인 (`sudo tail /var/log/nginx/access.log`) |
| 한참 가만히 있으면 끊김 | `proxy_read_timeout`이 설정에 있는지 |
| 서버가 멈추거나 느려짐 | 메모리. `free -m`. JVM `-Xmx512m`이 t3.micro 1 GiB 기준 |
| Nginx가 앱에 연결 못 함 (`connect() ... failed (13: Permission denied)`) | SELinux가 enforcing이면 `sudo setsebool -P httpd_can_network_connect 1` (AL2023 기본은 permissive) |
| 예상 못 한 요금 | Billing → Bills에서 서비스별. 다른 리전에 만든 리소스, Release 안 한 EIP |

---

## 10. 포트폴리오로 남길 것

- 아키텍처 그림(§1)을 draw.io 등으로 다시 그린 것
- 콘솔 스크린샷: 보안 그룹 규칙(sg-db 소스가 sg-web), RDS Public access = No,
  systemd status, 두 기기에서 동시에 플레이하는 화면
- §9 문제 기록 — "무엇이 왜 안 됐고 어떻게 찾았는지"가 면접에서 가장 많이 물어보는 것
- 결정 기록(§6) — "왜 ECS가 아니라 EC2인가", "왜 Redis를 안 썼나"에 답할 수 있게
- 이력서 한 줄 예: *Spring Boot WebSocket 실시간 게임 서버를 EC2(Nginx + systemd)와
  RDS MySQL로 배포. DB는 보안 그룹 참조로 웹 서버에서만 접근, 앱은 루프백에만 바인드.
  리버스 프록시 뒤 WebSocket same-origin 403을 로컬 리허설로 사전에 발견·수정.*
