# 개발 규칙 (DEVELOPMENT_RULES)

이 문서는 프로젝트 전반의 개발 규칙입니다.

## 1. 원칙

1. **필수 기능은 임의로 삭제하거나 축소하지 않습니다.** F1-01 ~ F1-09는 모두 구현 대상입니다.
2. **설계를 바꾸기 전에 제안합니다.** 변경 이유, 대안, 영향 범위를 먼저 정리하고 승인을 받습니다 (4절).
3. **ERD와 API 명세를 일관되게 유지합니다.** 두 문서와 `FUNCTION_SPEC.md`는 항상 같은 커밋에서 바꿉니다.
4. **보안 정보는 코드에 직접 쓰지 않습니다.** (5절)
5. **기능은 테스트 가능한 단위로 개발합니다.** 기능 하나를 끝내면 테스트를 통과시킨 뒤 다음 기능으로 넘어갑니다.
6. **진행 상황은 `PROGRESS.md`에 기록합니다.**
7. **기존 파일은 덮어쓰기 전에 확인합니다.**

## 2. 기술 스택 (고정)

Java 21 · Spring Boot 3.x · Spring Security · Spring Data JPA · Spring AI · OpenRouter · Thymeleaf · Bootstrap 5 · MySQL 8 · AWS EC2(Ubuntu) · AWS S3 · GitHub Actions · Gradle

- 위 목록 밖의 라이브러리나 프레임워크는 설계 변경 절차(4절)를 거쳐야 추가할 수 있습니다.
- 라이브러리 버전은 Spring Boot BOM과 Spring AI BOM을 따르고, 임의로 고정하지 않습니다.
- 테스트 도구(JUnit 5, Mockito, Testcontainers 등)는 테스트 목적의 추가로 보고, 추가 시 `PROGRESS.md`에 기록합니다. Testcontainers 등 기술 스택 밖의 도구 도입 여부는 T9에서 확정합니다.

## 3. 브랜치·커밋·PR 규칙

### 3.1 브랜치
- `main`에는 직접 push하지 않습니다.
- 작업은 기능 또는 문서 단위 브랜치에서 합니다. 예: `docs/architecture-decisions-q1-q6`, `feat/f1-01-auth`, `feat/f1-02-restaurant`.
- 자동화 세션에서 지정한 작업 브랜치(`claude/...`)가 있으면 그 브랜치를 사용합니다.

### 3.2 커밋
- 한 커밋은 한 가지 변경만 담습니다.
- 메시지 형식: `<type>: <요약>` (type: `docs`, `feat`, `fix`, `test`, `refactor`, `chore`).
- 예: `docs: 기초 문서 구조 추가`, `feat(F1-02): 맛집 등록 API 구현`.
- 커밋 메시지에 보안 정보와 모델 식별자를 넣지 않습니다.

### 3.3 Pull Request
- 사용자가 요청할 때만 생성합니다.
- 저장소에 PR 템플릿이 있으면 그 섹션 구조를 따릅니다.
- 기능 PR에는 해당 기능 ID(F1-0X), 변경한 문서, 테스트 결과를 적습니다.

## 4. 설계 변경 절차

설계 변경은 아래 순서로 진행합니다.

1. **변경 제안서 작성**: 무엇을 왜 바꾸는지, 대안 2개 이상, 각 대안의 장단점을 적습니다.
2. **영향 범위 확인**: `REQUIREMENTS.md`, `FUNCTION_SPEC.md`, `DATABASE_ERD.md`, `API_SPEC.md` 중 바뀌는 문서를 목록으로 만듭니다.
3. **승인**: 사용자가 승인하기 전에는 구현하지 않습니다.
4. **반영**: 관련 문서를 같은 커밋에서 함께 고칩니다.
5. **기록**: `PROGRESS.md`의 "설계 변경 이력"에 날짜와 요약을 남깁니다.

변경 제안서 예시 형식:

```
제목: 리프레시 토큰 보관 방식 변경
이유: ...
대안 A (현재): DB 해시 저장 — 장점: 강제 폐기 가능 / 단점: 조회 1회 추가
대안 B: 무상태 리프레시 토큰 — 장점: DB 부담 없음 / 단점: 강제 폐기 어려움
영향 문서: DATABASE_ERD.md, API_SPEC.md(2.3, 2.4), FUNCTION_SPEC.md(F1-01)
결정 필요: 사용자 승인
```

