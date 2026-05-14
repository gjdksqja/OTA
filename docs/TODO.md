# 작업 일지 및 TODO

---

## 도입 검토 문서

- [ADOPTION_REVIEW.md](./ADOPTION_REVIEW.md) — 하네스 도입 / 오케스트라 범위 / 테스트 자동 개선 / 보고 체계 검토
- 결론 요약: **테스트 자동 개선 → 가벼운 하네스 MVP → Markdown/JSON 보고 체계 → Docker Compose 오케스트라 확장** 순서 권장
- 도입 여부는 위 문서를 보고 결정. 아직 TODO 본 작업으로 확정하지 않음.

---

## 2026-05-14 작업 완료 내용

### DevInfo/FUMO 상수화 및 패키지 정책 정리

| 항목 | 결과 |
|------|------|
| DevInfo 표준 노드 Enum화 | ✅ `DevInfoNode` 추가 |
| FUMO LocURI constants 분리 | ✅ `SyncMLLocUri` 추가 |
| `./DevInfo/SwV` 현재 버전 매핑 | ✅ `Device.currentVersion` 갱신 |
| 다운로드/설치 실패 로그 타입 판정 | ✅ 상태 변경 전 단계 기준으로 수정 |
| 업데이트 파일 정책 문서화 | ✅ 단일 패키지 아티팩트 + Manifest 확장 정책 |

### 정책 결정

- 현재 구현은 **작업 1개 : `PkgURL` 1개** 기준이다.
- `PkgURL` 이 ZIP/TAR 같은 압축 파일이면 **압축 해제와 내부 파일 적용 순서는 단말/시뮬레이터 책임**으로 둔다.
- 여러 파일을 서버가 명시적으로 관리해야 하면, 다음 단계에서는 `PkgURL` 을 manifest URL 로 보고 manifest 안에 파일 목록/순서/sha256/대상 ECU 를 넣는 방식을 우선 검토한다.
- 파일별 상태까지 운영툴에서 보여줘야 할 때만 `update_package` / `update_package_artifact` 테이블 분리를 검토한다.

### 다음 구현 후보

- [ ] `SyncMLStatusCode`, `SyncMLAlertCode`, `SyncMLCommandName` constants 추가
- [ ] `JobController.JobCreateRequest` 에 `pkgSize`, `pkgSha256` 입력 또는 패키지 업로드 API 추가
- [ ] `SyncMLMessageService` Step 4 에 `PackageSize`, `PackageHash` 동봉 (PKI 본작업 전 hash-only 가능)
- [ ] Manifest 방식 샘플 JSON + 시뮬레이터 manifest 처리 실험

---

## 2026-05-13 작업 완료 내용

### SyncML 1.2 표준 정합성 1차 (DevInfo · 사이징 · MoreData)

