# Spring Boot 백엔드 (SyncML Server)

## 사전 요구사항

- **Java 17+** 설치 필요
- Gradle (Wrapper 포함)

### Java 설치 (Windows)

```powershell
# winget으로 설치
winget install Oracle.JDK.17

# 또는 수동 다운로드
# https://adoptium.net/temurin/releases/?version=17
```

## 프로젝트 구조

```
backend/
├── src/main/java/com/syncml/server/
│   ├── SyncmlServerApplication.java      # 메인 클래스 (@EnableScheduling)
│   ├── config/
│   │   └── DataLoader.java               # 초기 데이터 + 인증 정보 로드
│   ├── controller/
│   │   ├── DeviceController.java         # 단말 REST API
│   │   └── JobController.java            # 작업 REST API
│   ├── domain/
│   │   ├── Device.java                   # 단말 엔티티
│   │   ├── DeviceCredential.java         # 단말 인증 정보 ⭐
│   │   ├── DeviceStatus.java
│   │   ├── EventLog.java
│   │   ├── JobStatus.java
│   │   ├── SyncSession.java              # SyncML 세션 ⭐
│   │   ├── SyncSessionStatus.java        # 세션 상태 ⭐
│   │   └── UpdateJob.java
│   ├── repository/
│   │   ├── DeviceCredentialRepository.java ⭐
│   │   ├── DeviceRepository.java
│   │   ├── EventLogRepository.java
│   │   ├── SyncSessionRepository.java    ⭐
│   │   └── UpdateJobRepository.java
│   ├── service/
│   │   ├── DeviceService.java
│   │   ├── EventLogService.java
│   │   └── UpdateJobService.java
│   ├── simulator/                        # ⭐ 단말 시뮬레이터
│   │   ├── DeviceSimulator.java          # 개별 단말 시뮬레이션
│   │   └── SimulatorManager.java         # 시뮬레이터 관리자
│   └── syncml/                           # ⭐ SyncML 처리
│       ├── controller/
│       │   ├── SyncMLController.java     # SyncML 엔드포인트
│       │   └── SyncSessionController.java # 세션 조회 API
│       ├── dto/                          # SyncML 메시지 DTO
│       │   ├── Alert.java
│       │   ├── Command.java
│       │   ├── Cred.java
│       │   ├── Result.java
│       │   ├── Status.java
│       │   ├── SyncHdr.java
│       │   └── SyncMLMessage.java
│       ├── service/
│       │   ├── SyncMLAuthService.java    # 인증 처리
│       │   ├── SyncMLMessageService.java # 메시지 처리 핵심
│       │   └── SyncMLSessionService.java # 세션 관리
│       └── util/
│           └── SyncMLXmlUtil.java        # XML 파싱/생성
├── src/main/resources/
│   └── application.yml
├── build.gradle
└── settings.gradle
```

## 실행 방법

```powershell
# 빌드
.\gradlew.bat build

# 실행 (H2 인메모리 DB + 시뮬레이터 자동 시작)
.\gradlew.bat bootRun

# 또는 JAR 실행
java -jar build/libs/syncml-server-0.0.1-SNAPSHOT.jar
```

### 시뮬레이터 비활성화

```yaml
# application.yml
simulator:
  enabled: false  # 시뮬레이터 끄기
```

## API 엔드포인트

### SyncML 엔드포인트 (`/syncml`)

| Method | Endpoint | Content-Type | 설명 |
|--------|----------|--------------|------|
| POST | `/syncml` | `application/vnd.syncml.dm+xml` | SyncML 메시지 처리 |
| GET | `/syncml/health` | - | 서버 상태 확인 |

### 단말 API (`/api/devices`)

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/devices` | 모든 단말 조회 |
| GET | `/api/devices/{vin}` | 단말 상세 조회 |
| POST | `/api/devices` | 단말 등록 |
| POST | `/api/devices/{vin}/heartbeat` | Heartbeat 갱신 |
| GET | `/api/devices/{vin}/logs` | 단말 로그 조회 |
| GET | `/api/devices/offline` | 오프라인 단말 조회 |

### 작업 API (`/api/jobs`)

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/jobs` | 모든 작업 조회 |
| POST | `/api/jobs` | 작업 생성 |
| GET | `/api/jobs/next/{vin}` | 다음 작업 조회 (Polling) |
| PATCH | `/api/jobs/{jobId}/status` | 작업 상태 변경 |
| POST | `/api/jobs/{jobId}/fail` | 작업 실패 처리 |
| GET | `/api/jobs/vin/{vin}` | VIN별 작업 조회 |
| GET | `/api/jobs/{jobId}/logs` | 작업 로그 조회 |

