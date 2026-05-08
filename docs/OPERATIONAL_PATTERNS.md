# 운영 패턴 (이 프로젝트 범위 외, 알아둘 가치)

> 실제 OTA 운영 환경에서 표준적으로 쓰이지만, 이 프로젝트(시뮬레이터 데모/포트폴리오)에서는 **구현하지 않는** 패턴 모음.
> 면접/설계 토론에서 "왜 안 했나" 또는 "운영하면 어떻게 할 거냐" 답변 근거.

---

## 1. MQTT 게이트웨이 패턴 (실제 OTA 표준)

### 1.1 왜 이 프로젝트에 안 넣었나

- 시뮬레이터는 같은 Docker 네트워크 안. MQTT 의 비대칭 통신/저전력 최적화/방화벽 우회가 의미 없음.
- RabbitMQ Phase 2 가 사실상 같은 역할 (서버 → 워커 알림). 시뮬레이터 환경에서는 RabbitMQ 로 충분.
- MQTT 까지 넣으면 데모 복잡도가 폭발 (브로커 추가, TLS 설정, Last Will, QoS 튜닝 등).

### 1.2 실제 운영에서는 왜 필요한가

```
[차량 TCU] ── MQTT keep-alive (저전력, 방화벽 뒤) ──► [MQTT Broker]
                                                         │
                                                작업 발생 │
                                                         ▼
[차량 TCU] ◄── MQTT publish "wakeup" ──────────── [OTA Backend]
   │
   │ wakeup 받고 SyncML/HTTPS 세션 시작
   ▼
[차량 TCU] ── HTTPS POST /syncml ─────────────────► [SyncML Backend]
   │       (큰 페이로드, 신뢰성)
   ▼
[차량 TCU] ── HTTPS GET /pkg/firmware.bin ─────────► [CDN]
```

**역할 분리:**

| 채널 | 용도 | 특성 |
|------|------|------|
| **MQTT** | "깨우기" 알림만 | 경량, 지속 연결, Push |
| **SyncML/HTTPS** | 본 명령 교환 | 신뢰성, 인증 강함, 큰 페이로드 |
| **CDN/HTTPS** | 펌웨어 다운로드 | 대용량, 캐싱, Range 지원 |
| **RabbitMQ (백엔드 내부)** | 백엔드 → MQTT publisher 워커 분배 | 작업 큐 |

### 1.3 이 프로젝트에 적용하려면

- Phase 5 정도에 옵션으로 추가:
  - Mosquitto 컨테이너
  - 시뮬레이터에 Paho MQTT 클라이언트 (`vehicle/{vin}/wakeup` 토픽 구독)
  - 백엔드: 작업 생성 시 `vehicle/{vin}/wakeup` publish
  - 시뮬레이터: wakeup 받으면 SyncML POST 시작 (현재 polling 대신)

> **결론**: "안 했다" 가 답이 아니라 "내부 시뮬레이터 환경엔 RabbitMQ 가 정합 / 외부 차량이라면 MQTT 가 표준" 으로 답.

---

## 2. Campaign / Wave / 카나리 롤아웃

### 2.1 왜 이 프로젝트에 안 넣었나

- 단말 3대 짜리 데모. Campaign 개념이 의미 없음.
- 운영툴 (Django) 영역인데 운영툴 자체가 Phase 5.

### 2.2 실제 운영에서의 의미

10만 대 차량에 한 번에 배포하면 안 되는 이유:
- 새 펌웨어가 뭔가 깨졌을 때 전체가 동시에 망가짐
- 다운로드 트래픽 폭주 (CDN 비용 / 통신망 부하)
- 사고 발생 시 안전 이슈 (브레이크 ECU 같은 건 단계적 검증 필수)

### 2.3 표준 패턴 (단계적 배포)

```
[Wave 1: Canary 1%]
   ├─ 100대 배포
   ├─ 24h 관찰 (실패율, 사용자 보고, 차량 텔레메트리)
   ├─ 실패율 > 5% → 자동 롤백 + 전체 일시중단
   └─ OK → Wave 2 진행
        │
[Wave 2: Early 10%]
   ├─ 1000대 배포
   ├─ 48h 관찰
   └─ OK → Wave 3
        │
[Wave 3: Broad 50%]
   ├─ 50000대 배포
   └─ 72h 관찰
        │
[Wave 4: Full 100%]
   └─ 잔여 차량 전체
```