| 항목 | 결과 |
|------|------|
| #1 DevInfo 사전 교환 (Pattern A 수신 + Pattern B 발행) | ✅ |
| #2 Device.maxMsgSize/MaxObjSize + UpdateJob 패키지 메타 컬럼 | ✅ |
| #3 `<MoreData/>` 파싱·생성 + 송신 측 청킹 + 수신 측 reassembly | ✅ |
| TODO 순서 조정: PKI(#4) 를 WBXML(#7) 뒤로 이동 | ✅ |

### 신규/변경 파일
- `domain/Device.java`, `domain/UpdateJob.java`, `db/init.sql` — 컬럼 확장
- `syncml/dto/Command.java`, `syncml/dto/Result.java` — `moreData` 필드
- `syncml/util/SyncMLXmlUtil.java` — `<MoreData/>` 파싱·생성
- `syncml/util/MessageChunker.java` (신규) — 송신 측 청킹
- `syncml/service/ChunkReassemblyService.java` (신규) — 수신 측 reassembly
- `syncml/service/SyncMLMessageService.java` — DevInfo 양방향 처리, 청킹/reassembly 통합, `Device` 갱신

### 다음 차례
- #5 Status 200/202 분리 + Generic Alert 1226 (단독 작업, 가벼움)
- #6 RabbitMQ Phase 2
- #7 WBXML 인코딩 추상화
- #4 PKI 패키지 검증 (WBXML 후)

### 검토 후 도입 후보
- [ ] 테스트 자동 개선: `MessageChunkerTest`, `ChunkReassemblyServiceTest`, `SyncMLXmlUtil` MoreData round-trip 테스트
- [ ] 하네스 MVP: `normal-update` 시나리오 자동 실행
- [ ] 보고 체계: `build/reports/syncml-harness/report.md`, `summary.json`
- [ ] 오케스트라 확장: Docker Compose smoke test (RabbitMQ Phase 2 이후)

---

## 🔴 우선순위 표준 준수 (SyncML 1.2 정합성)

> 2026-04-27 갭 분석 추가. 모두 **아직 작업 안됨**. 위에서부터 순서대로 작업 권장 (의존 관계 있음).
> 상세 설명은 [ARCHITECTURE.md 섹션 5.0.3 ~ 5.11](./ARCHITECTURE.md#503-devinfo-사전-교환-표준-정합성) 참조.

### 인증·식별 구현 매트릭스 (헷갈림 방지)

| 영역 | 구현 상태 | 비고 |
|------|-----------|------|
| Cred Basic 인증 (`syncml:auth-basic`) | ✅ 구현됨 | `SyncMLAuthService` |
| Cred MD5 + Nonce 인증 (`syncml:auth-md5`) | ✅ 구현됨 | NextNonce 재발급 포함 |
| Cred HMAC 인증 (`syncml:auth-hmac`) | ❌ 미구현 | 우선순위 낮음 |
| 인증 실패 카운터 / 잠금 | ✅ 구현됨 | `DeviceCredential.failCount`, 5회 잠금 |
| SessionID 연속성 | ✅ 구현됨 | `SyncSession` |
| **DevInfo 트리 사전 교환** | ✅ 구현됨 | `DevInfoNode`, `SyncMLMessageService` |

| # | 항목 | 의존 관계 | 상태 |
|---|------|-----------|------|
| 1 | **DevInfo 사전 교환** (`./DevInfo` 트리 표준 교환) | (없음) | ✅ 완료 (2026-05-13) |
| 2 | **파일 사이징 + 패키지 메타** (Device.maxMsgSize, UpdateJob.pkgSize/sha256) | #1 | ✅ 완료 (2026-05-13) |
| 3 | **MoreData 청킹** (`<MoreData/>` 파싱·생성·reassembly) | #1, #2 | ✅ 완료 (2026-05-13) |
| 5 | **Status 202 분리** + **Generic Alert 1226 핸들러** | (없음, 단독 가능) | 🔴 아직 작업 안됨 |
| 6 | **RabbitMQ Phase 2 연동** (기존 TODO #6) | (없음) | 🟡 계획됨 |
| 7 | **WBXML 인코딩** (Content-Type 협상, codec 추가) | (없음, 큰 작업) | 🔴 아직 작업 안됨 |
| 4 | **PKI 패키지 검증 흐름** (Replace에 Hash/Sig 동봉, 업로드 시 검증) | #2, #7 | 🔴 아직 작업 안됨 (WBXML 뒤로 이동) |

> ⚠️ **순서 변경 (2026-05-13)**: PKI 검증(#4)을 WBXML(#7) **뒤로** 이동.
> 사유: WBXML 인코딩이 끝나야 패키지 메타(Hash/Sig) 가 양쪽 인코딩(XML/WBXML) 모두에서 일관되게 발행/검증되므로,
> WBXML 추상화가 먼저 들어가는 게 PKI 작업의 재작업 비용을 줄인다.

### 작업 순서 권장

```
[1] DevInfo 사전 교환 ✅ ──► [2] 파일 사이징/패키지 메타 ✅ ──► [3] MoreData 청킹 ✅
                                                                       │
[5] Status 202 + Alert 1226   (위 흐름과 독립적, 언제든 가능)            │
[6] RabbitMQ Phase 2          (위 흐름과 독립적)                        │
[7] WBXML                     (큰 작업, 인코딩 추상화)  ◄────────────────┘
                                       │
                                       ▼
[4] PKI 패키지 검증 흐름      (WBXML 뒤. 양쪽 인코딩 모두에서 Hash/Sig 일관 발행)
```

### 각 항목 상세

#### 1. DevInfo 사전 교환 ✅ 완료 (2026-05-13)
- **선행 확인**: Cred 인증(Nonce/MD5) 로직은 ✅ **이미 구현됨** (`SyncMLAuthService`, `DeviceCredential.serverNonce`, `SyncMLMessageService:84` `updateNonce`). 이 작업은 **인증을 새로 만드는 게 아니라**, 인증이 끝난 첫 메시지(Pkg #1)에 같이 들어오는 `./DevInfo/*` 트리를 파싱·저장하는 로직을 추가하는 것이다.
- **Cred vs DevInfo 구분**: [ARCHITECTURE.md 5.0.3 표 참조](./ARCHITECTURE.md#503-devinfo-사전-교환-표준-정합성) — Cred는 `<SyncHdr>` 안의 신원 확인, DevInfo는 `<SyncBody>` 안의 단말 스펙 통보.
- **변경 파일**:
  - `domain/Device.java` — `maxMsgSize`, `maxObjSize`, `supportLargeObj`, `manufacturer`, `dmClientVersion`, `lang`, `devId` 컬럼 추가
  - `db/init.sql` — 동일 컬럼 추가
  - `syncml/service/SyncMLMessageService.java` — `handleDevInfoReplace` (Pattern A: client-push), `handleResults` 의 `./DevInfo/*` 분기 (Pattern B: server-pull), `applyDevInfoNode` 매퍼
  - `determineNextCommands` step 1 에서 `device.maxMsgSize == null` 이면 `Get ./DevInfo/Ext/MaxMsgSize|MaxObjSize|Man|DmV` 동봉 (Pattern B)

#### 2. 파일 사이징 + 패키지 메타 ✅ 완료 (2026-05-13)
- **변경 파일**:
  - `domain/UpdateJob.java` — `pkgSize`, `pkgSha256`, `pkgSignature`, `signatureAlgorithm`, `signingCertChain` 컬럼 추가
  - `db/init.sql` — 동일 컬럼 추가
- **남은 일** (PKI 작업 #4 에서 처리): 패키지 업로드 API + sha256/사이즈 자동 계산 + 서버가 Replace `./FUMO/PackageHash` 동봉

#### 3. MoreData 청킹 ✅ 완료 (2026-05-13)
- **변경 파일**:
  - `syncml/dto/Command.java`, `syncml/dto/Result.java` — `boolean moreData` 필드 추가
  - `syncml/util/SyncMLXmlUtil.java` — `<MoreData/>` 파싱(`getFirstChildElement(... "MoreData") != null`) 및 생성(`appendChild(doc.createElement("MoreData"))`)
  - `syncml/util/MessageChunker.java` (신규) — `chunkIfNeeded(message, device.maxMsgSize)` 로 송신 측 Item 분할 (마진 512 byte)
  - `syncml/service/ChunkReassemblyService.java` (신규) — sessionId+cmdId 기준 In-Memory reassembly buffer
  - `syncml/service/SyncMLMessageService.java` — `processMessage` 응답 직렬화 직전 `messageChunker.chunkIfNeeded(...)` 호출, `handleResults` 에서 `chunkReassemblyService.accept(...)` 로 누적, 세션 완료/실패 시 `clearSession`

#### 4. PKI 패키지 검증 흐름 🔴 아직 작업 안됨 (WBXML 뒤로 이동, 2026-05-13)
- **순서 변경 사유**: 패키지 메타(Hash/Sig)가 XML/WBXML **양쪽 인코딩에서 동일하게 발행·검증**되어야 하므로,
  WBXML 인코딩 추상화(#7)가 먼저 들어간 뒤 그 위에서 PKI를 구현하는 것이 재작업 비용을 줄임.
  의존: #2 ✅ + #7 🔴
- **파일**: `SyncMLMessageService.determineNextCommands case 3`, 신규 `PackageController`, 신규 `PkiService`
- **로직**:
  - 업로드 시: 인증서 체인 검증 + 서명 검증 (실패 거부)
  - SyncML 응답 시: `Replace ./FUMO/PkgURL` 옆에 `./FUMO/PackageHash`, `./FUMO/PackageSig`, `./FUMO/SigAlg` 같이 발행
  - 단말이 다운로드 후 sha256 비교 + 서명 검증 → 실패 시 Alert 1226 으로 보고
- **참조**: [ARCHITECTURE.md 5.9](./ARCHITECTURE.md#59-패키지-무결성--pki-검증-흐름)

#### 5. Status 202 분리 + Generic Alert 1226 🔴 아직 작업 안됨
- **파일**: `SyncMLMessageService.handleClientStatus`, `handleAlert`, `JobStatus` enum
- **로직**:
  - 200 vs 202 분리: 진행률 보고는 202, 최종 완료만 200
  - Alert 1226 핸들러: Type별 라우팅 (battery / progress / pki_failed / userdefer)
  - `JobStatus` 에 `DOWNLOAD_IN_PROGRESS`, `INSTALL_IN_PROGRESS` 추가
- **참조**: [ARCHITECTURE.md 5.10, 5.11](./ARCHITECTURE.md#510-status-코드-의미-분리-200--202--212)

#### 6. RabbitMQ Phase 2 연동 🟡 (기존 계획)
- 아래 "다음 작업 TODO" 의 #6 참조

#### 7. WBXML 인코딩 🔴 아직 작업 안됨 (큰 작업)
- **신규 파일**: `WbxmlCodec.java`, DM 1.2 코드페이지 리소스
- **변경**: `SyncMLController` 에 Content-Type 협상 (`application/vnd.syncml+wbxml` vs `+xml`)
- **추상화**: 기존 `SyncMLXmlUtil` 을 인터페이스로 분리 → XML/WBXML 둘 다 구현체로
- **참조**: [ARCHITECTURE.md 5.8](./ARCHITECTURE.md#58-wbxml-인코딩-운영-환경)

---

## 2026-03-31 작업 완료 내용

### 1. SyncML 핵심 기능 구현

| 항목 | 파일 | 상태 |
|------|------|------|
| Cred 인증 | `SyncMLAuthService.java`, `DeviceCredential.java` | ✅ 완료 |
| SessionID 세션 관리 | `SyncMLSessionService.java`, `SyncSession.java` | ✅ 완료 |
| 9단계 FUMO 흐름 | `SyncMLMessageService.java` | ✅ 완료 |
| XML 파싱/생성 | `SyncMLXmlUtil.java` | ✅ 완료 |
| SyncML 엔드포인트 | `SyncMLController.java` | ✅ 완료 |

### 2. 단말 시뮬레이터 구현 (Phase 1: DB 큐)

| 항목 | 파일 | 상태 |
|------|------|------|
| 개별 단말 시뮬레이션 | `DeviceSimulator.java` | ✅ 완료 |
| 시뮬레이터 관리자 | `SimulatorManager.java` | ✅ 완료 |
| 로그 분리 설정 | `logback-spring.xml` | ✅ 완료 |

### 3. 문서 정리

| 문서 | 내용 | 상태 |
|------|------|------|
| `ARCHITECTURE.md` | 9단계 FUMO 흐름, 인프라 고려사항, 프로젝트 가치 | ✅ 업데이트 |
| `MESSAGING_COMPARISON.md` | MQTT/RabbitMQ/Kafka 비교 (본질 동일성) | ✅ 신규 |
| `backend/README.md` | 시뮬레이터 설명, 로그 구조, API 목록 | ✅ 업데이트 |

### 4. 핵심 개념 정리

- **MQTT vs RabbitMQ vs Kafka**: 본질은 같음 (Consumer에게 알림), 환경/프로토콜만 다름
- **단말 vs 시뮬레이터**: 이 프로젝트에서 단말 = 시뮬레이터 (실제 차량 없이 테스트용)
- **단말 API vs 시뮬레이터**: API는 정보 관리(수동), 시뮬레이터는 차량 동작(자동)

---

## 다음 작업 TODO

### 🔴 우선순위 높음 (필수)

#### 1. 서버 실행 및 테스트
```powershell
# Java 17 설치 확인
java -version

# 서버 실행
cd backend
.\gradlew.bat bootRun
```

**테스트 항목:**
- [ ] 서버 정상 기동 확인
- [ ] H2 Console 접속 (http://localhost:8080/h2-console)
- [ ] 초기 데이터 로드 확인 (Device 3개, Credential 3개)
- [ ] 시뮬레이터 자동 시작 확인 (로그)
- [ ] SyncML 세션 흐름 확인 (simulator.log)

#### 2. REST API 테스트
```powershell
# 단말 조회
curl http://localhost:8080/api/devices

# 작업 생성
curl -X POST http://localhost:8080/api/jobs `
  -H "Content-Type: application/json" `
  -d '{"targetVin":"VIN-0001","commandType":"FUMO_UPDATE","payloadVersion":"2.0.0","pkgUrl":"http://example.com/pkg.zip"}'

# 세션 조회
curl http://localhost:8080/api/sessions/active/VIN-0001
```

#### 3. 컴파일 에러 수정 (있다면)
- [ ] `gradlew compileJava` 실행
- [ ] 에러 확인 및 수정

---

### 🟡 우선순위 중간 (권장)

#### 4. Vue 대시보드 기본 구조
```
dashboard/
├── src/
│   ├── views/
│   │   ├── DeviceList.vue      # 단말 목록
│   │   ├── DeviceDetail.vue    # 단말 상세 + 로그
│   │   └── JobCreate.vue       # 작업 생성
│   ├── api/
│   │   └── index.ts            # Axios 설정
│   └── types/
│       └── index.ts            # TypeScript 타입
```

**필요 기능:**
- [ ] 단말 목록 조회 (상태별 색상)
- [ ] 작업 생성 버튼
- [ ] 실시간 상태 갱신 (polling 또는 WebSocket)

#### 5. Docker Compose 테스트
```powershell
docker-compose up -d
```
- [ ] PostgreSQL 연결 확인
- [ ] prod 프로파일 동작 확인

---

### 🟢 우선순위 낮음 (Phase 2)

#### 6. RabbitMQ 연동 (Phase 2)
```yaml
# docker-compose.yml에 추가
rabbitmq:
  image: rabbitmq:3-management
  ports:
    - "5672:5672"
    - "15672:15672"
```

**구현 항목:**
- [ ] `spring-boot-starter-amqp` 의존성 추가
- [ ] 작업 생성 시 RabbitMQ 발행
- [ ] 시뮬레이터가 RabbitMQ 구독 → SyncML 세션 시작
- [ ] Polling vs Push 비교 문서화

#### 7. 명령 타입 확장
- [ ] `DIAG_REQUEST`: 진단 데이터 요청 (Get)
- [ ] `CONFIG_UPDATE`: 설정 변경 (Replace)

#### 8. Django Admin (내부 운영툴)
- [ ] 별도 컨테이너 구성
- [ ] DB 직접 조회 (읽기 전용)
- [ ] VIN 검색, 세션 이력, 실패 필터링

---

## 테스트 시나리오

### 시나리오 1: 정상 업데이트 흐름
1. 서버 시작 → 시뮬레이터 자동 기동
2. VIN-0001에 작업 생성 (POST /api/jobs)
3. 시뮬레이터가 5초 내 작업 감지
4. SyncML 9단계 흐름 진행
5. 작업 완료 (status=SUCCESS)
6. 로그 확인 (simulator.log, server.log)

### 시나리오 2: 설치 실패 → 재시도
1. 시뮬레이터 10% 랜덤 실패 발생
2. 작업 상태 FAIL 확인
3. 재시도 로직 확인 (retryCount 증가)

### 시나리오 3: 세션 타임아웃
1. 시뮬레이터 중지
2. 5분 후 세션 EXPIRED 확인
3. 스케줄러 정리 로직 확인

### 시나리오 4: 인증 실패
1. 잘못된 비밀번호로 접속 시도
2. Status 401 응답 확인
3. failCount 증가 확인
4. 5회 실패 시 locked 확인

---

## 예상 이슈

| 이슈 | 원인 | 해결 방법 |
|------|------|----------|
| JAVA_HOME 미설정 | Java 미설치 | `winget install Oracle.JDK.17` |
| 포트 충돌 (8080) | 다른 서버 사용 중 | `server.port=8081` 변경 |
| 시뮬레이터 즉시 종료 | 서버 시작 전 접속 시도 | `start-delay-ms` 증가 |
| XML 파싱 에러 | 네임스페이스 문제 | `SyncMLXmlUtil` 디버깅 |

---

## 파일 변경 요약 (오늘)

### 신규 생성
```
backend/src/main/java/com/syncml/server/
├── domain/
│   ├── DeviceCredential.java
│   ├── SyncSession.java
│   └── SyncSessionStatus.java
├── repository/
│   ├── DeviceCredentialRepository.java
│   └── SyncSessionRepository.java
├── simulator/
│   ├── DeviceSimulator.java
│   └── SimulatorManager.java
└── syncml/
    ├── controller/
    │   ├── SyncMLController.java
    │   └── SyncSessionController.java
    ├── dto/
    │   ├── Alert.java
    │   ├── Command.java
    │   ├── Cred.java
    │   ├── Result.java
    │   ├── Status.java
    │   ├── SyncHdr.java
    │   └── SyncMLMessage.java
    ├── service/
    │   ├── SyncMLAuthService.java
    │   ├── SyncMLMessageService.java
    │   └── SyncMLSessionService.java
    └── util/
        └── SyncMLXmlUtil.java

backend/src/main/resources/
└── logback-spring.xml

docs/
└── MESSAGING_COMPARISON.md
```

### 수정됨
```
backend/src/main/java/com/syncml/server/
├── SyncmlServerApplication.java     # @EnableScheduling 추가
├── config/DataLoader.java           # Credential 초기화 추가
└── repository/UpdateJobRepository.java  # findFirst 메서드 추가

backend/src/main/resources/
└── application.yml                  # simulator 설정 추가

docs/
└── ARCHITECTURE.md                  # 대폭 업데이트

backend/README.md                    # 대폭 업데이트
.gitignore                           # logs/ 추가
```

---

**다음 작업 시작점**: 서버 실행 (`gradlew bootRun`) → 로그 확인 → 에러 수정

