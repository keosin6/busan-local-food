# busan-local-food
부산 지역 기반 맛집 정보 및 리뷰 공유 플랫폼

사용자가 부산의 맛집을 등록·검색하고, 리뷰와 평점을 남기며, AI가 요약한 리뷰와 AI 맛집 추천을 받을 수 있는 웹 서비스입니다.

> **현재 상태:** 설계 문서 작성 단계입니다. 애플리케이션 코드는 아직 없습니다. 진행 상황은 [docs/PROGRESS.md](docs/PROGRESS.md)를 확인하세요.

## 필수 기능

| ID | 기능 | 요약 |
|----|------|------|
| F1-01 | 회원 | 회원가입, 이메일 로그인, 로그아웃, JWT 인증(Refresh Token 로테이션), Google 로그인, 기존 계정에 Google 계정 직접 연동 |
| F1-02 | 맛집 | 맛집 등록·수정·삭제, 목록, 카테고리, 검색 |
| F1-03 | 이미지 | 맛집 이미지 AWS S3 업로드 |
| F1-04 | 리뷰 | 리뷰 작성·삭제, 평점(1~5), 평균 평점 |
| F1-05 | AI 리뷰 요약 | 리뷰 3개 이상일 때 AI 요약 생성 및 DB 캐싱 |
| F1-06 | 찜 | 찜하기/찜 취소, 내 찜 목록 |
| F1-07 | AI 맛집 추천 | DB에 등록된 맛집만 대상으로 AI 추천 |
| F1-08 | 메인 페이지 | 카테고리, 최신 맛집 |
| F1-09 | 관리자 | 전체 맛집·리뷰 관리, 회원 조회, 관리자 대시보드, S3 삭제 재시도 (`ADMIN` 권한) |

기능 상세는 [docs/FUNCTION_SPEC.md](docs/FUNCTION_SPEC.md)에 있습니다.

## 기술 스택 (고정)

| 구분 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.x, Spring Security, Spring Data JPA, Spring AI |
| AI | OpenRouter (Spring AI의 OpenAI 호환 클라이언트 사용) |
| View | Thymeleaf, Bootstrap 5 |
| Database | MySQL 8 (AWS EC2 내부 설치, RDS 미사용) |
| Infra | AWS EC2 (Ubuntu, MySQL 포함), AWS S3 |
| CI/CD | GitHub Actions |
| Build | Gradle |

기술 스택은 고정입니다. 바꿔야 할 이유가 생기면 [docs/DEVELOPMENT_RULES.md](docs/DEVELOPMENT_RULES.md)의 설계 변경 절차를 따릅니다.

## 문서 목록

| 문서 | 내용 |
|------|------|
| [docs/PROJECT_OVERVIEW.md](docs/PROJECT_OVERVIEW.md) | 프로젝트 목표, 범위, 사용자, 아키텍처 |
| [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) | 기능·비기능 요구사항 |
| [docs/FUNCTION_SPEC.md](docs/FUNCTION_SPEC.md) | 기능별 상세 명세(화면, 처리 흐름, 예외) |
| [docs/DATABASE_ERD.md](docs/DATABASE_ERD.md) | ERD, 테이블 정의, 제약 조건 |
| [docs/API_SPEC.md](docs/API_SPEC.md) | REST API 및 화면 URL 명세 |
| [docs/DEVELOPMENT_RULES.md](docs/DEVELOPMENT_RULES.md) | 개발 규칙, 브랜치·커밋 규칙, 보안 규칙 |
| [docs/PROGRESS.md](docs/PROGRESS.md) | 개발 진행 상황 기록 |

## 환경 변수

보안 정보는 코드나 저장소에 넣지 않고 환경 변수로 주입합니다. 전체 목록은 [docs/DEVELOPMENT_RULES.md](docs/DEVELOPMENT_RULES.md#5-보안-규칙)에 있습니다.

```
DB_URL, DB_USERNAME, DB_PASSWORD
JWT_SECRET, JWT_ACCESS_TOKEN_EXPIRY_SECONDS, JWT_REFRESH_TOKEN_EXPIRY_SECONDS
GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET
AWS_REGION, AWS_S3_BUCKET
OPENROUTER_API_KEY, OPENROUTER_MODEL
```

## 실행 방법

애플리케이션 코드가 작성된 후 이 절에 로컬 실행, 테스트, 배포 방법을 추가합니다.