## 5. 보안 규칙

### 5.1 보안 정보 관리
- 다음 값은 코드, 설정 파일, 문서, 커밋, 로그에 직접 쓰지 않습니다.
  - DB 접속 정보 · JWT 서명 키 · Google OAuth 클라이언트 ID/Secret · AWS 액세스 키 · OpenRouter API 키
- 모든 보안 값은 환경 변수로 주입합니다. Spring에서는 `${ENV_NAME}` 형태로 참조합니다.
- 로컬 개발용 값은 개인 `.env` 파일 또는 IDE 실행 설정에 두고, `.gitignore`로 제외합니다.
- 문서 예시는 `<...>` 자리표시자를 씁니다.

### 5.2 환경 변수 목록

| 변수 | 용도 |
|------|------|
| `DB_URL` | MySQL JDBC URL (개발과 운영에서 값이 다름, 5.5 참조) |
| `DB_USERNAME` | DB 사용자 |
| `DB_PASSWORD` | DB 비밀번호 |
| `JWT_SECRET` | JWT 서명 키 (최소 256비트) |
| `JWT_ACCESS_TOKEN_EXPIRY_SECONDS` | 액세스 토큰 만료 초 (기본 1800) |
| `JWT_REFRESH_TOKEN_EXPIRY_SECONDS` | 리프레시 토큰 만료 초 (기본 1209600) |
| `GOOGLE_CLIENT_ID` | Google OAuth 클라이언트 ID |
| `GOOGLE_CLIENT_SECRET` | Google OAuth 클라이언트 시크릿 |
| `AWS_REGION` | S3 리전 |
| `AWS_S3_BUCKET` | 이미지 버킷 이름 |
| `OPENROUTER_API_KEY` | OpenRouter API 키 |
| `OPENROUTER_MODEL` | 사용할 모델 ID (프로필별로 다른 값 가능, 5.4 참조) |

> 이 목록이 바뀌면 `README.md`의 환경 변수 절도 함께 수정합니다.

### 5.3 애플리케이션 보안
- 비밀번호는 BCrypt로 해시합니다.
- 쓰기 API는 인증과 권한 검사를 모두 거칩니다. 권한 검사는 컨트롤러가 아니라 서비스 계층에서 수행합니다.
- 관리자 권한(`ADMIN`)은 Spring Security 규칙(`/admin/**`, `/api/admin/**`)과 서비스 계층 검사로 **서버에서** 검증합니다. URL을 숨기는 것만으로는 보호하지 않습니다 (FR-09-08).
- 일반 회원이 자신의 `role`을 바꾸는 API나 입력 경로를 만들지 않습니다 (FR-09-09).
- 쿠키 속성:
  - `access_token`, `refresh_token`: `HttpOnly`, `SameSite=Lax`, 운영 환경에서는 `Secure`.
  - `refresh_token`의 Path는 `/api/auth`로 제한합니다.
- Refresh Token 원문은 쿠키에만 두고, DB에는 SHA-256 해시만 저장합니다 (FR-01-05). 로그와 응답 본문에도 원문을 남기지 않습니다.
- CSRF: Cookie 기반 인증을 쓰므로 Spring Security CSRF 보호를 유지합니다. 상태 변경 요청은 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 보냅니다.
- Thymeleaf의 `th:text`를 사용하고, `th:utext`로 사용자 입력을 출력하지 않습니다.
- 파일 업로드는 MIME 타입과 확장자, 크기를 모두 검사합니다.
- 에러 응답에 스택 트레이스나 SQL을 노출하지 않습니다.
- 로그에 비밀번호, 토큰, API 키, 리뷰 원문 대량 출력을 남기지 않습니다.
- S3 오류를 `storage_delete_task.last_error`에 저장할 때는 자격 증명, 서명 값, 요청 헤더를 제거한 짧은 메시지만 저장합니다.

### 5.4 AI 응답 검증 및 보안 규칙 (Spring AI · OpenRouter)
- AI 호출은 서버에서만 합니다. 브라우저에는 API 키를 내려주지 않습니다.
- OpenRouter 설정은 환경 변수로만 받습니다.

  ```
  OPENROUTER_API_KEY=${OPENROUTER_API_KEY}
  OPENROUTER_MODEL=${OPENROUTER_MODEL}
  ```

