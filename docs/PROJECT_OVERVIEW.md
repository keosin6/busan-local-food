# 프로젝트 개요

## 1. 프로젝트 이름

**busan-local-food** — 부산 맛집 정보 및 리뷰 공유 플랫폼

## 2. 목표

- 부산 지역의 맛집을 한곳에서 찾을 수 있게 합니다.
- 사용자가 직접 맛집을 등록하고, 리뷰와 평점을 남겨 정보를 함께 쌓아갑니다.
- 리뷰가 충분히 쌓인 맛집은 AI가 리뷰를 요약해 빠르게 특징을 파악하게 합니다.
- 플랫폼에 등록된 맛집 안에서 AI가 취향에 맞는 곳을 추천합니다.

## 3. 대상 사용자

| 사용자 | 설명 |
|--------|------|
| 비회원 (Guest) | 맛집 목록, 상세, 검색, 리뷰 열람, AI 요약 열람 |
| 회원 (Member) | 위 기능 + 맛집 등록·수정·삭제, 리뷰 작성·삭제, 찜, AI 추천 |

> 맛집 수정·삭제 권한 정책은 [FUNCTION_SPEC.md](FUNCTION_SPEC.md)의 F1-02 절을 따릅니다.

## 4. 범위

### 포함 (필수 기능)

| ID | 기능 |
|----|------|
| F1-01 | 회원가입, 로그인, 로그아웃, JWT, Google 로그인 |
| F1-02 | 맛집 등록, 수정, 삭제, 목록, 카테고리, 검색 |
| F1-03 | 맛집 이미지 AWS S3 업로드 |
| F1-04 | 리뷰 작성 및 삭제, 평점, 평균 평점 |
| F1-05 | 리뷰 3개 이상일 때 AI 리뷰 요약 및 DB 캐싱 |
| F1-06 | 찜하기 및 찜 목록 |
| F1-07 | DB에 등록된 맛집만 대상으로 한 AI 맛집 추천 |
| F1-08 | 메인 페이지, 카테고리 및 최신 맛집 |

### 제외 (현재 범위 밖)

- 모바일 앱 (네이티브 앱)
- 예약, 결제, 주문 기능
- 외부 지도 API 기반 맛집 자동 수집 (맛집은 사용자가 등록)
- 관리자 승인 워크플로우 (필요 시 추후 설계 변경 제안으로 검토)

> 제외 항목을 범위에 넣으려면 [DEVELOPMENT_RULES.md](DEVELOPMENT_RULES.md)의 설계 변경 절차를 따릅니다.

## 5. 기술 스택 (고정)

| 영역 | 기술 |
|------|------|
| 언어 | Java 21 |
| 프레임워크 | Spring Boot 3.x |
| 보안 | Spring Security (JWT, OAuth2 Client for Google) |
| 데이터 접근 | Spring Data JPA |
| AI | Spring AI, OpenRouter (OpenAI 호환 API) |
| 화면 | Thymeleaf, Bootstrap 5 |
| 데이터베이스 | MySQL 8 |
| 파일 저장소 | AWS S3 |
| 서버 | AWS EC2 (Ubuntu) |
| CI/CD | GitHub Actions |
| 빌드 | Gradle |

## 6. 아키텍처 개요

```
[브라우저]
    │  HTTP (HTML: Thymeleaf / JSON: REST)
    ▼
[Spring Boot 애플리케이션 on AWS EC2]
    ├─ Spring Security  : 회원 인증 (JWT, Google OAuth2)
    ├─ Controller       : 화면 렌더링 + REST API
    ├─ Service          : 비즈니스 로직 (맛집, 리뷰, 찜, AI 요약·추천)
    ├─ Repository (JPA) : 데이터 접근
    ├─ Spring AI ───────────► [OpenRouter] ─► LLM
    └─ S3 Client ───────────► [AWS S3] (맛집 이미지)
    │
    ▼
[MySQL 8]
```

- 모놀리식 구조입니다. 화면과 API는 하나의 Spring Boot 애플리케이션에서 제공합니다.
- 이미지 바이너리는 DB에 저장하지 않고 S3에 저장합니다. DB에는 S3 객체 키만 저장합니다.
- AI 호출은 서버에서만 수행합니다. 브라우저에 API 키를 노출하지 않습니다.

## 7. 배포 개요

- GitHub Actions: `main` 병합 또는 태그 기준으로 빌드·테스트 후 EC2에 배포 (세부 절차는 구현 단계에서 확정)
- EC2(Ubuntu): JAR 실행, 환경 변수는 서버에만 보관
- MySQL 8: 초기 단계에서 EC2 내 또는 별도 인스턴스 중 하나를 선택 (미결정 사항 참조)

## 8. 성공 기준

- 8개 필수 기능 모두 동작하고 기능별 테스트가 통과합니다.
- ERD, API 명세, 실제 구현이 서로 일치합니다.
- 리뷰 3개 이상인 맛집의 AI 요약이 한 번 생성된 뒤 DB에서 재사용됩니다.
- AI 추천 결과에 DB에 없는 맛집이 포함되지 않습니다.