### 2.4 데이터 모델 (참고)

```sql
CREATE TABLE campaign (
    campaign_id SERIAL PRIMARY KEY,
    name VARCHAR(200),
    target_version VARCHAR(50),
    status VARCHAR(20),  -- DRAFT, RUNNING, PAUSED, COMPLETED, ABORTED
    created_at TIMESTAMP
);

CREATE TABLE campaign_wave (
    wave_id SERIAL PRIMARY KEY,
    campaign_id INT REFERENCES campaign(campaign_id),
    sequence INT,            -- 1, 2, 3, 4
    target_percentage INT,   -- 1, 10, 50, 100
    target_count INT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    failure_threshold_pct INT DEFAULT 5,
    status VARCHAR(20)
);

-- update_job 에 campaign_wave_id FK 추가
```

### 2.5 설계 토론 포인트

- **선정 기준**: 차종/지역/주행거리/마지막 정비일/오너 동의 여부
- **롤백 전략**: 실패율 임계값 자동 / 운영자 수동 / 안전 critical 즉시
- **재시도**: 실패 차량은 다음 Wave에서 자동 재시도 vs 격리

---

## 3. Resume / Range 다운로드

### 3.1 왜 이 프로젝트에 안 넣었나

- 시뮬레이터의 다운로드는 `Thread.sleep(2~5초)` 시뮬레이션. 실제 byte 다운로드 안 함.
- Resume 정책이 의미를 갖는 건 실제 패키지 파일이 있을 때.

### 3.2 실제 운영에서의 의미

- 250MB 펌웨어 받다가 200MB 지점에서 끊기면 처음부터 다시 받기 = 통신비 낭비 + 사용자 분노
- 모바일/차량 환경은 네트워크 끊김이 일상

### 3.3 표준 패턴

```
[다운로드 도중 끊김]
   ├─ 단말: 받은 byte 수 + sha256 (부분) 기록
   ├─ 단말 → 서버: Alert 1226 + Item Data="{bytesReceived: 209715200}"
   │
[재개 시]
   ├─ 단말 → CDN: HTTP GET + Range: bytes=209715200-
   │              + If-Match: <ETag> (혹시 패키지 교체됐는지 체크)
   ├─ CDN: 206 Partial Content
   └─ 단말: 이어 받기 → 전체 sha256 검증
```

### 3.4 SyncML 표준 매핑

- Alert 1223: Session Resume — 끊긴 세션 이어가기
- Alert 1224: Next Message — 청크 단위 진행
- Alert 1226: Generic Alert — 진행률/상태 비정형 보고

---

## 4. 동시 세션 잠금 정책

### 4.1 왜 이 프로젝트에서 가볍게 처리하나

- 시뮬레이터는 각 VIN 1개 스레드로 동작. 동시 두 세션 시작이 발생할 일이 거의 없음.
- 표준 단말은 보통 자체적으로 한 번에 하나의 SyncML 세션만 유지.

### 4.2 실제 운영에서의 이슈

- 단말이 네트워크 일시 끊김 후 재연결하면서 새 세션 시작 → 서버에 두 활성 세션 공존 가능
- 서로 다른 IP 에서 같은 VIN 으로 접속 (도난/스푸핑 가능성)
- Race condition: 두 세션이 같은 Job 을 동시 ASSIGNED 시도

### 4.3 해결 옵션

| 정책 | 장단점 |
|------|--------|
| **DB unique constraint** (`vin + status=ACTIVE`) | 단순, 강함. 두 번째 세션 즉시 거부 |
| **낙관적 잠금** (`@Version` on SyncSession) | 충돌 시 retry 가능. 트래픽 적은 편 |
| **분산 락 (Redis)** | 멀티 인스턴스 환경 필수 |
| **이전 세션 강제 만료 + 새 세션 발급** | 단말 친화적 (재연결 잘 됨). 도난 케이스 노출 |

### 4.4 권장 (운영용)

