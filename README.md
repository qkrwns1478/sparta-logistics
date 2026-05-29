# 스파르타 물류 — MSA 기반 물류 관리 및 배송 시스템

## 프로젝트 개요

전국 허브 네트워크를 기반으로 B2B 상품 주문, 재고 관리, 배송 경로 추적, AI 발송 시한 산출, Slack 알림까지 통합 관리하는 MSA 플랫폼입니다.

생산업체가 보유한 상품을 수령업체가 주문하면, 시스템은 상품 재고를 확인하고 주문과 동시에 배송 정보를 생성합니다. 이후 출발 허브에서 목적지 허브까지의 이동 경로를 관리하고, 최종적으로 수령업체까지 배송이 완료되도록 전체 물류 흐름을 추적합니다.

### 핵심 특징

- **B2B 주문 처리**: 업체 간 온라인 주문, 주문 생성 시 재고 자동 차감. 재고 부족 시 즉시 실패 처리
- **Saga 패턴 기반 분산 트랜잭션**: 주문 생성은 Choreography Saga(Kafka 이벤트 체이닝), 주문 취소는 Orchestration Saga(CancelOrderOrchestrator)로 데이터 정합성 보장
- **허브 기반 배송 경로 관리**: 허브 간 이동 경로를 사전 등록하고, 주문 생성 시 최적 경로로 배송 경로를 자동 구성
- **AI 발송 시한 산출**: Gemini 1.5 Flash API로 주문 정보·배송 경로·납기일을 분석해 최종 발송 시한을 계산하고 담당 허브 매니저에게 Slack 알림 발송
- **역할 기반 접근 제어**: MASTER / HUB_MANAGER / DELIVERY_MANAGER / COMPANY_MANAGER 4단계 권한 체계
- **Soft Delete**: 모든 엔티티를 물리 삭제 없이 `deleted_at`, `deleted_by` 필드로 관리

---

## 목차

