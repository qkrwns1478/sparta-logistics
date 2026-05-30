# 인증 흐름 이해하기

외부 요청이 Gateway에 진입한 뒤 내부 서비스까지 인증 정보가 전달되는 전체 흐름을 설명합니다.

## 전체 구조 개요

이 시스템의 인증은 세 단계로 나뉩니다.

1. **Gateway 진입** — 외부 요청의 JWT를 검증하고 사용자 정보를 `X-User-*` 헤더로 변환해 내부로 전달
2. **내부 서비스** — 헤더를 읽어 SecurityContext에 세팅하고 권한 체크
3. **서비스 간 호출** — Feign 호출 시 `X-Internal-Call` 헤더를 자동으로 추가해 내부 호출임을 식별

```text
외부 클라이언트
      │  Authorization: Bearer <JWT>
      ▼
  API Gateway (WebFlux)
  └── JwtHeaderFilter (Ordered.HIGHEST_PRECEDENCE)
        ├── X-Internal-Call 헤더 제거 (위조 방지)
        ├── 화이트리스트 경로 → 그대로 통과
        │   (/api/v1/auth/login, /signup, /refresh, /swagger-ui/**, /v3/api-docs/**, /webjars/**)
        └── JWT 검증 → X-User-* 헤더 세팅
      │  X-User-Id, X-User-Role, X-User-HubId(선택), X-User-CompanyId(선택)
      ▼
  내부 서비스 (MVC)
  └── CommonSecurityConfig (Security FilterChain)
        └── GatewayAuthFilter (addFilterBefore UsernamePasswordAuthenticationFilter)
              ├── X-Internal-Call 있음 → ROLE_SYSTEM 세팅
              └── X-User-* 있음 → 사용자 권한 세팅
                    └── hubId, companyId → authentication.details에 저장
      │
      ▼
  서비스 간 Feign 호출
  └── FeignClientInterceptor
        └── X-Internal-Call: true 자동 추가
```

## 주요 설계 결정

**Gateway에서 JWT를 검증하고 헤더로 변환하는 이유**

각 내부 서비스가 직접 JWT를 검증하면 시크릿 키를 모든 서비스가 공유해야 하고 검증 로직이 분산됩니다. Gateway에서 한 번만 검증하고 신뢰할 수 있는 헤더로 변환해 전달하면 내부 서비스는 헤더만 읽으면 됩니다.

**내부 서비스도 Security FilterChain을 통과하는 이유**

모든 서비스가 `CommonSecurityConfig`를 공유하므로 Feign 호출도 외부 요청과 동일하게 FilterChain을 통과합니다. signup처럼 로그인 없이 접근 가능한 경로는 X-User-* 헤더 가 없습니다. 이때, 내부 호출임을 식별하는 `X-Internal-Call` 헤더가 없으면 `anyRequest().authenticated()` 적용 시 401이 반환됩니다.

**X-Internal-Call 헤더 방식을 채택한 이유**

내부 호출 경로를 Security 화이트리스트에 직접 등록하는 방법도 있지만, 경로가 추가될 때마다 `CommonSecurityConfig`를 수정해야 합니다. `FeignClientInterceptor`를 통한 자동 헤더 추가 방식은 새로운 Feign 호출이 생겨도 별도 설정 변경이 불필요합니다.

## 참고 자료

- [JWT 전략 이해하기](about-jwt-strategy.md)
- [내부 서비스 인증 처리 이해하기](about-internal-service-auth.md)
- [인가 흐름 이해하기](about-authorization.md)