- 같은 VIN 의 새 세션 요청 시:
  1. 활성 세션 있으면 → `current_step` 체크
  2. step ≤ 1 (DevInfo 교환 전): 기존 세션 EXPIRED 처리, 새 세션 발급
  3. step ≥ 2 (작업 진행 중): 새 세션 거부 (Status 503), 기존 작업 보호
  4. 기존 세션 last_activity 가 5분 초과: 자동 EXPIRED 후 새 세션

---

## 5. mTLS + 디바이스 인증서

### 5.1 왜 이 프로젝트에 안 넣었나

- 데모 단순화. Cred (Basic/MD5) 만으로 인증 흐름은 충분히 보여줌.
- 인증서 발급/회전/취소는 별도 PKI 인프라 필요 (CA, OCSP, CRL 배포).

### 5.2 실제 운영에서는

```
[차량 출고 시]
   ├─ 제조사 PKI 가 차량별 디바이스 인증서 발급 (개인키는 ECU TPM 안에)
   └─ 인증서 시리얼 + 공개키를 백엔드에 등록

[OTA 접속 시]
   ├─ TLS 핸드셰이크: 차량이 클라이언트 인증서 제시
   ├─ 백엔드: 인증서 체인 + 시리얼 + 폐기 여부 검증
   ├─ 검증 통과 시에만 SyncML 처리 진입
   └─ Cred 인증은 추가 보호층으로 격하 (또는 생략)
```

**장점:**
- 비밀번호 기반 인증보다 훨씬 강력
- 차량 도난 시 인증서 폐기로 즉시 차단
- ECU TPM 안에 개인키 → 추출 어려움

**단점:**
- PKI 인프라 운영 비용
- 인증서 만료/회전 정책 필요

---

## 6. Telemetry 확장 시 파티셔닝 / 아카이빙 (현재 OTA에는 불필요)

### 6.1 왜 현재 OTA 프로젝트에 안 넣었나

- OTA 도메인 데이터 (`Device`, `SyncSession`, `UpdateJob`, `SyncMessage`) 누적량은 차량 1만 대 가정 시 1년에 **1억 건 미만**.
- 일반 PostgreSQL 인덱스로 충분. 파티션 도입 시 운영 복잡도(파티션 생성·머지·제약·통계 동기화)가 더 큼.
- "있으면 좋다"가 아니라 **"데이터 규모가 부르면 도입한다"**가 정답. 도입 트리거는 §6.7 참조.

### 6.2 Telemetry 확장 시나리오 (가정)

OTA 위에 차량 운행 데이터 수집(속도/RPM/위치/CAN 등)을 얹는 경우.

| 항목 | 추정 |
|------|------|
| 차량 수 | 10,000 |
| 패킷 주기 | 1 Hz (초당 1건) |
| 1일 누적 | 10K × 86,400 = **약 8.6억 건** |
| 1년 누적 | **3,000억 건+** |
| 1건 평균 크기 | 200~500 byte |
| 1년 raw 데이터 | **60~150 TB** |

→ 단일 테이블로는 인덱스 빌드·통계 갱신·VACUUM이 모두 깨짐. **파티셔닝 + 콜드 스토리지 archive 필수.**

### 6.3 표준 아키텍처 (Telemetry + OTA 공존)

```
[차량 TCU] ──MQTT──► [MQTT Broker]  ──bridge──► [Kafka]  ──consumer──► [TimescaleDB / Partition PG]
   │                  (Mosquitto/                (재처리·다중                │
   │                   EMQX/HiveMQ)              컨슈머·고처리율)             ├─► hot  (SSD, 인덱스 풀)
   │                                                                         ├─► warm (압축, 인덱스 축소)
   │                                                                         └─► cold (S3 Parquet, Athena 쿼리)
   │
   └── HTTPS POST /syncml ──► [SyncML Backend] ──► [RabbitMQ] ──► [OTA 워커]
       (OTA 양방향 신뢰성)
```

**채널·큐 역할 분리**:

| 채널/큐 | 용도 | 특성 |
|---------|------|------|
| **MQTT** | 차량 ↔ 클라우드 게이트웨이 | 저전력, 지속 연결, push |
| **Kafka** | Telemetry 본류 (수집·재처리) | 보존·다중 컨슈머·고처리량 |
| **RabbitMQ** | OTA 작업 분배 (백엔드 내부) | 작업 큐 정합 |
| **SyncML/HTTPS** | OTA 본 명령 교환 | 큰 페이로드·신뢰성 |