1. [팀원 소개](#팀원-소개)
2. [서비스 구성](#서비스-구성)
3. [기술 스택](#기술-스택)
4. [시스템 아키텍처](#시스템-아키텍처)
5. [로컬 실행 방법](#로컬-실행-방법)
6. [API 문서](#api-문서)
7. [브랜치 및 커밋 전략](#브랜치-및-커밋-전략)
8. [관련 문서](#관련-문서)

---

## 팀원 소개

| 이름 | 담당 | 모듈                                                                  |
|---|---|---------------------------------------------------------------------|
| 김다은 | 인프라 & 사용자 | `discovery-server`, `config-server`, `api-gateway`, `user-service`  |
| 김승현 | 허브 | `hub-service`                                                       |
| 박준식 | 주문 | `order-service`                                                     |
| 이다혜 | 업체 & 상품 | `company-service`, `product-service`                                |
| 장하영 | 인프라 & Slack + AI | `discovery-server`, `config-server`, `api-gateway`, `slack-service` |
| 조아영 | 배송 | `delivery-service`                                                  |
 
---

## 서비스 구성

| 모듈 | 포트 | 역할 |
|---|---|---|
| `discovery-server` | 8761 | Eureka 서비스 디스커버리 |
| `config-server` | 8888 | Spring Cloud Config (중앙 설정 관리) |
| `api-gateway` | 8080 | 라우팅, JWT 검증, 헤더 전파 |
| `user-service` | 19091 | 회원가입/로그인/사용자 관리 |
| `hub-service` | 19092 | 허브 CRUD, 허브 간 경로, 허브 재고 관리 |
| `company-service` | 19093 | 업체(생산/수령) 관리 |
| `product-service` | 19094 | 상품 관리 및 재고 정보 |
| `order-service` | 19095 | 주문 생성/취소 (Saga Orchestrator 내장) |
| `delivery-service` | 19096 | 배송 생성, 경로 관리, 담당자 배정 |
| `slack-service` | 19097 | Gemini AI 발송 시한 산출 및 Slack 알림 |

공통 모듈:

| 모듈 | 역할 |
|---|---|
| `common` | 공통 예외(`BusinessException`, `ErrorCode`), 공통 응답 형식 |
| `arch-rules` | ArchUnit 기반 아키텍처 규칙 강제 |

---

## 기술 스택

| 구분 | 기술 |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.x, Spring Cloud |
| Security | Spring Security + JWT |
| ORM | JPA / Hibernate |
| DB | PostgreSQL 15 |
| Cache | Redis 7.2 |
| Message Broker | Apache Kafka (Confluent 7.6) |
| Service Discovery | Spring Cloud Eureka |
| API Gateway | Spring Cloud Gateway (WebFlux) |
| Service Communication | FeignClient (동기), Kafka (비동기) |
| AI | Google Gemini 1.5 Flash |
| Notification | Slack Bot API |
| 분산 추적 | Zipkin |
| 아키텍처 검증 | ArchUnit |
| 컨테이너 | Docker / Docker Compose |
| Build | Gradle 8.x (Multi-Module) |
| API 문서 | springdoc-openapi (Swagger UI) |

---

## 시스템 아키텍처

### 서비스 간 통신

```
Client
  └─▶ API Gateway (8080)          ← JWT 검증, X-User-Id / X-User-Role 헤더 주입
        ├─▶ user-service    (FeignClient / REST)
        ├─▶ hub-service     (FeignClient / REST)
        ├─▶ company-service (FeignClient / REST)
        ├─▶ product-service (FeignClient / REST)
        ├─▶ order-service   (FeignClient / REST + Kafka Producer)
        ├─▶ delivery-service(FeignClient / REST + Kafka Consumer/Producer)
        └─▶ slack-service   (Kafka Consumer/Producer)
```

### 주문 흐름 (Choreography Saga)

주문 생성은 Kafka 이벤트 체이닝으로 처리됩니다.

```
OrderService → [order.created]
  → HubService (재고 예약) → [stock.reserved]
    → DeliveryService (배송 생성) → [delivery.created]
      → OrderService (ACCEPTED 전이)
      → SlackService (AI 시한 산출) → [ai.deadline.calculated]
        → DeliveryService (finalDeadlineAt 저장) → [delivery.started]
          → HubService (실제 재고 차감)
```

### 주문 취소 (Orchestration Saga)

주문 취소는 `CancelOrderOrchestrator`(OrderService 내장)가 중앙 조율합니다.

```
OrderService(Orch.) → [cancel.delivery.command]
  → DeliveryService → [delivery.cancelled.ack]
    → OrderService(Orch.) → [restore.stock.command]
      → HubService → [stock.restored.ack]
        → OrderService(Orch.) → CANCELLED 확정
```

자세한 Kafka 토픽 목록, 단계별 처리 내용, 보상 트랜잭션은 [SAGA.md](./docs/SAGA.md)를 참고하세요.

### 인증 흐름

Gateway에서 JWT를 검증한 후 `X-User-Id`, `X-User-Role`, `X-User-HubId`, `X-User-CompanyId` 헤더를 다운스트림 서비스로 전달합니다. 각 서비스는 `HeaderAuthFilter`로 SecurityContext를 구성합니다.

---

## 로컬 실행 방법

### 사전 요구사항

- Java 17
- Docker / Docker Compose

### 1단계 — 환경변수 설정

`.env.example`을 복사해 `.env`를 생성하고 값을 채웁니다.

```bash
cp .env.example .env
```

| 변수 | 설명 |
|---|---|
| `POSTGRES_USER` | PostgreSQL 사용자명 |
| `POSTGRES_PASSWORD` | PostgreSQL 비밀번호 |
| `POSTGRES_DB` | 데이터베이스명 |
| `REDIS_HOST` | Redis 호스트 (로컬: `localhost`) |
| `REDIS_PORT` | Redis 포트 (기본: `6379`) |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka 브로커 주소 (로컬: `localhost:9092`) |
| `JWT_SECRET` | JWT 서명 시크릿 키 |

> **참고**: Gemini API Key, Slack Bot Token 등 서비스별 추가 설정은 각 서비스의 Config Server 설정 파일(`config-repo/`)에서 관리합니다.

### 2단계 — 인프라 기동

```bash
# PostgreSQL, Redis, Kafka(+Zookeeper), Zipkin 한 번에 기동
docker compose up -d
```

기동 확인:

| 서비스 | 확인 URL / 방법 |
|---|---|
| PostgreSQL | `localhost:5432` |
| Redis | `localhost:6379` |
| Kafka | `localhost:9092` |
| Zipkin | `localhost:9411` |

### 3단계 — 서비스 기동 순서

IntelliJ 또는 Gradle로 아래 순서로 실행합니다.

```
1. discovery-server   (8761)
2. config-server      (8888)
3. api-gateway        (8080)
4. user-service       (19091)
5. hub-service        (19092)
6. company-service    (19093)
7. product-service    (19094)
8. order-service      (19095)
9. delivery-service   (19096)
10. slack-service     (19097)
```

Gradle로 특정 서비스 실행 예시:

```bash
./gradlew :discovery-server:bootRun
./gradlew :config-server:bootRun
./gradlew :api-gateway:bootRun
# ...
```

---

## API 문서

각 서비스가 기동된 후 Swagger UI에서 API를 확인할 수 있습니다.

| 서비스 | Swagger UI |
|---|---|
| user-service | http://localhost:19091/swagger-ui/index.html |
| hub-service | http://localhost:19092/swagger-ui/index.html |
| company-service | http://localhost:19093/swagger-ui/index.html |
| product-service | http://localhost:19094/swagger-ui/index.html |
| order-service | http://localhost:19095/swagger-ui/index.html |
| delivery-service | http://localhost:19096/swagger-ui/index.html |
| slack-service | http://localhost:19097/swagger-ui/index.html |

---

## 브랜치 및 커밋 전략

### 브랜치 전략

| 브랜치 | 용도 |
|---|---|
| `main` | 최종 배포 및 심사 제출 (직접 push 금지) |
| `develop` | 개발 통합 브랜치 |
| `feature/*` | 기능 개발 (예: `feature/68-hub-stock-api`) |
| `fix/*` | 버그 수정 (예: `fix/15-cart-count-error`) |
| `refactor/*` | 리팩토링 |
| `chore/*` | 의존성/빌드 스크립트 수정 |
| `docs/*` | 문서 작성 |
| `release/*` | 배포 준비 QA |

브랜치 네이밍 규칙: `[유형]/[이슈번호]-[영문요약]`

### PR 규칙

- 최소 2명 Approve + CI 통과 필수
- PR 코드 400줄 이내 (기능 단위로 분리)
- PR 템플릿 작성 필수

### 커밋 메시지 컨벤션

형식: `[유형]: [작업 요약] (#[이슈번호])`

| 유형 | 설명 |
|---|---|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 기능 변경 없는 코드 개선 |
| `test` | 테스트 코드 작성 |
| `config` | 환경설정 / CI·CD 관련 변경 |
| `docs` | 문서 수정 |
| `chore` | 오타, 변수명 등 자잘한 수정 |
| `move` | 패키지/파일 이동 |
| `remove` | 불필요한 코드/파일 삭제 |

예시: `feat: 허브 재고 생성 API 구현 (#68)`

---

## 관련 문서

| 문서 | 링크 |
|---|---|
| SA 문서 (도메인, API 명세) | [Notion](https://www.notion.so/teamsparta/SA-3602dc3ef514809eb4defc610e32fa9e#3602dc3ef514802e8552f556bfb471fd) |
| ERD | [ERD.md](./docs/ERD.md) |
| Kafka Saga 패턴 설계 | [SAGA.md](./docs/SAGA.md) |