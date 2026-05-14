# SyncML OTA 시스템 아키텍처 문서

> **핵심 한 줄**: SyncML은 "외부 단말 프로토콜", RabbitMQ는 "내부 작업 분배 계층"으로 두고 하나의 프로젝트에 녹인다.

---

## 1. 프로젝트 개요

**목표**: 단말 시뮬레이터 + SyncML OTA 서버 + 운영자 대시보드

| 구분 | 역할 |
|------|------|
| 외부 프로토콜 | SyncML (Alert/Status/Exec) |
| 내부 작업 분배 | DB 큐 → RabbitMQ 확장 |
| 프론트엔드 | Vue 3 + TypeScript |
| 운영 조회 | Django Admin/Internal API |
| 컨테이너화 | Docker + Docker Compose |

---

## 2. 시스템 아키텍처

```
[사용자]
    │
    ▼
[Vue 3 대시보드] ─────────────────────────────────┐
    │                                             │
    ▼                                             │
[Java SyncML Backend]                             │
    ├─ 단말 세션 관리                              │
    ├─ Alert/Status/Exec 처리                     │
    ├─ 작업 큐 관리                                │
    ├─ 로그 기록                                   │
    │                                             │
    ├───► [DB] ◄──────────────────────────────────┤
    │                                             │
    └───► [RabbitMQ] ──► [Device Simulator]       │
                              │                   │
                              ▼                   │
                         상태 업데이트 ────────────┘

[내부 운영자]
    │
    ▼
[Django Admin/Internal API]
    ├─ VIN 검색
    ├─ 세션 상태 조회
    ├─ 작업 이력 조회
    ├─ 실패건 필터링
    └─ 운영자 메모
```

---

## 3. 기술 스택

### 3.1 프론트엔드 (Vue 3 + TypeScript)

| 항목 | 선택 |
|------|------|
| 프레임워크 | Vue 3.x (Composition API) |
| 언어 | TypeScript |
| 빌드 도구 | Vite |
| 상태 관리 | Pinia |
| HTTP 클라이언트 | Axios |
| UI 컴포넌트 | PrimeVue 또는 Vuetify 3 (오픈소스) |
| 실시간 갱신 | WebSocket 또는 Polling |

### 3.2 백엔드

| 서비스 | 기술 |
|--------|------|
| SyncML 서버 | Java (Spring Boot) |
| 운영 API | Django + Django REST Framework |
| 메시지 브로커 | RabbitMQ |
| DB | PostgreSQL |

### 3.3 인프라

| 항목 | 선택 |
|------|------|
| 컨테이너 | Docker |
| 오케스트레이션 | Docker Compose |
| 네트워크 분리 | public-net / private-net |

---

## 4. 핵심 데이터 모델

### 4.1 device (단말)

```sql
CREATE TABLE device (
    vin VARCHAR(50) PRIMARY KEY,
    model VARCHAR(100),
    current_version VARCHAR(50),
    target_version VARCHAR(50),
    last_seen_at TIMESTAMP,
    device_status VARCHAR(20),  -- IDLE, UPDATING, FAILED
    -- ✅ 2026-05-13 구현: DevInfo 사전 교환으로 채워지는 컬럼
    max_msg_size BIGINT,            -- ./DevInfo/Ext/MaxMsgSize
    max_obj_size BIGINT,            -- ./DevInfo/Ext/MaxObjSize
    support_large_obj BOOLEAN,      -- MoreData 지원 여부 (MaxObjSize>0 으로 추정)
    manufacturer VARCHAR(100),      -- ./DevInfo/Man
    dm_client_version VARCHAR(50),  -- ./DevInfo/DmV
    lang VARCHAR(20),               -- ./DevInfo/Lang
    dev_id VARCHAR(100)             -- ./DevInfo/DevId
);
```

