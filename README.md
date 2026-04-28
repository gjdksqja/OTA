# SyncML OTA 시스템

> SyncML 기반 OTA 시스템 + 내부 작업 큐(RabbitMQ 확장 가능) + 운영자 조회 분리

## 프로젝트 구조

```
TESTPRO/
├── docs/
│   └── ARCHITECTURE.md     # 상세 아키텍처 문서
├── backend/                # Java SyncML 서버 (Spring Boot)
├── dashboard/              # Vue 3 + TypeScript 대시보드
├── admin/                  # Django 운영 API
├── device-simulator/       # 단말 시뮬레이터
├── db/
│   └── init.sql           # DB 초기 스키마
├── docker-compose.yml      # 전체 컨테이너 구성
└── README.md
```

## 핵심 아키텍처

| 역할 | 기술 |
|------|------|
| 외부 단말 프로토콜 | SyncML (Alert/Status/Exec) |
| 내부 작업 분배 | DB 큐 → RabbitMQ |
| 프론트엔드 | Vue 3 + TypeScript |
| 운영 조회 | Django (private-net only) |
| 컨테이너 | Docker Compose |

## 핵심 정책 요약

### 큐 책임 범위
> 큐는 **"업데이트 작업 지시 전달"**에만 사용. 상태 조회/로그/세션 관리는 DB 직접 처리.

### 노출 정책
| 서비스 | 외부 노출 |
|--------|-----------|
| Vue Dashboard | ✅ public |
| Java SyncML API | ✅ public |
| Django Admin | ⛔ private only |
| DB / RabbitMQ | ⛔ private only |

### 단말 시뮬레이터 동작 규칙
| 항목 | 값 |
|------|-----|
| Poll 주기 | 5초 |
| VIN당 동시 작업 | 1개 |
| Heartbeat | 10초 |
| 재시도 | 최대 3회 (30초 대기) |

## 빠른 시작

```bash
# 전체 서비스 실행
docker-compose up -d

# 서비스 확인
# - Vue 대시보드: http://localhost:3000
# - SyncML API: http://localhost:8080
# - Django Admin: http://localhost:8000
# - RabbitMQ 관리: http://localhost:15672
```

## 개발 단계

- [x] Phase 1: 아키텍처 문서화
- [x] Phase 2: Java 백엔드 + DB 큐 (구조 완성, Java 설치 후 빌드 필요)
- [ ] **Phase 2.5: SyncML 표준 정합성 갭 메우기** 🔴 진행 예정
  - DevInfo 사전 교환, 파일 사이징, MoreData 청킹, PKI 패키지 검증
  - Status 202 분리, Generic Alert 1226, WBXML 인코딩
  - 상세: [TODO.md 표준 준수 우선순위](./docs/TODO.md#-우선순위-표준-준수-syncml-12-정합성)
- [ ] Phase 3: Vue 3 대시보드
- [ ] Phase 4: RabbitMQ 확장
- [ ] Phase 5: Django 운영 도구
- [ ] Phase 6: Docker 컨테이너화

## 상세 문서

| 문서 | 내용 |
|------|------|
| [ARCHITECTURE.md](./docs/ARCHITECTURE.md) | 전체 아키텍처 (구현 범위 + 표준 정합성 갭 표시) |
| [TODO.md](./docs/TODO.md) | 작업 일지 + 표준 준수 우선순위 |
| [MESSAGING_COMPARISON.md](./docs/MESSAGING_COMPARISON.md) | SyncML / MQTT / RabbitMQ / Kafka 본질 비교 |
| [OPERATIONAL_PATTERNS.md](./docs/OPERATIONAL_PATTERNS.md) | 알아두면 좋은 운영 패턴 (이 프로젝트 범위 외) |
| [VSCode_Java_Setup_Guide.md](./docs/VSCode_Java_Setup_Guide.md) | VS Code Java 개발 환경 셋팅 |
| [AI_COLLABORATION.md](./docs/AI_COLLABORATION.md) | AI(Claude Code) 활용 방침·지식 계층 설계·검토 이력 |

