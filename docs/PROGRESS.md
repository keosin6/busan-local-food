# 개발 진행 상황 (PROGRESS)

작업 규칙은 [DEVELOPMENT_RULES.md](DEVELOPMENT_RULES.md#9-진행-기록-규칙)를 따릅니다.

상태 값: `미착수` · `진행 중` · `테스트 중` · `완료` · `보류`

## 0. 현재 요약

| 항목 | 상태 |
|------|------|
| Stage 0 문서 기획 | 완료 (Q1~Q6 의사결정 반영 완료, 2026-11-06) |
| Q1~Q6 문서 반영 | 완료 — PR #2로 main 병합 |
| 인증 구조 보완 (JWT 기본 인증과 OAuth2 임시 세션 구분) | 완료 — PR #2에 포함되어 main 병합. Q1 결정 변경 없음 |
| Stage 1 프로젝트 기본 구조 | 완료 — 기본 골격 구현과 검증 완료, PR #3으로 `main` 병합 (2026-11-09). 기능 코드는 포함하지 않음 |
| Stage 2 F1-01 회원·인증 | 진행 중. **`main` 병합 완료 (PR #17, `9f881a0`; PR #19, `dd147e3`)**. 병합된 구현: 회원·Refresh Token 엔티티와 수동 SQL 001~003, 회원가입·로그인 검증 서비스, JWT Access Token 발급·검증, Refresh Token 발급·회전·재사용 탐지·로그아웃 폐기 서비스, 서비스 시간대 고정, 요청 제한 서비스 R1~R10, Refresh 요청 제한 연결과 잠금 재시도, 공통 429 `RATE_LIMITED`와 `Retry-After`, 회원가입 요청 집계 필터, 요청 제한 저장소 장애 시 fail-closed, API 전용 JWT 인증 필터와 API·화면 SecurityFilterChain 분리, Bearer·쿠키 인증, API CSRF 정책. **구현 완료, `main` 병합 대기 (6.17, `feat/stage2-3-auth-http-api`)**: API 인증 HTTP 5종(`POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`, `GET /api/auth/me`), JWT 쿠키 발급·갱신·만료, 로그인·재발급의 요청 제한 HTTP 연결. **`main`에 없는 것**: 화면 로그인·회원가입·로그아웃, Google OAuth2의 HTTP 엔드포인트와 요청 제한 연결, 닉네임 제출 흐름, 프런트엔드 자동 갱신(T20). 나머지는 아래 7절 |
| **다음 개발 단계** | 화면 인증 (Stage 2-4): 화면 로그인·회원가입·로그아웃(2.9~2.11), 화면 로그인 실패 리다이렉트, 화면 세션과 API JWT 인증의 실제 연결. Google OAuth2 시작·계정 연결·콜백의 요청 제한 연결과 Google 신규 가입 닉네임 제출 흐름(경로 확정 후). 6.17 병합 후 `main` 기준으로 다시 확인. 상세는 7절 |

## 1. 단계 요약

| 단계 | 내용 | 상태 | 비고 |
|------|------|------|------|
| 0 | 문서 기획 (기초 구조 + Q1~Q6 의사결정 반영) | 완료 | 2026-11-06 |
| 1 | 프로젝트 기본 구조 (Gradle, Spring Boot 3.5.16, Java 21, 설정 프로필 local/prod, 공통 오류 응답, Security 기본 설정, Bootstrap 5 홈 화면) | 완료 | CI 파이프라인은 Stage 11로 이월. T9 확정 (Testcontainers 미도입) |
| 2 | F1-01 회원·인증 (화면 세션 로그인, API JWT, Refresh Token 로테이션, Google 로그인·수동 연동/해제, 로그아웃) | 진행 중. `main` 병합 완료(PR #17 `9f881a0`, PR #19 `dd147e3`): 회원·DB 기반, 회원가입·로그인 검증 서비스, JWT Access Token, Refresh Token 발급·회전·재사용 탐지·로그아웃 폐기, 요청 제한 서비스 R1~R10, 공통 429와 `Retry-After`, 회원가입 집계 필터, API JWT 인증 필터, API·화면 SecurityFilterChain 분리, Bearer·쿠키 인증, API CSRF 정책. 구현 완료, `main` 병합 대기(6.17, `feat/stage2-3-auth-http-api`): 회원가입·로그인·재발급·로그아웃·내 정보 API, JWT 쿠키 발급·갱신·만료, 로그인·재발급 요청 제한 HTTP 연결. **미구현**: 화면 로그인·회원가입·로그아웃, Google OAuth2 HTTP와 계정 연동·해제, Google 요청 제한 연결과 닉네임 제출 집계, 화면 세션과 API JWT 인증의 실제 연결, 프런트엔드 T20 | 인증 설계 결정 완료 (T3, T7, T8, T11, T12, T13, T14 A안, 2026-11-10). 서비스 계층과 HTTP 연결은 6.2~6.17 |
| 3 | F1-02 맛집 관리 + F1-08 메인 페이지 기본 | 미착수 | 카테고리 시드 포함, 작성자 권한 |
| 4 | F1-03 맛집 이미지 (S3 업로드, `storage_delete_task` 추적) | 미착수 | 의존: F1-02. T6 확인 |
| 5 | F1-04 리뷰 | 미착수 | 평균 평점 포함 |
| 6 | F1-05 AI 리뷰 요약·캐싱 | 미착수 | 의존: F1-04 |
| 7 | F1-06 찜 | 미착수 | |
| 8 | F1-07 AI 맛집 추천 | 미착수 | 의존: F1-02. T10 (모델 설정) |
| 9 | F1-08 메인 페이지 완성 (최신 맛집, 카테고리 연결) | 미착수 | 의존: F1-02 |
| 10 | F1-09 관리자 기능 (role, `/admin/**`, `/api/admin/**`, 대시보드, S3 재시도) | 미착수 | 의존: F1-01, F1-02, F1-03, F1-04. T1, T2 확정 필요 |
| 11 | 배포 (EC2, MySQL 설치, 영속 저장소, 백업, GitHub Actions 배포) | 미착수 | T4, T5 확정 필요 |

## 2. 기능별 상태

| ID | 기능 | 상태 | 테스트 | 관련 문서 상태 |
|----|------|------|--------|----------------|
| F1-01 | 회원가입, 이메일 로그인, 로그아웃, 화면 세션 + API JWT(Refresh Token 로테이션), Google 로그인, Google 계정 수동 연동·해제 | 진행 중 | 아래 두 값은 작업 당시 보고값이며 현재 `main`에서 새로 실행한 결과가 아닙니다. (1) PR #19 (커밋 `eceb2e8`): DB 연결 340/340 통과, DB 미설정 288 통과·52 건너뜀. (2) 6.17 브랜치 (`feat/stage2-3-auth-http-api`): DB 연결 382/382 통과, DB 미설정 323 통과·59 건너뜀, 실패 0. 요청 제한 집계 필터 12개와 공통 429·fail-closed 2개 포함 (6.15). 웹 계층 테스트(MockMvc)는 회원가입·로그인·재발급·로그아웃·내 정보 엔드포인트 포함 | 명세 반영 완료. 인증 설계 확정(T3, T7, T8, T11, T12, T13, T14 A안). API 인증 HTTP 5종 구현 완료, `main` 병합 대기(6.17). 화면 로그인·회원가입·로그아웃, Google OAuth2 HTTP와 계정 연동·해제, Google 요청 제한 연결, 프런트엔드 Access Token 자동 갱신과 T20은 미구현 |
| F1-02 | 맛집 등록, 수정, 삭제(완전 삭제), 목록, 카테고리, 검색 | 미착수 | - | 명세 반영 완료 (Q2, Q5) |
| F1-03 | 맛집 이미지 AWS S3 업로드, 삭제 실패 추적·재시도 | 미착수 | - | 명세 반영 완료 (Q2) |
| F1-04 | 리뷰 작성 및 삭제 (본인만), 평점, 평균 평점 | 미착수 | - | 명세 반영 완료 (Q5) |
| F1-05 | 리뷰 3개 이상 AI 요약 및 DB 캐싱 | 미착수 | - | 명세 반영 완료 (Q6) |
| F1-06 | 찜하기 및 찜 목록 | 미착수 | - | 명세 작성 완료 |
| F1-07 | DB 등록 맛집 대상 AI 추천 | 미착수 | - | 명세 반영 완료 (Q6) |
| F1-08 | 메인 페이지, 카테고리 및 최신 맛집 | 미착수 | - | 명세 작성 완료 |
| F1-09 | 관리자 기능 (USER/ADMIN, 관리자 화면·API, 대시보드, S3 재시도) | 미착수 | - | 명세 신규 작성 (Q5) |

## 3. 결정 현황 (Q1~Q6, T3, T7, T8, T11~T18, T20)

| 번호 | 주제 | 상태 | 결정 내용 요약 |
|------|------|------|----------------|
| Q1 | Google 로그인과 기존 계정 연동 | 결정됨 | 자동 연결 없음. 기존 계정 로그인 후 마이페이지에서 직접 연동·해제. 한 Google 계정은 한 회원에만 연결 |
| Q2 | 맛집 삭제 정책 | 결정됨 | 관련 데이터 포함 완전 삭제. DB는 한 트랜잭션, S3는 커밋 후 삭제. 실패 키는 `storage_delete_task`로 추적·재시도 |
| Q3 | Refresh Token 관리 | 결정됨 | Access/Refresh 분리. Refresh 원문은 HttpOnly Cookie, DB는 SHA-256 해시. 로테이션, 로그아웃 폐기, 운영 Secure |
| Q4 | MySQL 운영 환경 | 결정됨 | EC2 내부 MySQL 8. RDS 미사용. 영속 저장소, 3306 비공개, 백업, 환경 변수 분리, RDS 이전 가능 설계 |
| Q5 | 관리자 기능 | 결정됨 | 필수 범위(F1-09)에 포함. `member.role`(USER/ADMIN). 관리자 전용 화면·API 분리. 서버 측 검증 |
| Q6 | AI 모델 관리 | 결정됨 | `OPENROUTER_API_KEY`, `OPENROUTER_MODEL` 환경 변수. 코드 고정 없음. 개발·운영 상이 가능. 특정 모델 ID 미확정 |
| T11 | OAuth2 임시 상태 저장 방식 | 결정됨 (A안, 2026-11-10) | 화면 세션 기반 Google 계정 연동. OAuth2 `state`와 Authorization Request는 HTTP 세션에 저장. 연동 대상 회원 ID는 연동 시작 시점의 로그인 세션에서 확보. 일반 로그인과 계정 연동은 처리 경로 분리. 연동 과정에서 기존 세션과 API JWT 유지. `/api/**`는 JWT 인증만 허용 |
| T3 | Google 신규 가입 닉네임 정책 | 결정됨 (A안, 2026-11-10) | 프로필 이름 자동 사용 안 함. 별도 닉네임 설정 화면에서 2~20자, 중복 불가로 입력. 검증 후에만 회원 생성. 생성 후 세션과 JWT 발급. OAuth2 임시 세션은 가입 진행 상태만 보관 |
| T7 | Refresh Token 재사용 탐지 정책 | 결정됨 (A안, 2026-11-10) | 로테이션. 폐기된 토큰 재사용 시 회원의 모든 활성 Refresh Token 폐기, 재로그인 필요. 해시 행 보존. 탐지와 폐기는 한 트랜잭션 |
| T8 | Google 연동·해제 본인 확인 | 결정됨 (A안, 2026-11-10) | 연동: 로그인 세션, CSRF 포함 POST, `state` 검증. 해제: 로그인 세션 + 현재 비밀번호 재입력. 비밀번호 없는 회원은 먼저 로컬 비밀번호 설정. 추가 인증·소셜 자동 연결 없음 |
| T12 | 화면 로그아웃 시 Refresh Token 폐기 방식 | 결정됨 (A안, 2026-11-10) | `POST /api/auth/logout` 성공 후에만 `POST /logout`. 첫 요청 실패 시 세션 유지. `refresh_token` Path `/api/auth` 유지, CSRF 유지 |
| T13 | Access Token 만료 시 자동 재발급 방식 | 결정됨 (A안, 2026-11-10) | 401 → 재발급 1회 → 원요청 1회 재시도. 동시 401에도 재발급 1회. 실패 시 재로그인 안내. 화면 세션 갱신 없음 |
| T14 | Refresh Token 동시 갱신 및 재사용 처리 | 결정됨 (A안, 2026-11-10) | 엄격한 T7 재사용 탐지 유지. 탭 간 갱신은 Web Locks API로 조정하고 잠금 안에서 인증 상태를 재확인. 회원 행 잠금 후 재조회, 재사용 탐지 결과는 커밋 후 응답. 네트워크 응답 유실 시 재로그인은 수용된 한계 |
| T15 | 회원가입 Rate Limit 집계 시점 | 결정됨 (2026-11-11) | JSON 파싱·DTO 검증 이전 집계. 잘못된 입력과 Content-Type 오류 포함 (R6) |
| T16 | IP 식별자 저장 방식 | 결정됨 (2026-11-11) | 모든 IP 기반 식별자 HMAC-SHA256, 원문 IP 미저장, 신뢰 프록시 규칙 (R7) |
| T17 | 계열 확인 불가 Refresh 요청 | 결정됨 (2026-11-11) | 별도 IP 제한 없음. 재사용 탐지 우선 (R8) |
| T18 | Google OAuth2 이벤트 보존 | 결정됨 (2026-11-11) | 생성 후 24시간 뒤 삭제 (R9) |
| T20 | Rate Limit 429의 화면 처리 | 결정됨 (2026-11-11) | 재로그인 사유 아님. 쿠키 유지, 자동 로그아웃 없음 (R10) |

## 4. 미확정 사항 (TODO)

| 번호 | 항목 | 필요 시점 |
|------|------|-----------|
| T1 | 회원 정지·탈퇴 정책 | Stage 10 전 |
| T2 | 관리자 권한 부여·변경 방식, 최초 관리자 생성 방법 | Stage 10 전 |
| T4 | DB 백업 주기, 보관 기간, 저장 위치, 복구 절차 | Stage 11 전 |
| T5 | MySQL 설치 방식과 영속 저장소 구성 | Stage 11 전 |
| T6 | S3 삭제 재시도 자동화 여부 | Stage 4 이후 |
| T10 | OpenRouter 사용 모델 ID 선정 (운영 설정 값) | Stage 8 전 |

## 5. 설계 변경 이력

> 이 섹션의 각 행은 기록 날짜 시점의 상태와 작업 범위입니다. 행의 "변경 없음", "미완료", "승인 대기" 같은 표현은 그 날짜 기준입니다. 최신 상태는 1절(단계 요약), 2절(기능 상태), 3절(결정 현황), 4절(미확정 사항), 7절(다음 작업)을 봅니다.

| 날짜 | 변경 내용 | 영향 문서 | 승인 |
|------|-----------|-----------|------|
| 2026-11-06 | 최초 설계 문서 작성 | 전체 | - |
| 2026-11-06 | Q1 반영: 동일 이메일 자동 연결 제거, Google 계정 수동 연동·해제 흐름 추가 | REQUIREMENTS, FUNCTION_SPEC, DATABASE_ERD, API_SPEC | 사용자 결정 |
| 2026-11-06 | Q1 관련 구조 변경: `member`의 `provider`·`provider_id`를 `google_sub`(UNIQUE)로 교체. 한 회원이 이메일 로그인과 Google 연동을 함께 가질 수 있어야 하므로 단일 provider 값으로 표현할 수 없음 | DATABASE_ERD, API_SPEC, FUNCTION_SPEC | 사용자 결정 (구조는 제안 반영) |
| 2026-11-06 | Q2 반영: 맛집 완전 삭제, S3 삭제 분리, 실패 추적용 `storage_delete_task` 테이블 신규 추가 | REQUIREMENTS, FUNCTION_SPEC, DATABASE_ERD, API_SPEC | 사용자 결정 |
| 2026-11-06 | Q3 반영: Refresh Token 로테이션, 쿠키 Path를 `/api/auth`로 확장(로그아웃 포함), 로그아웃은 인증 없이 호출 가능 | REQUIREMENTS, FUNCTION_SPEC, DATABASE_ERD, API_SPEC | 사용자 결정 |
| 2026-11-06 | Q4 반영: 운영 DB를 EC2 내부 MySQL 8로 통일, RDS 미사용, 3306 비공개 | REQUIREMENTS, PROJECT_OVERVIEW, DEVELOPMENT_RULES, README | 사용자 결정 |
| 2026-11-06 | Q5 반영: F1-09 관리자 기능 추가, `member.role` 도입, `/admin/**`·`/api/admin/**` 분리. 별도 관리자 테이블은 만들지 않음 | 전체 | 사용자 결정 |
| 2026-11-06 | Q6 반영: `OPENROUTER_MODEL`로 모델 지정, 코드 고정 금지 | REQUIREMENTS, FUNCTION_SPEC, DEVELOPMENT_RULES, README | 사용자 결정 |
| 2026-11-06 | 맛집 수정·삭제 권한을 작성자 본인으로 한정하고, 관리자 작업은 별도 관리자 API로 분리 | FUNCTION_SPEC, API_SPEC | 사용자 결정 (Q5에 따른 분리) |
| 2026-11-06 | 인증 구조 명확화: 일반 API는 JWT 기반 유지, OAuth2 임시 세션은 Google 흐름에만 사용. 세션 정책을 완전 Stateless로 고정하지 않음 | DEVELOPMENT_RULES 5.7, FUNCTION_SPEC F1-01, API_SPEC 1.1·2.5·2.6, REQUIREMENTS FR-01-14~16·T11 | 사용자 지시 |
| 2026-11-10 | 인증 구조 최종 확정: 화면은 Spring Security 세션, API는 JWT. 화면 로그인·화면 회원가입·Google 로그인은 세션과 JWT를 함께 발급하고, API 로그인·회원가입은 JWT만 발급. 화면 체인과 API 체인을 필터 체인으로 분리 | DEVELOPMENT_RULES 5.3·5.7, FUNCTION_SPEC F1-01, API_SPEC 1.1·2·10·12, REQUIREMENTS FR-01-03·07·08·17~20, README, PROJECT_OVERVIEW | 사용자 결정 |
| 2026-11-10 | 미확정 항목 T12 등록: 화면 로그아웃에서 Refresh Token 쿠키를 읽을 수 없는 문제. 제안만 기록하고 확정하지 않음 | REQUIREMENTS 4.2, FUNCTION_SPEC F1-01, API_SPEC 2.11 | 설계 점검 결과 (보고) |
| 2026-11-10 | 인증 문서 정합성 검토: 로그아웃(T12)·재발급(T13)은 제안만 기록하고 승인 대기. OAuth2 임시 상태와 `/api/**` STATELESS 체인의 충돌을 T11 쟁점으로 기록. 개발 MySQL을 개발 PC 직접 설치로 정정 | DEVELOPMENT_RULES 5.5·5.7·7, FUNCTION_SPEC F1-01, API_SPEC 2.3·2.6·2.11, REQUIREMENTS 4.2, README, PROJECT_OVERVIEW | 점검 결과 (승인 대기 항목 미확정) |
| 2026-11-10 | T11 결정 (A안): 화면 세션 기반 Google 계정 연동으로 확정. OAuth2 임시 상태는 HTTP 세션에 저장, 연동 대상 회원은 연동 시작 시점 세션에서 확보, 일반 로그인과 계정 연동 처리 분리, 연동 중 기존 세션과 JWT 유지 | DEVELOPMENT_RULES 5.7.1, API_SPEC 2.6·2.6.1, FUNCTION_SPEC F1-01, REQUIREMENTS FR-01-15·21, 4.1절 | 사용자 결정 (T11) |
| 2026-11-09 | T9 확정: 테스트 라이브러리는 `spring-boot-starter-test` 유지, Testcontainers·H2·Docker 기반 테스트 환경 미도입. DB 연동 테스트는 테스트 전용 MySQL 8을 사용하고 개발 DB와 분리 | DEVELOPMENT_RULES 2·7절, README, REQUIREMENTS 4.1절 | 사용자 결정 (T9) |

## 6. 작업 로그

> 이 섹션의 각 행은 기록 날짜 시점의 상태와 작업 범위입니다. 행의 "변경 없음", "미완료", "승인 대기" 같은 표현은 그 날짜 기준입니다. 최신 상태는 1절(단계 요약), 2절(기능 상태), 3절(결정 현황), 4절(미확정 사항), 7절(다음 작업)을 봅니다.

| 날짜 | 작업 | 결과 |
|------|------|------|
| 2026-11-06 | 기초 문서 구조 생성 (README, docs 7종) | 완료 (`main` 병합, PR #1) |
| 2026-11-06 | Q1~Q6 의사결정 문서 반영 (브랜치 `docs/architecture-decisions-q1-q6`) | 완료. PR #2로 main 병합. 애플리케이션 코드는 작성하지 않음 |
| 2026-11-06 | PR #2 보완: JWT 기본 인증과 OAuth2 임시 세션의 역할 구분, Google 연결의 `state`·회원 일치 검증 명시 | 완료. 문서만 수정. Q1~Q6 결정 변경 없음 |
| 2026-11-06 | PR #2 최종 보완: Google 연결 콜백의 Access Token 만료 의존 제거. 검증은 연결 시작 시점에만 하고, 콜백은 임시 세션과 `state`로 대상 회원을 정함 | 완료. 문서만 수정 |
| 2026-11-09 | Stage 1 프로젝트 기본 구조 구현 (`feature/project-skeleton`). Spring Boot 3.5.16, Java 21, Gradle, Spring Security 기본 설정(임시 전체 허용), DB 환경변수 설정, 공통 오류 응답, Thymeleaf 홈 화면, Bootstrap 5.3.8 포함 | 완료. 아래 검증 참조. 기능 코드 없음 |
| 2026-11-09 | Stage 1 검증: `gradle clean test` 9개 통과, `gradle bootJar` 성공. MySQL 8.0.46에서 기동, `/`와 정적 리소스 200, 브라우저 확인. 누락·잘못된 DB 설정 시 안내 메시지 확인 | 완료 |
| 2026-11-09 | Gradle wrapper 배포본 SHA-256(gradle.org 공식 체크섬) 설정. wrapper 기반 `./gradlew clean test` 9개 통과, `./gradlew bootJar` 성공 | 완료 |
| 2026-11-09 | Stage 1 중 발견·수정: 설정되지 않은 `${DB_PASSWORD}`가 비밀번호로 쓰이던 문제 → 기동 시점에 누락 변수 이름을 알리고 중단하도록 수정. 방언을 MySQL로 명시하고 기동 시 DB 연결 확인 추가 | 완료 |
| 2026-11-09 | PR #3 Stage 1 프로젝트 기본 구조 구현 완료 및 `main` 병합. Merge commit `519170d8776d86eddaf7433143063af0661ea925` | 병합 완료 상태 기록 |
| 2026-11-09 | 기술 스택 준수 점검 (`build.gradle`, 소스 import, 설정, 정적 리소스). 금지 기술 미사용 확인, 의존성 변경 없음. 인증 방식 충돌은 보고 후 결정 대기. T9 테스트 정책 확정 | 완료 (문서·정책 정리) |
| 2026-11-10 | 화면 세션 + API JWT 인증 구조 문서 반영 (커밋 `627f071`) | 완료. 문서만 수정 |
| 2026-11-10 | 인증 문서 정합성 검토: MySQL 개발 환경을 개발 PC 직접 설치로 정정, OAuth2 임시 상태와 `/api/**` STATELESS 체인의 충돌을 T11 쟁점으로 기록, T12(로그아웃)·T13(재발급) 제안 기록 | 완료. 승인 대기 항목은 미확정 |
| 2026-11-10 | T3(신규 가입 닉네임), T7(Refresh Token 재사용 탐지), T8(연동·해제 본인 확인), T12(화면 로그아웃 순서), T13(401 자동 재발급) A안 확정. 연동 해제를 화면 세션 기반으로 바꾸고 `DELETE /api/me/google` 제거. 로컬 비밀번호 설정 요구(FR-01-22) 추가. FR-01-22~26 추가 | 완료 (문서). 구현 미완료. DATABASE_ERD 263행 "T7에서 확정" 문구는 ERD 변경 금지 조건에 따라 이번에 수정하지 않음 |
| 2026-11-10 | T11 확정 A안을 PROGRESS, DEVELOPMENT_RULES, API_SPEC, FUNCTION_SPEC, REQUIREMENTS, PROJECT_OVERVIEW에 반영 (문서만 수정). 구현은 Stage 2에서 진행 | 완료 (문서). 계정 연동 기능 구현 미완료 |
| 2026-11-10 | T14 Refresh Token 동시 갱신 및 재사용 처리 A안 확정 (브랜치 `docs/finalize-auth-decisions-t3-t7-t8-t12-t13`). 서버는 회원 행 잠금 후 재조회하여 판정하고, 재사용 탐지 결과는 커밋 후 응답. 탭 간 갱신은 Web Locks API로 조정하고 잠금 안에서 인증 상태를 재확인. T7·T13 유지. 네트워크 응답 유실은 수용된 한계. FR-01-27 추가, DEVELOPMENT_RULES 5.7.2·5.7.4, API_SPEC 2.3·2.4, FUNCTION_SPEC F1-01, DATABASE_ERD 2.8 반영. 테이블 구조 변경 없음 | 완료 (문서). 구현 미완료 |
| 2026-11-10 | PR #6 보완: `member.nickname`에 `uk_member_nickname` UNIQUE 추가 (ERD, REQUIREMENTS, 개발 규칙 6.4, API·화면 중복 응답 정리). Refresh Token 동시 갱신 분석을 T14로 등록하고 대안 3개와 권장안(옵션 1)을 기록. 사용자 결정 전 구현하지 않음. ※ 같은 날 T14 A안으로 확정됨 (위 2026-11-10 행 참조). ERD 263행 T7 문구 정리 | 완료 (문서). 코드·DB 미변경
| 2026-11-10 | T3·T7·T8·T12·T13 A안 문서 반영 (브랜치 `docs/finalize-auth-decisions-t3-t7-t8-t12-t13`, `main` 기준) | 완료. 문서만 수정. 당시 범위: 코드·의존성·DB·ERD 변경 없음 (이 행 이후 7586d62에서 ERD에 `uk_member_nickname`과 T14 정책 반영). Stage 2 미착수 |

### 6.1 Stage 2-1 기록 (회원·DB 기반 구현)

- 작업 브랜치: `feat/f1-01-member-foundation` (기준: `main` 789b476, PR #6 병합 커밋)
- 범위: 회원 Entity·Repository, Refresh Token Entity·Repository, BCrypt 설정, 수동 DDL. 회원가입·로그인·JWT·Google 로그인·토큰 발급은 미구현.
- 추가한 의존성 (Spring Boot BOM 관리, 버전 임의 지정 없음): `spring-boot-starter-oauth2-client`, `spring-security-oauth2-jose`. 기존 `spring-boot-starter-security`와 `spring-boot-starter-test`는 유지.
- Entity·Repository:
  - `member.domain.Member`: DATABASE_ERD 2.1 컬럼 매핑. 이메일 소문자·공백 제거 정규화. 생성 시 역할은 항상 `USER`. 비밀번호 해시와 Google sub 중 최소 하나 필요 (DB CHECK와 같은 조건).
  - `member.domain.Role`: `USER`, `ADMIN`.
  - `member.domain.RefreshToken`: DATABASE_ERD 2.8 매핑. 원문을 받는 생성 경로 없음. 해시는 소문자 16진수 64자만 허용.
  - `member.repository.MemberRepository`: 이메일 조회·존재 여부, 닉네임 존재 여부, Google sub 조회.
  - `member.repository.RefreshTokenRepository`: 해시 조회, 회원별 폐기되지 않은 토큰 조회.
  - `global.config.PasswordEncoderConfig`: `BCryptPasswordEncoder` Bean.
- 수동 DDL: `db/manual/001_create_member_refresh_token.sql` (MySQL 8.0.16 이상). 자동 실행되지 않으며 CREATE TABLE만 포함하고 데이터는 건드리지 않음. 제약 이름은 `uk_member_email`, `uk_member_nickname`, `uk_member_google_sub`, `ck_member_role`, `ck_member_login_method`, `uk_refresh_token_hash`, `fk_refresh_member`(ERD 2.8 이름 준수), `idx_refresh_member`.
- 테스트 DB 연동 테스트 실행 방법: 테스트 전용 MySQL 8에 `db/manual/001_create_member_refresh_token.sql`을 적용한 뒤 `db/manual/002_add_refresh_token_family_id.sql`을 순서대로 적용하고, 환경 변수 `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`를 설정하여 실행. 002를 적용하지 않은 테스트 DB에서는 Refresh Token 관련 DB 테스트가 실패합니다. 변수가 없으면 DB 테스트 18개(`MemberPersistenceDbTest` 15개, `MemberRegistrationDbTest` 3개)는 건너뜀. 개발 DB에는 연결하지 않음. H2·Testcontainers 미사용.
- 테스트 결과:
  - `./gradlew clean test` (테스트 DB 변수 미설정): 29개 중 19개 통과, 10개 건너뜀(DB 테스트), 실패 0개.
  - `./gradlew clean test` (테스트 DB 설정 및 DDL 적용, 격리된 MySQL 8.0.46 테스트 인스턴스): 29개 모두 통과. DB 제약(UNIQUE, CHECK, FK) 검증 포함.
  - `./gradlew bootJar`: 성공.
  - 기동 확인: 생성된 jar를 테스트 DB 설정으로 실행. `/`와 `/css/app.css`가 200 응답, 시작 로그에 오류 없음.
- 확인한 사실:
  - MySQL은 CHECK 제약 위반을 SQLSTATE `HY000`으로 보고하므로 Spring에서 `DataIntegrityViolationException`이 아니라 `UncategorizedSQLException`(`DataAccessException`의 하위)으로 감싸집니다. 서비스 계층에서 CHECK 위반을 사용자 메시지로 바꾸지 않고, 입력 검증을 먼저 수행합니다.
  - 테이블 기본 collation을 `utf8mb4_unicode_ci`로 두었으므로 닉네임·이메일의 유일성 비교는 대소문자와 악센트를 구분하지 않습니다. 이전에 확인이 필요하다고 기록한 collation 항목은 이 기준으로 정리되었습니다.
- 설계와의 차이 및 결정 필요 사항:
  - Refresh Token FK 이름: 작업 지시서는 `fk_refresh_token_member`를 요구했으나, 설계 기준인 DATABASE_ERD 2.8은 `fk_refresh_member`입니다. 지시서의 "ERD와 일치" 원칙에 따라 ERD 이름을 사용했습니다. 이름을 바꾸려면 ERD를 함께 고쳐야 합니다.
  - CHECK 제약 이름(`ck_member_role`, `ck_member_login_method`)은 ERD에 이름이 없어 이번 DDL에서 부여했습니다. ERD에도 같은 이름을 기록하는 것이 좋습니다.
  - `ddl-auto`는 `none`을 유지했습니다. JPA 매핑과 실제 스키마의 일치는 DB 연동 테스트로 확인했습니다. 스키마 검증 모드(`validate`)는 설정하지 않았습니다.
- 미완료 항목 (이번 단계 제외):
  - 회원가입·로그인 서비스와 화면·API
  - JWT 생성·검증, Refresh Token 발급·로테이션·재사용 탐지·폐기
  - Google OAuth2 로그인·계정 연동·신규 가입 닉네임 설정
  - SecurityFilterChain 분리 (현재 임시 전체 허용 정책 유지)
  - 자동 토큰 갱신 JavaScript
- 기타: 격리 테스트용 MySQL 인스턴스(포트 3307)는 작업 디렉터리 밖 데이터 경로에만 두었고, 기존 개발 DB에는 접속하지 않았습니다. 테스트 계정 비밀번호는 저장소에 기록하지 않았습니다.

### 6.2 Stage 2-2 기록 (회원가입·로그인 자격 증명 검증 서비스)

- 작업 브랜치: `feat/f1-01-signup-login-service` (기준: `main` bcdac46, Stage 2-1 병합 커밋 `Feat/f1 01 member foundation (#7)`)
- 범위: 회원가입 서비스, 로그인 자격 증명 검증 서비스, 입력 검증 규칙, 오류 코드, 단위 테스트, DB 제약 위반 변환. 컨트롤러, JWT, 세션, Refresh Token, Google OAuth2, SecurityFilterChain은 미구현.
- 구현:
  - `member.service.MemberRegistrationService`: `register(email, password, passwordConfirm, nickname)`. 검증 → 이메일 중복 → 닉네임 중복 → BCrypt 해시 → `Member.create` → `saveAndFlush`. 저장 시 UNIQUE 위반은 제약 이름(`uk_member_email`, `uk_member_nickname`)으로 구분해 `EMAIL_DUPLICATED`, `NICKNAME_DUPLICATED`로 바꾸고, 그 외 무결성 오류는 그대로 전파.
  - `member.service.MemberLoginService`: `authenticate(email, password)`. 미가입·비밀번호 불일치·Google 전용 계정은 모두 `INVALID_CREDENTIALS`. Google 전용 계정은 BCrypt 비교를 하지 않음. 미가입 이메일은 더미 BCrypt 비교를 수행.
  - `member.service.MemberInputRules` (패키지 전용): 이메일·닉네임·비밀번호 규칙. 이메일 정규화는 `Member.normalizeEmail`(public으로 변경)을 재사용.
  - `global.exception.ErrorCode`: `EMAIL_REQUIRED`, `EMAIL_INVALID`, `EMAIL_DUPLICATED`, `NICKNAME_REQUIRED`, `NICKNAME_INVALID`, `NICKNAME_DUPLICATED`, `PASSWORD_REQUIRED`, `PASSWORD_INVALID`, `INVALID_CREDENTIALS` 추가 (HTTP 상태는 지시서 표 기준).
- 검증 규칙 요약: FUNCTION_SPEC F1-01 "회원가입·로그인 검증 규칙" 참조. 비밀번호는 8~20자, 영문·숫자·특수문자 각 1자 이상, 공백 불가, 확인값 일치. 로그인은 필수 여부만 확인.
- 추가·변경한 테스트:
  - `MemberRegistrationServiceTest` (40개): 정상 가입, 정규화, BCrypt 해시 저장, 필수·형식·길이·중복 오류, 이메일 우선 순위, UNIQUE 위반 변환, 다른 무결성 오류 전파, 오류 메시지에 원문 미포함.
  - `MemberLoginServiceTest` (15개): 정상 로그인, 로그인에 가입 규칙 미적용, 필수·형식 오류, 미가입·불일치·Google 전용의 동일 오류 코드, 메시지에 해시·원문 미포함.
  - `MemberRegistrationDbTest` (3개, DB 테스트): 정상 저장(BCrypt 해시), 사전 검사를 spy로 우회한 이메일·닉네임 경합에서 실제 MySQL UNIQUE 위반이 중복 오류로 변환되는지 확인. 환경 변수 `TEST_DB_*`가 없으면 건너뜀.
- 테스트 결과:
  - `./gradlew clean test` (테스트 DB 변수 미설정): 87개 중 74개 통과, 13개 건너뜀(DB 테스트), 실패 0개.
  - `./gradlew clean test` (테스트 DB 설정, DDL 적용, 격리된 MySQL 8.0.46): 87개 모두 통과. 테스트 후 `member`, `refresh_token` 행 0개 확인.
  - `./gradlew bootJar`: 성공.
- 설계와 다른 점 및 결정 사항:
  - API_SPEC 1.3의 `VALIDATION_FAILED`는 일반 입력 검증 실패로 남기고, 회원가입·로그인 입력 오류는 세부 코드를 사용하도록 정리. 다른 API의 `VALIDATION_FAILED` 사용은 그대로 유지.
  - 비밀번호 규칙이 기존 문서의 "8자 이상"보다 강화됨. FUNCTION_SPEC, API_SPEC 2.1·2.7.1, DEVELOPMENT_RULES 5.7.5를 함께 수정. 로컬 비밀번호 설정과 Google 신규 가입 닉네임 규칙도 같은 기준을 사용.
  - 비밀번호 확인값이 없으면 `PASSWORD_REQUIRED`로 처리. 지시서에 명시되지 않은 부분이므로 확인 필요.
  - 이메일 형식은 단순 정규식. 필요 시 정책 재검토.
  - 닉네임 중복은 테이블 collation(`utf8mb4_unicode_ci`)에 따라 대소문자와 악센트를 구분하지 않음. 사전 검사와 UNIQUE 제약이 같은 기준을 따른다.
  - Google 전용 계정 응답 시간 차이는 PR #8 보완에서 해소: 모든 로그인 경로가 BCrypt 검증을 정확히 한 번 수행하며, 비밀번호 해시가 없으면 더미 해시와 비교한다.
- 미완료 항목: 컨트롤러와 화면 폼 연결, JWT·Refresh Token, Google OAuth2, SecurityFilterChain 분리, 신규 가입 닉네임 설정 화면.

### 6.3 PR #8 보완 기록 (로그인 검증 시간 차이, Unicode 공백)

- 브랜치: `feat/f1-01-signup-login-service` (기존 PR #8에 추가 커밋).
- 보완 ①: `MemberLoginService.authenticate`가 모든 경로에서 `PasswordEncoder.matches`를 정확히 한 번 호출. 비밀번호 해시가 없는 경우(미가입, Google 전용)는 더미 해시와 비교. 응답은 `INVALID_CREDENTIALS`로 동일.
- 보완 ②: 비밀번호 공백 검사를 정규식 `\s`에서 코드 포인트 단위 `Character.isWhitespace || Character.isSpaceChar`로 변경. NBSP, EM SPACE, IDEOGRAPHIC SPACE 등 Unicode 공백을 거부.
- 추가 테스트:
  - `MemberLoginBcryptCallTest` (5개, Mockito): 미가입·Google 전용·일반 회원·불일치 경로의 `matches` 호출 횟수, 미가입과 Google 전용의 외부 응답 동일성.
  - `MemberRegistrationServiceTest` (49개 중 추가): Unicode 공백 8종 거부, 공백 없는 정상 비밀번호 허용.
- 검증 결과:
  - `./gradlew clean test` (테스트 DB 미설정): 101개 중 88개 통과, 13개 건너뜀(DB 테스트), 실패 0개.
  - 격리된 MySQL 8.0.46, DDL 적용, `TEST_DB_*` 설정: 101개 모두 통과. 테스트 후 `member`, `refresh_token` 0행.
  - `./gradlew bootJar`: 성공.
  - 보완 ②의 테스트가 실제로 결함을 잡는지 확인: 수정 전 `\s` 검사로 되돌리면 NBSP, EM SPACE, IDEOGRAPHIC SPACE 케이스가 실패함을 확인한 뒤 수정본을 복원.
- 참고 (이번 범위 밖): 이메일 형식 정규식과 `trim()`은 Unicode 공백을 완전히 다루지 않는다. 예: 이메일 로컬 파트의 NBSP는 현재 형식 검사를 통과할 수 있다. 정책 결정 후 별도 보완이 필요하다. Java의 `isWhitespace`에 포함되지 않는 제로 폭 공백(U+200B)은 이번 검사 대상이 아니다.

### 6.4 인증 보안 설계 통합 반영 기록 (2026-11-11)

- 브랜치: `docs/finalize-auth-rate-limit-security` (기준: `main` 65a29b3, PR #8 병합 커밋).
- 범위: 문서 전용. 코드, 테스트, DDL, SQL 파일, 의존성, Gradle 설정은 변경하지 않았습니다.
- 반영 내용:
  - 요청 제한 정책 (API_SPEC 1.4, FR-01-28~FR-01-36, 당시 기준 R1~R4). 로그인, 회원가입, Refresh, Google OAuth2 기준, `RATE_LIMITED`와 `Retry-After`, 429 응답의 쿠키 보존.
  - Refresh 계열 UUID `family_id`. 회전 시 유지, 새 기기 로그인에서 새로 생성 (DATABASE_ERD 2.8).
  - 재사용 탐지(T7)를 요청 제한보다 먼저 처리하고, 커밋 후 401 응답 (API_SPEC 2.3, DEVELOPMENT_RULES 5.7.4).
  - 개인정보: 로그인 이벤트에는 정규화 이메일과 IP의 HMAC-SHA256만 저장. 원문 이메일과 원문 IP 미저장. 프록시 전달 IP는 신뢰 설정된 프록시만 사용.
  - 이벤트 보존 24시간 (로그인, 회원가입, Refresh). Google 이벤트 보존은 권장안 (T18).
  - 요청 제한 테이블 5개의 설계안과 MySQL 행 잠금 구조 (DATABASE_ERD 2.10). 정확한 컬럼 타입과 인덱스는 구현 전 검토.
  - 쿠키 Domain 미설정 명시, JWT 원문은 응답 본문에 포함하지 않음 명시 (API_SPEC 1.1).
- 정정: DATABASE_ERD 6의 Flyway 정책을 `db/manual/` 수동 SQL 정책으로 바꿨습니다. 프로젝트는 Flyway를 사용하지 않습니다 (REQUIREMENTS 3과 일치).
- 미결정: T15~T20 (REQUIREMENTS 4.2에 권장안 기록).
- 코드 범위 밖 확인 사항: `src/main/resources/application.yml`의 주석이 Flyway를 언급합니다. 코드 변경 금지 조건에 따라 수정하지 않았습니다.
- 기존 DDL `db/manual/001_create_member_refresh_token.sql`은 변경하지 않았습니다. 신규 컬럼과 테이블은 구현 단계에서 새 순번 SQL로 추가합니다.

### 6.5 PR #9 보완 기록 (인증 보안 설계 일관성)

- 브랜치: `docs/finalize-auth-rate-limit-security` (기존 PR #9에 추가 커밋).
- 범위: 문서 전용. 코드, 테스트, DDL, SQL 파일, 의존성, Gradle 및 애플리케이션 설정 파일은 변경하지 않았습니다.
- Google OAuth2 집계 경계 (R5, T19 대체): 연동 시작 POST는 문서에 정의된 엔드포인트이며 Google OAuth2 IP 카운터에 포함합니다. 로그인 시작(GET), 연동 시작(POST), 콜백(GET)은 각각 1회 집계합니다. 연동 시작 POST 뒤의 Google 인증 페이지 `302`와 내부 전달은 추가로 세지 않습니다. 존재하지 않는 엔드포인트는 만들지 않았습니다.
- HMAC 키 관리 (DEVELOPMENT_RULES 5.8): 단일 키, 이중 키 미도입, 교체 전 기존 요청 제한 데이터 삭제를 권장 절차로 정리했습니다. 기다려서 만료시키는 방식은 교체 전 24시간 동안 요청 제한 기능을 멈춰야 하므로 권장하지 않는다고 명시했습니다. 키 유출 의심 시에는 삭제 방식만 씁니다.
- `family_id` 표현 (DATABASE_ERD 2.8, DEVELOPMENT_RULES 5.3·5.7.4, API_SPEC 2.3): "개인정보가 아니다"를 제거하고, 서버 생성 가명 식별자로서 접근 통제, 로그 원문 미출력, 외부 노출 금지를 명시했습니다.
- 변경 금지 항목 (임계값, 집계 기준, T7·T14 정책, 429 쿠키 유지, 보존 기간, Flyway 미사용)은 바꾸지 않았습니다. T15~T18, T20은 미확정 상태를 유지했습니다.

### 6.6 PR #9 최종 정책 반영 기록 (2026-11-11)

- 브랜치: `docs/finalize-auth-rate-limit-security` (기존 PR #9에 추가 커밋).
- 범위: 문서 전용. 코드, 테스트, DDL, SQL 파일, 의존성, Gradle 및 애플리케이션 설정 파일은 변경하지 않았습니다.
- T15: 회원가입 요청 제한은 JSON 파싱과 DTO 검증 이전에 집계합니다 (R6).
- T16: 모든 IP 기반 식별자는 HMAC-SHA256으로 저장하고 원문 IP는 이벤트 테이블에 저장하지 않습니다. 신뢰 프록시가 전달한 `X-Forwarded-For`만 신뢰합니다 (R7).
- T17: 계열을 확인할 수 없는 Refresh 요청에는 별도 IP 제한을 두지 않습니다. 재사용 탐지(T7)가 Rate Limit보다 먼저 실행됩니다 (R8).
- T18: Google OAuth2 이벤트도 생성 후 24시간 뒤 삭제합니다 (R9).
- T20: 429는 재로그인 사유가 아닙니다. 쿠키와 인증 상태를 유지하고 `Retry-After`에 따라 재시도합니다. Refresh 429에서 자동 로그아웃하지 않습니다 (R10).
- 미확정 Rate Limit 항목은 없습니다. 기존 확정 정책(임계값, 집계 단위, T7·T14, 429 쿠키 유지, Flyway 미사용)은 바꾸지 않았습니다.

### 6.7 Stage 2-3 요청 제한 DB 스키마 확정 (2026-11-11)

- 브랜치: `docs/stage2-3-rate-limit-db-schema` (기준: `main` 53b131b, PR #9 병합 커밋).
- 범위: 수동 SQL 002, 003 작성과 ERD 확정. 코드, Entity, Repository, Controller, JWT, Refresh 회전, Rate Limit 서비스, Spring Security 설정, 테스트 코드, Gradle, 애플리케이션 설정은 변경하지 않았습니다.
- SQL 002 (`db/manual/002_add_refresh_token_family_id.sql`): `refresh_token.family_id`를 `CHAR(36) ascii_bin`으로 추가합니다. NULL 허용으로 추가한 뒤 행마다 UUID를 부여하고, NOT NULL로 바꾸고, `idx_refresh_family`를 만듭니다. 기존 001 파일과 기존 행의 나머지 컬럼은 변경하지 않습니다.
- SQL 003 (`db/manual/003_create_auth_rate_limit_tables.sql`): `login_failure_event`, `signup_request_event`, `refresh_request_event`, `google_oauth_request_event`와 `auth_rate_limit_counter`를 생성합니다. FK는 두지 않습니다. 원문 이메일, 원문 IP, 원문 Refresh Token 컬럼은 없습니다.
- ERD: 2.8의 `family_id`를 확정 타입으로 표시하고 `idx_refresh_family`를 확정했습니다. 2.10을 확정 스키마 표로 바꾸고, 다이어그램에 요청 제한 엔티티를 추가했습니다. 6.1절에 SQL 적용 순서를 기록했습니다.
- 검증 (격리된 MySQL 8.0.46 테스트 DB, 개발 DB와 운영 DB에는 실행하지 않음):
  - SQL 002 실행 후 샘플 5행 모두 `family_id`가 NOT NULL이고 서로 다른 값을 가졌습니다. 나머지 컬럼의 체크섬은 실행 전후 같았습니다.
  - SQL 003 실행 후 5개 테이블의 컬럼 타입, 문자셋, NULL 조건, 인덱스 이름과 대상 컬럼이 ERD와 일치하며 FK는 0개입니다.
  - 카운터 잠금 패턴(`INSERT ... ON DUPLICATE KEY UPDATE` 후 `SELECT ... FOR UPDATE`)은 같은 키로 두 번 실행해도 행이 하나만 남았습니다.
  - SQL 002와 003을 다시 실행하면 중복 컬럼과 기존 테이블 오류로 중단됩니다.
- 미검증 사항: 운영 DB와 개발 DB에서의 실제 적용, 애플리케이션과 DB 연동 테스트, 정리 작업과 카운터 정리 절차는 구현 단계에서 검증합니다.
- UUID 정책: 2026-11-12 확정 (6.8 참조). 002의 `UUID()` 백필은 UUID v1 일회성 사용이며, 신규 계열 ID는 애플리케이션이 생성하는 UUID v4입니다. MySQL `RANDOM_BYTES()`는 8.0.18부터 지원되므로 최소 버전 8.0.16과 맞지 않아 신규 값 생성에 쓰지 않습니다.

### 6.8 Refresh 계열 ID(family_id) UUID 정책 확정 (2026-11-12)

- 범위: 문서 보완. SQL 001, 002, 003과 Java 코드는 변경하지 않았습니다. 신규 계열 ID 생성 로직은 아직 구현되지 않았으며 완료로 보지 않습니다.
- 확정 정책 (사용자 A안):
  1. 기존 Refresh Token 행의 `family_id` 백필(SQL 002)에는 MySQL `UUID()`가 생성하는 UUID v1을 허용합니다.
  2. UUID v1은 기존 데이터의 일회성 백필에만 사용합니다. 신규 계열 생성에는 사용하지 않습니다.
  3. 신규 Refresh Token 계열 ID는 애플리케이션이 UUID v4로 생성합니다.
  4. 토큰 회전 시 기존 `family_id`를 그대로 유지합니다.
  5. 새 기기 로그인 시에만 새로운 UUID v4를 생성합니다.
  6. `family_id`는 회원 및 인증 세션과 연관될 수 있는 가명 식별자입니다. 외부 API 응답, 클라이언트 데이터, 일반 애플리케이션 로그에 노출하지 않습니다.
  7. UUID v1은 시간 및 서버 노드 관련 정보를 포함할 수 있음을 인지합니다. 백필된 값도 내부 식별 목적으로만 사용합니다.
- 백필과 신규 생성의 구분:
  - SQL 002의 백필은 002 적용 시점에 기존 행에만 적용되는 일회성 작업입니다.
  - 002 적용 이후 생성되는 모든 행의 `family_id`는 애플리케이션이 UUID v4로 만든 값을 INSERT에 명시해야 합니다. `family_id`는 NOT NULL이며 DB 기본값이 없습니다.
- 근거: FR-01-36, DEVELOPMENT_RULES 5.3·5.7.4, DATABASE_ERD 2.8. 기존 6.7절의 "확인 필요" 항목은 이 절에서 확정되었습니다.

### 6.9 Stage 2-3 JWT Access Token 발급·검증 구현 (2026-11-12)

- 브랜치: `feat/stage2-3-jwt-issuance-validation` (기준: `main` 2db3534).
- 구현 (`member.jwt` 패키지):
  - `AccessTokenIssuer`: HS256 서명, 클레임 `sub`, `iss`, `aud`, `iat`, `exp`, `role`. 만료는 설정값(기본 1800초).
  - `AccessTokenVerifier`: 서명, 알고리즘(HS256 고정), 발급자, 대상, 만료와 미래 발급 시각(60초 시계 오차), 클레임 존재와 형식을 검증. 성공 시 회원 ID와 권한을 돌려줌.
  - `JwtValidators`: 검증 규칙 조립. `JwtSigningKeys`: Base64 키 로딩과 시작 시 검증. `JwtProperties`, `JwtConfig`, `JwtSpec`, 값 타입 3종.
  - `application.yml`: `jwt.secret`(환경 변수 `JWT_SECRET`), `jwt.access-token-expiry-seconds`(환경 변수 `JWT_ACCESS_TOKEN_EXPIRY_SECONDS`, 기본 1800) 추가.
- 의존성: 추가 없음. 기존 `spring-security-oauth2-jose`를 사용.
- 미구현 (이번 범위 밖): Refresh Token 발급·저장·회전, 재사용 탐지(T7), T14 동시 갱신, 요청 제한 서비스(R1~R10), 로그인·회원가입 API 컨트롤러, 로그아웃, 최종 SecurityFilterChain 분리와 JWT 인증 필터 연결, Google OAuth2.
- 검증:
  - `./gradlew clean test` (DB와 `JWT_SECRET` 미설정): 160개 중 147개 통과, 13개 건너뜀 (DB 테스트), 실패 0개. JWT 테스트 59개 전부 통과.
  - `./gradlew clean test` (격리된 MySQL 8.0.46, `TEST_DB_*` 설정): 160개 중 158개 통과, 실패 2개.
    - 실패: `MemberPersistenceDbTest`의 Refresh Token 2건.
    - 원인: `main`에 병합된 002 SQL로 `refresh_token.family_id`가 NOT NULL이 되었지만, `RefreshToken` Entity는 이 컬럼을 채우지 않습니다. INSERT가 `Field 'family_id' doesn't have a default value`로 실패합니다.
    - 이 문제는 JWT 변경 이전부터 `main`에 있던 불일치입니다. Refresh Token 발급 범위이므로 이번 PR에서는 Entity를 수정하지 않았습니다.
  - `./gradlew bootJar`: 성공.
  - 기동 확인 (생성된 jar, 격리 DB 연결):
    - `JWT_SECRET` 없음: 기동 실패. 메시지에 `JWT_SECRET`이 표시됩니다.
    - 32바이트 이상 Base64 키: 기동 성공, `/`는 200.
    - 16바이트 키: 기동 실패.
    - 실패 로그에 키 값은 나타나지 않았습니다.
- 운영 주의:
  - 로컬과 운영에서 `JWT_SECRET`을 반드시 설정해야 기동됩니다.
  - 키를 교체하면 기존 Access Token이 무효화될 수 있습니다.
  - `JWT_REFRESH_TOKEN_EXPIRY_SECONDS`는 Refresh Token 구현 단계에서 사용합니다.
- 보완 (민감정보 로그 노출 방지, 기존 브랜치 후속 커밋):
  - `IssuedAccessToken.toString()`: `token`을 `[REDACTED]`로 마스킹. `issuedAt`, `expiresAt`는 그대로 표시.
  - `JwtProperties.toString()`: `secret`을 `[REDACTED]`로 마스킹. `accessTokenExpirySeconds`는 그대로 표시.
  - 두 record의 생성자, 접근자, 환경 변수 바인딩과 검증 로직은 변경하지 않았습니다.
  - 신규 테스트 `SensitiveToStringTest` 6개 추가: 발급 토큰 원문 미노출, Base64 secret 원문 미노출, `null` 필드에서도 예외 없음, 접근자와 만료 값 유지.
  - 보안 스캔: `member.jwt` 패키지에 로깅 호출과 `printStackTrace`, `System.out` 사용이 없음을 확인. 예외 메시지는 고정 문자열이며 키나 토큰 값을 넣지 않음.
    - 잔여 확인 사항(원인 예외 보존)은 아래 "검증 실패 예외 원인 노출 방지" 보완에서 해결했습니다.
  - 검증 결과 (이번 보완 커밋 기준, DB 미설정):
    - `./gradlew clean test`: 166개 중 153개 통과, 13개 건너뜀(DB 테스트), 실패 0개. JWT 테스트는 65개 전부 통과 (기존 59 + 신규 6).
    - `./gradlew bootJar`: 성공.
    - 이번 실행에서는 격리 MySQL을 연결하지 않았습니다. DB 테스트 2건의 실패는 위 "검증"에 기록한 기존 `family_id` 불일치이며 별도 과제로 남깁니다.
- 검증 실패 예외 원인 노출 방지 (기존 브랜치 후속 커밋):
  - `AccessTokenVerifier`: 디코더의 `JwtException`과 `IllegalArgumentException`을 원인 예외 없이 `InvalidAccessTokenException("token rejected")`로 변환합니다. subject 숫자 변환 실패도 원인 예외 없이 `subject format`으로 변환합니다.
  - `InvalidAccessTokenException`: 원인 예외를 받는 생성자를 제거했습니다. 사용처는 `AccessTokenVerifier`뿐이었습니다. 고정 사유 생성자만 남깁니다.
  - 고정 내부 사유 메시지(`token missing`, `token rejected`, `subject format`, `role value`)와 발생 조건은 그대로입니다. 외부 응답 규격과 검증 정책은 바꾸지 않았습니다.
  - 신규 테스트 `AccessTokenVerifierExceptionTest` 5개: `JwtException` 변환 시 cause 없음, 라이브러리 메시지에 토큰 조각이 섞여도 고정 메시지만 유지되고 토큰 원문 미포함, `IllegalArgumentException` 동일 처리, 비숫자 subject 변환 시 cause 없고 subject 값 미포함, 토큰 누락 고정 메시지 유지. Mockito로 디코더를 대체했습니다. 기존 JWT 테스트는 삭제하거나 축소하지 않았습니다.
  - 잔여 보안 확인 사항 해결 여부: 해결. 검증 실패 예외에는 원인 예외와 토큰 원문이 더 이상 포함되지 않으므로, 전체 예외 로깅에서도 라이브러리 메시지 경로가 열려 있지 않습니다.
  - 검증 결과 (이번 보완 커밋 기준, DB 미설정):
    - `./gradlew clean test`: 171개 중 158개 통과, 13개 건너뜀(DB 테스트), 실패 0개. JWT 테스트는 70개 전부 통과 (기존 59 + 민감정보 6 + 원인 예외 5).
    - `./gradlew bootJar`: 성공.
    - 격리 MySQL 미연결. `family_id` 관련 DB 테스트 2건은 이번 작업에서 재확인하지 않았으며, 별도 과제로 유지합니다.

### 6.10 Stage 2-3 Refresh Token Family ID 적용 (2026-11-12)

- 브랜치: `feat/stage2-3-refresh-token-family-id` (기준: `main` e595bf2, JWT PR #11 병합본).
- 구현 (`member.domain.RefreshToken`):
  - `familyId` 필드를 `family_id CHAR(36) ascii_bin NOT NULL`에 매핑합니다. 값은 소문자 문자열로 관리합니다.
  - 기존 `issue(...)`는 family_id 없이 행을 만들 수 있으므로 제거하고, 두 팩토리로 바꿨습니다.
    - `issueNewFamily(member, tokenHash, expiresAt)`: `UUID.randomUUID()`로 UUID v4를 만들어 새 계열을 시작합니다.
    - `issueInFamily(member, familyId, tokenHash, expiresAt)`: 전달받은 family_id를 그대로 유지합니다. 회전 시 이전 행의 값을 넘기면 됩니다.
  - `issueInFamily`는 소문자 canonical 형식의 UUID v1(SQL 002 백필)과 v4만 허용합니다. 대문자, 하이픈 없는 값, 앞뒤 공백, v5, nil UUID, 잘못된 variant는 거부합니다. 예외 메시지에는 입력값을 넣지 않습니다.
  - `tokenHash` 검증 정책은 그대로 두고, 두 팩토리 모두 같은 검증을 거칩니다.
  - `toString()`에서 `family_id`와 `token_hash`를 출력하지 않습니다.
- 변경하지 않은 것: SQL 001·002·003, JWT 발급·검증 로직, 요청 제한, Gradle 의존성.
- 테스트:
  - `RefreshTokenTest` 12개: 기존 해시 검증 2개 유지, 신규 Family UUID v4 생성과 서로 다른 값 확인, 기존 Family ID(v1·v4) 유지, 회전 시 같은 계열 유지, 잘못된 값 거부, 예외 메시지와 `toString()`에 민감값 없음.
  - `MemberPersistenceDbTest` 15개: 기존 10개 유지, 신규 5개 추가. 신규 Family 저장과 재조회, 회전 토큰이 같은 family_id로 저장됨, v1 family_id 저장, `family_id` 컬럼이 `CHAR(36) ascii_bin NOT NULL`인지 확인, `family_id`를 빼면 NOT NULL 위반, FK 위반 테스트는 유효한 UUID v4를 넣어 `fk_refresh_member` 위반을 직접 확인하도록 수정했습니다.
  - `MemberRegistrationDbTest`: 주석의 테스트 DB 전제조건을 001+002로 갱신했습니다. 테스트 로직은 바꾸지 않았습니다.
- 실행 결과 (최종 커밋 기준):
  - `./gradlew clean test`, `TEST_DB_*` 설정, 격리된 MySQL 8.0.46에 SQL 001 → 002 순서로 적용: 186개 중 186개 통과, 건너뜀 0개, 실패 0개. 6.9에 기록된 DB 테스트 실패 2건(`family_id` 불일치)은 이번 변경으로 해결되었습니다.
  - `./gradlew clean test`, `TEST_DB_URL` 미설정: 186개 중 168개 통과, 건너뜀 18개(모두 DB 테스트), 실패 0개. DB 테스트를 통과로 세지 않았습니다.
  - `./gradlew bootJar`: 성공.
- 운영 주의:
  - 이 코드는 `family_id` 컬럼이 있어야 INSERT됩니다. 배포 대상 DB에 SQL 002가 적용되어 있는지 `SHOW CREATE TABLE refresh_token`으로 먼저 확인합니다.
  - SQL 002는 한 번만 실행합니다. 이미 적용된 DB에 다시 실행하지 않습니다.
- 미구현 (이번 범위 밖): Refresh Token 실제 발급 서비스, 회전(rotation) 서비스, 재사용 감지, Family 단위 전체 폐기, 요청 제한, JWT 인증 필터 연결, 로그아웃.

### 6.11 Stage 2-3 Refresh Token 생명주기 구현 (2026-11-12)

- 브랜치: `feat/stage2-3-refresh-token-lifecycle` (기준: `main` b8be4e4, PR #12 병합본).
- 구현 (`member.refresh` 패키지, 엔티티와 저장소 보강):
  - `RefreshTokenGenerator`: `SecureRandom`으로 256비트를 뽑아 URL-safe Base64(패딩 없음, 43자)로 인코딩합니다.
  - `RefreshTokenHasher`: 원문의 SHA-256을 소문자 16진수 64자로 계산합니다. 입력값은 예외 메시지에 넣지 않습니다.
  - `RefreshTokenProperties`: `JWT_REFRESH_TOKEN_EXPIRY_SECONDS`(기본 1209600초). Access Token 만료 설정(`JwtProperties`)과 분리했습니다.
  - `RefreshTokenConfig`: 만료 계산과 폐기 시각에 쓰는 `Clock`을 제공합니다.
  - `IssuedRefreshToken`: 원문과 만료 시각. `toString()`에서 원문을 `[REDACTED]`로 마스킹합니다.
  - `RefreshTokenRotation`: 결과 타입(sealed). `Rotated`(정상 회전, 회원 ID·권한·새 토큰), `Rejected`(알 수 없음·만료), `ReuseDetected`(재사용 탐지, 폐기 건수). 예외로 던지지 않습니다.
  - `RefreshTokenService`: `issueForNewLogin(memberId)`, `rotate(rawToken)`, `logout(rawToken)`.
  - `RefreshToken` 엔티티: `revoke(at)`(처음 폐기 시각 유지, 중복 호출 안전), `isRevoked()`, `isExpiredAt(now)`. 원문 필드는 없습니다.
  - `MemberRepository.findByIdForUpdate`: 회원 행 `PESSIMISTIC_WRITE` 잠금.
  - `RefreshTokenRepository`: 잠금 없는 사전 조회(`findMemberIdByTokenHash`, 회원 ID만 읽음), 잠금 재조회(`findByTokenHashForUpdate`), 회원 활성 토큰 잠금(`findActiveByMemberIdForUpdate`, ID 오름차순).
- 처리 흐름:
  - 신규 로그인: 회원 확인 → 원문 생성 → 해시 → `issueNewFamily`(UUID v4) → INSERT → 원문과 만료 시각 반환.
  - 정상 회전: 사전 조회 → 회원 행 잠금 → 토큰 행 잠금 재조회 → 폐기 여부 판정 → 기존 행 `revoked_at` 기록 → `issueInFamily`로 같은 family_id의 새 행 INSERT → 커밋 후 반환.
  - 판정 순서: 폐기됨(재사용) → 만료(거부, 다른 행 유지) → 정상 회전. 알 수 없는 토큰은 행을 바꾸지 않고 거부합니다.
  - 재사용 탐지: 같은 트랜잭션에서 회원의 활성 행을 ID 순서로 잠가 모두 폐기하고 `ReuseDetected`를 반환합니다. 예외를 던지지 않으므로 전체 폐기가 롤백되지 않습니다. 폐기된 행은 삭제하지 않습니다.
  - 로그아웃: 회원 행 → 토큰 행 순서로 잠가 해당 행만 폐기합니다. 알 수 없는 토큰과 이미 폐기된 토큰은 아무 작업 없이 끝납니다. 로그아웃된 토큰을 나중에 쓰면 재사용으로 탐지됩니다 (5.7.4).
- 동시성 (T14 A안, 잠금 순서 회원 → 토큰): 신규 발급(`issueForNewLogin`)도 회원 행을 `findByIdForUpdate`로 잠근 뒤 발급합니다. 재사용 탐지·회전·로그아웃과 같은 잠금으로 직렬화됩니다 (PR #13 보완). 참고: 잠금 없는 조회라도 INSERT가 FK 때문에 부모 회원 행을 기다리므로, 블로킹 테스트만으로는 명시적 잠금 호출을 구분할 수 없습니다. 명시적 호출은 단위 테스트가 `findByIdForUpdate` 호출과 `findById` 미호출로 확인합니다. 사전 조회는 판정에 쓰지 않습니다. 잠금 뒤에 다시 읽습니다. InnoDB의 REPEATABLE READ 스냅샷에 가려지지 않도록 재조회도 `FOR UPDATE` 잠금 읽기로 합니다. 외부 호출은 트랜잭션 안에서 하지 않습니다.
- 보안 점검 (JWT 패키지와 동일 기준):
  - 난수는 `SecureRandom`만 사용합니다. `Random`과 `Math.random()`은 쓰지 않습니다.
  - 원문은 `IssuedRefreshToken`으로만 전달합니다. Entity, 로그, 예외 메시지, `toString()`에 넣지 않습니다.
  - DB에는 SHA-256 해시만 저장합니다. 통합 테스트에서 원문이 어떤 컬럼에도 없음을 확인했습니다.
  - 로그는 재사용 탐지 시 회원 ID와 폐기 건수만 남깁니다. `family_id`와 토큰은 남기지 않습니다.
  - 고정 토큰이나 테스트용 키를 운영 코드에 넣지 않았습니다. 신규 라이브러리를 추가하지 않았습니다.
- 테스트:
  - 단위: `RefreshTokenGeneratorAndHasherTest` 7개(원문 길이와 인코딩, 중복 없음, SHA-256 표준 벡터, 원문 미노출, 빈 값 거부, 만료 설정 검증), `RefreshTokenServiceTest` 8개(신규 계열 발급, 정상 회전과 family 유지, 만료 거부, 알 수 없는 토큰 거부, 재사용 탐지 결과 반환과 예외 없음, 회원 행 → 토큰 행 잠금 순서, 로그아웃 안전성, 로그아웃의 빈 잠금), `RefreshTokenStateTest` 3개, `RefreshTokenTest` 12개.
  - MySQL 통합: `RefreshTokenLifecycleDbTest` 9개. 테스트 트랜잭션을 열지 않아 커밋된 값을 직접 확인합니다. 각 테스트 후 생성한 행을 삭제합니다.
    1. 신규 계열 발급 후 해시만 저장되고 family_id는 UUID v4
    2. 정상 회전 후 이전 행 폐기, 새 행은 같은 family_id, 이전 해시 보존
    3. 만료 토큰 거부, 다른 활성 토큰 유지
    4. 폐기 토큰 재사용 시 회원의 모든 활성 토큰 폐기가 커밋되고 예외 없이 결과 반환, 폐기 행 보존
    5. 서로 다른 회원 간 영향 없음
    6. 로그아웃 후 중복 로그아웃 안전, 로그아웃된 토큰 재사용은 재사용 탐지
    7. 같은 토큰 동시 갱신 5회 반복: 한 요청은 `Rotated`, 다른 요청은 `ReuseDetected`, 끝난 뒤 활성 토큰 0개
    8. 다른 트랜잭션이 회원 행을 잠근 동안 신규 발급이 끝나지 않고 대기, 해제 후 완료
    9. 신규 발급과 재사용 탐지를 동시에 5회 실행: 결과는 두 직렬 순서 중 하나와 일치, 신규 발급은 옛 family_id를 쓰지 않고 UUID v4 생성, 옛 계열 행은 모두 폐기
- 실행 결과 (최종 커밋 기준):
  - `./gradlew clean test`, DB 미설정: 213개 중 186개 통과, 27개 건너뜀(DB 테스트 27개: `MemberPersistenceDbTest` 15, `MemberRegistrationDbTest` 3, `RefreshTokenLifecycleDbTest` 9), 실패 0개.
  - `./gradlew clean test`, 격리 MySQL 8.0.46에 SQL 001 → 002 적용, `TEST_DB_*` 설정: 213개 전부 통과, 건너뜀 0개, 실패 0개.
  - `./gradlew bootJar`: 성공.
- 설정: `application.yml`에 `jwt.refresh-token-expiry-seconds: ${JWT_REFRESH_TOKEN_EXPIRY_SECONDS:1209600}`를 추가했습니다. README와 DEVELOPMENT_RULES의 환경 변수 표에는 이미 같은 항목이 있어 바꾸지 않았습니다.
- 미구현 (이번 범위 밖):
  - 로그인·회원가입 컨트롤러와 쿠키 설정 (화면, API, Google 공통 서비스만 준비됨)
  - `POST /api/auth/refresh`, `POST /api/auth/logout` HTTP 컨트롤러. 결과 타입을 401 `TOKEN_INVALID`로 바꾸는 매핑도 여기서 합니다. `ErrorCode`에 `TOKEN_INVALID`는 아직 없습니다.
  - Access Token 재발급과 JWT 인증 필터 연결, SecurityFilterChain 분리
  - Google OAuth2 로그인과 연동
  - 요청 제한 서비스 (R1~R10). 재발급의 제한 판정 순서는 컨트롤러 단계에서 맞춥니다.
  - T13 프런트엔드 자동 갱신, T14 Web Locks API
- 운영 주의:
  - 재사용 탐지는 회원의 모든 기기에서 로그아웃시킵니다 (T7 A안). 응답 유실로 정상 사용자가 재로그인해야 할 수 있습니다 (T14 A안이 수용한 한계).
  - Refresh Token 폐기만으로 이미 발급된 Access Token이 즉시 무효화되지 않습니다. 이는 확정된 정책입니다 (5.7.4 한계).

### 6.12 전체 코드 검수 후 정리 (2026-11-12)

- 브랜치: `fix/timezone-and-auth-cleanup` (기준: `main` 0fe4356, PR #13 병합본).
- 시간대 고정 (서비스 시간대 Asia/Seoul):
  - 문제: 시각은 JVM 기본 시간대의 `LocalDateTime`으로 저장하는데, JVM·JDBC·DB 세션 시간대가 고정되어 있지 않았습니다. JVM이 UTC이고 DB URL이 `serverTimezone=Asia/Seoul`인 조합에서 앱이 저장한 값과 DB의 `NOW()`가 9시간 어긋나는 것을 실제 실행으로 확인했습니다.
  - `global.config.TimeConfig`: `SERVICE_ZONE` 상수, 공용 `Clock` 빈, `applyDefaultTimeZone()`. `main()`이 기동 직후 JVM 기본 시간대를 고정합니다.
  - `application.yml`: `spring.datasource.hikari.connection-init-sql: SET time_zone = '+09:00'`로 DB 세션 시간대를 고정합니다.
  - `build.gradle`: 테스트 JVM에 `user.timezone=Asia/Seoul`을 지정합니다 (의존성 변경 없음).
  - 주의: `DB_URL`에 다른 시간대 옵션을 넣지 않습니다. 이 변경 이전에 KST가 아닌 JVM으로 저장한 행이 있다면 그 시각은 보정되지 않습니다.
- `Clock` 통일: JWT 발급·검증과 Refresh Token 서비스가 같은 `Clock` 빈을 주입받습니다. `RefreshTokenConfig`의 별도 Clock 빈과 `JwtConfig`의 `Clock.systemUTC()` 직접 사용을 없앴습니다.
- Refresh 요청 제한 판정 지점: `RefreshRotationGate`와 결과 `RefreshTokenRotation.RateLimited`를 추가했습니다. `rotate()`의 판정 순서는 재사용 탐지 → 요청 제한 → 만료 → 정상 회전입니다 (API_SPEC 2.3). 요청 제한 서비스(R4)는 아직 없으므로 기본 구현은 모든 요청을 통과시킵니다. 서비스 구현 시 `RefreshRotationGate` 빈을 등록해 대체합니다.
- 미사용 코드 제거: 잠금 없는 `RefreshTokenRepository.findAllByMemberIdAndRevokedAtIsNull`을 삭제했습니다. 상태 변경 경로가 실수로 잠금을 우회하지 않게 하기 위해서입니다.
- 테스트 추가:
  - `ApplicationContextDbTest` 3개 (DB 테스트): 전체 컨텍스트 기동과 인증 빈 연결, `Clock` 빈 1개, JVM·DB 세션 시간대 일치, 앱 저장 시각과 DB `NOW()`의 기준 일치.
  - `RefreshTokenServiceTest` 4개 추가: 제한 시 토큰 미변경, 재사용 탐지가 제한보다 먼저, 제한 판정이 만료 확인보다 먼저, 알 수 없는 토큰은 제한 판정 없음.
- 실행 결과:
  - `./gradlew clean test`, 격리 MySQL 8.0.46 (SQL 001 → 002), `TEST_DB_*` 설정: 220개 전부 통과, 건너뜀 0개. DB URL에 `serverTimezone=Asia/Seoul`을 붙인 경우와 붙이지 않은 경우 모두 같은 결과입니다.
  - `./gradlew clean test`, DB 미설정: 220개 중 190개 통과, 30개 건너뜀(DB 테스트), 실패 0개.
  - `./gradlew bootJar`: 성공. jar 기동 확인: 정상 기동, `/` 200, 로그 시각이 +09:00으로 표시됩니다.
- 이번에 정리하지 않은 항목 (결정이 필요함):
  - 폐기·만료된 `refresh_token` 행의 보존 기간과 정리 방식.
  - 이메일 형식의 Unicode 공백 처리 (아래 7번 4항).
  - `GlobalExceptionHandler`의 공통 예외 처리와 `SecurityConfig` 임시 전체 허용: API 컨트롤러 단계에서 교체합니다.

### 6.13 문서 동기화와 잠금 없는 조회 제거 (2026-11-12)

- 브랜치: `docs/sync-progress-and-rules` (기준: `main` eda7885, PR #14 병합본). 병합 후 재검수에서 나온 항목입니다.
- 문서:
  - `PROGRESS.md` 0·1·2절의 요약을 현재 구현 상태로 갱신했습니다.
  - `API_SPEC.md` 1.5의 "Refresh Token 발급과 회전은 후속 단계" 문구를 현재 상태(서비스 구현, HTTP 미구현)로 고쳤습니다.
  - `DEVELOPMENT_RULES.md`에 5.10 시간 처리 규칙을 추가하고, 7절에 테스트 DB 준비 절차(SQL 001 → 002, `TEST_DB_*`)를 추가했습니다.
  - `README.md`: 현재 상태, 실제로 읽는 환경 변수(`JWT_SECRET` 필수), 수동 SQL 적용, DB 테스트 실행 방법, 시간대, 문제 해결 항목을 갱신했습니다.
- 코드: 잠금 없이 Entity를 읽는 `RefreshTokenRepository.findByTokenHash`를 제거했습니다. 운영 코드에서는 쓰지 않았고 테스트 한 곳만 사용했습니다. 해당 테스트는 `findByTokenHashForUpdate`를 쓰도록 바꿨습니다. `RefreshRotationGate` 주석에 실제 구현의 등록 방법을 적었습니다.

### 6.14 요청 제한 서비스 R1~R10 구현 (2026-11-12)

- 브랜치: `feat/stage2-3-auth-rate-limiting`. **`main` 병합 완료**: PR #16, 병합 커밋 `50963bc405bb91d03e5f365a83e489c366024622` (2b643b1 기준).
- 상태 구분:
  - 서비스 계층: 구현 완료. R1~R10 판정과 기록, Refresh 게이트 연결, 이벤트 정리.
  - HTTP 계층: 미구현. 필터·컨트롤러 연결, 429 `RATE_LIMITED`와 `Retry-After` 변환, 회원가입 JSON 파싱 이전 집계, Google OAuth2 시작·연동·콜백 연결, 프런트엔드 T20 처리.
- 구현 (`member.ratelimit` 패키지):
  - `AuthRateLimitService`: `attemptLogin`(로그인 두 제한과 자격 증명 확인), `recordSignupRequest`, `recordGoogleOAuthRequest`, `recordRefreshRequest`. 각 호출은 카운터 잠금, 판정, 기록을 한 트랜잭션에서 끝냅니다. 한도에 도달하면 기록 없이 `Limited`를 반환합니다.
  - 동시성: 카운터 행을 `INSERT ... ON DUPLICATE KEY UPDATE`로 확보하고 `SELECT ... FOR UPDATE`로 사전순 잠금을 건 뒤 이벤트를 셉니다.
  - 로그인은 자격 증명 확인을 잠금 안에서 합니다. 잠금 밖에서 세면 동시 요청이 한도를 넘습니다. 대신 BCrypt 시간만큼 같은 이메일+IP와 같은 IP의 로그인이 직렬화됩니다.
  - 성공 로그인은 해당 이메일+IP 조합의 실패 이벤트만 지웁니다. 같은 행에 IP가 있으므로 지운 실패는 IP 보조 집계에서도 빠집니다. 한 조합의 실패는 최대 4회까지 쌓이므로 영향은 제한적입니다.
  - `LockRetryTemplate`(보완 1): 잠금 대기와 교착 상태를 최대 3번 재시도합니다. 활성 트랜잭션이 없으면 새 트랜잭션을 열고, 실패하면 롤백한 뒤 처음부터 다시 실행합니다. 이미 외부 트랜잭션이 열려 있으면 재시도하지 않습니다. 계속 실패하면 `RateLimitUnavailableException`으로 요청을 허용하지 않습니다(fail-closed).
  - `RefreshTokenRateLimitGate`: `RefreshRotationGate`의 실제 구현(빈 하나). 재사용 탐지 → 요청 제한 → 만료 → 회전 순서를 지킵니다. 제한된 요청은 토큰을 바꾸지 않습니다. 기본 허용 구현은 제거했습니다.
  - `ClientIpResolver`: 신뢰 프록시(`app.security.trusted-proxies`)의 체인을 오른쪽부터 판별합니다. 목록이 비어 있으면 헤더를 신뢰하지 않습니다. 잘못된 항목이 있으면 Remote Address를 씁니다.
  - `AuthRateLimitHasher`: 이벤트 식별자는 HMAC-SHA256, 카운터 키는 접두어+식별값의 SHA-256입니다. 키는 `AUTH_RATE_LIMIT_HMAC_SECRET`(Base64, 32바이트 이상)이며, 누락, 형식 오류, 길이 부족, JWT 키와 같은 값이면 기동이 실패합니다.
  - 정리: 요청 시(배치 100건, 외부 트랜잭션 밖에서만) + 주기(`AuthRateLimitCleanupScheduler`). 생성 후 24시간이 지난 이벤트만 지웁니다. 카운터 행은 지우지 않습니다. 정리 실패는 로그에 예외 종류만 남기고 요청 결과를 바꾸지 않습니다.
- 보완 1: Refresh 경로의 재시도
  - 문제: `rotate()`의 `@Transactional`이 바깥 트랜잭션을 먼저 열어서, 게이트의 `inTransaction()`이 재시도 없이 그 트랜잭션에 참여했습니다.
  - 해결: `rotate()`에서 `@Transactional`을 제거했습니다. `rotate()`는 `LockRetryTemplate`으로 트랜잭션을 열고 `rotateInTransaction()`을 실행합니다. 재시도는 트랜잭션 경계 바깥에서 새 트랜잭션으로 합니다.
  - 잠금 순서는 바뀌지 않았습니다: 회원 행 → Refresh Token 행 → 재사용 확인 → 요청 제한 카운터 → 제한 판정 → 만료 → 회전.
  - 재사용 탐지의 전체 폐기는 마지막 시도가 성공할 때만 커밋됩니다. 중간 실패는 롤백되므로 일부만 폐기되는 상태가 생기지 않습니다.
  - 재시도 중 이벤트가 중복되지 않습니다. 실패한 시도의 이벤트도 같은 트랜잭션이므로 롤백됩니다.
  - 검증: `rotate()`에 `@Transactional`을 다시 붙이면 재시도 테스트 3건이 실패합니다(잠금 실패 예외가 그대로 전파됨). 되돌린 뒤 통과했습니다.
- 보완 1에서 추가한 테스트 (`RefreshRateLimitRetryDbTest`, 5개, MySQL): 카운터 잠금 실패 후 새 트랜잭션 재시도와 실패 시도 이벤트 미잔존, 재시도 한도 초과의 fail-closed(토큰과 이벤트 불변), 제한된 Refresh 요청의 토큰 불변, 정상 회전의 이벤트 1건, 재사용 탐지의 활성 토큰 잠금 실패 후 재시도 뒤 전체 폐기 커밋.
- 검증 결과 (커밋 기준, 보완 후):
  - 테스트 DB 연결(SQL 001 → 002 → 003): 275개 전부 통과, 건너뜀 0개, 실패 0개. 기존 270개와 신규 5개.
  - 테스트 DB 미설정: 275개 중 223개 통과, 건너뜀 52개(DB 테스트), 실패 0개. 기존 47개와 신규 5개.
  - `bootJar`: 성공.
  - 요청 제한 단위 테스트 33개, 요청 제한 MySQL 테스트 17개는 그대로 유지되고 통과합니다.
  - 잠금 검증(보완 전 실험): 로그인 카운터 잠금을 제거하면 동시성 테스트 2건이 실패합니다(기대 20회, 실제 26회 / 기대 5회, 실제 11회). 되돌린 뒤 통과했습니다.
- 미구현 (이번 범위 밖):
  - 요청 제한 HTTP 연결(필터·컨트롤러, 429·Retry-After 변환)
  - 회원가입 집계를 JSON 파싱 이전에 연결 (CSRF 뒤, 본문 읽기 이전)
  - Google OAuth2 시작·연동·콜백에서 `recordGoogleOAuthRequest` 호출, 콜백 제한 시 OAuth2 임시 상태 정리
  - 로그인·회원가입·재발급·로그아웃 컨트롤러와 쿠키 발급·삭제
  - JWT 인증 필터, SecurityFilterChain 분리
  - 프런트엔드 T20 처리
- 운영 주의:
  - HMAC 키 교체는 DEVELOPMENT_RULES 5.8의 절차를 따릅니다. 이벤트와 카운터를 먼저 지우고 새 키를 설정합니다.
  - 로그인 집계는 같은 IP의 로그인을 BCrypt 시간만큼 직렬화합니다. 같은 NAT 뒤 사용자가 몰리면 대기가 늘 수 있으므로 운영에서 확인합니다.
  - `issueForNewLogin`과 `logout`은 이번 보완의 재시도 대상이 아닙니다. 두 메서드의 잠금 대기 실패는 기존처럼 바로 예외로 전파됩니다.

### 6.15 요청 제한 HTTP 연결 (회원가입 집계, 공통 429) (2026-11-17)

- 브랜치: `feat/stage2-3-auth-rate-limit-http` (기준: `main` 50963bc, PR #16 병합본). **`main` 병합 완료: PR #17, merge commit `9f881a0cca892b28f342dec9fc399814b40001a8`, 2026-11-17.** 아래 기록은 작업 당시 기준이며, 현재 상태는 7절을 따릅니다.
- 구현:
  - `ErrorCode.RATE_LIMITED`(429). 메시지에는 제한 기준이나 식별 정보를 넣지 않습니다.
  - `RateLimitExceededException`(재시도 초, 1 이상 검증). `GlobalExceptionHandler`가 429 `RATE_LIMITED`와 `Retry-After` 헤더로 바꿉니다. 쿠키는 건드리지 않습니다.
  - `RateLimitUnavailableException`: `GlobalExceptionHandler`가 500 `INTERNAL_ERROR`로 바꿉니다(fail-closed). 제한 초과가 아니므로 429가 아닙니다.
  - `SignupRateLimitFilter`: `POST /signup`, `POST /api/auth/signup` 정확 일치만 집계합니다. `SecurityConfig`에서 `CsrfFilter` 뒤에 추가합니다. 서블릿 빈으로 등록하지 않아 두 번 실행되지 않습니다. 본문을 읽지 않으므로 Controller가 본문을 그대로 읽습니다. 판정 실패 예외는 공통 오류 응답으로 보냅니다. 처리되지 않은 예외는 다시 던집니다.
- 실행 순서 (확인됨): CSRF 검증 → 회원가입 집계 → Content-Type 확인과 JSON 파싱 → DTO 검증 → 회원가입 처리. CSRF 실패는 집계되지 않고 403입니다. 제한 초과는 JSON 파싱 전에 429로 끝납니다.
- 조사 결과와 설계 판단:
  - 회원가입, 로그인, 재발급, 로그아웃 컨트롤러와 OAuth2 클라이언트 설정이 `main`에 없습니다. 그래서 로그인과 Refresh, Google OAuth2의 집계 HTTP 연결은 이번 작업에서 안전하게 할 수 없어 미구현으로 둡니다. 존재하지 않는 엔드포인트를 임의로 만들지 않았습니다.
  - 회원가입 집계 필터는 `/signup`과 `/api/auth/signup`에 대해 Controller가 없어도 동작합니다. 그 경우 POST는 집계된 뒤 404가 됩니다. 실제 jar에서 CSRF 토큰 없는 요청은 403이며 집계 테이블에 0건이었습니다.
  - Google 닉네임 제출 경로는 문서에서도 미확정이므로 집계 대상에 넣지 않았습니다.
- 검증 (커밋 기준):
  - 신규 테스트 14개: `SignupRateLimitFilterTest` 12개(CSRF 실패 미집계, CSRF 통과 후 집계와 본문 읽기, 잘못된 JSON·필수 필드 누락·지원하지 않는 Content-Type 집계, 제한 시 JSON 파싱 전 429·`Retry-After`·쿠키 미변경·본문에 IP와 내부 정보 없음, 화면과 API의 공유 카운터, 대상이 아닌 POST와 GET 미집계, 신뢰 프록시 X-Forwarded-For 사용과 비신뢰 무시, fail-closed 500). `GlobalExceptionHandlerTest` 2개 추가(429·Retry-After·Set-Cookie 없음·내부 값 미포함, fail-closed 500).
  - 필터 순서 검증: 필터를 `CsrfFilter` 앞으로 옮기면 CSRF 실패 미집계 테스트가 실패하는 것을 확인했습니다. 되돌린 뒤 통과했습니다.
  - Refresh: 게이트가 준 재시도 초가 `RateLimited` 결과에 그대로 실리는지 단위 테스트로 확인했습니다. HTTP 연결은 재발급 컨트롤러가 없어 미구현입니다.
  - 기존 테스트 모두 유지. 기존 `HomeControllerTest`와 `GlobalExceptionHandlerTest`에는 `SecurityConfig`가 요구하는 요청 제한 빈을 목으로 추가했습니다(테스트 삭제나 검증 축소 없음).
  - 테스트 DB 연결(SQL 001→003): 289개 전부 통과, 건너뜀 0개, 실패 0개.
  - 테스트 DB 미설정: 289개 중 237개 통과, 건너뜀 52개(DB 테스트), 실패 0개.
  - `bootJar`: 성공. jar 기동 확인: 정상 기동, `GET /` 200, CSRF 토큰 없는 `POST /signup`과 `POST /api/auth/signup`은 403이고 집계 0건.
- 미구현 (작업 당시 범위 밖, 현재 상태 아님. 현재 미구현 목록은 7절):
  - Google OAuth2 시작·연동·콜백 집계와 OAuth2 임시 상태 보존 규칙 (OAuth2 흐름 미구현)
  - Google 닉네임 제출 경로 확정과 집계
  - 로그인 HTTP 연결 (로그인 API 미구현)
  - Refresh HTTP 연결과 `RateLimited` → `RateLimitExceededException`(429) 매핑 (재발급 API 미구현)
  - 로그인·회원가입·재발급·로그아웃 컨트롤러와 쿠키, JWT 인증 필터, SecurityFilterChain 분리, 화면 세션, 프런트엔드 T20
- 운영 주의: 회원가입 집계는 Controller가 생기기 전까지 `/signup`과 `/api/auth/signup`의 CSRF 통과 POST를 집계한 뒤 404로 응답합니다. 집계 테이블 기록이 그만큼 늘 수 있으므로 회원가입 Controller를 가까운 시점에 연결합니다.

### 6.16 API/화면 SecurityFilterChain 분리와 API JWT 인증 필터 (2026-11-17)

- 브랜치: `feat/stage2-3-jwt-auth-filter-security-chains` (기준: `main` 5168f07, PR #18 병합본). PR #19는 `main`에 squash 병합.
- 상태: PR #19 `main` 병합 완료 (merge commit `dd147e3f595825047d017ddcbb20df95f55ec40f`). 구현 범위, 보안 정책, 테스트 기록은 아래에 보존한다. 미구현 항목은 아래 미구현 목록과 7절을 따른다.
- PR #19 구현 범위 (`main` 병합 완료):
  - API 전용 SecurityFilterChain 분리 (`/api/**`, `@Order(1)`)
  - 화면용 SecurityFilterChain 유지 (`@Order(2)`, 기존 세션 인증 정책 그대로)
  - API Stateless 인증 (세션 미생성·미사용)
  - JWT Access Token 인증 필터 (`AccessTokenAuthenticationFilter`)
  - Bearer 헤더 및 `access_token` 쿠키 인증
  - Bearer 우선순위, 잘못된 Bearer의 쿠키 대체 방지
  - Bearer 상태 변경 요청의 CSRF 면제 (스킴 기준)
  - 쿠키 인증 및 비-Bearer 요청의 CSRF 보호
  - `XSRF-TOKEN` 쿠키 설정 (API_SPEC 1.1)
  - 회원가입 요청 제한 필터를 두 체인에 연결 (CSRF 뒤, 본문 읽기 이전). 필터 자체는 PR #17에서 `main`에 병합됨
  - 미구현(완료로 보지 않음): 화면 로그인·리다이렉트, API 로그인·Refresh·로그아웃 엔드포인트, Google OAuth2 체인, 닉네임 제출 집계, 공개 조회 API 경로
- 구현:
  - API 체인 (`/api/**`, `@Order(1)`): JWT만 인증 근거로 쓴다. `STATELESS`, `NullSecurityContextRepository`. 세션을 만들지도 읽지도 않는다. 화면 세션만 있는 API 요청은 401이다.
  - 화면 체인 (그 밖의 모든 요청, `@Order(2)`): 기존 세션 인증과 기존 임시 허용 정책 유지. JWT 필터를 넣지 않는다. JWT 쿠키만 있어도 화면은 인증 상태가 되지 않는다.
  - `AccessTokenAuthenticationFilter` (첫 구현): `access_token` 쿠키를 기존 `AccessTokenVerifier`로 검증한다. 성공하면 `AccessTokenAuthentication`(주체 = 회원 ID, 권한 = `ROLE_<role>`, 자격 증명 없음)을 설정한다. 실패하면 인증 없이 진행하고, 쿠키는 건드리지 않는다. 매 요청 새 컨텍스트를 만든다. 원문과 원인은 로그에 남기지 않는다. Bearer 헤더 지원과 우선순위는 아래 PR #19 보완에서 추가했다.
  - 인증 진입점: 보호된 API의 인증 실패는 401 `TOKEN_INVALID`(공통 오류 응답 형식)다. `ErrorCode.TOKEN_INVALID`를 추가했다.
  - CSRF (API 체인): 켜 둔다. 토큰은 API_SPEC 1.1대로 `XSRF-TOKEN` 쿠키와 `X-XSRF-TOKEN` 헤더로 주고받는다. `CsrfCookieFilter`가 API 응답마다 쿠키를 내려준다. 화면 체인의 세션 기반 CSRF는 바꾸지 않았다. 면제 조건(Bearer 스킴만)은 아래 PR #19 추가 보완에서 정했다.
  - 공개 인증 경로: API 체인에서 POST `/api/auth/signup`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`만 인증 없이 허용한다. 공개 조회 API(예: `GET /api/categories`)는 아직 구현되지 않았으므로 열지 않았다. 구현 시 경로를 명시해서 추가한다.
  - 회원가입 집계 필터: 두 체인 모두에 두었다. CSRF 뒤, 본문 읽기 이전에 실행된다. 기존 429와 `Retry-After`, 쿠키 미삭제 정책 그대로다.
- 설계 판단: 세션 저장소 설정만으로는 MockMvc에서 세션 재사용을 찾지 못했다. Spring Security 6은 명시적으로 저장할 때만 세션에 쓰고, JWT 필터는 매 요청 컨텍스트를 덮어쓴다. 그래서 두 겹으로 막았다. 설정(STATELESS, Null 저장소)과 필터의 컨텍스트 교체다. 그리고 "정상 API JWT 요청은 세션을 만들지 않는다"는 테스트를 추가했다. 설정만 제거한 돌연변이에서는 이 테스트도 통과했다. 설정 자체의 제거는 관찰 가능한 동작으로는 드러나지 않는다. 이 한계는 보고한다.
- 테스트 (첫 구현 시점, 신규 24개. 이후 보완 결과는 아래 참조):
  - `AccessTokenAuthenticationFilterTest` 4개: 쿠키 없음, 검증 성공 시 회원 ID와 ROLE, 검증 실패 시 이전 컨텍스트를 이어받지 않음, 빈 쿠키.
  - `ApiSecurityFilterChainTest` 20개(첫 구현 시점, 보완 후 40개. 실제 SecurityConfig와 실제 JwtConfig): 정상 USER·ADMIN JWT, JWT 없음 401, 만료·위조·형식 오류 401, 화면 세션만 있는 API 401, JWT만 있는 화면 익명, 화면 세션 인증 유지, 두 수단 동시 존재 시 각 체인 분리, API POST의 CSRF 403과 통과, 공개 인증 경로 허용, 목록 밖 `/api` POST 401, 인증 경로 GET 401, 인증 응답에 access_token 쿠키 없음, API 회원가입 집계와 429, API CSRF 실패 미집계, 한 번만 집계, 정상 API JWT 요청은 세션 미생성.
  - 기존 테스트 유지. `HomeControllerTest`, `GlobalExceptionHandlerTest`에 `AccessTokenVerifier` 목을 추가했고, `SignupRateLimitFilterTest`의 `/api/auth/signup` 요청은 API 체인의 CSRF 방식(XSRF 쿠키와 헤더)으로 바꿨다. 검증 내용은 줄이지 않았다.
  - 돌연변이 확인: API CSRF를 끄면 CSRF 관련 테스트 7개가 실패한다. 되돌린 뒤 통과했다.
- 검증 (첫 구현 커밋 `932765b` 기준. 최종 결과는 아래 보완 항목 참조):
  - 테스트 DB 연결(SQL 001~003): 313개 전부 통과, 건너뜀 0개, 실패 0개.
  - 테스트 DB 미설정: 313개 중 261개 통과, 건너뜀 52개(DB 테스트), 실패 0개.
  - `./gradlew bootJar`: 성공.
  - 실제 jar: `GET /` 200. 인증 없는 `GET /api/x` 401 `TOKEN_INVALID`. 위조 JWT 401. XSRF 쿠키 발급. CSRF 토큰이 있는 `POST /api/auth/logout`은 인증을 통과해 404(엔드포인트 없음). CSRF 토큰 없는 `POST /api/auth/signup`은 403이고 집계 0건.
- PR #19 보완 (2026-11-17, Bearer 인증과 CSRF 명세 일치):
  - `AccessTokenAuthenticationFilter`: `Authorization: Bearer` 헤더를 읽는다. 헤더가 있으면 쿠키는 보지 않는다. 잘못된 Bearer 형식(빈 값, 공백 포함, 다른 스킴)과 위조·만료 토큰은 인증 실패이며, 쿠키로 우회하지 않는다. 기존 `AccessTokenVerifier`를 그대로 쓴다. 토큰과 실패 원인은 로그에 남기지 않는다. 쿠키는 지우거나 바꾸지 않는다.
  - CSRF (API 체인): 상태 변경 요청은 `Authorization` 스킴이 `Bearer`(대소문자 무시)인 경우에만 면제한다. Basic 등 다른 스킴, 쿠키 인증, 헤더 없는 익명 요청은 CSRF를 검사한다. 면제 판단은 스킴만 보고 토큰 값은 보지 않는다. 잘못된 Bearer는 CSRF 403이 아니라 인증 401이다. CSRF 필터가 JWT 필터보다 먼저 실행된다. 화면 체인과 CSRF 전체 비활성화는 바꾸지 않았다.
  - `XSRF-TOKEN` 쿠키 속성 검증: HttpOnly 없음, SameSite=Lax, Path=/, 운영 프로필에서만 Secure. 서블릿 6 환경에서 SameSite는 쿠키 속성으로 붙으며, MockMvc는 이를 헤더 문자열에 쓰지 않으므로 테스트는 쿠키 객체의 속성을 확인한다. 실제 서버 응답 헤더는 별도 확인했다(아래 검증).
  - 테스트 추가: Bearer 정상·잘못된·만료·ADMIN 권한, Bearer 우선순위, 잘못된 Bearer로 쿠키 우회 불가, 스킴 대소문자, Bearer 상태 변경 CSRF 없이 통과, 쿠키 상태 변경 CSRF 없이 403과 올바른 헤더 통과와 틀린 헤더 403, CSRF가 인증보다 먼저 판정됨, 잘못된 Bearer 상태 변경은 401, 쿠키 속성(로컬·운영), 필터 단위 Bearer 파싱.
  - 돌연변이 확인: (1) CSRF 대상을 모든 상태 변경 요청으로 되돌리면 Bearer CSRF 테스트 2개가 실패한다. (2) 쿠키를 Bearer보다 먼저 읽으면 우선순위와 우회 테스트 4개가 실패한다. 되돌린 뒤 통과했다.
- PR #19 추가 보완 (2026-11-17, CSRF 면제를 Bearer 스킴으로 제한): 이전 구현은 `Authorization` 헤더만 있으면 CSRF를 면제했다. 그래서 Basic 헤더가 있는 상태 변경 요청이 CSRF를 우회했다. 면제 조건을 `Bearer` 스킴(대소문자 무시)으로 좁혔다. 토큰 값 검증은 하지 않으므로 CSRF 판단과 JWT 검증은 분리된다. 필터 순서(CSRF가 JWT보다 먼저)와 쿠키 인증 정책, 화면 체인, 429 처리는 바꾸지 않았다.
  - 추가 테스트: Basic POST는 CSRF 없으면 403, 올바른 CSRF가 있으면 CSRF를 통과해 인증 401. Basic과 유효 JWT 쿠키가 함께 있어도 쿠키로 우회하지 않고 403. 헤더 없는 익명 상태 변경 403. 공개 인증 경로 4개는 Basic 헤더로 CSRF를 우회하지 못함. `Bearer`와 토큰 사이에 공백이 없으면 Bearer가 아니므로 403. 대소문자 혼합 스킴(`bEARER`) 면제. 면제된 Bearer 요청에 쿠키가 있어도 잘못된 Bearer는 401.
  - 검증: `./gradlew clean test` DB 미연결 340개 중 52개 건너뜀, 실패 0. DB 연결(isolated MySQL 8.0.46, SQL 001→002→003 적용) 340개, 실패 0, 건너뜀 0.
- 미구현 (6.16 범위 밖):
  - 화면 체인의 로그인 실패 리다이렉트(현재 임시 전체 허용)
  - 화면 로그인과 API 로그인 엔드포인트, 로그인 요청 제한 연결
  - Refresh HTTP 엔드포인트와 재발급 요청 제한·429 매핑
  - Google OAuth2 체인(`/oauth2/**`, `/login/oauth2/**`)과 시작·연결·콜백 집계
  - Google 신규 가입 닉네임 제출 집계
  - 공개 조회 API의 명시적 공개 경로 (구현 시 추가)
  - 프론트엔드 Access Token 자동 갱신과 T20

### 6.17 API 인증 HTTP 연결 (회원가입·로그인·재발급·로그아웃·내 정보) (2026-11-17)

- 브랜치: `feat/stage2-3-auth-http-api` (기준: `main` 83d8323). `main` 병합 대기.
- 구현 (API_SPEC 2.1~2.4, 2.8):
  - `POST /api/auth/signup`: 기존 `MemberRegistrationService`를 쓴다. 가입과 자동 로그인(새 Refresh 계열 발급, Access Token 발급)을 한 트랜잭션에서 한다. 201. 응답 `memberId`, `email`, `nickname`, `role`. 집계는 `SignupRateLimitFilter`가 한 번만 한다.
  - `POST /api/auth/login`: 입력 형식 검증 후 `AuthRateLimitService.attemptLogin`으로 제한 판정과 자격 증명 확인을 한 잠금 구간에서 한다. 성공하면 새 Refresh 계열을 발급한다. 200. 응답 `memberId`, `nickname`, `role`, `accessTokenExpiresIn`. 미가입·비밀번호 불일치·Google 전용 계정은 같은 401 `INVALID_CREDENTIALS`. 제한 초과는 429와 `Retry-After`이고, 이때 자격 증명을 확인하지 않는다.
  - `POST /api/auth/refresh`: `RefreshTokenService.rotate`를 쓴다. Access Token 인증은 요구하지 않는다. `Rotated`는 200과 새 쿠키 두 개, `Rejected`와 `ReuseDetected`는 401 `TOKEN_INVALID`와 두 쿠키 만료, `RateLimited`는 429와 `Retry-After`이며 쿠키를 건드리지 않는다. 재사용 탐지는 예외가 아닌 결과로 반환되므로 폐기가 커밋된다. 요청 제한은 `RefreshTokenRateLimitGate`가 한 번만 판정한다.
  - `POST /api/auth/logout`: 인증 없이 호출한다. `RefreshTokenService.logout`으로 쿠키의 토큰을 폐기한다. 쿠키가 없거나 폐기된 토큰이어도 200. 두 쿠키를 만료시킨다.
  - `GET /api/auth/me`: API JWT 인증이 필요하다. DB에서 회원을 확인한다. 응답 `memberId`, `email`, `nickname`, `role`, `googleLinked`, `hasPassword`. 비밀번호 해시, `google_sub`, Refresh Token은 응답하지 않는다. 회원이 없으면 401 `TOKEN_INVALID`.
- 새 클래스: `member.service.AuthApiService`(가입·로그인·재발급·로그아웃 흐름과 판정), `member.web.AuthController`(HTTP 입출력), `member.web.AuthCookies`(쿠키 발급과 만료), `global.response.ApiSuccessResponse`(성공 응답 형식 1.2).
- 변경 클래스와 설정:
  - `MemberLoginService.verifyCredentials` 추가: 실패를 예외가 아니라 빈 `Optional`로 돌려준다. `authenticate`는 이를 감싸도록 정리했다. 이유: 로그인 제한 판정 트랜잭션 안에서 예외를 던지면 트랜잭션이 rollback-only가 되어 실패 기록이 사라지고 커밋이 실패한다. DB 통합 테스트에서 발견해 고쳤다.
  - `ErrorCode.VALIDATION_FAILED`(400) 추가. `GlobalExceptionHandler`가 본문 JSON 오류(파싱 실패, 본문 누락, 알 수 없는 필드, 타입 불일치)를 400 `VALIDATION_FAILED`로 바꾼다. 지원하지 않는 Content-Type은 기존대로 415다.
  - `application.yml`: `spring.jackson.deserialization.fail-on-unknown-properties: true`. 요청 본문의 알 수 없는 필드를 거부한다. 이전에는 Spring 기본값으로 무시했다.
- 쿠키 정책 (API_SPEC 1.1): `access_token`은 Path `/api`, Max-Age는 Access Token 만료(1800초), `refresh_token`은 Path `/api/auth`, Max-Age는 Refresh 만료(1209600초). 둘 다 HttpOnly, SameSite=Lax, 운영 프로필에서만 Secure. Domain 없음. 만료 쿠키는 같은 이름과 Path에 `Max-Age=0`. 응답 본문에 토큰 원문은 없다.
- 요청 제한 연결: 회원가입은 기존 필터(중복 집계 없음), 로그인은 `attemptLogin`(실패 기록과 초기화 포함), 재발급은 `RefreshTokenRateLimitGate`. 429는 `RateLimitExceededException`에서 기존 공통 핸들러가 만든다. 쿠키와 세션은 건드리지 않는다.
- 세션: 5개 엔드포인트 모두 `JSESSIONID`를 만들지 않는다. API 체인의 STATELESS 정책은 그대로다. CSRF는 상태 변경 요청(POST)에 그대로 적용된다.
- 테스트 (추가):
  - `AuthApiHttpTest` 34개 (MockMvc, 실제 SecurityConfig·JWT·쿠키 정책, 서비스 경계만 목): 가입 12, 로그인 6, 재발급 6, 로그아웃 3, 내 정보 7.
  - `AuthApiProdCookieTest` 1개: 운영 프로필에서 로그인 쿠키 두 개에 Secure가 붙는다.
  - `AuthApiDbTest` 7개 (실제 MySQL 8.0.46, SQL 001→002→003 적용, 테스트 전 관련 테이블 비움): 가입·로그인·내 정보·재발급·로그아웃 흐름과 계열 수, 폐기된 토큰 재사용 시 회원의 활성 토큰 전부 커밋 폐기(DB 확인), 실패 5회 뒤 6번째 429와 `Retry-After`, 성공 뒤 실패 횟수 초기화, 가입 집계 DB 기록 1건, 미가입과 불일치 비밀번호의 같은 401 응답, 로그아웃 뒤 재사용 401.
  - 기존 슬라이스 테스트 4개(`SignupRateLimitFilterTest`, `XsrfCookieAttributeTest`, `XsrfCookieProdAttributeTest`, `GlobalExceptionHandlerTest`)에 새 컨트롤러가 필요로 하는 빈(`AuthApiService`, `AuthCookies`, 필요 시 `ClientIpResolver`)을 목으로 추가했다. 검증 내용은 줄이지 않았다.
  - `./gradlew clean test`, DB 연결(격리 MySQL 8.0.46, SQL 001→003): 382개 전부 통과, 실패 0, 건너뜀 0.
  - `./gradlew clean test`, DB 미설정: 382개 중 323개 통과, 59개 건너뜀(DB 테스트), 실패 0.
  - `./gradlew bootJar`: 성공.
  - 실제 jar (로컬 프로필, 격리 MySQL): 가입 201과 쿠키 속성(HttpOnly, SameSite=Lax, Path, Max-Age) 확인, 세션 쿠키 없음. 잘못된 비밀번호 5회 뒤 6회째 429와 `Retry-After: 900`. 알 수 없는 필드 400 `VALIDATION_FAILED`. 정상 재발급 200과 새 쿠키 두 개. 이전 토큰 재사용 401. 로그아웃 200과 쿠키 만료.
- 미구현 (이번 범위 밖): 화면 로그인·회원가입·로그아웃(2.9~2.11)과 화면 로그인 실패 리다이렉트, Google OAuth2 시작·콜백·계정 연동·해제, Google 요청 제한 연결과 닉네임 제출 집계, 프런트엔드 Access Token 자동 갱신과 T20, 공개 조회 API 경로.
- 위험과 한계:
  - 재발급 응답이 네트워크에서 유실되면 이전 토큰 재제출이 재사용으로 판정된다(T14 A안이 수용한 한계, 2.3).
  - 로그인의 BCrypt 검증은 제한 잠금 구간 안에서 실행된다. 같은 이메일과 IP의 동시 로그인은 직렬화된다. 정책대로이며 지연은 BCrypt 한 번분이다.
  - 알 수 없는 필드 거부는 전역 Jackson 설정이다. 이후 추가되는 요청 DTO도 같은 규칙을 따라야 한다.
  - DB 테스트는 로컬 격리 MySQL에서만 실행했다. CI는 없다.

## 7. 다음 작업

1. 완료 (`main` 병합: PR #17 `9f881a0cca892b28f342dec9fc399814b40001a8`, PR #19 `dd147e3f595825047d017ddcbb20df95f55ec40f`):
   - 인증 요청 제한 R1~R10 서비스 구현 (PR #16, `50963bc`, 6.14)
   - 회원가입 요청 제한 HTTP 연결: `SignupRateLimitFilter`가 `POST /signup`, `POST /api/auth/signup`을 집계합니다. 화면 회원가입과 API 회원가입은 같은 IP 제한을 공유합니다.
   - 실행 순서: CSRF 검증 → 회원가입 요청 제한 필터 → Controller(본문 읽기, 파싱, DTO 검증). 요청 본문을 읽기 전에 제한 여부를 검사합니다. CSRF 실패 요청은 집계되지 않습니다.
   - HTTP 429 `RATE_LIMITED` 응답과 `Retry-After` 헤더 반환 (공통 예외 처리 `GlobalExceptionHandler`)
   - 요청 제한 초과 시 쿠키를 삭제하거나 로그아웃시키지 않습니다. 세션과 JWT는 그대로 둡니다.
   - 요청 제한 저장소 장애 시 fail-closed 처리: 판정을 끝내지 못하면 요청을 진행시키지 않고 500 `INTERNAL_ERROR`로 응답합니다.
   - 상세 기록: 6.14(R1~R10), 6.15(HTTP 연결)
   - PR #19 (`main` 병합 완료, merge commit `dd147e3f595825047d017ddcbb20df95f55ec40f`, 6.16):
     - JWT 인증 필터 (`AccessTokenAuthenticationFilter`)
     - API용 SecurityFilterChain (`/api/**`, Stateless, `@Order(1)`)
     - 화면용 SecurityFilterChain 유지 (`@Order(2)`)
     - Bearer·Cookie 인증 정책 (Bearer 우선, 잘못된 Bearer의 쿠키 대체 방지)
     - API CSRF 정책 (Bearer 스킴 상태 변경만 면제, 그 밖은 검사, `XSRF-TOKEN` 쿠키)
2. 다음 작업 (`main` 기준 미구현. 완료 항목은 1번에 기록).
   - 구현 완료, `main` 병합 대기 (6.17, `feat/stage2-3-auth-http-api`): API 인증 HTTP 5종(`POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`, `GET /api/auth/me`), JWT 쿠키 발급·갱신·만료(API_SPEC 1.1), 로그인·재발급 요청 제한 HTTP 연결(429·`Retry-After`, 재발급 429는 쿠키 유지), 세션 미생성.
   - 화면 로그인 `POST /login`(화면 세션 로그인, 2.10)과 화면 회원가입(2.9), 화면 로그아웃(2.11) (미구현)
   - 화면 로그인 실패 리다이렉트 (화면 체인, 미구현)
   - Google OAuth2 시작(`GET /oauth2/authorization/google`), 계정 연결 시작 POST(경로 미확정), 콜백(`GET /login/oauth2/code/google`)의 요청 제한 연결 (OAuth2 흐름 미구현)
   - Google 신규 가입 닉네임 설정 제출 경로와 요청 제한 연결 (경로 미확정)
   - Google OAuth2 로그인 및 계정 연결·해제 (미구현)
   - 화면 세션 인증과 API JWT 인증의 실제 연결: 화면 로그인 구현 뒤 가능 (미구현)
   - 프론트엔드 Access Token 자동 갱신과 T20 처리 (미구현)
   - 공개 조회 API 경로 (구현 시 명시적으로 추가, 미구현)
   - F1-02~F1-09 기능 (미착수)
   위 경로 중 현재 코드에 없는 것은 확정하지 않았습니다. API_SPEC의 표기는 설계 기준입니다.
3. Stage 2-4: 화면 세션 로그인·회원가입 (API 인증 HTTP는 6.17에서 구현, 병합 대기), Google OAuth2 로그인, 계정 연동·해제, 신규 가입 닉네임 설정(T3 A안), 화면 로그인 실패 리다이렉트(화면 체인).
4. 확인 필요: 비밀번호 확인값 누락 처리, 닉네임 대소문자 구분 정책, 이메일 형식의 Unicode 공백 처리.
5. 미확정 항목(T1, T2, T4, T5, T6, T10)은 각 단계 시작 전에 결정합니다.
6. 실제 MySQL 스키마 적용은 사용자가 `db/manual/`의 SQL 파일을 직접 실행합니다. 이번 작업에서는 실행하지 않았습니다.