> **요지**: Telemetry는 Kafka가 사실상 표준. RabbitMQ로도 받을 수 있지만 보존/replay/다중 컨슈머에서 Kafka 우위. 우리 OTA의 RabbitMQ 사용은 "작업 분배" 자리라 정합.

### 6.4 파티션 전략

#### 시간 기반 (일/월) — 가장 일반적

```sql
CREATE TABLE telemetry (
    vin         VARCHAR(17)  NOT NULL,
    sampled_at  TIMESTAMPTZ  NOT NULL,
    speed_kmh   REAL,
    rpm         INT,
    -- ...
    PRIMARY KEY (vin, sampled_at)
) PARTITION BY RANGE (sampled_at);

CREATE TABLE telemetry_2026_01 PARTITION OF telemetry
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
-- pg_partman 으로 월 단위 자동 생성 권장
```

- 장점: 시간 범위 쿼리 효율 / 오래된 파티션 통째로 detach·archive·drop 용이.
- 단점: 단일 차량 전체 이력 쿼리는 모든 파티션 스캔.

#### 시간 + VIN 해시 (초대규모)

```sql
CREATE TABLE telemetry_2026_01 PARTITION OF telemetry
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01')
    PARTITION BY HASH (vin);

CREATE TABLE telemetry_2026_01_h0 PARTITION OF telemetry_2026_01
    FOR VALUES WITH (modulus 16, remainder 0);
-- h0 ~ h15
```

- 장점: 시간+VIN 양쪽 쿼리 모두 효율.
- 단점: 파티션 수 폭발 (월 × 해시 × 연 → 수백 개).

#### TimescaleDB (PostgreSQL 확장) — 시계열이라면 1순위

- `SELECT create_hypertable('telemetry', 'sampled_at')` 한 줄로 자동 파티션.
- 컬럼 단위 압축 / retention 정책 / continuous aggregate 내장.
- **시계열이면 원시 PostgreSQL 파티션보다 TimescaleDB가 정답.**

### 6.5 아카이빙 전략 (Hot / Warm / Cold)

```
[수집] ──► [Hot: 최근 7일]      ─7일 후→ [Warm: 90일]        ─90일 후→ [Cold: S3]
            SSD, 인덱스 풀                  HDD, 압축, 인덱스 축소         Parquet
            대시보드 응답 50ms              분석 응답 500ms                Athena/Trino 쿼리
            (실시간 대시보드)               (운영 분석/리포트)             (법적 보존·감사)
```

**자동화 예시**:
1. 매일 자정: 7일 전 파티션 detach → warm 테이블스페이스로 attach + 압축 적용
2. 매일 자정: 90일 전 파티션 → `COPY ... TO PROGRAM 'aws s3 cp ... -'` 후 drop
3. 검색: hot/warm은 PostgreSQL, cold는 Athena/Trino로 별도 경로

### 6.6 현재 OTA에서 "당장" 적용 가능한 부분

**굳이 미리 넣는다면** 가장 큰 로그 테이블 (`SyncMessage`)에만 월별 파티션을 적용해두는 게 가장 ROI 높음.

```sql
-- 가상의 적용 예 (현재는 미적용)
CREATE TABLE sync_message (
    message_id   BIGSERIAL,
    session_id   BIGINT,
    direction    VARCHAR(8),       -- IN / OUT
    received_at  TIMESTAMPTZ NOT NULL,
    payload      TEXT,
    PRIMARY KEY (received_at, message_id)
) PARTITION BY RANGE (received_at);
```

- 1년 후 월 단위 archive/drop이 한 줄.
- **하지만 1억 건 미만 단계에서는 일반 인덱스로 충분 — 도입 시점은 "운영 1년 누적 후 측정해서 결정"이 원칙**.
- 결정 보류 사유는 `db/init.sql`에 주석으로만 표시 (구현체에는 없음).

### 6.7 도입 트리거

다음 중 **2개 이상** 해당 시 파티셔닝 도입 검토:

