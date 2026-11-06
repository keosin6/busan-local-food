# 개발 규칙 (DEVELOPMENT_RULES)

이 문서는 프로젝트 전반의 개발 규칙입니다.

## 1. 원칙

1. **필수 기능은 임의로 삭제하거나 축소하지 않습니다.** F1-01 ~ F1-08은 모두 구현 대상입니다.
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
- 테스트 도구(JUnit 5, Mockito, Testcontainers 등)는 테스트 목적의 추가로 보고, 추가 시 `PROGRESS.md`에 기록합니다.

## 3. 브랜치·커밋·PR 규칙

### 3.1 브랜치
- `main`에는 직접 push하지 않습니다.
- 작업은 기능 또는 문서 단위 브랜치에서 합니다. 예: `docs/initial-structure`, `feat/f1-01-auth`, `feat/f1-02-restaurant`.
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
| `DB_URL` | MySQL JDBC URL |
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
| `OPENROUTER_MODEL` | 사용할 모델 ID |

> 이 목록이 바뀌면 `README.md`의 환경 변수 절도 함께 수정합니다.

### 5.3 애플리케이션 보안
- 비밀번호는 BCrypt로 해시합니다.
- 쓰기 API는 인증과 작성자 권한 검사를 모두 거칩니다. 권한 검사는 컨트롤러가 아니라 서비스 계층에서 수행합니다.
- 인증 쿠키는 `HttpOnly`, `SameSite=Lax`, 운영에서는 `Secure`를 설정합니다.
- CSRF: 쿠키 기반 인증을 쓰므로 Spring Security CSRF 보호를 유지합니다. 화면 폼과 `fetch` 요청에 토큰을 포함합니다.
- Thymeleaf의 `th:text`를 사용하고, `th:utext`로 사용자 입력을 출력하지 않습니다.
- 파일 업로드는 MIME 타입과 확장자, 크기를 모두 검사합니다.
- 에러 응답에 스택 트레이스나 SQL을 노출하지 않습니다.
- 로그에 비밀번호, 토큰, API 키, 리뷰 원문 대량 출력을 남기지 않습니다.

### 5.4 AI 응답 검증 및 보안 규칙 (Spring AI · OpenRouter)
- AI 호출은 서버에서만 합니다.
- 사용자 입력과 리뷰 내용은 데이터로만 전달하고, 시스템 지시를 따르지 않도록 프롬프트에 명시합니다.
- AI가 반환한 맛집 ID는 항상 DB 후보 목록과 대조합니다.

## 6. 코드 구조 규칙 (구현 단계)

### 6.1 패키지 구조 (기능 단위)

```
com.busanlocalfood
├─ global        # 공통 설정, 예외, 응답 형식, 보안 설정
├─ member        # F1-01 회원·인증
├─ restaurant    # F1-02 맛집, F1-08 메인 조회 보조
├─ image         # F1-03 S3 업로드
├─ review        # F1-04 리뷰
├─ summary       # F1-05 AI 리뷰 요약·캐싱
├─ favorite      # F1-06 찜
├─ recommend     # F1-07 AI 추천
└─ web           # Thymeleaf 컨트롤러
```

- 각 기능 패키지는 `controller`, `service`, `repository`, `domain`, `dto`로 나눕니다.
- 패키지 간 직접 참조는 서비스 계층을 통해서만 합니다. 다른 기능의 Repository를 직접 주입하지 않습니다.

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

## 7. 테스트 규칙

- 기능마다 최소한 다음 테스트를 작성합니다.
  - 서비스 단위 테스트 (Mockito): 정상 흐름, 권한 없음, 중복, 없는 대상
  - 리포지토리·통합 테스트: JPA 제약 조건(UNIQUE, FK), 집계 갱신
  - 웹 계층 테스트 (MockMvc): 응답 형식과 상태 코드가 `API_SPEC.md`와 일치하는지 확인
- F1-05는 "리뷰 3개 미만 → 호출 없음", "캐시 히트 → 호출 없음", "리뷰 변경 → 캐시 삭제"를 테스트합니다.
- F1-07은 "후보 0개 → 호출 없음", "DB에 없는 ID 제외"를 테스트합니다.
- 외부 서비스(S3, OpenRouter, Google)는 테스트에서 모킹하거나 가짜 구현체를 사용합니다. 실제 키를 테스트에 넣지 않습니다.
- CI(GitHub Actions)에서 `./gradlew test`가 통과해야 합니다.

## 8. 문서 규칙

- 기능을 구현하거나 바꾸면 `FUNCTION_SPEC.md`, `API_SPEC.md`, `DATABASE_ERD.md` 중 해당되는 문서를 같이 고칩니다.
- 기능 ID, 테이블명, 엔드포인트 경로, 오류 코드는 문서 전체에서 같은 표기를 씁니다.
- 미결정 사항이 해결되면 `REQUIREMENTS.md`의 4절에서 "결정됨"으로 바꾸고 결정 내용을 적습니다.

## 9. 진행 기록 규칙

- 기능 작업이 끝날 때마다 `PROGRESS.md`의 해당 기능 상태와 작업 로그를 갱신합니다.
- 상태 값: `미착수`, `진행 중`, `테스트 중`, `완료`, `보류`.
- 완료 기준: 구현, 단위 테스트, 관련 문서 일치, CI 통과.
