# 작업 일지 및 TODO

---

## 🔴 우선순위 표준 준수 (SyncML 1.2 정합성)

> 2026-04-27 갭 분석 추가. 모두 **아직 작업 안됨**. 위에서부터 순서대로 작업 권장 (의존 관계 있음).
> 상세 설명은 [ARCHITECTURE.md 섹션 5.0.3 ~ 5.11](./ARCHITECTURE.md#503-devinfo-사전-교환-표준-정합성) 참조.

| # | 항목 | 의존 관계 | 상태 |
|---|------|-----------|------|
| 1 | **DevInfo 사전 교환** (`./DevInfo` 트리 표준 교환) | (없음) | 🔴 아직 작업 안됨 |
| 2 | **파일 사이징 + 패키지 메타** (Device.maxMsgSize, UpdateJob.pkgSize/sha256) | #1 | 🔴 아직 작업 안됨 |
| 3 | **MoreData 청킹** (`<MoreData/>` 파싱·생성·reassembly) | #1, #2 | 🔴 아직 작업 안됨 |
| 4 | **PKI 패키지 검증 흐름** (Replace에 Hash/Sig 동봉, 업로드 시 검증) | #2 | 🔴 아직 작업 안됨 |
| 5 | **Status 202 분리** + **Generic Alert 1226 핸들러** | (없음, 단독 가능) | 🔴 아직 작업 안됨 |
| 6 | **RabbitMQ Phase 2 연동** (기존 TODO #6) | (없음) | 🟡 계획됨 |
| 7 | **WBXML 인코딩** (Content-Type 협상, codec 추가) | (없음, 큰 작업) | 🔴 아직 작업 안됨 |

### 작업 순서 권장

```
[1] DevInfo 사전 교환 ──────► [2] 파일 사이징/패키지 메타 ──┬─► [3] MoreData 청킹
                                                          │
                                                          └─► [4] PKI 패키지 검증

[5] Status 202 + Alert 1226   (위 흐름과 독립적, 언제든 가능)
[6] RabbitMQ Phase 2          (위 흐름과 독립적)
[7] WBXML                     (큰 작업, 1~4 끝나고)
```

### 각 항목 상세

#### 1. DevInfo 사전 교환 🔴 아직 작업 안됨
- **파일**: `SyncMLMessageService.handleMessage`, `SyncMLXmlUtil.parseSyncBody`, `Device` 엔티티
- **컬럼 추가**: `device.max_msg_size`, `max_obj_size`, `support_large_obj`, `manufacturer`, `dm_client_version`
- **로직**: 인증 성공 직후 첫 응답에서 `Get ./DevInfo` 발행 → Results 받으면 Device 갱신
- **참조**: [ARCHITECTURE.md 5.0.3](./ARCHITECTURE.md#503-devinfo-사전-교환-표준-정합성)

#### 2. 파일 사이징 + 패키지 메타 🔴 아직 작업 안됨
- **파일**: `UpdateJob` 엔티티, 패키지 업로드 API (신규)
- **컬럼 추가**: `update_job.pkg_size`, `pkg_sha256`, `pkg_signature`, `signature_algorithm`, `signing_cert_chain`
- **로직**: 패키지 업로드 시 sha256 계산, 사이즈 측정, 메타 저장
- **참조**: [ARCHITECTURE.md 5.7, 5.9](./ARCHITECTURE.md#57-메시지-사이징--moredata-청킹)

#### 3. MoreData 청킹 🔴 아직 작업 안됨
- **파일**: `Command.Item`, `Result.Item` DTO + `SyncMLXmlUtil` + `SyncMLMessageService` + `SyncSession`
- **DTO 변경**: `boolean moreData` 필드 추가
- **로직**: 응답 직렬화 직전 byte 측정 → device.maxMsgSize 초과 시 Item 분할 + `<MoreData/>` 부착. 수신 시 같은 CmdID로 reassembly buffer에 누적.
- **참조**: [ARCHITECTURE.md 5.7](./ARCHITECTURE.md#57-메시지-사이징--moredata-청킹)

#### 4. PKI 패키지 검증 흐름 🔴 아직 작업 안됨
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

