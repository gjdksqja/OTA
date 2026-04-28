# SyncML OTA 시스템 — Claude 작업 가이드

> 이 파일은 프로젝트 전반 규칙만 담는다. 언어/프레임워크 특화 규칙은 추후 하위 디렉터리(`backend/`, `dashboard/`, `admin/`)의 `CLAUDE.md`로 분리한다.

## 한 줄 요약
SyncML(외부 단말 프로토콜) + DB 큐/RabbitMQ(내부 작업 분배) + Vue 대시보드 + Django 운영 API. 멀티 모듈 모노레포.

## 문서 참조 우선순위
판단 근거가 필요할 때 반드시 이 순서로 확인할 것. 추측 금지.

1. [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — 전체 아키텍처. 모든 설계 판단의 1순위 근거.
2. [docs/TODO.md](docs/TODO.md) — 작업 일지 + Phase 2.5 표준 준수 우선순위. 진행 중 작업의 정답.
3. [README.md](README.md) — 외부 공개용 요약. 정책 요약은 OK, 세부는 ARCHITECTURE.md 우선.
4. [docs/MESSAGING_COMPARISON.md](docs/MESSAGING_COMPARISON.md), [docs/OPERATIONAL_PATTERNS.md](docs/OPERATIONAL_PATTERNS.md) — 배경 지식.
5. [docs/AI_COLLABORATION.md](docs/AI_COLLABORATION.md) — AI 활용 방침·지식 계층 설계·검토 이력. 신규 도구/계층 도입 검토 시 먼저 확인.

## 핵심 불변 정책 (변경 시 사용자 확인 필수)

### 큐 책임 범위
큐(DB 큐 / RabbitMQ)는 **"업데이트 작업 지시 전달"에만** 사용. 상태 조회·로그·세션 관리는 DB 직접 처리.

### 외부 노출 정책
| 서비스 | 외부 노출 |
|--------|-----------|
| Vue Dashboard / Java SyncML API | ✅ public |
| Django Admin / DB / RabbitMQ | ⛔ private only |

Django/DB/RabbitMQ를 외부 포트에 바인딩하는 변경은 자동 수행 금지.

### 단말 시뮬레이터 규칙 (수정 시 사용자 확인)
| 항목 | 값 |
|------|-----|
| Poll 주기 | 5초 |
| VIN당 동시 작업 | 1개 |
| Heartbeat | 10초 |
| 재시도 | 최대 3회 (30초 대기) |

## 모듈 현황
| 디렉터리 | 역할 | 상태 |
|----------|------|------|
| [backend/](backend/) | Java Spring Boot SyncML 서버 | 구조 완성, Phase 2.5 진행 |
| [db/](db/) | 초기 스키마 (`init.sql`) | 사용 중 |
| [docs/](docs/) | 아키텍처·작업 일지 | 사용 중 |
| `dashboard/` | Vue 3 + TS 대시보드 | **미생성** (Phase 3) |
| `admin/` | Django 운영 API | **미생성** (Phase 5) |
| `device-simulator/` | 단말 시뮬레이터 | **미생성** |

> 미생성 모듈을 import/호출하는 코드를 추가하지 말 것. README의 디렉터리 트리는 **목표 구조**임.

## SyncML 표준 정합성 (Phase 2.5)
DevInfo 사전 교환·MoreData 청킹·PKI 검증·WBXML 등은 **현재 미구현**. 코드에 없다고 "필요 없다"고 단정하지 말 것 — TODO.md의 우선순위가 정답.

## 공통 작업 원칙
- **언어**: 사용자 응답·문서·커밋 메시지·코드 주석은 **한국어** 우선. 식별자는 영어.
- **DB 스키마 변경**: `db/init.sql` 수정 시 ARCHITECTURE.md 도메인 모델 섹션과 TODO.md 영향 범위를 같이 갱신.
- **새 의존성**: 추가 전 사용자 확인. 오픈소스 라이선스 확인.
- **Docker Compose 포트 변경**: 위 노출 정책 위반 여부부터 확인.

## 절대 금지
- 추측으로 SyncML 표준 동작 구현 (모르면 ARCHITECTURE.md 5장 확인 또는 사용자에게 질문)
- ARCHITECTURE.md / TODO.md 갱신 없이 도메인 모델·정책 변경
- 미생성 모듈을 import/호출하는 코드 추가
- 사용자 명시 승인 없는 파괴적 git 명령 (`--force`, `reset --hard`, `--no-verify` 등)