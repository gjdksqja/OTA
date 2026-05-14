# 도입 검토 문서: 하네스 · 오케스트라 · 테스트 자동화 · 보고 체계

> **목적**: 지금 바로 코딩할 항목과, 아직 문서/운영 개념으로만 둘 항목을 구분한다.  
> **핵심 결론**: 이 프로젝트에는 **가벼운 SyncML 테스트 하네스 + 자동 테스트 + Markdown/JSON 보고 체계**는 도입 가치가 높다. 반면 **오케스트레이션은 Docker Compose 범위까지만** 두고, Kubernetes/복잡한 워크플로 엔진은 지금 단계에서 제외한다.

---

## 1. 한 줄 판단

| 항목 | 도입 판단 | 이유 |
|------|-----------|------|
| 하네스 | ✅ 도입 권장 | SyncML 세션은 단계/상태/메시지 연속성이 중요해서 수동 curl 만으로 검증하기 어렵다. |
| 오케스트라 범위 | 🟡 제한 도입 | 로컬 Docker Compose 까지는 좋지만, Kubernetes/Argo 같은 운영형 오케스트라는 과하다. |
| 테스트 자동 개선 | ✅ 도입 권장 | DevInfo, Cred, MoreData, Status 흐름은 회귀 버그가 생기기 쉬워 자동화 가치가 높다. |
| 보고 체계 | ✅ 가볍게 도입 | 포트폴리오/면접에서 “흐름이 실제로 검증된다”를 보여주기 좋다. |

---

## 2. 용어 정리

### 2.1 하네스(Harness)

이 프로젝트에서 하네스는 **SyncML 세션을 자동으로 재현하고 검증하는 실행기**다.

즉, 사람이 매번 다음을 수동으로 하지 않게 만든다.

1. VIN 하나를 선택한다.
2. 작업을 생성한다.
3. 단말 시뮬레이터 또는 테스트 클라이언트가 SyncML 메시지를 보낸다.
4. 서버 응답 XML을 파싱한다.
5. 세션 단계, Job 상태, EventLog, Device 상태를 검증한다.
6. 결과를 보고서로 남긴다.

하네스는 운영 기능이 아니다. **테스트/데모/검증용 도구**다.

### 2.2 오케스트라(Orchestration)

여기서 오케스트라는 여러 구성 요소를 어떤 순서와 범위로 띄우고 제어할지의 문제다.

예:

```text
[PostgreSQL] → [Spring Boot Backend] → [Device Simulator 3대] → [테스트 시나리오 실행]
```

다만 이 프로젝트는 포트폴리오/학습용이므로 범위를 과하게 잡으면 안 된다.

### 2.3 테스트 자동 개선

테스트 자동 개선은 단순히 테스트 개수를 늘리는 것이 아니라, **SyncML 흐름의 깨지기 쉬운 규칙을 자동으로 확인하는 것**이다.

중요한 대상:

- Cred Basic / MD5 + Nonce 인증
- SessionID 연속성
- DevInfo 사전 교환
- FUMO 단계 전이
- MoreData 청킹 / reassembly
- 실패 Status 처리
- Job 상태 전이

### 2.4 보고 체계

보고 체계는 테스트 실행 결과를 사람이 보기 좋게 정리하는 방식이다.

추천 산출물:

```text
build/reports/syncml-harness/
├── summary.json
├── report.md
└── scenarios/
    ├── normal-update.md
    ├── auth-fail.md
    └── moredata.md
```

---

## 3. 현재 프로젝트에 맞는 권장 방향

### 3.1 추천 구조

```text
[JUnit / Harness Runner]
        │
        ├─ XML fixture 로 SyncML 요청 생성
        ├─ SyncMLController 또는 SyncMLMessageService 호출
        ├─ DB 상태 검증 (H2)
        ├─ 응답 XML 검증
        └─ Markdown/JSON 리포트 생성
```

처음부터 Docker, RabbitMQ, Vue, Django 를 모두 묶은 E2E 하네스를 만들 필요는 없다.

**먼저 Spring Boot 내부 테스트로 빠르게 돌 수 있는 하네스**가 좋다.

---

## 4. 하네스 도입 검토

### 4.1 도입하면 좋은 이유

SyncML은 REST API처럼 단발 요청으로 끝나지 않는다.

- `SessionID` 가 이어져야 한다.
- `MsgID`, `CmdID`, `CmdRef` 가 맞아야 한다.
- 서버가 어떤 단계에서 어떤 명령을 내려야 하는지 정해져 있다.
- 단말이 Status/Results 를 보내면 서버 상태가 바뀌어야 한다.

