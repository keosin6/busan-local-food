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
| **다음 개발 단계** | **Stage 2: F1-01 회원·인증** (T3, T7, T8, T11, T12, T13, T14 확정 완료, 구현 준비) |

## 1. 단계 요약

| 단계 | 내용 | 상태 | 비고 |
|------|------|------|------|
| 0 | 문서 기획 (기초 구조 + Q1~Q6 의사결정 반영) | 완료 | 2026-11-06 |
| 1 | 프로젝트 기본 구조 (Gradle, Spring Boot 3.5.16, Java 21, 설정 프로필 local/prod, 공통 오류 응답, Security 기본 설정, Bootstrap 5 홈 화면) | 완료 | CI 파이프라인은 Stage 11로 이월. T9 확정 (Testcontainers 미도입) |
| 2 | F1-01 회원·인증 (화면 세션 로그인, API JWT, Refresh Token 로테이션, Google 로그인·수동 연동/해제, 로그아웃) | 진행 중 (Stage 2-1 회원·DB 기반, Stage 2-2 회원가입·로그인 검증 서비스 구현 완료. DB 스키마 002·003 확정과 수동 SQL 작성 완료, 인증 기능 연결은 미구현) | 인증 설계 결정 완료 (T3, T7, T8, T11, T12, T13, T14 A안, 2026-11-10). 구현 준비 단계 |
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
| F1-01 | 회원가입, 이메일 로그인, 로그아웃, 화면 세션 + API JWT(Refresh Token 로테이션), Google 로그인, Google 계정 수동 연동·해제 | 진행 중 | Stage 2-2 단위 테스트 완료 (101개 중 DB 테스트 13개는 테스트 DB 환경 변수 설정 시 실행) | 명세 반영 완료 (Q1, Q3, 인증 구조 2026-11-10). 인증 설계 확정: T3, T7, T8, T11, T12, T13, T14 A안 (2026-11-10). 구현 미완료 |
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
- 테스트 DB 연동 테스트 실행 방법: 테스트 전용 MySQL 8에 위 DDL을 먼저 적용한 뒤, 환경 변수 `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`를 설정하여 실행. 변수가 없으면 해당 테스트 10개는 건너뜀. 개발 DB에는 연결하지 않음. H2·Testcontainers 미사용.
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
- 확인 필요:
  - 002의 `UUID()`는 버전 1 형식이며 시각 정보와 서버 노드 값을 포함합니다. 마이그레이션 시점의 한 번 채우는 값으로만 사용합니다.
  - 애플리케이션의 신규 `family_id`를 버전 4(난수 기반)로 만드는 방식은 구현 단계에서 정합니다. MySQL `RANDOM_BYTES()`는 8.0.18부터 지원되므로 최소 버전 8.0.16과 맞지 않아 쓰지 않았습니다.

## 7. 다음 작업

1. 확정된 요청 제한 정책(R1~R10)을 구현에 반영합니다. 구현 시 확인할 사항: CSRF 검사와 집계 위치의 순서, 신뢰 프록시 목록 설정, HMAC 키 생성·보관 방식.
2. Stage 2-3: 다음 작업은 JWT 발급·검증 설계와 구현입니다. DB 스키마(SQL 002·003)는 확정되었고 수동 SQL은 작성 완료했습니다. 실제 DB 적용은 사용자가 수행합니다. 이후 Refresh Token 발급·로테이션·재사용 탐지(T7 A안, `family_id` 포함), 요청 제한 서비스(R1~R10), API 로그인·회원가입 컨트롤러, 로그아웃(T12 A안) 순으로 진행합니다.
3. Stage 2-4: 화면 세션 로그인·회원가입, Google OAuth2 로그인, 계정 연동·해제, 신규 가입 닉네임 설정(T3 A안), SecurityFilterChain 분리.
4. 확인 필요: 비밀번호 확인값 누락 처리, 닉네임 대소문자 구분 정책, 이메일 형식의 Unicode 공백 처리.
5. 미확정 항목(T1, T2, T4, T5, T6, T10)은 각 단계 시작 전에 결정합니다.
6. 실제 MySQL 스키마 적용은 사용자가 `db/manual/`의 SQL 파일을 직접 실행합니다. 이번 작업에서는 실행하지 않았습니다.
