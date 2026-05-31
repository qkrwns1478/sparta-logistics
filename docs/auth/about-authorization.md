# 인가 흐름 이해하기
URL 기반 접근 제어와 서비스 레이어에서의 권한 검증이 어떻게 동작하는지 설명합니다.

## 인증과 인가의 차이

인증과 인가는 순서대로 일어나는 별개의 단계입니다.

- **인증(Authentication)** — "누구인지 확인". `GatewayAuthFilter`가 `X-User-*` 헤더를 읽어 SecurityContext에 사용자 정보를 세팅하는 과정
- **인가(Authorization)** — "무엇을 할 수 있는지 확인". 사용자의 권한(`Role`)으로 특정 URL 또는 기능 접근을 허용/차단하는 과정

인가는 인증이 완료된 이후에 이루어집니다.

## URL 기반 인가 — CommonSecurityConfig

`CommonSecurityConfig`에서 URL 패턴별로 접근 권한을 설정합니다. 모든 서비스가 이 설정을 공유합니다.

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
        .requestMatchers("/actuator/**").permitAll()
        .requestMatchers(
                "/api/v1/auth/login",
                "/api/v1/auth/signup",
                "/api/v1/auth/refresh"
        ).permitAll()
        // TODO: 배포 전에는 authenticated()로 변경 필요
        .anyRequest().permitAll()
)
```

현재는 개발 편의를 위해 `.anyRequest().permitAll()`로 설정되어 있습니다. 배포 전 `.anyRequest().authenticated()`로 변경하면 인증되지 않은 모든 요청이 차단됩니다.

## 서비스 레이어 권한 검증

URL 기반 인가가 큰 그림(인증 여부)을 잡는다면, 세부적인 권한 제어는 각 서비스의 컨트롤러와 서비스 레이어에서 직접 처리합니다.

컨트롤러에서 `X-User-Id`, `X-User-Role`, `X-User-HubId`, `X-User-CompanyId` 헤더를 받아 요청자의 권한과 본인 여부를 직접 검증합니다. `X-User-HubId`, `X-User-CompanyId`는 해당 권한인 경우에만 전달되므로 `required = false`로 선언합니다.
 
```java
// 헤더 수신 패턴
@GetMapping("/{id}")
public ResponseEntity<?> get(
        @PathVariable UUID id,
        @RequestHeader("X-User-Id")                               UUID userId,
        @RequestHeader("X-User-Role")                             String role,
        @RequestHeader(value = "X-User-HubId",     required = false) UUID hubId,
        @RequestHeader(value = "X-User-CompanyId", required = false) UUID companyId
) { ... }
```

**권한별 접근 범위 (user-service 기준)**

| 작업 | MASTER | 본인 | 그 외 |
|------|:------:|:----:|:-----:|
| 전체 사용자 조회 | ✓ | ✗ | ✗ |
| 단건 조회 | ✓ | ✓ | ✗ |
| 사용자 수정 | ✓ | ✗ | ✗ |
| 사용자 삭제 | ✓ | ✗ | ✗ |

## 권한 종류

| 권한 | 설명 |
|------|------|
| `MASTER` | 전체 관리자. 모든 리소스에 접근 가능 |
| `HUB_MANAGER` | 허브 관리자. 담당 허브 리소스만 접근 가능 (`X-User-HubId` 기준) |
| `DELIVERY_MANAGER` | 배달 담당자. 담당 허브 소속 (`X-User-HubId` 기준) |
| `COMPANY_MANAGER` | 업체 담당자. 본인 업체 리소스만 접근 가능 (`X-User-CompanyId` 기준) |
| `ROLE_SYSTEM` | 내부 Feign 호출 전용. `X-Internal-Call` 헤더로 식별 |

## 인가 처리 흐름

```text
요청
  ↓
GatewayAuthFilter
  └── SecurityContext 세팅 (ROLE_* 권한 포함)
  ↓
AuthorizationFilter
  └── URL 기반 접근 제어 (CommonSecurityConfig)
  ↓
Controller
  └── X-User-Id, X-User-Role 헤더 수신
        ├── MASTER → 허용
        ├── 본인 (X-User-Id == pathVariable userId) → 허용
        └── 그 외 → ACCESS_DENIED (403)
```

## 참고 자료

- [인증 흐름 이해하기](about-authentication-flow.md)
