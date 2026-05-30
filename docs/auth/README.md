# 인증/인가 문서

MSA 환경에서의 인증/인가 흐름을 설명하는 문서 모음입니다.

## 문서 목록

| 문서 | 설명 |
|------|------|
| [인증 흐름 이해하기](about-authentication-flow.md) | Gateway 진입부터 내부 서비스까지 전체 인증 흐름 개요 |
| [JWT 전략 이해하기](about-jwt-strategy.md) | JWT 발급, 검증, 갱신, 탈취 감지 전략 |
| [내부 서비스 인증 처리 이해하기](about-internal-service-auth.md) | GatewayAuthFilter, X-Internal-Call, AuditorAware |
| [인가 흐름 이해하기](about-authorization.md) | URL 기반 접근 제어와 서비스 레이어 권한 검증 |

## 읽는 순서

처음 접하는 경우 아래 순서로 읽는 것을 권장합니다.

1. [인증 흐름 이해하기](about-authentication-flow.md) — 전체 그림 파악
2. [JWT 전략 이해하기](about-jwt-strategy.md) — 토큰 발급/검증 상세
3. [내부 서비스 인증 처리 이해하기](about-internal-service-auth.md) — 서비스 간 인증 처리
4. [인가 흐름 이해하기](about-authorization.md) — 권한 검증 방식
