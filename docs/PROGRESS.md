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
| **다음 개발 단계** | **Stage 2: F1-01 회원·인증** (T3, T7, T8, T11 확정 후 시작) |

## 1. 단계 요약

| 단계 | 내용 | 상태 | 비고 |
|------|------|------|------|
| 0 | 문서 기획 (기초 구조 + Q1~Q6 의사결정 반영) | 완료 | 2026-11-06 |
| 1 | 프로젝트 기본 구조 (Gradle, Spring Boot 3.5.16, Java 21, 설정 프로필 local/prod, 공통 오류 응답, Security 기본 설정, Bootstrap 5 홈 화면) | 완료 | CI 파이프라인은 Stage 11로 이월. T9 확인 필요 |
| 2 | F1-01 회원·인증 (이메일 가입·로그인, Refresh Token 로테이션, Google 로그인·수동 연동/해제, 로그아웃) | 미착수 | T3, T7, T8 확정 필요 |
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
| F1-01 | 회원가입, 이메일 로그인, 로그아웃, JWT(Refresh Token 로테이션), Google 로그인, Google 계정 수동 연동·해제 | 미착수 | - | 명세 반영 완료 (Q1, Q3) |
| F1-02 | 맛집 등록, 수정, 삭제(완전 삭제), 목록, 카테고리, 검색 | 미착수 | - | 명세 반영 완료 (Q2, Q5) |
| F1-03 | 맛집 이미지 AWS S3 업로드, 삭제 실패 추적·재시도 | 미착수 | - | 명세 반영 완료 (Q2) |
| F1-04 | 리뷰 작성 및 삭제 (본인만), 평점, 평균 평점 | 미착수 | - | 명세 반영 완료 (Q5) |
| F1-05 | 리뷰 3개 이상 AI 요약 및 DB 캐싱 | 미착수 | - | 명세 반영 완료 (Q6) |
| F1-06 | 찜하기 및 찜 목록 | 미착수 | - | 명세 작성 완료 |
| F1-07 | DB 등록 맛집 대상 AI 추천 | 미착수 | - | 명세 반영 완료 (Q6) |
| F1-08 | 메인 페이지, 카테고리 및 최신 맛집 | 미착수 | - | 명세 작성 완료 |
| F1-09 | 관리자 기능 (USER/ADMIN, 관리자 화면·API, 대시보드, S3 재시도) | 미착수 | - | 명세 신규 작성 (Q5) |

## 3. 결정 현황 (Q1~Q6)

| 번호 | 주제 | 상태 | 결정 내용 요약 |
|------|------|------|----------------|
| Q1 | Google 로그인과 기존 계정 연동 | 결정됨 | 자동 연결 없음. 기존 계정 로그인 후 마이페이지에서 직접 연동·해제. 한 Google 계정은 한 회원에만 연결 |
| Q2 | 맛집 삭제 정책 | 결정됨 | 관련 데이터 포함 완전 삭제. DB는 한 트랜잭션, S3는 커밋 후 삭제. 실패 키는 `storage_delete_task`로 추적·재시도 |
| Q3 | Refresh Token 관리 | 결정됨 | Access/Refresh 분리. Refresh 원문은 HttpOnly Cookie, DB는 SHA-256 해시. 로테이션, 로그아웃 폐기, 운영 Secure |
| Q4 | MySQL 운영 환경 | 결정됨 | EC2 내부 MySQL 8. RDS 미사용. 영속 저장소, 3306 비공개, 백업, 환경 변수 분리, RDS 이전 가능 설계 |
| Q5 | 관리자 기능 | 결정됨 | 필수 범위(F1-09)에 포함. `member.role`(USER/ADMIN). 관리자 전용 화면·API 분리. 서버 측 검증 |
| Q6 | AI 모델 관리 | 결정됨 | `OPENROUTER_API_KEY`, `OPENROUTER_MODEL` 환경 변수. 코드 고정 없음. 개발·운영 상이 가능. 특정 모델 ID 미확정 |

## 4. 미확정 사항 (TODO)

| 번호 | 항목 | 필요 시점 |
|------|------|-----------|
| T1 | 회원 정지·탈퇴 정책 | Stage 10 전 |
| T2 | 관리자 권한 부여·변경 방식, 최초 관리자 생성 방법 | Stage 10 전 |
| T3 | Google 신규 가입 시 닉네임 생성 규칙 | Stage 2 전 |
| T4 | DB 백업 주기, 보관 기간, 저장 위치, 복구 절차 | Stage 11 전 |
| T5 | MySQL 설치 방식과 영속 저장소 구성 | Stage 11 전 |
| T6 | S3 삭제 재시도 자동화 여부 | Stage 4 이후 |
| T7 | Refresh Token 재사용 탐지 시 정책 | Stage 2 |
| T8 | Google 연동·해제 본인 확인 강도 (현재 제안: 비밀번호 재입력) | Stage 2 |
| T9 | 테스트 라이브러리(Testcontainers 등) 도입 여부. Stage 1에서는 `spring-boot-starter-test`(JUnit 5, Mockito, AssertJ)만 사용하고 Testcontainers는 도입하지 않았음 | Stage 1 (결정 확인 필요) |
| T10 | OpenRouter 사용 모델 ID 선정 (운영 설정 값) | Stage 8 전 |
| T11 | OAuth2 임시 세션 구현 세부 (저장 위치, 만료 시간, 세션 정책 값) | Stage 2 전 |

## 5. 설계 변경 이력

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

## 6. 작업 로그

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

## 7. 다음 작업

1. T9(테스트 라이브러리 도입 여부)를 결정합니다.
2. Stage 2(F1-01) 시작 전에 T3(닉네임 규칙), T7(Refresh Token 재사용 탐지), T8(연동 본인 확인), T11(OAuth2 임시 세션 세부)을 결정합니다.
3. Stage 2(F1-01 회원·인증) 구현을 준비합니다.
