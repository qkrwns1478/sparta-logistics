# JWT 전략 이해하기

액세스 토큰과 리프레시 토큰의 발급, 검증, 갱신 전략과 토큰 탈취 감지 방식을 설명합니다.

## 토큰 구성

JWT는 HMAC-SHA 알고리즘으로 서명되며, `user-service`의 `JwtUtil`이 발급하고 Gateway의 `ReactiveJwtDecoder`가 검증합니다. 두 곳이 동일한 시크릿 키(`jwt.secret-key`)를 공유합니다.

**클레임 구성**

| 클레임 | 값 | 설명 |
|--------|---|------|
| `subject` | 사용자 UUID | Gateway에서 `X-User-Id`로 전달 |
| `auth` | 사용자 권한 | Gateway에서 `X-User-Role`로 전달 |
| `token_type` | `access` / `refresh` | 토큰 타입 구분 |
| `hubId` | 허브 UUID | 허브 매니저, 배달 담당자인 경우만 포함. Gateway에서 `X-User-HubId`로 전달 |
| `companyId` | 업체 UUID | 업체 담당자인 경우만 포함. Gateway에서 `X-User-CompanyId`로 전달 |

**권한 종류**

`MASTER`, `HUB_MANAGER`, `DELIVERY_MANAGER`, `COMPANY_MANAGER`

## 토큰 유효 시간

| 토큰 | 유효 시간 | 저장 위치 |
|------|---------|---------|
| 액세스 토큰 | 15분 | 클라이언트 |
| 리프레시 토큰 | 5일 | Redis |

별도 로그아웃 기능이 없으므로 액세스 토큰 유효 시간을 짧게 설정해 탈취 시 피해를 최소화합니다. 리프레시 토큰의 유효 시간이 곧 자동 로그아웃 기간입니다.

## 토큰 발급 흐름

**로그인**

1. 사용자명 / 비밀번호 검증
2. 계정 승인 상태(`APPROVED`) 확인
3. 액세스 토큰 + 리프레시 토큰 발급
4. 리프레시 토큰을 Redis에 저장 (`userId` 키)

**토큰 갱신**

1. 리프레시 토큰의 `token_type` 클레임이 `refresh`인지 확인
2. Redis에 저장된 토큰과 일치하는지 검증
3. 일치하지 않으면 토큰 탈취로 간주 → Redis에서 즉시 삭제 후 오류 반환
4. 사용자 승인 상태 재확인
5. 새 액세스 토큰 + 새 리프레시 토큰 발급 후 Redis 갱신

## Gateway의 JWT 검증 오류 처리

JWT 검증 실패 시 Gateway가 반환하는 오류 코드입니다. 검증 흐름 전체는 [인증 흐름 이해하기](about-authentication-flow.md)를 참고하세요.

| 상황 | 응답 |
|------|------|
| `Authorization` 헤더 없음 | `TOKEN_NOT_FOUND` |
| 토큰 만료 | `TOKEN_EXPIRED` |
| 서명 불일치 / 형식 오류 | `INCORRECT_TOKEN` |
| `subject` 또는 `auth` 클레임 누락 | `INCORRECT_TOKEN` |

## 보안 고려사항
 
| 항목 | 현재 상태 | 개선 방향 |
|------|---------|---------|
| JWT 서명 알고리즘 | HS256 (대칭키) | 운영 환경 RS256(비대칭키) 전환 필요 |
 
> **왜 운영 환경에서 RS256으로 전환해야 하나요?**
> HS256은 발급과 검증에 동일한 시크릿 키를 사용합니다. 현재 와 Gateway가 같은 키를 공유하는 구조인데, 서비스가 늘어날수록 키 노출 위험이 커집니다. RS256은 비공개 키로 발급하고 공개 키로 검증하므로 Gateway와 내부 서비스는 공개 키만 갖고 있으면 됩니다.

## 참고 자료

- [인증 흐름 이해하기](about-authentication-flow.md)