### 세션 API (`/api/sessions`)

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/sessions/{sessionId}` | 세션 조회 |
| GET | `/api/sessions/active/{vin}` | VIN의 활성 세션 |
| GET | `/api/sessions/history/{vin}` | VIN의 세션 이력 |

## 개발 환경

- H2 Console: http://localhost:8080/h2-console
  - JDBC URL: `jdbc:h2:mem:syncml_ota`
  - Username: `sa`
  - Password: (빈값)

## 테스트 데이터

서버 시작 시 자동 생성 (dev 프로파일):

**단말 3대:**
| VIN | Model | Version | Username | Password |
|-----|-------|---------|----------|----------|
| VIN-0001 | TEST-MODEL-A | 1.0.0 | device0001 | password123 |
| VIN-0002 | TEST-MODEL-B | 1.0.0 | device0002 | password123 |
| VIN-0003 | TEST-MODEL-C | 1.1.0 | device0003 | password123 |

## 단말 시뮬레이터

### 이 프로젝트에서 "단말" = "시뮬레이터"

```
┌─────────────────────────────────────────────────────────────────────┐
│                                                                     │
│   실제 운영: 단말 = 실제 차량 (TCU/ECU)                              │
│   이 프로젝트: 단말 = 시뮬레이터 (가짜 차량 프로그램)                  │
│                                                                     │
│   → 실제 차량이 없으니 "차량인 척 하는 프로그램"을 만든 것            │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

| 환경 | "단말"의 정체 |
|------|--------------|
| **실제 운영** | 차량에 탑재된 TCU/ECU (실제 하드웨어) |
| **이 프로젝트** | DeviceSimulator.java (가짜 차량 프로그램) |

### 왜 시뮬레이터로 만들었나?

- 실제 차량 없이 전체 흐름 테스트 가능
- 다양한 시나리오 검증 (성공, 실패, 타임아웃)
- 포트폴리오 데모용

### 시뮬레이터 동작 방식

dev 프로파일에서 자동 실행 (서버 시작 5초 후):

- 3개 가상 단말 (VIN-0001 ~ VIN-0003)
- 5초마다 서버에 SyncML 세션 시도 (Polling)
- 10초마다 Heartbeat 전송
- 다운로드/설치 시간 시뮬레이션 (2~8초)
- 10% 확률로 설치 실패

### 시뮬레이터 설정

```yaml
simulator:
  enabled: true           # 활성화 여부
  start-delay-ms: 5000    # 서버 시작 후 대기 시간
```

## SyncML FUMO 흐름 (9단계)

```
Step 1: Client → Server   Alert 1201 + Cred (세션 시작)
Step 2: Server → Client   Status 212 + Get SwV (버전 요청)
Step 3: Client → Server   Results SwV (버전 응답)
Step 4: Server → Client   Replace PkgURL (다운로드 URL)
Step 5: Client → Server   Status 200 (다운로드 완료)
Step 6: Server → Client   Exec Install (설치 명령)
Step 7: Client → Server   Status 200 (설치 완료)
Step 8: Server → Client   Final (세션 종료)
Step 9: Client 대기       다음 Poll까지 휴식
```

## 관련 문서

- [ARCHITECTURE.md](../docs/ARCHITECTURE.md) - 전체 아키텍처
- [MESSAGING_COMPARISON.md](../docs/MESSAGING_COMPARISON.md) - MQTT/RabbitMQ/Kafka 비교

## 로그 구조

로그는 역할별로 분리되어 `logs/` 폴더에 저장됩니다.

```
logs/
├── server.log      # 서버 로직 (API, 서비스, DB)
├── simulator.log   # 시뮬레이터 동작 (단말 행동)
└── syncml.log      # SyncML 메시지 처리 (XML 상세)
```

| 로그 파일 | 내용 | 용도 |
|-----------|------|------|
| `server.log` | API 호출, DB 작업, 작업 상태 변경 | 서버 디버깅 |
| `simulator.log` | 단말 접속, 다운로드/설치 진행, 성공/실패 | 단말 동작 확인 |
| `syncml.log` | SyncML XML 파싱, 세션 관리, 인증 | 프로토콜 디버깅 |

### 로그 예시

**simulator.log** (단말 동작)
```
14:30:05.123 [VIN-0001] INFO  - 시뮬레이터 시작 (버전: 1.0.0)
14:30:10.456 [VIN-0001] INFO  - SyncML 세션 시작: VIN-0001-1234567890
14:30:11.789 [VIN-0001] INFO  - 다운로드 명령 받음: http://server/pkg/2.0.0.zip
14:30:14.012 [VIN-0001] INFO  - 다운로드 완료
14:30:15.345 [VIN-0001] INFO  - 설치 명령 받음
14:30:20.678 [VIN-0001] INFO  - 설치 완료
14:30:20.890 [VIN-0001] INFO  - 업데이트 완료, 새 버전: 2.0.0
```

**server.log** (서버 로직)
```
14:30:00.100 [main] INFO  - 단말 생성: VIN-0001 (TEST-MODEL-A)
14:30:00.200 [main] INFO  - 인증 정보 생성: VIN-0001 (device0001)
14:30:10.500 [http-1] INFO  - Client initiated session for VIN: VIN-0001
14:30:10.600 [http-1] INFO  - Job 1 assigned to session
```

### 로그 레벨 조정

```yaml
# application.yml
logging:
  level:
    com.syncml.server.simulator: DEBUG  # 시뮬레이터 상세
    com.syncml.server.syncml: DEBUG     # SyncML 상세
    com.syncml.server: INFO             # 서버 기본
```