- **모델 ID를 Java 코드에 고정하지 않습니다.** 모델을 바꿀 때 코드 수정 없이 환경 변수만 바꿀 수 있어야 합니다 (NFR-18).
- 개발(`local`)과 운영(`prod`) 프로필은 서로 다른 `OPENROUTER_MODEL` 값을 쓸 수 있습니다.
- 특정 무료 모델 ID는 이 문서에서 확정하지 않습니다 (T10).
- 사용자 입력(취향 문장)과 리뷰 내용은 데이터로만 전달하고, 시스템 지시를 따르지 않도록 프롬프트에 명시합니다.
- AI가 반환한 맛집 ID는 항상 DB 후보 목록과 대조합니다. 목록에 없는 ID는 버립니다 (FR-07-03).
- AI 응답이 기대한 JSON 형식이 아니면 결과를 노출하지 않고 `AI_RESPONSE_INVALID`로 처리합니다.
- AI 호출은 타임아웃(기본 10초)을 둡니다. 실패가 상세 화면 전체를 막지 않도록 합니다 (FR-05-06).

### 5.5 데이터베이스 운영 규칙 (Q4)

운영 DB는 **AWS EC2 내부에 설치한 MySQL 8**입니다. RDS는 사용하지 않습니다.

- **환경 분리**
  - 개발(`local`): 개발자 PC의 MySQL 8 (설치 또는 컨테이너, 개발자 선택). 운영 DB 접속 정보를 개발 환경에 두지 않습니다.
  - 운영(`prod`): EC2 내부 MySQL 8. 접속 정보는 서버 환경 변수로만 주입합니다.
  - 두 환경의 DB 설정은 Spring 프로필(`local`, `prod`)로 나눕니다 (NFR-11).
- **네트워크**
  - 3306 포트는 외부 인터넷에 공개하지 않습니다. 보안 그룹에서 외부 접근을 차단합니다 (NFR-14).
  - 애플리케이션은 EC2 내부에서 MySQL에 접속합니다. 운영자의 DB 접속도 서버 내부 경로로만 합니다.
- **영속성**
  - MySQL 데이터 디렉터리는 서버나 컨테이너를 다시 만들어도 남는 영속 저장소에 둡니다 (NFR-13). 구체 구성은 T5에서 확정합니다.
- **백업**
  - 정기 백업을 수행합니다. 주기, 보관 기간, 저장 위치, 복구 절차는 T4에서 확정하고, 확정 전에는 운영 DB를 배포하지 않습니다.
- **이식성 (RDS 이전 대비, NFR-16)**
  - DB 접속 정보는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경 변수로만 받습니다. 코드에 호스트명을 쓰지 않습니다.
  - MySQL 전용 기능(예: 특정 함수, 저장 프로시저)에 기대는 코드는 리포지토리 계층에 격리합니다.
  - 추후 RDS로 옮길 때 애플리케이션 코드를 바꾸지 않고 환경 변수만 바꾸는 것을 목표로 합니다.

### 5.6 S3 삭제 규칙 (Q2)

DB와 S3는 분산 트랜잭션으로 묶지 않습니다. 아래 순서를 지킵니다.

1. DB 트랜잭션에서 행을 삭제하고, 같은 트랜잭션에서 `storage_delete_task`에 S3 키를 기록합니다. 그 후 커밋합니다.
2. 커밋 후 S3 객체를 삭제합니다.
   - 성공하면 `storage_delete_task` 행을 삭제합니다.
   - 실패하면 `retry_count`와 `last_error`를 갱신합니다. DB 삭제는 되돌리지 않습니다.
3. 재시도는 관리자 API로 합니다 (F1-09). 자동 재시도 여부는 T6에서 정합니다.
4. S3에 객체가 없으면 삭제 성공으로 처리합니다 (재시도가 중복되어도 안전해야 합니다).
5. 문서와 코드 주석, 응답 메시지에서 "DB와 S3가 함께 롤백된다" 또는 "원자적으로 처리된다"고 표현하지 않습니다.

### 5.7 인증 구조와 세션 정책 (JWT · OAuth2 임시 세션)

인증은 두 역할로 나눕니다. 두 역할을 혼동하지 않습니다.