따라서 하네스가 있으면 다음을 자동으로 검증할 수 있다.

| 검증 대상 | 예시 |
|-----------|------|
| 인증 | Cred 없으면 401, MD5 성공 시 212 |
| 세션 | 같은 SessionID 로 step 이 이어지는지 |
| DevInfo | `./DevInfo/Ext/MaxMsgSize` 가 device 컬럼에 저장되는지 |
| MoreData | `<MoreData/>` 청크가 합쳐지는지 |
| FUMO | QUEUED → ASSIGNED → DOWNLOADING → INSTALLING → SUCCESS |
| 로그 | 주요 이벤트가 EventLog 에 남는지 |

### 4.2 최소 구현 범위

처음 하네스는 아래 정도면 충분하다.

```text
backend/src/test/java/com/syncml/server/harness/
├── SyncMLHarnessRunner.java
├── SyncMLScenario.java
├── SyncMLScenarioResult.java
└── SyncMLReportWriter.java

backend/src/test/resources/syncml/
├── auth-md5-init.xml
├── devinfo-replace.xml
├── version-results.xml
├── download-status.xml
└── install-status.xml
```

#### 최소 시나리오 5개

| 시나리오 | 목적 |
|----------|------|
| `normal-update` | 정상 FUMO 업데이트 성공 |
| `auth-required` | Cred 없음 → 401 + Chal 확인 |
| `auth-fail` | 잘못된 Cred → failCount 증가 |
| `devinfo-client-push` | 첫 메시지에 DevInfo Replace 동봉 |
| `moredata-reassembly` | Results Item 청크 합치기 |

### 4.3 도입하지 말아야 할 과한 범위

아래는 지금 단계에서 과하다.

- 실제 차량/외부 네트워크 수준의 복잡한 단말 에뮬레이터
- 브라우저 UI까지 포함한 완전 E2E 자동화
- Kubernetes Job 기반 테스트 러너
- 모든 SyncML 표준 케이스를 다 검증하는 범용 conformance suite
- 부하 테스트/성능 테스트 중심의 대형 하네스

### 4.4 도입 판단

**추천: 도입.**  
단, “테스트용 미니 하네스”로 시작해야 한다.

---

## 5. 오케스트라 범위 검토

### 5.1 이 프로젝트의 오케스트라 단계

| 단계 | 범위 | 도입 판단 |
|------|------|-----------|
| Level 0 | JUnit + H2, JVM 내부 테스트 | ✅ 지금 도입 |
| Level 1 | Spring Boot + 내장 시뮬레이터 | ✅ 현재 구조 유지 |
| Level 2 | Docker Compose: backend + db + rabbitmq + simulator | 🟡 Phase 2 이후 |
| Level 3 | Docker Compose scale 로 simulator 여러 대 | 🟡 데모용으로만 |
| Level 4 | Kubernetes / Helm / Argo Workflow | ❌ 현재 제외 |

### 5.2 추천 범위

현재는 아래까지만 목표로 둔다.

```text
개발/테스트:
JUnit + H2 + Harness

로컬 데모:
Spring Boot bootRun + SimulatorManager

확장 데모:
Docker Compose + PostgreSQL + RabbitMQ + simulator containers
```

### 5.3 왜 Kubernetes는 제외하는가

- 현재 단말 수는 3대 수준의 데모다.
- Kubernetes 는 학습 포인트가 SyncML/RabbitMQ 에서 인프라 운영으로 흐려진다.
- CI/CD, secret, ingress, readiness/liveness 까지 들어가면 문서와 코드 복잡도가 커진다.
- 포트폴리오 설명에서는 Docker Compose 만으로도 네트워크 분리와 컨테이너 구성 경험을 충분히 보여줄 수 있다.

### 5.4 Docker Compose 까지는 왜 좋은가

- PostgreSQL, RabbitMQ, Backend, Simulator 를 분리해서 보여줄 수 있다.
- `public-net`, `private-net` 구조를 설명하기 좋다.
- RabbitMQ Phase 2 의 역할이 명확해진다.
- “DB 큐 → 브로커 기반 작업 분배” 확장 과정을 시연할 수 있다.

### 5.5 도입 판단

**추천: 제한 도입.**  
JUnit/H2 는 바로, Docker Compose 는 Phase 2 부터. Kubernetes 는 제외.

---

## 6. 테스트 자동 개선 검토

### 6.1 테스트 피라미드