> 위 컬럼은 [5.0.3 DevInfo 사전 교환](#503-devinfo-사전-교환-표준-정합성) 과 [5.7 MoreData](#57-메시지-사이징--moredata-청킹) 의 결과로 채워진다.

### 4.2 update_job (작업)

```sql
CREATE TABLE update_job (
    job_id SERIAL PRIMARY KEY,
    target_vin VARCHAR(50) REFERENCES device(vin),
    command_type VARCHAR(50),  -- FUMO_UPDATE, SCOMO_INSTALL 등
    payload_version VARCHAR(50),
    pkg_url VARCHAR(500),
    status VARCHAR(20),  -- QUEUED, ASSIGNED, DOWNLOADING, INSTALLING, SUCCESS, FAIL
    retry_count INT DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    -- ✅ 2026-05-13 컬럼만 구현 (PKI 검증 로직은 #4 작업 예정 - WBXML 뒤)
    pkg_size BIGINT,                       -- 패키지 byte 크기
    pkg_sha256 VARCHAR(128),               -- SHA-256 hex
    pkg_signature TEXT,                    -- Base64 코드사이닝 서명
    signature_algorithm VARCHAR(50),       -- RSA-PSS-SHA256, ECDSA-P256-SHA256
    signing_cert_chain TEXT                -- PEM 체인
);
```

> 컬럼은 ✅ 추가됨. 실제 검증 흐름(업로드 시 sha256 계산, Replace `./FUMO/PackageHash` 동봉, Alert 1226 PKI 실패 핸들러)은 [5.9 PKI 검증 흐름](#59-패키지-무결성--pki-검증-흐름) 의 **WBXML 인코딩(#7) 완료 이후** 구현 예정.

### 4.3 sync_session (세션)

```sql
CREATE TABLE sync_session (
    session_id VARCHAR(100) PRIMARY KEY,
    vin VARCHAR(50) REFERENCES device(vin),
    current_step INT DEFAULT 0,
    last_msg_id INT DEFAULT 0,
    last_cmd_id INT DEFAULT 0,
    session_status VARCHAR(30),  -- INITIALIZED, AUTHENTICATED, IN_PROGRESS, COMPLETED, FAILED, EXPIRED
    authenticated BOOLEAN DEFAULT FALSE,
    current_job_id BIGINT,
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    last_activity_at TIMESTAMP
);
```

### 4.4 device_credential (인증 정보)

```sql
CREATE TABLE device_credential (
    vin VARCHAR(50) PRIMARY KEY,
    auth_type VARCHAR(50) DEFAULT 'syncml:auth-basic',  -- syncml:auth-basic, syncml:auth-md5, syncml:auth-hmac
    username VARCHAR(100),
    password_hash VARCHAR(256),
    server_nonce VARCHAR(256),  -- MD5/HMAC 인증용
    last_auth_at TIMESTAMP,
    fail_count INT DEFAULT 0,
    locked BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

### 4.5 event_log (로그)

```sql
CREATE TABLE event_log (
    id SERIAL PRIMARY KEY,
    vin VARCHAR(50),
    job_id INT,
    event_type VARCHAR(50),
    message TEXT,
    created_at TIMESTAMP
);
```

---

## 5. SyncML FUMO 흐름 (최소 버전)

### 5.0 핵심 개념: 클라이언트가 먼저 접속한다

> **중요**: 서버가 클라이언트를 찾아가는 게 아니다. **클라이언트(단말)가 먼저 서버에 접속**한다.

```
[단말/Client] ─────────────────────► [서버]
              "나 VIN-0002야, 할 일 있어?"
              
[단말/Client] ◄───────────────────── [서버]
              "응, 이 패키지 다운받아 (PkgURL)"
              "그리고 설치해 (Exec)"
```

**왜 이렇게 하냐?**
- 단말이 방화벽 뒤에 있을 수 있음
- 서버가 단말 IP를 모를 수 있음  
- 단말이 언제 켜질지 모름

그래서 **단말이 주기적으로 서버에 물어보는 구조 (Polling)**가 IoT/OTA 환경의 표준이다.

### 5.0.1 SyncML 인증 (Cred) 흐름

SyncML에서 인증은 `<Cred>` 요소를 통해 수행된다. 세션 시작 시 인증이 필수.

```
┌──────────────────────────────────────────────────────────────┐
│                    SyncML 인증 흐름                           │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  [Client]                              [Server]              │
│     │                                      │                 │
│     │  1. SyncML 요청 (Cred 없음)           │                 │
│     │ ────────────────────────────────────►│                 │
│     │                                      │                 │
│     │  2. Status 401 + Chal (인증 요구)     │                 │
│     │ ◄────────────────────────────────────│                 │
│     │     └─ NextNonce (MD5용)            │                 │
│     │                                      │                 │
│     │  3. SyncML 요청 + Cred               │                 │
│     │ ────────────────────────────────────►│                 │
│     │     └─ Type: syncml:auth-basic       │                 │
│     │     └─ Data: Base64(user:pass)       │                 │
│     │                                      │                 │
│     │  4. Status 212 (인증 성공)            │                 │
│     │ ◄────────────────────────────────────│                 │
│     │                                      │                 │
└──────────────────────────────────────────────────────────────┘
```

**지원 인증 타입:**

| 타입 | 설명 | Data 형식 |
|------|------|-----------|
| `syncml:auth-basic` | Basic 인증 | `Base64(username:password)` |
| `syncml:auth-md5` | MD5 인증 | `Base64(MD5(username:password:nonce))` |
| `syncml:auth-hmac` | HMAC 인증 | HMAC 기반 (미구현) |

**Cred XML 예시:**

```xml
<Cred>
  <Meta>
    <Format>b64</Format>
    <Type xmlns="syncml:metinf">syncml:auth-basic</Type>
  </Meta>
  <Data>ZGV2aWNlMDAwMTpwYXNzd29yZDEyMw==</Data>  <!-- Base64(device0001:password123) -->
</Cred>
```

### 5.0.1.1 Nonce 기반 MD5 인증 상세

MD5 인증은 Replay Attack 방지를 위해 **Nonce(일회용 토큰)**를 사용한다.

**MD5 해시 계산 공식:**
```
Cred.Data = Base64(MD5(username:password:nonce))
```

**Nonce 인증 전체 흐름:**

```
┌─────────────────────────────────────────────────────────────────┐
│                    Nonce 기반 MD5 인증 흐름                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  [최초 접속 - Nonce 없음]                                        │
│  ────────────────────────                                       │
│  Client → Server: SyncML 요청 (Cred 없음)                        │
│  Server → Client: Status 401 + Chal(Nonce₁ 발급)                │
│  Server: Nonce₁을 DB에 저장 (device_credential.server_nonce)    │
│                                                                 │
│  [이후 접속 - Nonce 사용]                                        │
│  ────────────────────────                                       │
│  Client: 저장된 Nonce₁으로 해시 계산                             │
│          → MD5(username:password:Nonce₁)                        │
│  Client → Server: Cred에 Base64(해시값) 담아서 전송              │
│                                                                 │
│  [인증 성공 시]                                                  │
│  ─────────────                                                  │
│  Server: DB의 Nonce₁으로 동일하게 해시 계산 → 비교 OK            │
│  Server → Client: Status 212 (인증 성공) + NextNonce(Nonce₂)    │
│  Server: Nonce₂를 DB에 저장 (기존 Nonce₁ 폐기)                  │
│  Client: Nonce₂ 저장 (다음 세션용)                               │
│                                                                 │
│  [인증 실패 시]                                                  │
│  ─────────────                                                  │
│  Server: 해시 불일치                                            │
│  Server → Client: Status 401 + 동일 Nonce₁ 유지                 │
│  Server: fail_count 증가 (5회 초과 시 locked = true)            │
│  Client: 동일 Nonce₁으로 재시도 가능                             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**비밀번호 저장 구조:**

```
┌─────────────────┐                    ┌─────────────────┐
│     Client      │                    │     Server      │
│   (단말/차량)    │                    │   (DB 저장)     │
├─────────────────┤                    ├─────────────────┤
│ username        │  ◄── 동일 ──►      │ username        │
│ password (평문) │                    │ password_hash   │
│ nonce (서버발급)│  ◄── 동일 ──►      │ server_nonce    │
└─────────────────┘                    └─────────────────┘

※ 클라이언트는 평문 비밀번호 보유
※ 서버는 해시된 비밀번호만 저장 (Basic 인증용)
※ MD5 인증 시에는 서버도 해시 계산을 위해 평문 또는 별도 저장 필요
```

**인증 실패 보호 메커니즘:**

| 항목 | 값 | 설명 |
|------|-----|------|
| 최대 실패 횟수 | 5회 | `fail_count >= 5` 시 잠금 |
| 잠금 상태 | `locked = true` | 이후 인증 시도 차단 |
| 잠금 해제 | 관리자 수동 | `unlock()` 메서드 호출 |
| Nonce 유효기간 | 세션 단위 | 성공 시 NextNonce로 교체 |

**MD5 인증 Cred XML 예시:**

```xml
<Cred>
  <Meta>
    <Format>b64</Format>
    <Type xmlns="syncml:metinf">syncml:auth-md5</Type>
  </Meta>
  <!-- Base64(MD5("device0001:password123:abc123nonce")) -->
  <Data>dGhpc2lzYW1kNWhhc2g=</Data>
</Cred>
```

**Challenge(Chal) 응답 XML 예시:**

```xml
<Status>
  <CmdID>1</CmdID>
  <Cmd>SyncHdr</Cmd>
  <Data>401</Data>  <!-- 인증 필요 -->
  <Chal>
    <Meta>
      <Type xmlns="syncml:metinf">syncml:auth-md5</Type>
      <Format>b64</Format>
      <NextNonce>YWJjMTIzbm9uY2U=</NextNonce>  <!-- Base64 인코딩된 Nonce -->
    </Meta>
  </Chal>
</Status>
```

### 5.0.2 SessionID를 통한 세션 연속성

SyncML 세션은 여러 HTTP 요청/응답으로 이루어진다. `SessionID`로 연속성 보장.

```
┌──────────────────────────────────────────────────────────────┐
│                    SessionID 흐름                            │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  Session: abc123 (새로 생성)                                  │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ Msg 1: Client → Server (Alert 1201)                  │   │
│  │        SessionID: abc123, MsgID: 1                   │   │
│  │ Msg 1: Server → Client (Status + Get)                │   │
│  │        SessionID: abc123, MsgID: 1                   │   │
│  ├──────────────────────────────────────────────────────┤   │
│  │ Msg 2: Client → Server (Results)                     │   │
│  │        SessionID: abc123, MsgID: 2                   │   │
│  │ Msg 2: Server → Client (Replace + Exec)              │   │
│  │        SessionID: abc123, MsgID: 2                   │   │
│  ├──────────────────────────────────────────────────────┤   │
│  │ Msg 3: Client → Server (Status)                      │   │
│  │        SessionID: abc123, MsgID: 3                   │   │
│  │ Msg 3: Server → Client (Final)                       │   │
│  │        SessionID: abc123, MsgID: 3                   │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

**세션 상태 전이:**

```
INITIALIZED ──(인증성공)──► AUTHENTICATED ──(작업시작)──► IN_PROGRESS
                                                              │
                                    ┌────────────────────────┘
                                    │
                              ┌─────▼─────┐
                              │ COMPLETED │ (정상 종료)
                              └───────────┘
                              
                              ┌─────▼─────┐
                              │  FAILED   │ (실패)
                              └───────────┘
                              
                              ┌─────▼─────┐
                              │  EXPIRED  │ (5분 타임아웃)
                              └───────────┘
```

### 5.0.3 DevInfo 사전 교환 (표준 정합성)

> ✅ **구현 완료 (2026-05-13)** — Pattern A(Client-Push) 와 Pattern B(Server-Pull) 모두 처리.
> - Pattern A: 단말이 첫 Pkg 에 `<Replace>` 로 `./DevInfo/*` 동봉 시 `SyncMLMessageService.handleDevInfoReplace` 가 파싱·저장 후 Status 200 응답
> - Pattern B: `device.maxMsgSize == null` 이면 서버가 step 1 응답에 `Get ./DevInfo/Ext/MaxMsgSize|MaxObjSize|Man|DmV` 발행, 단말 `<Results>` 를 `handleResults` 가 수신·저장
> - 매핑: `./DevInfo/Man → manufacturer`, `Mod → model`, `DmV → dmClientVersion`, `Lang → lang`, `DevId → devId`, `SwV → currentVersion`, `Ext/MaxMsgSize → maxMsgSize`, `Ext/MaxObjSize → maxObjSize (+ supportLargeObj=true)`
> - 상수 관리: 표준 `./DevInfo/*` 노드는 `DevInfoNode` enum, `./FUMO/*` 경로는 `SyncMLLocUri` constants 에서 중앙 관리한다.

#### DevInfo/FUMO 상수 관리 원칙

| 대상 | 관리 방식 | 이유 |
|------|-----------|------|
| 표준 DevInfo 노드 | `DevInfoNode` enum | 서버가 의미를 알고 `Device` 컬럼에 매핑해야 하므로 오타/분기 누락 방지 |
| FUMO LocURI | `SyncMLLocUri.Fumo` constants | `PkgURL`, `Download`, `Install`, `PackageHash` 등 명령 경로 재사용 |
| 알 수 없는 `./DevInfo/*` 확장 | 저장하지 않고 debug 로그 | 벤더 확장은 나중에 allow-list 로 확장 가능 |
| Status/Alert 코드 | 현재 서비스 내부 상수, 추후 `SyncMLStatusCode`/`SyncMLAlertCode` 분리 가능 | 코드 수가 늘면 중앙화 |

정책: **프로토콜 문자열은 서비스 로직에 직접 쓰지 않는다.** 새 DevInfo/FUMO 노드가 필요하면 먼저 enum/constants 에 추가하고, 그다음 서비스 매핑을 작성한다.

SyncML 1.2 표준은 첫 세션에서 단말이 자기 정보(`./DevInfo` 트리)를 서버에 전달해야 한다. 이게 있어야 서버가 메시지 사이즈 분할(MoreData), 언어, 모델별 분기를 할 수 있다.

#### Cred(인증) vs DevInfo(사전 교환) — 헷갈리지 말 것

둘은 **첫 메시지(Pkg #1)에 같이 실려서 오지만 목적이 완전히 다르다**. Cred 인증 로직은 이미 구현되어 있고(`SyncMLAuthService`, `DeviceCredential`), DevInfo는 인증 성공 후 단말 스펙을 저장하는 영역이다.

| 구분 | Cred (인증) | DevInfo (사전 교환) |
|------|-------------|---------------------|
| 질문 | "너 등록된 차 맞아?" | "너 어떤 스펙이야?" |
| 메시지 위치 | `<SyncHdr>` 안의 `<Cred>` | `<SyncBody>` 안의 `<Replace>`/`<Put>` 또는 서버 `<Get>` 응답의 `<Results>` |
| 데이터 형식 | `Base64(MD5(user:pass:nonce))` 또는 Basic | `./DevInfo/*` 트리 노드 값 |
| 검증/처리 결과 | Status 212 / 401 | `device` 테이블에 model/maxMsgSize/currentVersion 등 저장 |
| 빈도 | 매 세션 또는 인증 필요 시 | 첫 세션 또는 단말 정보 변경 시 |
| 코드 위치 | `SyncMLAuthService.authenticate()` | `SyncMLMessageService.handleDevInfoReplace()`, `handleResults()` |

**표준 DevInfo 트리:**

| 노드 | 의미 | 예시 |
|------|------|------|
| `./DevInfo/DevId` | 단말 고유 ID | `IMEI:VIN-0001` |
| `./DevInfo/Man` | 제조사 | `Hyundai` |
| `./DevInfo/Mod` | 모델명 | `TEST-MODEL-A` |
| `./DevInfo/DmV` | DM 클라이언트 버전 | `1.2` |
| `./DevInfo/Lang` | 언어 | `ko-KR` |
| `./DevInfo/SwV` | 현재 소프트웨어 버전 | `1.0.0` |
| `./DevInfo/Ext/MaxMsgSize` | 한 번에 받을 수 있는 SyncML 메시지 max byte | `16384` |
| `./DevInfo/Ext/MaxObjSize` | 한 Item의 max byte | `8192` |
| `./DevInfo/Ext/SupportLargeObj` | LargeObject(MoreData) 지원 여부 | `true` |

**교환 패턴:**

```text
패턴 A: Client-Push
Client → Server: Alert 1201 + Cred + Replace ./DevInfo/*
Server → Client: Status 212/200 + 다음 명령

패턴 B: Server-Pull
Client → Server: Alert 1201 + Cred
Server → Client: Status 212 + Get ./DevInfo/*
Client → Server: Results ./DevInfo/*
Server → Client: 다음 명령
```

---

### 5.1 FUMO 9단계 상세 흐름

> **핵심**: "Server → Client"는 서버가 클라이언트를 찾아가는 게 아니라,
> **클라이언트 요청에 대한 HTTP 응답으로 포함**되는 것이다.

```text
Step 1: 차량 → 서버   Alert 1201 + Cred + DevInfo 로 세션 시작
Step 2: 서버 → 차량   Status 212 + Get ./DevInfo/SwV 또는 필요한 DevInfo 요청
Step 3: 차량 → 서버   Results ./DevInfo/SwV 로 현재 버전 보고
Step 4: 서버 → 차량   Replace ./FUMO/PkgURL 로 다운로드 대상 전달
Step 5: 차량 → 서버   Status 200/500 으로 다운로드 결과 보고
Step 6: 서버 → 차량   Exec ./FUMO/Install 로 설치 시작 명령
Step 7: 차량 → 서버   Status 200/500 으로 설치 결과 보고
Step 8: 서버 → 차량   Status 200 + Final 로 세션 종료
Step 9: 차량 대기     다음 Poll 까지 휴식
```

### 5.2 시퀀스 다이어그램

```text
[차량/Client]                              [서버/Server]
     │ ① Alert 1201 + DevInfo + Cred           │
     │────────────────────────────────────────►│ 인증 확인 / 작업 할당
     │ ② Status 212 + Get SwV                  │
     │◄────────────────────────────────────────│
     │ ③ Results SwV="1.0.0"                   │
     │────────────────────────────────────────►│ 버전 비교 → 작업 있음
     │ ④ Replace PkgURL                        │
     │◄────────────────────────────────────────│
     │     [다운로드 중]                        │
     │ ⑤ Status 200                            │
     │────────────────────────────────────────►│
     │ ⑥ Exec Install                          │
     │◄────────────────────────────────────────│
     │     [설치 중]                            │
     │ ⑦ Status 200                            │
     │────────────────────────────────────────►│ 결과 저장
     │ ⑧ Status 200 + Final                    │
     │◄────────────────────────────────────────│
```

### 5.3 작업 없음 케이스 (Short Path)

```text
Client → Server: Alert 1201 + DevInfo + Cred
Server → Client: Status 212 + Get SwV
Client → Server: Results SwV="2.0.0" 또는 대기 작업 없음
Server → Client: Status 200 + Final
```

### 5.4 왜 Step 4와 Step 6을 분리하는가?

```text
[나쁜 설계]
서버: "이 URL에서 다운받고(Replace) + 설치해(Exec)"  ← 동시
단말: 아직 다운로드도 안 했는데 설치를? 실패 가능

[좋은 설계]
Step 4: "이 URL에서 다운받아" (Replace PkgURL)
Step 5: "다운 완료함" (Status 200)
Step 6: "이제 설치해" (Exec Install)
```

분리 이유:
- 다운로드 실패면 설치 명령을 내리지 않는다.
- 다운로드/설치 실패 지점을 로그로 구분할 수 있다.
- 재시도 범위를 다운로드 또는 설치 단계로 좁힐 수 있다.

### 5.5 상태 전이

```text
IDLE ──(작업생성)──► QUEUED ──(세션시작)──► ASSIGNED
                                              │
                                              ▼
                    DOWNLOADING ──(성공)──► INSTALLING ──(성공)──► SUCCESS
                         │                       │
                         └──(실패)──► FAIL ◄─────┘
```

### 5.6 Poll 주기 조정 전략

| 상황 | Poll 주기 | 이유 |
|------|-----------|------|
| 작업 완료 직후 | 60초 | 바로 새 작업이 올 확률 낮음 |
| 작업 실패 후 | 30초 | 재시도 대기 |
| 평상시 (Idle) | 5초 | 새 작업 빠르게 감지 |
| 업데이트 중 | 즉시 | 다음 명령 받기 위해 |

---

### 5.7 메시지 사이징 & MoreData 청킹

> ✅ **구현 완료 (2026-05-13)** —
> - `Command.Item.moreData`, `Result.Item.moreData` 필드 추가
> - `SyncMLXmlUtil` 에 `<MoreData/>` 파싱·생성 추가
> - 송신 측: `MessageChunker` 가 응답 직렬화 직전 `device.maxMsgSize` 기준으로 Item 분할 (안전 마진 512 byte)
> - 수신 측: `ChunkReassemblyService` 가 sessionId+CmdID 단위 In-Memory 버퍼로 누적, 마지막 청크에서 합쳐 처리
> - 세션 완료/실패 시 `clearSession` 으로 버퍼 정리
> - 한계 (포트폴리오 단순화): 한 응답 메시지 안에서의 Item 분할만 시연. 진짜 표준은 메시지 사이를 가로지르는 분할(여러 HTTP 요청에 걸친) 까지 요구하며, 그 흐름은 추후 확장.

**왜 필요한가:**
- 단말 메모리 버퍼는 보통 8~32KB. DevInfo Result, 다중 ECU 진단, 대용량 Replace 는 한 메시지에 못 담음.
- 표준 단말은 `MaxMsgSize` 초과 메시지를 받으면 Status 413/500 반환 → 작업 진행 불가.

**MoreData 동작 방식:**

```
[큰 데이터를 여러 메시지로 분할 전송]
─────────────────────────────────────
Msg N:
  <Replace>
    <CmdID>5</CmdID>
    <Item>
      <Target><LocURI>./FUMO/Description</LocURI></Target>
      <Data>(첫 8KB)</Data>
      <MoreData/>          ← "이 Item 아직 더 있음"
    </Item>
  </Replace>

Msg N+1:
  <Replace>
    <CmdID>5</CmdID>      ← 같은 CmdID로 이어붙임
    <Item>
      <Target><LocURI>./FUMO/Description</LocURI></Target>
      <Data>(나머지 4KB)</Data>
                           ← MoreData 없음 = 끝
    </Item>
  </Replace>
```

**구현 시 필요한 것:**

| 항목 | 위치 |
|------|------|
| `Command.Item`, `Result.Item` 에 `boolean moreData` 필드 | [DTO](../backend/src/main/java/com/syncml/server/syncml/dto/) |
| `<MoreData/>` 파싱/생성 | [SyncMLXmlUtil](../backend/src/main/java/com/syncml/server/syncml/util/SyncMLXmlUtil.java) |
| 세션 단위 reassembly buffer | [SyncSession](../backend/src/main/java/com/syncml/server/domain/SyncSession.java) |
| 응답 직렬화 직전 byte 측정 → 분할 | [SyncMLMessageService](../backend/src/main/java/com/syncml/server/syncml/service/SyncMLMessageService.java) |

---

### 5.7.1 업데이트 패키지 파일 정책

현재 구현은 `update_job.pkg_url` 기준의 **작업 1개 : 다운로드 대상 1개** 모델이다. 따라서 MVP 정책은 아래처럼 둔다.

#### MVP 정책: 단일 패키지 아티팩트

| 항목 | 정책 |
|------|------|
| 서버가 내려주는 값 | `./FUMO/PkgURL` 하나 |
| 파일 형태 | 단일 ZIP/TAR/패키지 파일 또는 단일 manifest 파일 |
| 서버 책임 | URL, 크기, 해시, 서명 같은 패키지 단위 메타 발행 |
| 단말/시뮬레이터 책임 | 다운로드, 압축 해제, 내부 파일 적용 순서, 설치 결과 보고 |
| 결과 보고 | 패키지 단위 `DOWNLOAD_COMPLETE`, `INSTALL_COMPLETE`, 실패 시 패키지 단위 FAIL |

즉, `PkgURL` 이 ZIP 같은 압축 파일을 가리킨다면 **압축을 푸는 행위와 내부 파일 적용 순서는 단말 쪽 책임**으로 본다. 서버는 압축 내부 구조를 직접 알지 않아도 된다. 이게 현재 코드와 가장 잘 맞는다.

#### 여러 파일이 필요한 경우의 확장 정책

여러 ECU/모듈 파일을 개별로 관리해야 한다면 `pkg_url` 하나에 여러 바이너리를 직접 나열하지 말고, 다음 둘 중 하나로 확장한다.

1. **Manifest 방식 권장**
   - 서버는 `PkgURL` 로 manifest URL 하나를 내려준다.
   - manifest 안에 파일 목록, URL, size, sha256, 적용 대상 ECU, 설치 순서를 적는다.
   - 단말은 manifest 를 읽고 여러 파일을 순서대로 다운로드/검증/설치한다.

2. **DB 정규화 방식**
   - `update_package` / `update_package_artifact` 테이블을 추가한다.
   - `UpdateJob` 은 `package_id` 만 참조한다.
   - 파일별 상태까지 서버에서 추적할 수 있지만 구현량이 커진다.

#### 결과 알림 정책

| 패키지 모델 | 결과 알림 단위 | 설명 |
|-------------|----------------|------|
| 단일 ZIP/패키지 | 패키지 단위 | 현재 MVP. 내부 파일 하나가 실패해도 최종 Status 는 패키지 실패로 보고 |
| Manifest 다중 파일 | 파일별 + 최종 요약 | 파일별 progress/fail 을 Generic Alert 1226 또는 확장 Results 로 보고, 마지막에 Job 최종 Status 저장 |
| DB artifact 모델 | artifact 단위 DB 상태 | 운영툴에서 파일별 성공/실패를 볼 수 있음 |

현재 단계에서는 **단일 패키지 아티팩트 정책을 명시하고**, 다중 파일은 manifest 방식으로 확장하는 것이 가장 자연스럽다.

---

### 5.8 WBXML 인코딩 (운영 환경)

> ❌ **미구현** — 현재 XML 텍스트만 지원. Content-Type `application/vnd.syncml.dm+xml` 만 처리.

**왜 필요한가:**
- 차량 OTA는 셀룰러 망. WBXML은 같은 SyncML 메시지를 **4~10배 압축** (태그를 1바이트 토큰으로 치환).
- 양산 차량 ECU의 표준은 WBXML. XML은 개발/디버깅용. Content-Type `application/vnd.syncml+wbxml`.

**XML vs WBXML 크기 비교:**

```
XML (현재):
<SyncML xmlns="SYNCML:SYNCML1.2">
  <SyncHdr>
    <VerDTD>1.2</VerDTD>
    <SessionID>abc</SessionID>
    <MsgID>1</MsgID>
  </SyncHdr>
  ...
</SyncML>
→ 약 500 bytes

WBXML (목표):
0x02 0x9F 0x53 0x00 0x4D 0x4C 0x01 ...
→ 약 80~120 bytes (5배 압축)
```

**구현 옵션:**

| 방법 | 장단점 |
|------|--------|
| `libwbxml` Java 바인딩 | 검증됨, 의존성 추가 |
| `kxml2-wbxml` | 안드로이드 계열, 가벼움 |
| 직접 구현 (SyncML 1.2 코드페이지 0~6) | 학습 가치 큼, 시간 소요 |

**구현 시 필요한 것:**

| 항목 | 위치 |
|------|------|
| WBXML 인코더/디코더 | 신규 `WbxmlCodec.java` |
| Content-Type 협상 | [SyncMLController](../backend/src/main/java/com/syncml/server/syncml/controller/SyncMLController.java) |
| 토큰 테이블 (DM 1.2 codepage) | 신규 리소스 |
| Pluggable serializer 인터페이스 | 기존 XmlUtil 추상화 |

---

### 5.9 패키지 무결성 & PKI 검증 흐름

> 🟡 **부분 구현** — `UpdateJob` 에 `pkgSize`, `pkgSha256`, `pkgSignature`, `signatureAlgorithm`, `signingCertChain` 컬럼은 추가됨. 단, 업로드 시 해시/서명 계산, Replace 응답에 `PackageHash/PackageSig` 동봉, 단말 측 PKI 검증은 WBXML 이후 구현 예정.

**역할 분리 (중요):**

```
[빌드/릴리스 파이프라인]              [백엔드 OTA 서버]                [차량 단말]
   ├─ 펌웨어 빌드                       │                              │
   ├─ HSM/서명서버에서                   │                              │
   │  코드사이닝 (빌드키)                │                              │
   ├─ 패키지+서명+메타 업로드 ────────► │                              │
   │                                  ├─ 업로드 시 1회 검증            │
   │                                  │  (서명/체인/OCSP)              │
   │                                  ├─ DB에 sha256+서명 보관          │
   │                                  ├─ SyncML 응답에 metadata 동봉 ─►│
   │                                  │                              ├─ 다운로드
   │                                  │                              ├─ sha256 검증
   │                                  │                              ├─ 서명 검증 (TA 공개키)
   │                                  │                              └─ 검증 OK 후 설치
```

**왜 백엔드가 직접 서명하면 안 되나:**
- 서버 침해 시 서명키 유출 = 모든 차량에 악성 펌웨어 푸시 (공급망 공격).
- 서명키는 빌드 환경의 HSM 안에서만 사용. 백엔드는 공개키만 보유.
- 단말이 신뢰하는 건 백엔드가 아니라 펌웨어에 박힌 **Trust Anchor 공개키**.

**SyncML 표준 트리 (FUMO):**

| 노드 | 의미 | 미구현 여부 |
|------|------|-------------|
| `./FUMO/PkgURL` | 패키지 URL | ✅ 구현됨 |
| `./FUMO/PackageSize` | 패키지 byte 크기 | ❌ |
| `./FUMO/PackageHash` | SHA-256 (Base64) | ❌ |
| `./FUMO/PackageSig` | 코드사이닝 서명 (Base64) | ❌ |
| `./FUMO/SigAlg` | 서명 알고리즘 (`RSA-PSS-SHA256`, `ECDSA-P256`) | ❌ |

**필요한 것:**

| 항목 | 위치 |
|------|------|
| `pkgSize`, `pkgSha256`, `pkgSignature`, `signatureAlgorithm` 컬럼 | [UpdateJob](../backend/src/main/java/com/syncml/server/domain/UpdateJob.java) |
| 패키지 업로드 API + 서명 검증 | 신규 `PackageController` |
| Replace 명령에 메타 동봉 | [SyncMLMessageService](../backend/src/main/java/com/syncml/server/syncml/service/SyncMLMessageService.java) `determineNextCommands case 3` |
| 인증서 체인 / OCSP 클라이언트 | 신규 `PkiService` |

> mTLS + 디바이스 인증서 패턴은 [OPERATIONAL_PATTERNS.md](./OPERATIONAL_PATTERNS.md) 참조.

---

### 5.10 Status 코드 의미 분리 (200 / 202 / 212)

> 🟡 **부분 구현** — 200, 212, 401, 500 사용 중. **202 미사용** ([SyncMLMessageService.java](../backend/src/main/java/com/syncml/server/syncml/service/SyncMLMessageService.java)).

**현재 코드의 문제:**
- 다운로드/설치 진행률 보고도 200, 최종 완료도 200 → 의미가 깨짐.
- 단말이 "받았고 처리 시작했어" 와 "받았고 끝났어" 를 같은 코드로 보고할 수밖에 없음.

**올바른 의미 분리:**

| 코드 | 의미 | 사용 시점 |
|------|------|-----------|
| `200 OK` | 처리 완료 | 최종 완료 (다운로드 끝, 설치 끝) |
| `202 Accepted` | 받았고 처리 중 | 진행률 보고, 비동기 처리 시작 |
| `212 Authentication accepted` | 인증 성공 | 세션 인증 성공 시 |
| `213 Chunked item accepted` | MoreData 청크 받음 | MoreData 분할 수신 시 |
| `401 Unauthorized` | 인증 필요/실패 | Cred 없거나 틀림 |
| `500 Command failed` | 처리 실패 | 다운로드/설치 실패 |

**필요한 것:**

| 항목 | 위치 |
|------|------|
| 진행률 페이로드 분리 (`DOWNLOAD_IN_PROGRESS` 추가) | [JobStatus](../backend/src/main/java/com/syncml/server/domain/JobStatus.java) |
| `handleClientStatus` 에 202 분기 추가 → step advance 안 함, 진행률만 갱신 | [SyncMLMessageService](../backend/src/main/java/com/syncml/server/syncml/service/SyncMLMessageService.java) |

---

### 5.11 Generic Alert (1226) 처리

> ❌ **미구현** — [SyncMLMessageService.handleAlert](../backend/src/main/java/com/syncml/server/syncml/service/SyncMLMessageService.java) 는 1201 (Client-Initiated) 만 분기.

**Alert 코드 종류:**

| 코드 | 의미 | 누가 보냄 |
|------|------|-----------|
| `1200` | Server-Initiated Session | 서버 → 단말 |
| `1201` | Client-Initiated Session | 단말 → 서버 (✅ 구현됨) |
| `1222` | Session Abort | 양쪽 |
| `1223` | Session Resume | 단말 → 서버 (Resume 정책 시) |
| `1224` | Next Message | 양쪽 (다음 메시지 있음) |
| `1225` | No End of Data | MoreData 종료 알림 |
| `1226` | Generic Alert | 단말 → 서버 (비정형 이벤트) |

**Generic Alert 1226 의 용도:**
- 단말 측 비정형 이벤트 보고: 배터리 부족, 네트워크 변경, 설치 보류, 진행률, 사용자 거부, PKI 검증 실패 등
- WBXML/MoreData/PKI 도입 시 단말이 에러를 리포팅할 채널이 됨

**예시 (단말 → 서버):**

```xml
<Alert>
  <CmdID>2</CmdID>
  <Data>1226</Data>
  <Item>
    <Meta>
      <Type xmlns="syncml:metinf">org.openmobilealliance.dm.firmwareupdate.userinteraction</Type>
      <Format>chr</Format>
      <Mark>indeterminate</Mark>
    </Meta>
    <Source><LocURI>./FUMO/State</LocURI></Source>
    <Data>SIGNATURE_VERIFY_FAILED</Data>
  </Item>
</Alert>
```

**필요한 것:**

| 항목 | 위치 |
|------|------|
| Alert 1226 핸들러 추가 | [SyncMLMessageService.handleAlert](../backend/src/main/java/com/syncml/server/syncml/service/SyncMLMessageService.java) |
| Alert Type별 라우팅 (battery / progress / pki / userdefer) | 신규 `GenericAlertHandler` |
| `event_log` 에 Alert 종류별 분류 저장 | [EventLog](../backend/src/main/java/com/syncml/server/domain/EventLog.java) |

---

## 6. 큐 전략 (2단계)

> **큐의 책임 범위**: 큐는 **"업데이트 작업 지시 전달"**에만 사용한다. 상태 조회, 로그 기록, 세션 관리 등 나머지는 DB/API로 직접 처리한다.

### 6.0 Phase 1 vs Phase 2 핵심 차이

```
┌─────────────────────────────────────────────────────────────────────┐
│  Phase 1: DB 큐 (Polling)                                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   [Vue] → [서버] → [DB에 Job 저장]                                   │
│                         │                                           │
│                         ▼                                           │
│   [시뮬레이터]         "작업 있나?"                                   │
│       │                  ↓                                          │
│       │  ─── 5초마다 ─── [서버] ─── DB 조회                          │
│       │                  ↓                                          │
│       │  ◄───────────── "있어, 접속해"                               │
│       │                                                             │
│       └──── SyncML 세션 시작 ────►                                  │
│                                                                     │
│   → 시뮬레이터가 직접 서버에 물어봄 (최대 5초 지연)                    │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│  Phase 2: RabbitMQ (Push 알림)                                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   [Vue] → [서버] → [DB에 Job 저장]                                   │
│                │                                                    │
│                └──► [RabbitMQ] ──► [시뮬레이터]                      │
│                     "VIN-0002에 작업 생김"  (즉시 알림)               │
│                                       │                             │
│                                       ▼                             │
│                              SyncML 세션 시작                        │
│                                                                     │
│   → 서버가 시뮬레이터에 즉시 알림 (지연 없음)                          │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 6.0.1 RabbitMQ vs MQTT: 본질은 같다

**둘 다 하는 일:**
```
"야 VIN-0002, 작업 있어. 접속해."
```

**차이는 "누구에게 보내느냐" (환경):**

| 구분 | RabbitMQ (이 프로젝트) | MQTT (실제 운영) |
|------|------------------------|------------------|
| **대상** | 내부 시뮬레이터 | 외부 실제 차량 |
| **네트워크** | 같은 Docker 네트워크 | 인터넷 (NAT, 방화벽 뒤) |
| **설계 목적** | 내부 서비스 간 통신 | 외부 IoT 기기 통신 |
| **프로토콜** | AMQP (무거움, 기능 많음) | 경량 (2바이트 헤더) |
| **연결 특성** | 안정적 연결 가정 | 불안정/저전력 대응 |

**비유:**
```
MQTT     = 국제 전화 (외국에 있는 사람에게)
RabbitMQ = 내선 전화 (같은 건물 안에서)

둘 다 "전화"지만, 환경이 달라서 시스템이 다름
```

**받은 후 행동은 100% 동일:**
```
[RabbitMQ 알림 받은 시뮬레이터]  →  SyncML 세션 시작
[MQTT 알림 받은 실제 차량]      →  SyncML 세션 시작
```

> **결론**: 본질은 같은 "알림". 환경(내부/외부)에 따라 도구가 다를 뿐.

### 6.1 Phase 1: DB 큐 (MVP) ← 현재 구현

- `update_job.status = 'QUEUED'`로 작업 등록
- Device Simulator가 polling으로 자기 VIN 작업 확인
- 처리 후 상태 갱신

**장점**: 단순, 빠른 구현  
**단점**: polling 지연

### 6.2 Phase 2: RabbitMQ 확장

- 작업 생성 시 `device.update.requested` 이벤트 발행
- Routing Key: `vin.<VIN-ID>`
- Device Simulator가 자기 VIN 큐만 구독

**장점**: 비동기 push, 확장성  
**면접 포인트**: "polling → broker 구조로 확장한 경험"

### 6.3 큐에 태우지 않는 것 (명시)

| 항목 | 처리 방식 |
|------|-----------|
| 단말 상태 조회 | DB 직접 조회 |
| 로그 기록 | DB INSERT |
| 세션 상태 관리 | DB UPDATE |
| 운영자 조회 | Django → DB |

---

## 7. 단말 시뮬레이터 구조

### 7.1 Phase 1: 단일 프로세스 다중 단말

```java
SimulatedDevice(VIN-0001)
SimulatedDevice(VIN-0002)
SimulatedDevice(VIN-0003)
```

- 각 객체가 별도 스레드/스케줄러로 동작
- 자기 VIN 작업만 처리

### 7.2 Phase 2: 컨테이너 분리

```yaml
device-sim-1:
  environment:
    DEVICE_VIN: VIN-0001

device-sim-2:
  environment:
    DEVICE_VIN: VIN-0002
```

### 7.3 동작 규칙 (구현 시 준수)

| 규칙 | 값 | 설명 |
|------|-----|------|
| **Poll 주기** | 5초 | Phase 1(DB 큐)에서 서버에 작업 확인 요청 주기 |
| **동시 작업 제한** | VIN당 1개 | 같은 VIN에 대해 동시에 2개 이상 작업 처리 금지 |
| **Heartbeat 주기** | 10초 | `device.last_seen_at` 갱신 주기 |
| **재시도 정책** | 최대 3회 | 실패 시 30초 대기 후 재시도, 3회 초과 시 FAIL 확정 |
| **처리 시간 시뮬레이션** | 랜덤 | 다운로드 2~5초, 설치 3~8초 (즉시 완료 방지) |

### 7.3.1 Poll vs Heartbeat 개념 비교

| 개념 | 질문 | 목적 | 예시 |
|------|------|------|------|
| **Poll (폴링)** | "나 할 일 있어?" | 작업 가져오기 | 5초마다 서버에 새 작업 있는지 확인 |
| **Heartbeat (심장박동)** | "나 아직 살아있어" | 상태 확인용 | 10초마다 last_seen_at 갱신 |

```
[Poll - 5초마다]
단말: "나 VIN-0002야, 새 작업 있어?"
서버: "없어" 또는 "있어, 이거 해"

[Heartbeat - 10초마다]  
단말: "나 아직 켜져있어"
서버: (DB에 last_seen_at 갱신 → 단말 생존 여부 판단 가능)
```

**왜 둘 다 필요한가?**
- **Poll**: 작업을 가져오기 위한 요청 (작업이 있을 때만 의미 있음)
- **Heartbeat**: 단말이 연결 가능한 상태인지 확인 (작업 없어도 계속 보냄)

예: 단말이 30초간 Heartbeat가 없으면 → "이 단말 연결 끊김" 판단 가능

### 7.4 실패 시나리오 (데모용)

| 시나리오 | 트리거 조건 | 결과 |
|----------|-------------|------|
| 다운로드 실패 | 20% 확률 또는 특정 VIN | `status = FAIL`, retry 진입 |
| 설치 실패 | 10% 확률 | `status = FAIL`, 로그 기록 |
| Timeout | 60초 내 상태 변화 없음 | 세션 강제 종료 |

---

## 8. Docker 네트워크 분리

### 8.1 네트워크 구조

```
┌─────────────────────────────────────────────┐
│                public-net                   │
│  ┌─────────┐  ┌─────────────────────────┐  │
│  │  Vue    │  │  Java SyncML Server     │  │
│  │Dashboard│  │  (외부 API 노출)         │  │
│  └─────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────┐
│               private-net                   │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │ RabbitMQ │  │   DB     │  │  Django  │  │
│  └──────────┘  └──────────┘  └──────────┘  │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │device-sim│  │device-sim│  │device-sim│  │
│  │    -1    │  │    -2    │  │    -3    │  │
│  └──────────┘  └──────────┘  └──────────┘  │
└─────────────────────────────────────────────┘
```

### 8.2 서비스별 노출 정책

| 서비스 | 네트워크 | 외부 노출 | 용도 |
|--------|----------|-----------|------|
| **Vue Dashboard** | public-net | ✅ `:3000` | 사용자 대시보드 |
| **Java SyncML Server** | public-net + private-net | ✅ `:8080` | 단말 API, 대시보드 API |
| **Django Admin** | private-net only | ⛔ 내부 전용 | 내부 운영자 조회 |
| **PostgreSQL** | private-net only | ⛔ 내부 전용 | 데이터 저장소 |
| **RabbitMQ** | private-net only | ⛔ 내부 전용 | 작업 분배 브로커 |
| **Device Simulator** | private-net only | ⛔ 내부 전용 | 단말 시뮬레이션 |

> **Django 노출 정책**: Django는 **private-net에만 연결**되며, 외부에서 직접 접근 불가.  
> 운영자는 VPN 또는 내부 네트워크를 통해서만 접근한다.  
> 개발 환경에서는 `localhost:8000` 포트 매핑으로 테스트 가능하나, 운영 시 반드시 제거.

---

## 9. 개발 단계 (MVP → 확장)

### Phase 1: 핵심 흐름 검증 (2주)

- [x] 아키텍처 문서 작성
- [ ] Java 백엔드 기본 구조 (Spring Boot)
- [ ] DB 스키마 생성 (device, update_job, event_log)
- [ ] 단일 프로세스 단말 시뮬레이터 (3 VIN)
- [ ] DB 큐 폴링 방식 작업 처리
- [ ] SyncML 최소 FUMO 흐름 구현

### Phase 2: 프론트엔드 (1주)

- [ ] Vue 3 + TypeScript 프로젝트 생성
- [ ] 단말 목록 화면 (VIN, 상태, 마지막 접속)
- [ ] 단말 상세 화면 (로그 타임라인)
- [ ] 업데이트 실행 버튼

### Phase 3: 메시지 브로커 (1주)

- [ ] RabbitMQ 컨테이너 추가
- [ ] VIN 기반 라우팅 키 설정
- [ ] Device Simulator → RabbitMQ 소비자 전환
- [ ] polling vs broker 비교 문서화

### Phase 4: 운영 도구 (1주)

- [ ] Django 프로젝트 생성
- [ ] VIN 검색 API
- [ ] 세션/이력 조회 API
- [ ] 실패건 필터링

### Phase 5: 컨테이너화 (3일)

- [ ] Docker Compose 작성
- [ ] public-net / private-net 분리
- [ ] 단말 시뮬레이터 컨테이너 분리

---

## 10. 면접 설명 포인트

### "왜 SyncML?"

> 상태 기반 프로토콜로, 단말과 서버 간 비동기적 상태 교환에 적합.
> OMA 표준 기반 OTA/디바이스 관리 방식.
> Alert/Status/Exec 명령-응답 구조.

### "왜 RabbitMQ?"

> 초기에는 DB 큐로 MVP를 검증했고,
> 이후 비동기 push 구조로 확장하여 특정 VIN 대상 작업이
> 해당 단말 시뮬레이터만 소비하도록 설계.

### "왜 분리 구조?"

> 외부 노출 API(SyncML)와 내부 운영 시스템(Django)을 분리.
> Docker 네트워크로 public/private 영역 분리.
> DB 직접 노출 회피.

---

## 11. 참고: Vue 3 + TypeScript 오픈소스 스택

```bash
# 프로젝트 생성
npm create vite@latest dashboard -- --template vue-ts

# 필수 패키지
npm install pinia axios vue-router

# UI 컴포넌트 (택 1)
npm install primevue primeicons  # PrimeVue
# 또는
npm install vuetify              # Vuetify 3
```

### 추천 폴더 구조

```
dashboard/
├── src/
│   ├── api/           # Axios 인스턴스, API 호출
│   ├── components/    # 재사용 컴포넌트
│   ├── composables/   # Composition API 훅
│   ├── router/        # Vue Router
│   ├── stores/        # Pinia 스토어
│   ├── types/         # TypeScript 타입 정의
│   ├── views/         # 페이지 컴포넌트
│   ├── App.vue
│   └── main.ts
├── index.html
├── package.json
├── tsconfig.json
└── vite.config.ts
```

---

## 12. Java 백엔드 구조 (현재 구현)

```
backend/src/main/java/com/syncml/server/
├── SyncmlServerApplication.java           # 메인 엔트리 (@EnableScheduling)
├── config/
│   └── DataLoader.java                    # 초기 데이터 로드 (dev 프로파일)
├── controller/
│   ├── DeviceController.java              # 단말 REST API
│   └── JobController.java                 # 작업 REST API
├── domain/
│   ├── Device.java                        # 단말 엔티티
│   ├── DeviceCredential.java              # 인증 정보 엔티티 ⭐
│   ├── DeviceStatus.java                  # 단말 상태 enum
│   ├── EventLog.java                      # 이벤트 로그 엔티티
│   ├── JobStatus.java                     # 작업 상태 enum
│   ├── SyncSession.java                   # 세션 엔티티 ⭐
│   ├── SyncSessionStatus.java             # 세션 상태 enum ⭐
│   └── UpdateJob.java                     # 업데이트 작업 엔티티
├── repository/
│   ├── DeviceCredentialRepository.java    # 인증 정보 Repository ⭐
│   ├── DeviceRepository.java
│   ├── EventLogRepository.java
│   ├── SyncSessionRepository.java         # 세션 Repository ⭐
│   └── UpdateJobRepository.java
├── service/
│   ├── DeviceService.java
│   ├── EventLogService.java
│   └── UpdateJobService.java
└── syncml/                                # SyncML 전용 패키지 ⭐
    ├── controller/
    │   ├── SyncMLController.java          # SyncML 엔드포인트 (POST /syncml)
    │   └── SyncSessionController.java     # 세션 조회 API
    ├── dto/
    │   ├── Alert.java                     # Alert 요소
    │   ├── Command.java                   # Get/Exec/Replace 명령
    │   ├── Cred.java                      # 인증 정보 (Cred 요소)
    │   ├── Result.java                    # Results 요소
    │   ├── Status.java                    # Status 요소 + Chal
    │   ├── SyncHdr.java                   # SyncHdr 요소
    │   └── SyncMLMessage.java             # 전체 SyncML 메시지
    ├── constant/
    │   ├── DevInfoNode.java               # ./DevInfo/* 표준 노드 enum
    │   └── SyncMLLocUri.java              # ./FUMO/* 등 LocURI constants
    ├── service/
    │   ├── SyncMLAuthService.java         # 인증 처리 (Basic/MD5)
    │   ├── SyncMLMessageService.java      # 메시지 처리 핵심 로직
    │   └── SyncMLSessionService.java      # 세션 관리 (생성/조회/만료)
    └── util/
        └── SyncMLXmlUtil.java             # XML 파싱/생성 유틸리티
```

> ⭐ = 이번에 추가된 핵심 파일 (Cred 인증 + SessionID 연속성)

---

## 13. API 엔드포인트 정리

### SyncML 엔드포인트

| Method | URL | Content-Type | 설명 |
|--------|-----|--------------|------|
| POST | `/syncml` | `application/vnd.syncml.dm+xml` | SyncML 메시지 처리 |
| GET | `/syncml/health` | - | 서버 상태 확인 |

### REST API

| Method | URL | 설명 |
|--------|-----|------|
| GET | `/api/devices` | 모든 단말 조회 |
| GET | `/api/devices/{vin}` | 단말 상세 조회 |
| POST | `/api/devices` | 단말 등록 |
| POST | `/api/devices/{vin}/heartbeat` | Heartbeat 갱신 |
| GET | `/api/devices/{vin}/logs` | 단말 로그 조회 |
| GET | `/api/sessions/{sessionId}` | 세션 조회 |
| GET | `/api/sessions/active/{vin}` | VIN의 활성 세션 |
| GET | `/api/sessions/history/{vin}` | VIN의 세션 이력 |
| GET | `/api/jobs` | 모든 작업 조회 |
| POST | `/api/jobs` | 작업 생성 |

---

## 14. 인프라 고려사항

### 14.1 세션 타임아웃 정책

| 환경 | 권장 타임아웃 | 이유 |
|------|--------------|------|
| OTA 업데이트 | 5~10분 | 다운로드/설치에 시간 소요 |
| 단순 조회 | 1~2분 | 빠른 응답 기대 |
| 실무 차량 OTA | 10~30분 | 대용량 패키지, 네트워크 불안정 |

**현재 설정**: 5분 (300초)

```java
private static final int SESSION_TIMEOUT_MINUTES = 5;
```

**타임아웃 시 동작**:
- `session_status = EXPIRED`로 변경
- 다음 접속 시 새 세션 생성
- 진행 중이던 Job은 유지 → 다음 세션에서 이어받기 가능

### 14.2 DB Connection Pool

- Spring Boot 기본 **HikariCP** 사용
- 설정: `spring.datasource.hikari.maximum-pool-size=10`
- 모니터링: Actuator `/actuator/health` 엔드포인트

### 14.3 Thread 관리

| 용도 | 방식 | 설정 |
|------|------|------|
| HTTP 요청 | Tomcat Thread Pool | `server.tomcat.threads.max=200` |
| 스케줄링 | @Scheduled | `@EnableScheduling` |
| 비동기 작업 | @Async | 필요 시 ThreadPoolExecutor 설정 |

### 14.4 동시성 고려

| 상황 | 해결 방안 |
|------|----------|
| 같은 VIN 동시 세션 | 기존 세션 만료 후 새 세션 생성 |
| Job 할당 경쟁 | SELECT FOR UPDATE 또는 낙관적 잠금 |
| 세션 정리 | 5분 주기 스케줄러 (`@Scheduled`) |

### 14.5 확장 시 고려사항

| 규모 | 구성 |
|------|------|
| 100대 미만 | 단일 서버, 기본 설정 |
| 1000대 | DB 분리, Connection Pool 튜닝 |
| 10000대+ | 서버 다중화, Redis 세션 저장, RabbitMQ 필수 |

---

## 15. 명령 타입 확장 (향후)

현재는 FUMO(펌웨어 업데이트)만 구현. 확장 시 아래 명령 추가 가능.

### 15.1 지원 가능한 명령 타입

| command_type | 설명 | SyncML 명령 |
|--------------|------|-------------|
| `FUMO_UPDATE` | 펌웨어 업데이트 (현재) | Replace + Exec |
| `DIAG_REQUEST` | 진단 데이터 요청 | Get |
| `CONFIG_UPDATE` | 설정 값 변경 | Replace |
| `SCOMO_INSTALL` | SW 컴포넌트 설치 | Replace + Exec |

### 15.2 진단 요청 예시

```xml
<!-- 서버 → 단말: 엔진 ECU 상태 요청 -->
<Get>
  <CmdID>3</CmdID>
  <Item>
    <Target><LocURI>./Diagnostics/ECU/Engine</LocURI></Target>
  </Item>
</Get>

<!-- 단말 → 서버: 결과 -->
<Results>
  <CmdID>4</CmdID>
  <CmdRef>3</CmdRef>
  <Item>
    <Source><LocURI>./Diagnostics/ECU/Engine</LocURI></Source>
    <Data>{"status":"OK","temperature":85,"rpm":2100}</Data>
  </Item>
</Results>
```

### 15.3 설정 변경 예시

```xml
<!-- 서버 → 단말: 헤드라이트 감도 변경 -->
<Replace>
  <CmdID>5</CmdID>
  <Item>
    <Target><LocURI>./Config/HeadlightSensitivity</LocURI></Target>
    <Data>HIGH</Data>
  </Item>
</Replace>
```

---

## 16. 프로젝트 가치 분석

### 16.1 SyncML 구현의 가치

| 관점 | 평가 |
|------|------|
| **표준 준수** | OMA 표준 → 자동차/IoT 업계 실제 사용 |
| **프로토콜 이해** | XML 기반 요청-응답 패턴 학습 |
| **상태 관리** | 세션 기반 다단계 트랜잭션 경험 |
| **실무 연관성** | 차량 OTA(현대, BMW 등)에서 유사 구조 사용 |

> **참고**: 최신 트렌드는 MQTT + JSON 조합이 더 많이 쓰이지만,
> SyncML은 "표준 준수가 필요한 환경"에서 여전히 유효함.

### 16.2 RabbitMQ 구현의 가치

| 관점 | 평가 |
|------|------|
| **비동기 처리** | 실무 필수 패턴 |
| **확장성** | 단말 수 증가 시 필수 |
| **장애 격리** | 서버-워커 분리로 안정성 확보 |
| **실무 연관성** | 거의 모든 대규모 서비스에서 사용 |

### 16.3 포트폴리오 차별화

```
[일반적인 프로젝트]
"REST API 서버 만들었습니다"
→ 차별화 없음

[이 프로젝트]
"SyncML 프로토콜 기반 OTA 서버를 구현했습니다.
 단말 시뮬레이터가 Polling으로 작업을 가져가고,
 이후 RabbitMQ 기반 Push 구조로 확장했습니다.
 외부 API(SyncML)와 내부 운영(Django)을 Docker 네트워크로 분리했습니다."
→ 차별화됨
```

### 16.4 실제 서비스 적용 가능성

| 영역 | 적용성 | 설명 |
|------|--------|------|
| 차량 OTA | ⭐⭐⭐⭐ | 현대/기아, BMW 등 실제 사용 구조와 유사 |
| IoT 디바이스 | ⭐⭐⭐ | 펌웨어 배포 시스템으로 활용 가능 |
| 모바일 MDM | ⭐⭐⭐ | 기업용 모바일 관리에 SyncML 기반 사례 있음 |
| 일반 웹서비스 | ⭐⭐ | 과한 감 있음, REST로 충분 |

### 16.5 실무 통신 방식 비교

#### 외부 통신 (단말 ↔ 서버)

| 방식 | 사용처 | 데이터 형식 | 특징 |
|------|--------|------------|------|
| **SyncML (OMA-DM)** | 구형 차량 OTA, 통신사 MDM | XML | 표준 준수 필수, 무거움 |
| **MQTT + JSON** | 신규 IoT, 스마트홈, 신형 차량 | JSON | 경량, 실시간, **현재 대세** |
| **HTTP + JSON** | 일반 REST API | JSON | 단순, 폴링 기반 |

#### 내부 통신 (서버 ↔ 서버)

| 기술 | 용도 |
|------|------|
| **RabbitMQ** | 작업 분배, 이벤트 전파, 재시도 큐 |
| **Kafka** | 대용량 로그, 이벤트 스트리밍 |
| **Redis Pub/Sub** | 실시간 알림, 캐시 무효화 |

> **핵심**: RabbitMQ는 **단말과 직접 통신하지 않는다**. 내부 백엔드 서비스 간 작업 분배용.
>
> 📄 **상세 비교**: [MESSAGING_COMPARISON.md](./MESSAGING_COMPARISON.md) 참조

#### 실제 차량 OTA 구조 (MQTT 기반)

```
[차량 TCU/Head Unit]
      │
      │  ① MQTT (연결 유지, 푸시 알림)
      │     └─ 토픽: vehicle/{vin}/commands
      │     └─ 메시지: JSON { "type": "OTA_AVAILABLE", ... }
      │
      │  ② HTTPS (대용량 다운로드)
      │     └─ 패키지 파일 다운로드
      │     └─ 진행 상태 보고
      │
      ▼
[OTA Cloud Platform]
      ├─ [MQTT Broker] ← 단말 연결
      ├─ [RabbitMQ/Kafka] ← 내부 작업 분배
      ├─ [패키지 저장소 - S3/CDN]
      └─ [캠페인 관리 시스템]
```

#### MQTT + JSON 메시지 예시

```json
// 서버 → 단말: OTA 알림
{
  "type": "OTA_AVAILABLE",
  "campaignId": "camp-2026-001",
  "targetVersion": "2.1.0",
  "packageUrl": "https://cdn.example.com/packages/2.1.0.zip"
}

// 단말 → 서버: 진행 상태
{
  "type": "OTA_PROGRESS",
  "status": "DOWNLOADING",
  "progress": 45
}
```

#### 이 프로젝트의 학습 가치

| 이 프로젝트 | 실무 (MQTT 기반) | 공통 개념 |
|------------|------------------|----------|
| SyncML (XML) | MQTT + JSON | 프로토콜 기반 통신 |
| SessionID | Connection ID | 연결/세션 관리 |
| Alert/Status/Exec | publish/subscribe | 명령-응답 패턴 |
| DB 큐 → RabbitMQ | RabbitMQ/Kafka | 내부 작업 분배 |

---

## 18. 문서 이력

| 날짜 | 내용 |
|------|------|
| 2026-03-24 | 초안 작성 |
| 2026-03-31 | SyncML Cred 인증 + SessionID 세션 관리 구현 |
| 2026-03-31 | 인프라 고려사항, 명령 확장, 프로젝트 가치 섹션 추가 |
| 2026-03-31 | 실무 통신 방식 비교 (SyncML vs MQTT+JSON, RabbitMQ 역할) 추가 |
| 2026-03-31 | Phase 1 단말 시뮬레이터 구현, RabbitMQ vs MQTT 본질 동일성 정리 |
| 2026-03-31 | 로그 분리 설정, TODO 문서 작성 |
| 2026-04-27 | 표준 정합성 갭 분석 추가: 5.0.3 DevInfo 사전 교환, 5.7 MoreData, 5.8 WBXML, 5.9 PKI 패키지 검증, 5.10 Status 코드 의미 분리, 5.11 Generic Alert 1226 (모두 ❌ 미구현 표시) |
| 2026-04-27 | 데이터 모델(4.1, 4.2)에 미구현 컬럼 명시 |
| 2026-05-13 | 5.0.3 DevInfo 사전 교환 ✅, 5.7 MoreData 청킹 ✅, 4.1/4.2 컬럼 ✅ 구현 반영. PKI(#4) 를 WBXML(#7) 뒤로 순서 변경 |
| 2026-05-14 | DevInfo/FUMO LocURI 상수화 정책 추가, `DevInfoNode`/`SyncMLLocUri` 도입, 업데이트 패키지 단일 아티팩트/Manifest 확장 정책 문서화 |

---

**관련 문서:**
- [MESSAGING_COMPARISON.md](./MESSAGING_COMPARISON.md) - MQTT/RabbitMQ/Kafka 비교
- [OPERATIONAL_PATTERNS.md](./OPERATIONAL_PATTERNS.md) - 알아두면 좋은 운영 패턴 (이 프로젝트 범위 외)
- [TODO.md](./TODO.md) - 다음 작업 계획

---

**작성자**: AI Copilot  
**프로젝트**: TESTPRO - SyncML OTA System