| 구분 | 역할 | 사용 범위 |
|------|------|-----------|
| JWT 기반 인증 (기본) | 일반 웹·API 요청의 회원 식별. Access Token(쿠키 또는 Bearer), Refresh Token(쿠키, DB에는 해시만 저장) | `/api/**`, 일반 화면 요청 |
| OAuth2 임시 세션 | Google 인증 진행 중의 상태 관리: `state`, Authorization Request, 연동 요청 회원 ID, 흐름 종류(로그인/연동) | Google 로그인 및 계정 연동 흐름에만 사용 |

원칙:
1. 일반 API 인증은 JWT 기반으로 유지합니다.
2. Google OAuth2 인증 과정에서만 필요한 임시 상태 관리를 위해 서버 세션을 사용할 수 있습니다.
3. OAuth2 임시 세션은 일반 API 인증을 대체하지 않습니다. `/api/**` 요청은 임시 세션만으로 인증하지 않습니다.
4. OAuth2 인증이 완료되면 기존 JWT 기반 인증 체계로 돌아갑니다.
5. Spring Security 세션 정책을 무조건 완전 Stateless로 고정하지 않습니다. OAuth2 인증 과정의 상태 관리가 막히지 않아야 합니다. 세부 설정 값(세션 생성 정책, 저장 위치, 만료 시간)은 구현 단계에서 확정합니다 (REQUIREMENTS T11).
6. 임시 세션에는 로그인 상태(회원 권한)를 저장하지 않고 흐름 상태만 저장합니다. Access Token, Refresh Token 원문, 비밀번호는 저장하지 않습니다.
7. 흐름이 끝나면(성공이든 실패든) 임시 상태를 제거합니다. `state` 값은 한 번만 사용할 수 있습니다.
8. Google 계정 연결은 시작 시점에만 Access Token으로 회원을 검증하고, 검증된 회원 ID를 OAuth2 임시 세션에 저장합니다. 콜백은 `state`가 같은 임시 세션의 값인지 확인한 뒤 임시 세션의 회원 ID를 기준으로 연결합니다. 콜백은 Access Token 유효기간에 의존하지 않습니다 (FR-01-15).

## 6. 코드 구조 규칙 (구현 단계)

### 6.1 패키지 구조 (기능 단위)

```
com.busanlocalfood
├─ global        # 공통 설정, 예외, 응답 형식, 보안 설정
├─ member        # F1-01 회원·인증 (Google 연동 포함)
├─ restaurant    # F1-02 맛집, F1-08 메인 조회 보조
├─ image         # F1-03 S3 업로드·삭제, storage_delete_task 처리
├─ review        # F1-04 리뷰
├─ summary       # F1-05 AI 리뷰 요약·캐싱
├─ favorite      # F1-06 찜
├─ recommend     # F1-07 AI 추천
├─ admin         # F1-09 관리자 기능 (관리자 API, 대시보드, 재시도)
└─ web           # Thymeleaf 컨트롤러
```

- 각 기능 패키지는 `controller`, `service`, `repository`, `domain`, `dto`로 나눕니다.
- 패키지 간 직접 참조는 서비스 계층을 통해서만 합니다. 다른 기능의 Repository를 직접 주입하지 않습니다.
- `admin` 패키지는 다른 기능의 서비스를 호출해 재사용합니다 (예: 맛집 삭제는 `restaurant` 서비스, 이미지 삭제는 `image` 서비스). 관리자 전용 로직을 복제하지 않습니다.

### 6.2 네이밍
- 엔티티: 단수형 (`Restaurant`, `Review`). 테이블명은 ERD의 snake_case를 따릅니다.
- DTO: 요청 `XxxRequest`, 응답 `XxxResponse`.
- 오류 코드: `API_SPEC.md`의 `code` 값과 동일한 enum 상수를 사용합니다.
- 환경 변수 접근은 `@ConfigurationProperties`로 감쌉니다.

### 6.3 API·화면 구현 방식
- JSON API는 `@RestController`, 화면은 `@Controller` + Thymeleaf로 구현합니다.
- 목록의 정렬·페이지네이션은 Spring Data `Pageable`을 사용합니다.
- 화면에서 AJAX가 필요한 동작(찜, 리뷰 작성·삭제, 추천)은 REST API를 호출합니다. 나머지 폼은 일반 POST로 처리합니다. (결정 사항: 구현 첫 기능 PR에서 확정하고 이 절에 반영)