```text
        [수동 데모 / UI 확인]
              ▲
        [Docker Compose Smoke]
              ▲
        [Spring Boot Integration]
              ▲
        [Unit Tests]
```

### 6.2 우선순위

| 우선순위 | 테스트 | 대상 |
|----------|--------|------|
| 1 | Unit | Auth, XML parse/generate, MessageChunker, ChunkReassembly |
| 2 | Integration | SyncMLController/SyncMLMessageService + H2 |
| 3 | Scenario Harness | 정상 업데이트, 인증 실패, DevInfo, MoreData |
| 4 | Docker Smoke | compose 기동 후 health/API 확인 |
| 5 | UI E2E | Vue 생긴 뒤 Playwright 등 검토 |

### 6.3 지금 바로 필요한 테스트

#### A. XML round-trip 테스트

목표:

- `<MoreData/>` 가 파싱된다.
- `moreData=true` 면 XML 생성 시 `<MoreData/>` 가 나온다.
- `Cred`, `Status`, `Results`, `Replace` 구조가 깨지지 않는다.

#### B. DevInfo 매핑 테스트

목표:

- `./DevInfo/Man` → `Device.manufacturer`
- `./DevInfo/DmV` → `Device.dmClientVersion`
- `./DevInfo/Ext/MaxMsgSize` → `Device.maxMsgSize`
- `./DevInfo/Ext/MaxObjSize` → `Device.maxObjSize`, `supportLargeObj=true`

#### C. MoreData reassembly 테스트

목표:

```text
chunk1 moreData=true
chunk2 moreData=false
→ complete data 반환
```

#### D. FUMO 정상 흐름 테스트

목표:

```text
QUEUED
→ ASSIGNED
→ DOWNLOADING
→ INSTALLING
→ SUCCESS
```

### 6.4 자동화 범위에서 제외할 것

지금은 아래를 자동화하지 않는다.

- 대규모 부하 테스트
- 실제 파일 다운로드 Range 테스트
- PKI 실제 인증서 체인 검증 테스트
- WBXML conformance 전체 테스트
- Vue UI 자동 클릭 테스트

이들은 해당 기능이 구현된 뒤 도입한다.

### 6.5 도입 판단

**추천: 도입.**  
다음 작업으로는 #5 Status 202 보다 먼저 “테스트 자동화 뼈대”를 잡아도 좋다.  
이미 DevInfo/MoreData 를 건드렸기 때문에 회귀 테스트 가치가 높다.

---

## 7. 보고 체계 검토

### 7.1 왜 필요한가

보고 체계는 단순 로그와 다르다.

로그는 개발자가 디버깅하는 용도이고, 보고서는 “이번 시나리오가 성공했는지”를 보여주는 용도다.

포트폴리오에서 강한 장면:

```text
Scenario: normal-update
VIN: VIN-0001
Session: 4f07...
Result: PASS
Steps:
  ✅ Alert 1201 received
  ✅ Auth accepted 212
  ✅ DevInfo saved
  ✅ Get SwV sent
  ✅ PkgURL replaced
  ✅ Exec Download sent
  ✅ Download Status 200
  ✅ Exec Install sent
  ✅ Install Status 200
  ✅ Job SUCCESS
```

### 7.2 최소 보고서 형식

#### `summary.json`

```json
{
  "runId": "2026-05-13T21-30-00",
  "total": 5,
  "passed": 5,
  "failed": 0,
  "durationMs": 1240,
  "scenarios": [
    {
      "name": "normal-update",
      "vin": "VIN-0001",
      "result": "PASS",
      "durationMs": 320
    }
  ]
}
```

#### `report.md`

```markdown
# SyncML Harness Report

| Scenario | VIN | Result | Duration |
|----------|-----|--------|----------|
| normal-update | VIN-0001 | PASS | 320ms |
```

### 7.3 보고서에 담을 항목

| 항목 | 이유 |
|------|------|
| runId | 실행 추적 |
| scenario name | 어떤 검증인지 식별 |
| VIN | 단말 기준 추적 |
| sessionId | SyncML 연속성 확인 |
| jobId | 작업 상태와 연결 |
| expected steps | 기대 흐름 |
| actual steps | 실제 흐름 |
| final job status | 성공/실패 판단 |
| key event logs | 디버깅 근거 |
| duration | 느려진 테스트 감지 |

### 7.4 도입 판단

**추천: 가볍게 도입.**  
처음에는 JSON + Markdown 만 만든다. HTML 리포트, 대시보드, Allure 같은 도구는 과하다.

