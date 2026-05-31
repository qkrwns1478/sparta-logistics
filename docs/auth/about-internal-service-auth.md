# 내부 서비스 인증 처리 이해하기

GatewayAuthFilter가 SecurityContext를 세팅하는 방식과 서비스 간 Feign 호출 인증, 감사 필드 자동 기록 흐름을 설명합니다.

## Security FilterChain 구성

모든 내부 서비스는 `CommonSecurityConfig`의 Security FilterChain을 공유합니다. 외부 요청과 내부 Feign 호출 모두 동일하게 이 FilterChain을 통과합니다.

`GatewayAuthFilter`는 `addFilterBefore(gatewayAuthFilter, UsernamePasswordAuthenticationFilter.class)`로 등록되어 SecurityContext를 채웁니다.

```text
Security FilterChain
├── GatewayAuthFilter                      ← SecurityContext 세팅
├── UsernamePasswordAuthenticationFilter   ← 이 앞에 등록
└── AuthorizationFilter                    ← 권한 체크
```

`@Component`만으로 등록된 필터는 FilterChain 바깥의 서블릿 필터로 실행되어 SecurityContext 세팅 타이밍이 어긋납니다. `FilterRegistrationBean`으로 서블릿 필터 중복 등록도 방지합니다.

## GatewayAuthFilter 처리 흐름

필터는 요청을 두 가지 경우로 분기합니다.

**내부 Feign 호출 (`X-Internal-Call: true`)**

`SYSTEM_UUID`(`00000000-0000-0000-0000-000000000001`)로 SecurityContext를 세팅하고 통과시킵니다. 호출자가 누구든 내부 Feign 호출은 항상 일관된 시스템 권한으로 처리됩니다.

**일반 사용자 요청 (`X-User-Id`, `X-User-Role` 헤더)**

Gateway에서 전달된 헤더를 읽어 사용자 권한으로 SecurityContext를 세팅합니다. `X-User-Id`가 UUID 형식이 아니면 위조된 헤더로 간주하고 `GatewayAuthEntryPoint`를 통해 오류를 반환합니다. `X-User-HubId`, `X-User-CompanyId` 헤더는 `authentication.details`에 Map 형태로 저장되어 각 서비스에서 꺼내 쓸 수 있습니다.

**두 헤더 모두 없는 경우**

SecurityContext를 세팅하지 않고 FilterChain을 통과시킵니다. 현재 설정이 `.anyRequest().permitAll()`이므로 통과되지만, `.anyRequest().authenticated()`로 변경하면 401이 반환됩니다.

## 서비스 간 Feign 호출 인증

`FeignClientInterceptor`는 모든 Feign 요청에 자동으로 `X-Internal-Call: true` 헤더를 추가합니다. 원래 요청에 `X-User-*` 헤더가 있으면 함께 전달합니다.

```java
// FeignClientInterceptor
public static final String INTERNAL_CALL_HEADER = "X-Internal-Call";
public static final String INTERNAL_CALL_VALUE = "true";

@Override
public void apply(RequestTemplate template) {
    template.header(INTERNAL_CALL_HEADER, INTERNAL_CALL_VALUE);
    // 기존 X-User-* 헤더도 함께 전달
}
```

`X-Internal-Call` 헤더가 없으면 `X-User-*` 헤더도 없는 public 엔드포인트 출발 Feign 호출은 SecurityContext가 빈 채로 `AuthorizationFilter`에 도달합니다. `.anyRequest().authenticated()` 적용 시 401이 반환됩니다.

## AuditorAware — 감사 필드 자동 기록

엔티티 저장 및 수정 시 `BaseEntity`의 `createdBy`, `updatedBy` 필드는 `AuditorAwareConfig`가 자동으로 채웁니다. `GatewayAuthFilter`의 SecurityContext 세팅과는 독립적으로 동작하며, `RequestContextHolder`에서 `X-User-Id` 헤더를 직접 읽습니다.

| 상황 | `createdBy` / `updatedBy` |
|------|--------------------------|
| 인증된 사용자 요청 | 해당 사용자 UUID |
| 인증된 사용자 요청에서 시작된 Feign 호출 | 원래 사용자 UUID (`X-User-Id`가 함께 전달됨) |
| public 엔드포인트에서 시작된 Feign 호출 | `SYSTEM_UUID` |

## 보안 고려사항
 
| 항목 | 현재 상태 | 개선 방향 |
|------|---------|---------|
| 서비스 간 내부 호출 인증 | 없음 (Gateway 신뢰 모델) | 운영 환경 mTLS 또는 내부 API Key 추가 고려 필요 |
 
현재는 Gateway를 통과한 요청은 신뢰한다는 전제 하에 `X-Internal-Call` 헤더로 내부 호출을 식별합니다. Gateway 외부에서 내부 서비스에 직접 접근하는 경우는 막을 수 없으므로, 운영 환경에서는 mTLS 또는 내부 API Key 방식 도입을 검토해야 합니다.

## 참고 자료

- [인증 흐름 이해하기](about-authentication-flow.md)