### 6.4 예외 처리
- 도메인 예외는 `BusinessException(ErrorCode)`를 상속한 클래스로 던집니다.
- `@RestControllerAdvice`에서 `API_SPEC.md` 형식으로 변환합니다.
- Google 리다이렉트 흐름의 실패는 JSON이 아니라 `error` 쿼리 파라미터로 전달합니다 (API_SPEC 2.5, 2.6).

## 7. 테스트 규칙

- 기능마다 최소한 다음 테스트를 작성합니다.
  - 서비스 단위 테스트 (Mockito): 정상 흐름, 권한 없음, 중복, 없는 대상
  - 리포지토리·통합 테스트: JPA 제약 조건(UNIQUE, FK, CHECK), 집계 갱신
  - 웹 계층 테스트 (MockMvc): 응답 형식과 상태 코드가 `API_SPEC.md`와 일치하는지 확인
- F1-01은 다음을 테스트합니다.
  - Refresh Token 재발급 시 기존 토큰이 폐기되고 새 토큰이 발급되는지 (로테이션)
  - 로그아웃 후 기존 Refresh Token이 거부되는지
  - DB에 원문이 없고 해시만 저장되는지
  - 동일 이메일 기존 계정이 있을 때 Google 로그인이 자동 연결하지 않는지
  - 이미 다른 회원에게 연결된 Google 계정을 연동할 수 없는지
  - 비밀번호가 없는 회원이 Google 연동을 해제하면 `LAST_LOGIN_METHOD`가 나는지
  - `state`가 없거나 다르면 Google 계정이 연결되지 않는지, 같은 `state`를 두 번 쓸 수 없는지
  - 임시 세션이 없거나 만료되었으면 연결되지 않는지
  - 콜백 시점에 Access Token이 만료되어도 유효한 임시 세션이면 연결되는지
  - `/api/**` 요청이 임시 세션만으로는 인증되지 않는지
- F1-02, F1-03은 다음을 테스트합니다.
  - 맛집 삭제 시 리뷰, 찜, 이미지, AI 요약이 함께 삭제되는지
  - S3 삭제 실패 시 `storage_delete_task`에 키가 남고, 재시도 후 행이 삭제되는지
  - 작성자가 아닌 회원의 수정·삭제가 403인지
- F1-05는 "리뷰 3개 미만 → 호출 없음", "캐시 히트 → 호출 없음", "리뷰 변경 → 캐시 삭제"를 테스트합니다.
- F1-07은 "후보 0개 → 호출 없음", "DB에 없는 ID 제외", "형식이 깨진 응답 → `AI_RESPONSE_INVALID`"를 테스트합니다.
- F1-09는 다음을 테스트합니다.
  - `USER`가 `/api/admin/**`에 접근하면 403인지
  - 비로그인 요청이 401인지
  - 일반 회원 API로 `role`을 바꾸는 경로가 없는지
  - 관리자 리뷰 삭제 후 평균 평점과 AI 요약 캐시가 갱신되는지
- 외부 서비스(S3, OpenRouter, Google)는 테스트에서 모킹하거나 가짜 구현체를 사용합니다. 실제 키를 테스트에 넣지 않습니다.
- CI(GitHub Actions)에서 `./gradlew test`가 통과해야 합니다.

## 8. 문서 규칙

- 기능을 구현하거나 바꾸면 `FUNCTION_SPEC.md`, `API_SPEC.md`, `DATABASE_ERD.md` 중 해당되는 문서를 같이 고칩니다.
- 기능 ID, 테이블명, 엔드포인트 경로, 오류 코드는 문서 전체에서 같은 표기를 씁니다.
- 미확정 사항이 해결되면 `REQUIREMENTS.md`의 4.2절(TODO)에서 빼고, 4.1절(확정된 결정)에 결정 내용을 적습니다.

## 9. 진행 기록 규칙

- 기능 작업이 끝날 때마다 `PROGRESS.md`의 해당 기능 상태와 작업 로그를 갱신합니다.
- 상태 값: `미착수`, `진행 중`, `테스트 중`, `완료`, `보류`.
- 완료 기준: 구현, 단위 테스트, 관련 문서 일치, CI 통과.