---

## 8. 추천 도입 순서

### Phase A — 바로 도입해도 좋은 것

| 순서 | 작업 | 산출물 |
|------|------|--------|
| 1 | Unit Test 추가 | `SyncMLXmlUtilTest`, `MessageChunkerTest`, `ChunkReassemblyServiceTest` |
| 2 | Harness 기본 모델 | `SyncMLScenario`, `SyncMLScenarioResult` |
| 3 | 정상 업데이트 시나리오 | `normal-update` |
| 4 | Markdown/JSON 리포트 | `build/reports/syncml-harness/*` |

### Phase B — #5 Status 202 와 함께 하면 좋은 것

| 순서 | 작업 | 산출물 |
|------|------|--------|
| 1 | Status 202 진행률 케이스 | `download-progress-202` |
| 2 | Generic Alert 1226 케이스 | `alert-1226-progress`, `alert-1226-pki-failed` |
| 3 | 실패/재시도 시나리오 | `install-fail-retry` |

### Phase C — RabbitMQ Phase 2 이후

| 순서 | 작업 | 산출물 |
|------|------|--------|
| 1 | Docker Compose smoke test | `compose-smoke` |
| 2 | RabbitMQ publish/consume 검증 | `rabbitmq-wakeup` |
| 3 | 시뮬레이터 다중 VIN 검증 | `multi-device-routing` |

---

## 9. 도입/비도입 판단 기준

### 도입하면 좋은 신호

- SyncML 로직을 수정할 때마다 기존 흐름이 깨질까 불안하다.
- XML 샘플을 매번 수동으로 만들고 있다.
- “정상 업데이트가 실제로 돈다”를 보여주고 싶다.
- DevInfo/MoreData/WBXML/PKI 처럼 표준 정합성 작업이 늘어난다.
- GitHub 에 올릴 때 테스트 결과를 같이 보여주고 싶다.

### 아직 미뤄도 되는 신호

- 서버가 아직 빌드/실행 안정화되지 않았다.
- JDK/Gradle 환경부터 정리해야 한다.
- SyncML 기본 흐름이 자주 바뀌고 있다.
- Vue/Django/RabbitMQ 를 먼저 보여주는 것이 더 급하다.

### 이 프로젝트 기준 최종 판단

```text
하네스: 도입 권장
오케스트라: Docker Compose 까지만, Kubernetes 제외
테스트 자동 개선: 도입 권장
보고 체계: Markdown/JSON 수준으로 도입 권장
```

---

## 10. 실제 다음 작업 후보

### 후보 1 — 테스트 안정화 먼저

추천도: ⭐⭐⭐⭐⭐

```text
1. Java 17 / Gradle 실행 환경 정리
2. compileJava 통과
3. MessageChunkerTest 추가
4. ChunkReassemblyServiceTest 추가
5. SyncMLXmlUtil MoreData round-trip 테스트 추가
```

장점:

- 현재 DevInfo/MoreData 변경에 대한 회귀 방지
- 범위가 작고 바로 성과가 보임

### 후보 2 — 하네스 MVP

추천도: ⭐⭐⭐⭐

```text
1. SyncMLScenario 모델 생성
2. normal-update 시나리오 하나 작성
3. 실행 결과를 report.md 로 저장
```

장점:

- 포트폴리오 데모에 강함
- “프로토콜 흐름이 자동 검증된다” 설명 가능

단점:

- 테스트 기반이 없으면 하네스가 금방 지저분해질 수 있음

### 후보 3 — Status 202 + Alert 1226 먼저

추천도: ⭐⭐⭐

장점:

- SyncML 표준 정합성 진전
- 진행률/실패 보고가 더 현실적이 됨

단점:

- 테스트 없이 구현하면 회귀 위험이 큼

---

## 11. 결론

지금 단계에서 가장 좋은 선택은 다음이다.

```text
1순위: 테스트 자동 개선
2순위: 가벼운 하네스 MVP
3순위: Markdown/JSON 보고 체계
4순위: Docker Compose 오케스트라 확장
```

즉, **하네스와 보고 체계는 도입 가치가 있고**, 오케스트라는 **Docker Compose까지만 제한**하는 것이 좋다.

다음 작업으로 바로 들어간다면 다음 순서를 추천한다.

```text
[테스트 자동화 뼈대]
  → [MoreData/DevInfo 회귀 테스트]
  → [normal-update 하네스]
  → [report.md + summary.json]
  → [Status 202 / Alert 1226]
```