1. 단일 테이블 누적 **1억 건 초과**
2. 인덱스 빌드/재구성 **30분 이상**
3. retention(오래된 데이터 삭제)이 일상 운영 부담
4. 시간 범위 쿼리가 주요 액세스 패턴
5. Telemetry 등 **고주파 적재 도메인 추가**

> 트리거가 발동하기 전에는 파티션 도입 자체가 비용. 이 문서의 §6.4~6.5는 "필요해진 순간 즉시 적용 가능한 레퍼런스"로만 보존.

---

## 7. 면접 답변 템플릿

**Q: "왜 MQTT 안 썼어요? 자동차는 보통 MQTT 쓰지 않나요?"**

> 시뮬레이터 환경에서는 RabbitMQ 가 정합한 선택이었습니다.
> 시뮬레이터는 같은 Docker 네트워크에 있어서 MQTT 의 저전력/방화벽 우회 같은 장점이 의미 없고,
> 백엔드 내부 작업 분배 용도라면 RabbitMQ 가 더 자연스럽습니다.
> 다만 실제 차량 환경이라면 MQTT 게이트웨이가 SyncML 앞에 위치하는 게 표준이라는 건 인지하고 있고,
> [OPERATIONAL_PATTERNS.md](./OPERATIONAL_PATTERNS.md) 에 그 구조를 정리해뒀습니다.

**Q: "10만 대 차량에 어떻게 배포할 건가요?"**

> 단계적 롤아웃이 필수입니다. 1% 카나리 → 10% → 50% → 100% 로 Wave 를 나누고,
> 각 Wave 마다 실패율 임계치 (예: 5%) 와 관찰 기간 (24~72h) 을 둡니다.
> 이 프로젝트에서는 데모 규모상 구현하지 않았지만 Campaign/Wave 데이터 모델 설계는 정리해뒀습니다.

**Q: "다운로드 도중 끊기면 어떻게 하나요?"**

> 시뮬레이터에서는 단순 재시도 (최대 3회) 만 구현했지만,
> 실제 운영에서는 Range 요청으로 이어받기, sha256 부분 검증,
> SyncML Alert 1223 (Session Resume) / 1224 (Next Message) 매핑이 필요합니다.

---

**Q: "차량 패킷(텔레메트리) 같은 대용량 시계열 데이터는 어떻게 처리할 건가요?"**

> 현재 OTA 도메인은 1년 누적이 1억 건 미만이라 일반 인덱스만으로 충분해 파티셔닝을 도입하지 않았습니다.
> 단, 텔레메트리(차량당 1Hz 패킷)까지 확장하면 1일 8억 건 이상이라 시간 기반 파티션 + Hot/Warm/Cold 아카이빙이 필수가 됩니다.
> 큐 측면에서도 OTA는 작업 분배라 RabbitMQ가 정합하지만, 텔레메트리는 보존·재처리·다중 컨슈머 요구로 Kafka가 표준입니다.
> 도입 트리거(테이블 1억 건 초과, 인덱스 빌드 30분 초과 등)와 표준 파티션 DDL 예시는 [OPERATIONAL_PATTERNS.md §6](./OPERATIONAL_PATTERNS.md#6-telemetry-확장-시-파티셔닝--아카이빙-현재-ota에는-불필요) 에 정리해뒀습니다.

---

## 8. 문서 이력

| 날짜 | 내용 |
|------|------|
| 2026-04-27 | 초안 작성 — MQTT 게이트웨이, Campaign 롤아웃, Resume, 동시 세션, mTLS 패턴 정리 |
| 2026-04-30 | §6 Telemetry 확장 시 파티셔닝/아카이빙 추가. OTA 본 도메인엔 불필요하나 확장 시 표준 패턴(파티션 전략 3종, Hot/Warm/Cold archive, 도입 트리거) 정리. §7 면접 답변에 텔레메트리 Q&A 추가. |

---

**관련 문서:**
- [ARCHITECTURE.md](./ARCHITECTURE.md) — 전체 아키텍처 (구현 범위)
- [TODO.md](./TODO.md) — 다음 구현 작업
- [MESSAGING_COMPARISON.md](./MESSAGING_COMPARISON.md) — MQTT/RabbitMQ/Kafka 본질 비교
