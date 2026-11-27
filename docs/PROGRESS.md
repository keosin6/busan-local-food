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
| Stage 2 F1-01 회원·인증 | 진행 중. **`main` 병합 완료 (PR #17 `9f881a0`, PR #19 `dd147e3`, PR #23 `56faff2`, PR #25 `d1b9e1d`, PR #28 `37df8e0`, PR #30 `05778895`)**: 회원·Refresh Token 엔티티와 수동 SQL 001~003, 회원가입·로그인 검증 서비스, JWT Access Token 발급·검증, Refresh Token 발급·회전·재사용 탐지·로그아웃 폐기, 요청 제한 서비스 R1~R10, 공통 429와 `Retry-After`, 회원가입 집계 필터, API JWT 인증 필터, API·화면 SecurityFilterChain 분리, Bearer·쿠키 인증, API CSRF 정책, API 인증 HTTP 5종과 JWT 쿠키 처리, 로그인·재발급 요청 제한 HTTP 연결, 화면 회원가입·로그인·로그아웃과 화면 세션, 세션 고정 방어, T12 서버 측 로그아웃 승인, Google 로그인 시작·콜백, Google 신규 가입과 닉네임 설정, Google 로그인·닉네임 제출 요청 제한. **PR #30(`main` 병합 완료, merge commit `05778895c03da90ad56570e5ddae3485acc97ba9`, 6.22)**: Google 계정 연결·해제, 로컬 비밀번호 설정, `/mypage`, 화면용 429 안내 페이지 `/rate-limited`. **PR #35(`main` 병합 완료, merge commit `57a1bcef6c33779a351f053a0bce9d19c67aa00c`, 6.26)**: `/restaurants/new`·`/restaurants/{id}/edit` 화면 세션 인증 보호(JWT 쿠키 단독 인증 불가, CSRF 유지). **보호 화면**: 찜 목록·찜 제출(PR #45, `main` 병합 완료), `/recommend`(PR #46, `main` 병합 완료)도 세션 인증으로 보호된다. **남은 작업**: 회원 탈퇴 기능은 이번 F1-09 범위에서 제외(T1, API 없음). 관리자 화면 `/admin/**`(F1-09)은 ADMIN 권한으로 보호되며 PR #48 병합 대기 상태다. T13 프런트엔드 자동 갱신은 PR #32로 `main` 병합 완료. T20 프런트엔드 429 처리는 PR #33으로 `main` 병합 완료(merge commit `d861d808f930d42519bb717ef978804460c7f24e`, 6.25). 나머지는 아래 7절 |
| Stage 2 F1-02 맛집 관리 | 완료 — `main` 병합 (PR #37, merge commit `1f885dd`, 6.27·6.28). 카테고리 시드, 맛집 등록·수정·삭제, 목록·검색·정렬·페이지, 상세, 작성자 소유권 검사, 위도·경도 쌍 검증, 페이지 이동 시 검색 조건 유지. 이미지·리뷰·찜 연동은 아직 없음 |
| Stage 3 F1-03 맛집 이미지 | `main` 병합 완료 — PR #39 (merge commit `123db18`, 6.30·6.31). S3 업로드·삭제, 이미지 메타데이터(SQL 005), `storage_delete_task`, 목록 썸네일, 상세 갤러리, 수정 화면 사진 관리, 삭제·업로드 동시성 잠금. 실제 AWS 버킷 검증은 배포 단계. 자동 재시도는 없고, 관리자 수동 재시도는 F1-09에서 구현(PR #48 병합 대기) |
| Stage 5 F1-04 리뷰 | `main` 병합 완료 — PR #41 (merge commit `0f8b1f8`, 6.32·6.33). 작성(평점 1~5, 맛집당 한 번), 최신순 목록, 본인 삭제, 평균 평점과 리뷰 수의 잠금 기반 동기화, 맛집 삭제 시 리뷰 정리. AI 요약 캐시 무효화는 F1-05 |
| Stage 6 F1-05 AI 리뷰 요약 | `main` 병합 완료 — PR #43 (merge commit `39532fa`, 6.34·6.35). Spring AI 1.1.8 OpenAI 스타터로 OpenRouter 호출. 3개 이상 맛집만 OpenRouter 요약, `ai_review_summary` 캐싱, 리뷰 작성·삭제 시 무효화, 동시 요청 공유, 실패는 UNAVAILABLE. 실제 OpenRouter 호출과 운영 설정은 미검증 |
| **다음 개발 단계** | Stage 11 배포 기반(파일·문서)은 `feat/stage11-deployment-foundation`에서 PR로 올린다(6.42). 병합 뒤 사용자가 AWS에서 EC2·EBS·MySQL·S3·IAM·OIDC를 설정하고, 그 뒤 실제 배포와 백업·복구를 검증한다. 기능 단계 F1-01~F1-09의 구현은 끝났다. F1-09 관리자 기능은 `main` 병합 완료(PR #48, merge commit `4a2a11c`, 6.41). 회원 정지·탈퇴는 이번 범위에서 제외(T1). F1-08 메인 홈페이지는 `main` 병합 완료(PR #47, `7b20233`, 6.40). F1-07 AI 맛집 추천은 `main` 병합 완료(PR #46, `9206026`, 6.39). F1-06 찜하기·찜 목록은 `main` 병합 완료(PR #45, `c9cb668`, 6.37·6.38). F1-02(PR #37), F1-03(PR #39), F1-04(PR #41), F1-05(PR #43)은 `main` 병합 완료. 상세는 7절 |

## 1. 단계 요약

| 단계 | 내용 | 상태 | 비고 |
|------|------|------|------|
| 0 | 문서 기획 (기초 구조 + Q1~Q6 의사결정 반영) | 완료 | 2026-11-06 |
| 1 | 프로젝트 기본 구조 (Gradle, Spring Boot 3.5.16, Java 21, 설정 프로필 local/prod, 공통 오류 응답, Security 기본 설정, Bootstrap 5 홈 화면) | 완료 | CI 파이프라인은 Stage 11로 이월. T9 확정 (Testcontainers 미도입) |
| 2 | F1-01 회원·인증 (화면 세션 로그인, API JWT, Refresh Token 로테이션, Google 로그인·수동 연동/해제, 로그아웃) | 진행 중. `main` 병합 완료(PR #17 `9f881a0`, PR #19 `dd147e3`, PR #23 `56faff2`, PR #25 `d1b9e1d`, PR #28 `37df8e0`, PR #30 `05778895`): 회원·DB 기반, 회원가입·로그인 검증 서비스, JWT Access Token, Refresh Token 발급·회전·재사용 탐지·로그아웃 폐기, 요청 제한 서비스 R1~R10, 공통 429와 `Retry-After`, 회원가입 집계 필터, API·화면 SecurityFilterChain 분리, Bearer·쿠키 인증, API CSRF 정책, API 인증 HTTP 5종, 로그인·재발급 요청 제한 HTTP 연결, 화면 회원가입·로그인·로그아웃, T12 서버 측 로그아웃 승인, Google 로그인 시작·콜백과 Google 신규 가입·닉네임 설정. **PR #30(6.22, `main` 병합 완료, merge commit `05778895c03da90ad56570e5ddae3485acc97ba9`)**: Google 계정 연결·해제(2.6, 2.6.1, 2.7), 로컬 비밀번호 설정(2.7.1), `/mypage`, 화면용 429 안내. **PR #35(6.26, `main` 병합 완료, merge commit `57a1bcef6c33779a351f053a0bce9d19c67aa00c`)**: `/restaurants/new`·`/restaurants/{id}/edit` 화면 세션 인증 보호. **보호 화면**: 찜 목록·찜 제출(PR #45, `main` 병합 완료), `/recommend`(PR #46, `main` 병합 완료)도 세션 인증으로 보호된다. **남은 작업**: 회원 탈퇴 기능은 이번 F1-09 범위에서 제외(T1, API 없음). 관리자 화면 `/admin/**`(F1-09)은 ADMIN 권한으로 보호되며 PR #48 병합 대기 상태다. T13 프런트엔드 자동 갱신은 PR #32로 `main` 병합 완료. T20 프런트엔드 429 처리는 PR #33으로 `main` 병합 완료(merge commit `d861d808f930d42519bb717ef978804460c7f24e`) | 인증 설계 결정 완료 (T3, T7, T8, T11, T12, T13, T14 A안, 2026-11-10). 서비스 계층과 HTTP 연결은 6.2~6.21 |
| 3 | F1-02 맛집 관리 + F1-08 메인 페이지 기본 | F1-02 완료 (PR #37 병합, `1f885dd`). F1-08 메인 홈 `main` 병합 완료 (PR #47, `7b20233`, 6.40) | 카테고리 시드 포함, 작성자 권한 |
| 4 | F1-03 맛집 이미지 (S3 업로드, `storage_delete_task` 추적) | `main` 병합 완료 (PR #39, 6.30·6.31). AWS 버킷 검증은 배포 단계 | 의존: F1-02 (완료). T6 확인 |
| 5 | F1-04 리뷰 | `main` 병합 완료 — PR #41 (6.32·6.33) | 평균 평점 포함 |
| 6 | F1-05 AI 리뷰 요약·캐싱 | `main` 병합 완료 — PR #43 (`39532fa`, 6.34·6.35) | 의존: F1-04 (구현 완료) |
| 7 | F1-06 찜 | `main` 병합 완료 — PR #45 (`c9cb668`, 6.37·6.38) | |
| 8 | F1-07 AI 맛집 추천 | `main` 병합 완료 — PR #46 (`9206026`, 6.39). `/recommend` 세션 인증 보호 | 의존: F1-02, F1-05 (AI 연동). T10 (모델 설정) |
| 9 | F1-08 메인 페이지 완성 (최신 맛집, 카테고리 연결) | `main` 병합 완료 — PR #47 (`7b20233`, 6.40) | 의존: F1-02 |
| 10 | F1-09 관리자 기능 (role, `/admin/**`, `/api/admin/**`, 대시보드, S3 재시도) | `main` 병합 완료 — PR #48 (merge commit `4a2a11c`, 6.41). 회원 정지·탈퇴·권한 변경은 이번 범위 제외(T1, T2) | 의존: F1-01, F1-02, F1-03, F1-04. T1 범위 제외 |
| 11 | 배포 (EC2, MySQL 설치, 영속 저장소, 백업, GitHub Actions 배포) | 배포 기반 파일·문서 작성 완료, PR 병합 대기 (6.42). 실제 AWS 리소스 생성·배포·백업 복구는 미검증 | T4, T5 결정 (6.42). 이미지 공개 방식 T21, 배포 버킷 T22 결정 필요. 상세는 `docs/DEPLOYMENT.md` |

## 2. 기능별 상태

| ID | 기능 | 상태 | 테스트 | 관련 문서 상태 |
|----|------|------|--------|----------------|
| F1-01 | 회원가입, 이메일 로그인, 로그아웃, 화면 세션 + API JWT(Refresh Token 로테이션), Google 로그인, Google 계정 수동 연동·해제 | 진행 중 | 아래 수치는 작업 당시 보고값이며 현재 `main`에서 이번 문서 작업으로 새로 실행한 결과가 아닙니다. (1) PR #19 (커밋 `eceb2e8`): DB 연결 340/340, DB 미설정 288 통과·52 건너뜀. (2) PR #23 (커밋 `e7c5059`): DB 연결 382/382, DB 미설정 323 통과·59 건너뜀. (3) PR #26 보완 HEAD (`8e3df87`): DB 연결 429/429, DB 미설정 362 통과·67 건너뜀. (4) PR #28 (병합 커밋 `37df8e0`): DB 연결 484/484 통과, DB 미설정 408 통과·76 건너뜀, 실패 0, bootJar 성공. (5) PR #30 (`feat/stage2-4-google-account-linking`, `main` 병합 커밋 `05778895c03da90ad56570e5ddae3485acc97ba9`, 6.22, **PR #30 최종 HEAD 기준 보고값. 이번 문서 PR에서 재실행하지 않음**): `./gradlew clean test` 테스트 DB 연결 시 536개 실행, 건너뜀 0, 실패 0. 테스트 DB 미설정 시 536개 중 453 통과·83 건너뜀·실패 0. `bootJar` 성공. `git diff --check` 통과. 요청 제한 집계 필터 12개와 공통 429·fail-closed 2개 포함 (6.15). 웹 계층 테스트(MockMvc)는 화면·API 인증과 Google OAuth2 엔드포인트 포함. (6) PR #35 (`feat/stage2-4-protected-restaurant-screens`, `main` 병합 커밋 `57a1bcef6c33779a351f053a0bce9d19c67aa00c`, 6.26, **PR #35 당시 보고값. 이번 문서 PR에서 재실행하지 않음**): DB 미설정 `./gradlew clean test` 551개 중 83 건너뜀, 실패 0. 테스트 DB 설정(MySQL 8.0.46) 551개 실행, 실패 0. `bootJar` 성공. | 명세 반영 완료. 인증 설계 확정(T3, T7, T8, T11, T12, T13, T14 A안). API 인증 HTTP 5종, 화면 인증, T12 승인, Google 로그인 시작·콜백과 신규 가입·닉네임 설정은 `main` 병합 완료(PR #23, #25, #26, #28). Google 계정 연동·해제와 로컬 비밀번호 설정은 PR #30(6.22, `main` 병합 완료)로 구현. 프런트엔드 Access Token 자동 갱신(T13)은 PR #32로 `main` 병합 완료, 프런트엔드 429 처리(T20)는 PR #33으로 `main` 병합 완료(merge commit `d861d808f930d42519bb717ef978804460c7f24e`, 6.25). 맛집 등록·수정 화면 세션 인증 보호는 PR #35로 `main` 병합 완료(merge commit `57a1bcef6c33779a351f053a0bce9d19c67aa00c`, 6.26). |
| F1-02 | 맛집 등록, 수정, 삭제(완전 삭제), 목록, 카테고리, 검색 | 구현 완료 (`main` 병합 PR #37, `1f885dd`). 삭제 흐름의 이미지·S3·자식 행 처리는 F1-03 이후 | PR #37 당시 실행값: DB 미설정 610개 중 93 건너뜀, 실패 0. 테스트 DB 설정 610개 실행, 실패 0 (6.27, 6.28) | 명세 반영 완료 (Q2, Q5). 구현 차이는 API_SPEC 4절, FUNCTION_SPEC F1-02에 기록 |
| F1-03 | 맛집 이미지 AWS S3 업로드, 삭제 실패 추적 | `main` 병합 완료 (PR #39). 자동 재시도 없음. 관리자 수동 재시도는 F1-09에서 구현(PR #48 병합 대기) | 기존 실행 기록(6.30 후속): DB 미설정 681개 중 112 건너뜀, 실패 0. 테스트 DB 설정 681개 실행, 실패 0 (이번 문서 작업에서 새로 실행하지 않음) | 명세 반영 완료 (Q2). 구현 차이는 API_SPEC 5절, FUNCTION_SPEC F1-03 |
| F1-04 | 리뷰 작성 및 삭제 (본인만), 평점, 평균 평점 | `main` 병합 완료 (PR #41, 6.32·6.33) | 6.32 참조: DB 미설정 727개 중 133 건너뜀, 실패 0. 테스트 DB 설정 727개 실행, 실패 0 | 명세 반영 완료 (Q5). 구현 차이는 API_SPEC 6절, FUNCTION_SPEC F1-04 |
| F1-05 | 리뷰 3개 이상 AI 요약 및 DB 캐싱 | `main` 병합 완료 (PR #43, `39532fa`, 6.34·6.35). 실제 OpenRouter 호출은 미검증 | PR #43 보고값(6.35 최종, 이번 문서 작업에서 새로 실행하지 않음): DB 미설정 767개 중 145 건너뜀, 실패 0. 테스트 DB 설정 767개 실행, 실패 0 | 명세 반영 완료 (Q6). 구현 차이는 API_SPEC 7절, FUNCTION_SPEC F1-05 |
| F1-06 | 찜하기 및 찜 목록 | `main` 병합 완료 — PR #45 (`c9cb668`, 6.37·6.38). 맛집 목록 카드의 찜 버튼 포함 | PR #45 보고값(이번 문서 작업에서 새로 실행하지 않음): 테스트 DB 설정 820개 실행, 건너뜀 0, 실패 0. DB 미설정 820개 중 165 건너뜀, 실패 0 | 명세 반영 완료. 구현 차이는 API_SPEC 8절, FUNCTION_SPEC F1-06 |
| F1-07 | DB 등록 맛집 대상 AI 추천 | `main` 병합 완료 — PR #46 (`9206026`, 6.39). 추천 화면 `/recommend`와 API `POST /api/recommendations` | 6.39 실행값: DB 미설정 853개 중 167 건너뜀, 실패 0. 테스트 DB 설정 853개 실행, 건너뜀 0, 실패 0. F1-07 신규 33개(서비스 15, API 7, 화면 9, DB 2) | 명세 반영 완료 (Q6). 구현 차이는 API_SPEC 9절, FUNCTION_SPEC F1-07 |
| F1-08 | 메인 페이지, 카테고리 및 최신 맛집 | `main` 병합 완료 — PR #47 (`7b20233`, 6.40). 홈 API 변경 없음 | 6.40 실행값: 테스트 DB 설정 865개 실행, 건너뜀 0, 실패 0. DB 미설정 865개 중 170 건너뜀, 실패 0. F1-08 신규 12개(HTTP 9, DB 3) | 명세 반영 완료. 구현 차이는 6.40 |
| F1-09 | 관리자 기능 (USER/ADMIN, 관리자 화면·API, 대시보드, S3 재시도) | `main` 병합 완료 — PR #48 (merge commit `4a2a11c`, 6.41). T1 회원 정지·탈퇴는 이번 범위 제외, T2 역할 변경 경로 없음(A안) | 6.41 실행값: 테스트 DB 설정 913개 실행, 건너뜀 0, 실패 0. DB 미설정 913개 중 191 건너뜀, 실패 0. 신규 48개(DB 21, 비DB 27) | 명세 반영 완료 (Q5). 구현 차이는 FUNCTION_SPEC F1-09 |

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
| T1 | 회원 정지·탈퇴 정책 | 결정됨 (A안, 2026-11-27). 이번 F1-09 범위에서 제외. 회원 정지·탈퇴 기능과 API는 구현하지 않음 |
| T2 | 관리자 권한 부여·변경 방식, 최초 관리자 생성 방법 | 결정됨 (A안, 2026-11-27). 관리자 화면·API에 역할 변경 없음. 최초 관리자는 MySQL에서 직접 지정 |
| T4 | DB 백업 주기, 보관 기간, 저장 위치, 복구 절차 | 결정됨 (Stage 11, 6.42). 매일 KST 03:20 백업, 일일 7일, 주간 4주(일요일), 백업 전용 S3 버킷, SSE-S3, 공개 차단. 스크립트·문서 작성, 실제 실행과 복구는 미검증 |
| T5 | MySQL 설치 방식과 영속 저장소 구성 | 결정됨 (Stage 11, 6.42). Ubuntu 24.04에 MySQL 8 직접 설치, 별도 EBS를 `/data/mysql`에 마운트(UUID, nofail), 3306 외부 차단. 실제 설치·AppArmor 적용은 미검증 |
| T21 | 이미지 공개 방식 | 결정 필요 (Stage 11, 6.42). 현재 앱은 공개 URL을 쓰므로 버킷 읽기 공개가 필요하다. 비공개 유지 원칙과 충돌. CloudFront(OAC), pre-signed URL(앱 변경), 공개 예외 중에서 선택 |
| T22 | 배포 아티팩트 전달용 S3 버킷 | 사용자 확인 필요 (Stage 11, 6.42). 기존 이미지·백업 버킷과 분리한 세 번째 버킷을 가정했다 |
| T6 | S3 삭제 재시도 자동화 여부 | 미확정 유지 (배포 영향: 자동 재시도 없음, 관리자 수동 재시도만 사용) |
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

- 브랜치: `feat/stage2-3-auth-http-api` (기준: `main` 83d8323). PR #23으로 `main` 병합 완료(merge commit `56faff2566743c89911d4d87407fff5ac6c1732e`, 6.18 참조).
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

### 6.18 PR #23 병합 기록: API 인증 HTTP 연결 (2026-11-18)

- 병합: PR #23 `feat: Stage 2-3 API 인증 HTTP 연결`, `main` merge commit `56faff2566743c89911d4d87407fff5ac6c1732e`. 작업 브랜치 `feat/stage2-3-auth-http-api`(기록 시점 HEAD `e7c5059`).
- 병합된 구현 (6.17 상세 참조):
  - API 인증 HTTP 5종: `POST /api/auth/signup`(201), `POST /api/auth/login`(200·401·429), `POST /api/auth/refresh`(200·401·429), `POST /api/auth/logout`(200), `GET /api/auth/me`(200, API JWT 필요)
  - 처리 클래스: `member.service.AuthApiService`(흐름과 판정), `member.web.AuthController`(HTTP), `member.web.AuthCookies`(쿠키 발급·만료), `global.response.ApiSuccessResponse`(API_SPEC 1.2 봉투)
  - 쿠키: `access_token`(Path `/api`, Max-Age 1800초), `refresh_token`(Path `/api/auth`, Max-Age 1209600초). 둘 다 HttpOnly, SameSite=Lax, 운영 프로필에서만 Secure, Domain 없음. 만료 쿠키는 같은 이름과 Path에 `Max-Age=0`.
  - 요청 제한: 로그인은 `AuthRateLimitService.attemptLogin`(제한 판정과 자격 증명 확인을 한 잠금 구간에서), 재발급은 `RefreshTokenRateLimitGate`, 회원가입 집계는 기존 `SignupRateLimitFilter`(중복 집계 없음).
  - 세션: 5개 엔드포인트 모두 `JSESSIONID`를 만들지 않음. API 체인 STATELESS와 CSRF 정책 유지.
- 병합 과정에서 바뀐 기존 코드:
  - `MemberLoginService.verifyCredentials` 추가. 실패를 예외가 아니라 `Optional`로 반환. 이유: 제한 판정 트랜잭션 안에서 예외를 던지면 트랜잭션이 rollback-only가 되어 실패 기록이 사라지고 커밋이 실패함. DB 통합 테스트에서 발견.
  - `ErrorCode.VALIDATION_FAILED`(400) 추가. 잘못된 JSON, 알 수 없는 필드, 타입 오류를 400으로 변환. 지원하지 않는 Content-Type은 기존대로 415.
  - `application.yml`: `spring.jackson.deserialization.fail-on-unknown-properties: true`.
  - 슬라이스 테스트 4개에 새 컨트롤러가 필요로 하는 빈을 목으로 추가. 검증 내용은 유지.
- 테스트 (작업 당시 보고값. 현재 `main`에서 새로 실행한 결과가 아님):
  - MySQL 8.0.46, SQL 001→002→003 적용, `./gradlew clean test`: 382개 전부 통과, 실패 0, 건너뜀 0.
  - DB 미설정, `./gradlew clean test`: 382개 중 323개 통과, 59개 건너뜀(DB 테스트), 실패 0.
  - `./gradlew bootJar`: 성공. `git diff --check`: 통과.
  - 신규 테스트: `AuthApiHttpTest` 34개(MockMvc, 서비스 경계만 목), `AuthApiProdCookieTest` 1개(운영 Secure), `AuthApiDbTest` 7개(실제 MySQL).
- 실제 jar 확인 (작업 당시, 격리 MySQL, 로컬 프로필): 가입 201과 쿠키 속성, 세션 쿠키 없음. 잘못된 비밀번호 5회 뒤 6회째 429와 `Retry-After`. 알 수 없는 필드 400 `VALIDATION_FAILED`. 정상 재발급 200과 새 쿠키 두 개. 이전 토큰 재사용 401. 로그아웃 200과 쿠키 만료.
- 미구현 (6.18 범위 밖): 화면 로그인·회원가입·로그아웃(2.9~2.11)과 로그인 실패 리다이렉트, Google OAuth2 시작·콜백·계정 연동·해제, Google 요청 제한 연결과 닉네임 제출 집계, 프런트엔드 Access Token 자동 갱신과 T20, 공개 조회 API 경로, F1-02~F1-09.
- 위험과 한계 (6.17과 같음): 재발급 응답 유실 시 재사용으로 판정(T14 A안이 수용한 한계), 로그인 BCrypt 검증이 잠금 구간 안에서 실행됨, 알 수 없는 필드 거부는 전역 Jackson 설정, Google 전용 계정과 동시 재발급은 새 HTTP 테스트에서 직접 검증하지 않음(목 기반 또는 기존 DB 테스트), DB 테스트는 로컬 격리 MySQL에서만 실행, CI 없음.

### 6.19 Stage 2-4 화면 세션 인증: 회원가입·로그인·로그아웃 (2026-11-18)

- 브랜치: `feat/stage2-4-screen-auth` (기준: `main` 15fd1df). PR #25로 `main` 병합 완료(merge commit `d1b9e1d`).
- 구현:
  - `GET /signup`, `POST /signup` (2.9): `AuthApiService.signup`(검증, 가입, Refresh 계열, Access Token)을 그대로 쓴다. 성공하면 화면 세션을 만들고 JWT 쿠키 두 개를 내려 `302 /`로 보낸다. 실패는 `302 /signup?error=email_duplicated | nickname_duplicated | validation_failed`. 회원은 만들지 않는다. 가입 집계는 `SignupRateLimitFilter`가 한 번만 한다. 제한 초과는 429와 `Retry-After`.
  - `GET /login`, `POST /login` (2.10): `AuthApiService.login`(입력 검증, `attemptLogin`, 자격 증명)을 그대로 쓴다. 성공하면 화면 세션과 JWT 쿠키를 만들고 `302 /`. 실패는 `302 /login?error=invalid_credentials`. 미가입, 비밀번호 불일치, Google 전용 계정은 같은 결과다. 입력 형식 오류는 `302 /login?error=validation_failed`(명세 밖 값, 확정 필요). 제한 초과는 429와 `Retry-After`이며 세션과 쿠키를 바꾸지 않는다.
  - `POST /logout` (2.11): 화면 체인의 로그아웃 필터가 처리한다. 세션을 무효화하고 SecurityContext를 지우며 `JSESSIONID`만 만료시킨 뒤 `302 /login`. `access_token`, `refresh_token` 쿠키는 읽지도 지우지도 않는다.
  - T12 순서: `static/js/logout.js`가 `GET /api/auth/me`로 `XSRF-TOKEN`을 받은 뒤 `POST /api/auth/logout`에 성공했을 때만 화면 폼을 제출한다. API가 실패하면 화면 세션을 유지하고 오류를 표시한다. 화면 로그아웃이 실패해도 API 토큰은 이미 폐기된 상태이므로 버튼을 다시 누르면 된다.
  - 세션 고정 방어: `ScreenSession.establish`가 로그인과 가입 성공 시 기존 세션을 버리고 새 세션에 SecurityContext를 저장한다.
  - 세션과 JWT 분리 유지: 화면 로그인 상태는 SecurityContext로만 판단한다(`ScreenAuthAdvice`). JWT 쿠키만으로는 화면에서 로그인되지 않는다. 세션만으로는 `/api/**`가 인증되지 않는다.
  - 템플릿: `login.html`, `signup.html` 신규. `fragments/layout.html`은 로그인 상태에 따라 로그인·회원가입 링크 또는 로그아웃 폼을 보여 준다. `home.html`의 미구현 목록을 고쳤다.
- 테스트 (작업 중 실행 결과, `feat/stage2-4-screen-auth` 기준):
  - `ScreenAuthHttpTest` 21개: MockMvc. 실제 화면·API 체인과 쿠키 정책을 쓰고 인증 서비스 경계만 목으로 바꾼다. 가입 성공·검증 실패·중복·제한 초과·CSRF, 로그인 성공·실패·Google 전용 동일 결과·입력 오류·제한 초과·CSRF, 세션 고정, 로그인 후 API JWT 인증, 세션만 있는 API 401, JWT만 있는 화면 비로그인, 로그아웃 세션·쿠키 정리, 세션 없는 로그아웃, 로그아웃 CSRF, API 실패 뒤 세션 유지와 재시도, API 성공 뒤 화면 실패와 재시도.
  - `ScreenAuthDbTest` 7개: 실제 MySQL 8. 가입 결과와 Refresh Token 해시 저장(원문 없음, 64자), 검증 실패 미생성, 중복 이메일 미추가, 로그인 실패 5회 뒤 6번째 429와 실패 기록 5건, Google 전용 계정과 미가입의 같은 결과, JWT만으로 화면 비로그인, T12 순서와 재시도 안전성.
  - 브라우저 확인(Playwright, 헤드리스 Chromium, 실제 jar와 격리 MySQL): 가입 후 홈에서 로그인 상태, 정상 로그아웃 순서 `POST /api/auth/logout` → `POST /logout` → `/login`. API 로그아웃을 실패시키면 오류가 표시되고 `/logout`은 호출되지 않으며 세션과 쿠키가 남는다. API 성공 뒤 화면 로그아웃을 403으로 실패시키면 API 쿠키는 제거되고 세션은 유지되며, 재시도하면 `/login`으로 간다. 이 확인은 저장소에 자동 테스트로 넣지 않았다(Playwright는 의존성에 없음).
  - `./gradlew clean test`, DB 연결(격리 MySQL 8.0.46, SQL 001→003): 410개 전부 통과, 실패 0, 건너뜀 0.
  - `./gradlew clean test`, DB 미설정: 410개 중 344개 통과, 66개 건너뜀(DB 테스트), 실패 0.
  - 기존 API 인증 테스트 382개는 모두 통과(회귀 없음).
  - `./gradlew bootJar`: 성공. `git diff --check`: 통과.
- 미구현 (6.19 범위 밖): Google OAuth2 시작·콜백·계정 연동·해제, Google 요청 제한 연결과 닉네임 제출 집계, 프런트엔드 Access Token 자동 갱신과 T20, 화면 보호 페이지와 `/login` 리다이렉트 규칙, 화면용 429 안내 페이지, `/mypage`, F1-02~F1-09.
- 위험과 한계:
  - `validation_failed`(로그인 입력 오류)는 6.20에서 API_SPEC 정식 명세에 추가되었다.
  - 화면 로그인은 Spring `formLogin` 대신 컨트롤러가 처리한다. 경로와 결과는 명세와 같지만 `formLogin`의 성공·실패 핸들러 이벤트는 쓰지 않는다.
  - T12 순서는 6.20에서 서버 측 승인으로 강제되었다. 이 절의 해당 설명은 PR #25 첫 구현 시점 기록이다.
  - 화면 429는 JSON 본문이다. 안내 페이지가 없다.
  - 화면 로그인 흐름의 브라우저 자동 테스트는 없다. 위 Playwright 확인은 수동 스크립트 실행이다.
  - 세션 타임아웃과 동시 세션 정책은 아직 정하지 않았다(T11의 OAuth2 세션 정책은 Google 단계에서 확정).
  - DB 테스트는 로컬 격리 MySQL에서만 실행했다. CI는 없다.

### 6.20 PR #25 보완: T12 서버 측 로그아웃 승인과 validation_failed 명세 (2026-11-18)

- 브랜치: `fix/stage2-4-screen-logout-guard` (기준: PR #25 head `26e3aee`, 보완 PR의 base는 `feat/stage2-4-screen-auth`). PR #26은 PR #25 브랜치(`feat/stage2-4-screen-auth`)에 `5022156`으로 병합되었고, PR #25의 `main` 병합(`d1b9e1d`)에 포함되었다.
- 배경: 6.19의 첫 구현은 T12 순서를 브라우저 스크립트에만 맡겼다. 서버의 `/logout`은 CSRF만 통과하면 세션을 끊었다.
- 구현:
  - 승인 소비 원자성 보완 (2026-11-18): `consume`은 승인 조회, 제거, 만료 검증을 세션 객체를 기준으로 한 동기화 구간에서 한다. `grant`도 같은 기준으로 동기화한다. 무효화된 세션에서 호출되면 예외 없이 false다. 동시 소비 테스트는 래치로 실행 순서를 맞추며, 동기화를 제거한 변형에서 실패함을 확인했다.
  - `ScreenLogoutApproval`: API 로그아웃이 예외 없이 끝난 뒤 같은 요청의 기존 화면 세션에만 일회성 승인을 기록한다(`getSession(false)`, 새 세션을 만들지 않음). 유효 기간 120초. 소비는 1회만 가능하다.
  - `AuthController.logout`: 서비스 로그아웃이 성공한 뒤에만 `ScreenLogoutApproval.grant`를 호출한다. API 체인의 STATELESS 설정과 NullSecurityContextRepository는 그대로다.
  - `ScreenLogoutController`: `POST /logout`을 처리한다. 세션이 없으면 `302 /login`. 승인이 유효하면 소비하고, SecurityContext를 지우고, 세션을 무효화하고, `JSESSIONID`를 만료시키고, `302 /login`. 승인이 없거나 만료되었거나 소비되었으면 `403`이고 세션을 유지한다.
  - `SecurityConfig`: 기본 LogoutFilter를 끈다(`logout` disable). 승인 검증 없이 세션을 끊는 경로가 남지 않도록 하기 위해서다. CSRF 필터는 여전히 컨트롤러보다 먼저 실행된다.
  - `docs/API_SPEC.md`: 2.9·2.10 화면 오류 식별자 표(`validation_failed` 포함)를 추가했다. 2.11에 서버 측 승인 규칙을 추가했다.
  - `logout.js`, 화면 템플릿, 세션 고정 방어(`ScreenSession`)는 바꾸지 않았다.
- 테스트:
  - 화면 로그아웃 승인 규칙 (MockMvc, `ScreenAuthHttpTest`): 승인 없는 직접 호출 거부와 세션 유지, API 로그아웃 실패 시 승인 없음과 화면 거부, 다른 세션 승인으로 로그아웃 불가, 만료된 승인 거부, 승인 유지 중 CSRF 실패 후 재시도 성공, API 예외 시 승인 미생성, API 로그아웃 멱등, 세션 없는 API 로그아웃이 세션을 만들지 않음, 승인이 있어도 API 요청은 세션으로 인증되지 않음, 로그인 시 승인이 새 세션으로 이전되지 않음, 로그인 실패 리다이렉트 주소에 이메일·비밀번호 없음, 중복 화면 로그아웃은 CSRF에서 거부되고 세션을 새로 만들지 않음, API 로그아웃은 Refresh Token을 한 번만 폐기.
  - `ScreenLogoutApprovalTest` (단위): 1회 소비, 만료 승인 제거, 승인 없음, 유효 기간 120초.
  - `ScreenAuthDbTest` (MySQL): T12 순서 흐름의 API 로그아웃에 같은 세션을 넘김. 승인 없는 재시도는 403. API 로그아웃 실패 시 DB 토큰이 그대로 남고 화면 로그아웃이 거부됨.
  - `AuthApiDbTest`: 미가입과 불일치 비밀번호의 응답 비교에서 `timestamp`를 제외한다. 초 단위 timestamp 때문에 간헐적으로 실패할 수 있던 테스트를 고쳤다.
- 실행 결과 (이 브랜치 HEAD, 작업 중 실행. 현재 `main` 결과가 아님):
  - 실행 조건: PR #26 최종 HEAD `8e3df8798f29c49bf760ccb4b899519c69151bf4`, 격리 MySQL 8.0.46, SQL 001→003 적용. 아래 수치는 PR #26 작업 당시 보고값이며, PR #27에서 새로 실행한 결과가 아니다.
  - `./gradlew clean test`, DB 연결: 429개 전부 통과, 건너뜀 0, 실패 0.
  - `./gradlew clean test`, DB 미설정: 429개 중 362개 통과, 67개 건너뜀(DB 테스트), 실패 0.
  - `./gradlew bootJar`: 성공. `git diff --check`: 통과.
- 브라우저 확인 (Playwright, 헤드리스 Chromium, 실제 jar와 격리 MySQL, 저장소 밖 스크립트): 정상 로그아웃 순서 `POST /api/auth/logout` → `POST /logout` → `/login`. API 실패 시 `/logout` 미호출과 세션 유지. API 성공 뒤 화면 실패 시 재시도 성공. 승인 없이 `POST /logout`을 직접 호출하면 403이고 로그인 상태가 유지된다.
- 명세 변경: `validation_failed`를 API_SPEC 2.9·2.10의 화면 오류 식별자 표에 추가했다. 기존 API 오류 응답은 바꾸지 않았다.
- 미구현 (6.20 범위 밖): 6.19의 미구현 목록과 같다.
- 위험과 한계:
  - 승인 저장소는 HttpSession이다. 세션 저장소를 외부화하면 속성 직렬화가 필요하다. 레코드는 `Serializable`로 선언했다.
  - 120초 안에 화면 로그아웃을 해야 한다. 지나면 API 로그아웃부터 다시 한다.
  - 브라우저 확인에서 화면 로그아웃 실패는 Playwright 라우팅으로 만들었다. 서버 측 재시도 규칙은 MockMvc와 DB 테스트가 확인했다.
  - 기본 LogoutFilter를 끈 탓에 `POST /logout` 이외의 로그아웃 처리(예: `remember-me`)가 필요해지면 다시 설계해야 한다. 지금은 `remember-me`를 쓰지 않는다.

### 6.21 Stage 2-4 PR A: Google OAuth2 로그인과 신규 가입 (2026-11-18)

- 브랜치: `feat/stage2-4-google-oauth2-login` (기준: `main` d4bf023). PR #28로 `main` 병합 완료(merge commit `37df8e024ffea4065c6c1213265010527970dc48`).
- PR #28 테스트 결과 (작업 당시 보고값. 이번 문서 PR에서 새로 실행한 결과가 아님): 격리 MySQL 8.0.46 연결 484/484 통과, DB 미설정 408 통과·76 건너뜀·실패 0, `./gradlew bootJar` 성공, `git diff --check` 통과.
- 구현:
  - 시작 `GET /oauth2/authorization/google`, 콜백 `GET /login/oauth2/code/google`: 화면 체인에 Spring `oauth2Login`을 구성한다. state와 인가 요청은 Spring 기본 HttpSession 방식을 그대로 쓴다. 설정 `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`이 둘 다 있을 때만 구성한다. API 체인은 바꾸지 않았다.
  - 요청 제한 `GoogleOAuth2LoginGuardFilter`: 시작과 콜백 각 1회를 같은 IP 카운터(15분 10회, `AuthRateLimitService.recordGoogleOAuthRequest`)로 센다. Spring의 시작·콜백 필터보다 앞에서 실행한다. 초과하면 429와 `Retry-After`를 내고 후속 인증을 하지 않는다. 콜백 초과는 그 요청의 인가 요청만 지우고, 기존 로그인 세션과 JWT는 유지한다.
  - 콜백 판정 `GoogleLoginService.decide`: 값 검증 → 이메일 미검증 거부(`google_unverified`) → sub가 연결된 회원 로그인 → 같은 이메일의 기존 회원이 있으면 자동 연결하지 않고 `email_account_exists` → 신규 가입 대상.
  - 성공 `GoogleOAuth2SuccessHandler`: 정식 로그인(sub 연결 회원)만 `ScreenSession.establish`로 화면 세션을 만들고 JWT 쿠키를 발급해 `/`로 보낸다. 신규 가입 대상은 `GoogleSignupPending`(sub, 검증된 이메일, 만료 15분)만 세션에 저장하고 `/signup/google/nickname`으로 보낸다. 이때 정식 인증은 만들지 않는다. 그 밖의 결과는 콜백 전의 인증 상태를 복원해 기존 로그인 세션을 바꾸지 않는다.
  - 실패 `GoogleOAuth2FailureHandler`: state 불일치, 인증 오류, 사용자 취소 모두 `/login?error=google_failed`. 인증 상태는 복원한다.
  - 닉네임 설정 `GET·POST /signup/google/nickname`(`GoogleSignupController`): 진행 상태가 없으면 `/login`. 검증은 기존 닉네임 규칙(`MemberInputRules`)을 쓴다. 중복은 사전 검사와 UNIQUE 제약 위반 모두 `nickname_duplicated`. 검증 실패는 `validation_failed`(진행 상태 유지). 성공하면 `GoogleSignupService.complete`가 한 트랜잭션에서 회원(role USER, password_hash NULL, google_sub, 검증된 이메일, 입력 닉네임)을 만들고 토큰을 발급한다. 그 뒤 화면 세션과 JWT 쿠키를 설정하고 `/`로 보낸다. 토큰 발급이 실패하면 회원도 롤백된다. 그 밖의 실패는 진행 상태를 지운다.
  - 제출 요청 제한: `SignupRateLimitFilter`에 `POST /signup/google/nickname`을 추가했다. 회원가입과 같은 IP 카운터(1시간 5회)를 쓰며, 검증과 생성보다 먼저 집계한다. 초과는 429와 `Retry-After`이고 회원을 만들지 않는다.
  - 로그인 화면: Google 로그인 버튼(설정이 있을 때만), 오류 안내 `email_account_exists`, `google_unverified`, `google_failed`.
  - 설정: `app.oauth2.google.client-id: ${GOOGLE_CLIENT_ID:}`, `client-secret: ${GOOGLE_CLIENT_SECRET:}`. 기본값은 없다. local에서 둘 다 없으면 Google 로그인이 꺼지고 기동은 된다(시작 경로 404). prod에서 없거나 한쪽만 있으면 기동하지 않는다. 설정 값은 로그와 오류 메시지에 없다.
  - 기존 코드 변경: `AuthApiService.issueSession` 공개, `MemberRegistrationService.findConstraintName` 패키지 공개(재사용), `ErrorCode.GOOGLE_ALREADY_LINKED`(409) 추가(API_SPEC 1.3에 이미 명세), `ScreenAuthAdvice`에 Google 사용 여부 추가.
  - DB 스키마와 SQL은 바꾸지 않았다. 기존 `member.google_sub`와 `uk_member_google_sub`를 쓴다. 의존성은 추가하지 않았다(`spring-boot-starter-oauth2-client`는 이미 있었다).
- 설계 판단:
  - 자동 연결을 하지 않는다. 미검증 이메일은 sub가 연결된 회원이어도 거부한다.
  - Spring이 콜백에서 넣는 OAuth2 인증이 화면 세션으로 승격되지 않도록, 정식 로그인이 아닌 결과에서는 이전 인증 상태를 복원한다.
  - 요청 제한 카운터는 기존 `AuthRateLimitService`를 쓴다. 테이블을 새로 만들지 않았다.
- 테스트 (작업 중 실행, `feat/stage2-4-google-oauth2-login` HEAD):
  - 단위: `GoogleOAuth2ConfigTest`(설정 검증 포함), `GoogleSignupPendingTest`, `GoogleLoginServiceTest`(자동 연결 금지, 미검증 거부), `GoogleOAuth2LoginGuardFilterTest`(요청 제한 순서와 임시 상태 정리), `GoogleOAuth2SuccessHandlerTest`(정식 로그인과 인증 상태 복원), `GoogleOAuth2FailureHandlerTest`.
  - MockMvc: `GoogleSignupHttpTest`(닉네임 화면 접근, 검증, 중복, 성공, 만료 진행 상태, CSRF, 제출 요청 제한), `GoogleOAuth2HttpTest`(실제 Spring OAuth2 필터: 시작 리다이렉트의 client_id·state·redirect_uri, 시작 제한, 잘못된 state, 콜백 제한과 기존 로그인 보존, 로그인 버튼), `GoogleOAuth2DisabledHttpTest`(설정 없는 local).
  - DB: `GoogleSignupDbTest`(신규 가입 행 검증, 사전 검사 세 종류, UNIQUE 제약 세 종류 재현), `GoogleSignupRollbackDbTest`(토큰 실패 시 회원 롤백).
  - 결과: `./gradlew clean test`, 격리 MySQL 8.0.46(SQL 001→003): 484개 전부 통과, 실패 0, 건너뜀 0. DB 미설정: 484개 중 408개 통과, 76개 건너뜀(DB 테스트), 실패 0. `./gradlew bootJar`: 성공. `git diff --check`: 통과.
- 실제 jar 확인 (격리 MySQL, 로컬 프로필):
  - Google 설정 없음: 시작 경로 404, 로그인 화면에 Google 버튼 없음, 닉네임 화면은 `/login`으로 감.
  - Google 설정(더미 값): 시작은 `accounts.google.com`으로 302. 잘못된 state의 콜백은 `/login?error=google_failed`. 설정 값은 로그에 없음.
  - 운영(prod) 설정 없음: 기동 실패. 원인은 우리 검증 메시지 `운영 환경에서는 GOOGLE_CLIENT_ID와 GOOGLE_CLIENT_SECRET을 설정해야 합니다`.
  - 시작 요청 10회는 302, 11회째는 429와 `Retry-After: 900`. 제한 중 콜백은 429. 카운터에는 허용된 10건만 남음.
- 미구현 (6.21 범위 밖): Google 계정 연동 시작·콜백과 해제(2.6, 2.6.1, 2.7), 로컬 비밀번호 설정(2.7.1), 연동 요청 제한, 프런트엔드 T20, 화면 보호 페이지, 화면용 429 안내 페이지, F1-02~F1-09.
- 위험과 한계:
  - 실제 Google 서버와의 토큰 교환과 id_token 검증은 테스트하지 않았다. 외부 자격 증명과 네트워크가 필요하다. 성공 처리는 합성 인증 객체로 단위 테스트했고, 실제 응답 형식은 배포 후 확인이 필요하다.
  - Spring OAuth2 클라이언트가 Google 액세스 토큰을 HttpSession에 저장한다. 로그에는 남지 않는다. 저장하지 않도록 조정하는 것은 후속 과제다.
  - 콜백 요청 제한 초과 응답은 JSON 429다. 화면 안내 페이지가 없다.
  - 리디렉션 URI는 요청의 기본 URL로 만든다. 운영의 리버스 프록시 뒤에서는 Google에 등록한 URI와 맞는지 배포 전에 확인해야 한다.
  - 화면 보호 페이지가 아직 없어 화면 세션 정책 전체(타임아웃, 동시 세션)는 확정되지 않았다(T11).

### 6.22 Stage 2-4 PR B: Google 계정 연동·해제와 로컬 비밀번호 설정 (2026-11-19)

- 브랜치: `feat/stage2-4-google-account-linking` (기준: `main` `0291bfd`). PR #30으로 `main` 병합 완료(merge commit `05778895c03da90ad56570e5ddae3485acc97ba9`).
- 구현 범위
  - 연동 시작 `POST /mypage/google/link`: 화면 세션 회원만, CSRF 검증, Google 요청 제한 1회 집계. 초과하면 429와 `Retry-After`를 화면 안내로 보내고 OAuth2 임시 상태를 만들지 않는다. 일반 로그인 진행 상태를 지운 뒤 같은 HTTP 세션 저장소에 인가 요청(state)을 저장하고 Google로 `302` 한다.
  - 연동 콜백(기존 `GET /login/oauth2/code/google`): 연동 진행 상태(연동 대상 회원 ID, state, 15분)가 세션에 있을 때만 연동 처리. 순서는 state, 연동 대상 확인, sub와 `email_verified` 확인, sub 중복 확인, 저장이다. 같은 회원에게 이미 연결되어 있으면 멱등 성공, 다른 회원에게 연결되어 있으면 `google_already_linked`. 동시 요청은 `uk_member_google_sub` UNIQUE가 최종 방어한다. 성공은 `/mypage?google=linked`, 실패는 `/mypage?error=...`.
  - 연동 모드의 세션 보존: 성공과 실패 모두 콜백 전 인증 상태를 SecurityContext에 되돌린다(`GoogleOAuth2AuthenticationRestore`). 연동 모드에서는 세션 ID를 바꾸는 전략을 건너뛴다(`GoogleLinkAwareSessionAuthenticationStrategy`). `ScreenSession.establish`와 JWT 쿠키 발급을 호출하지 않는다. 연동 모드에서 Spring이 OAuth2 클라이언트 저장소에 넣은 항목은 제거한다.
  - 연동 해제 `POST /mypage/google/unlink`: 순서는 연결 여부, 비밀번호 존재 여부, BCrypt 검증이다. 성공하면 `google_sub`를 NULL로 바꾼다. 회원 행은 잠금 조회로 읽는다.
  - 로컬 비밀번호 설정 `GET·POST /mypage/password`: 이미 설정된 회원은 `password_already_set`. 검증은 회원가입과 같은 `MemberInputRules`를 쓴다. BCrypt 해시만 저장한다. 회원 행 잠금으로 동시 요청을 직렬화하며, 두 번째 요청은 `password_already_set`이 된다. 비밀번호 변경은 지원하지 않는다.
  - `/mypage`: 로그인 회원 전용. 미인증은 `/login`으로 보낸다. 이메일, 닉네임, Google 연결 여부, 비밀번호 설정 여부를 표시한다. 비밀번호가 없는 회원에게는 해제 폼 대신 설정 안내를 보여준다.
  - 화면용 429 안내 페이지 `/rate-limited`(GET과 POST forward 모두 처리, PR #30 보완): 화면 경로의 필터 제한(`SignupRateLimitFilter`, `GoogleOAuth2LoginGuardFilter`)은 이 페이지로 전달하고, 화면 컨트롤러의 제한 초과는 같은 안내를 어드바이스로 보낸다. 상태 429와 `Retry-After`를 유지하고, 쿠키와 세션은 건드리지 않으며, 자동 리다이렉트나 재로그인을 요구하지 않는다. `/api/**`의 429 JSON 응답은 그대로다.
- 설계 결정과 근거
  - 연동 시작은 Spring의 GET 시작 경로를 거치지 않는다. 그 경로는 일반 로그인 시작으로도 집계되므로 POST 한 번이 두 번 세어진다. 같은 해석기로 인가 요청을 만들어 같은 저장소에 저장한다.
  - 리다이렉트 URI 템플릿의 동작 이름 기본값은 `authorize`다. 콜백 경로와 맞추려고 `action=login` 파라미터를 넣는다. 구현 과정에서 `redirect_uri`가 `/authorize/...`로 만들어지는 것을 테스트로 확인하고 고쳤다.
  - 만료된 연동 상태는 일반 로그인으로 처리하지 않는다. 존재 확인만 하는 `peek`로 읽고, 만료되면 `google_failed`로 끝낸다.
  - 일반 Google 시작 요청은 남아 있던 연동 상태를 지운다. 그렇지 않으면 이후 일반 콜백이 연동으로 잘못 분기된다.
  - 엔티티 변경은 `Member.attachGoogleSub`, `detachGoogleSub`, `assignPasswordHash`로 한정하고, 불변식(로그인 수단 최소 하나, 해시 중복 설정 금지)을 메서드 안에서 지킨다.
  - 트랜잭션 안에서는 업무 판정을 결과 값으로 돌려준다. DB UNIQUE 위반만 `BusinessException`으로 바꿔 트랜잭션 밖으로 던진다(`GoogleSignupService`와 같은 패턴).
- 영향 범위(기존 기능)
  - Google 일반 로그인: 세션 ID 변경 전략은 연동 상태가 있을 때만 건너뛴다. 일반 로그인 콜백과 신규 가입 흐름은 기존 전략과 기존 처리를 그대로 쓴다. 기존 회귀 테스트는 모두 통과했다.
  - 화면 체인: `/mypage/**`만 `authenticated()`이고 나머지는 기존 `permitAll()`이다. 인증 실패의 진입점을 `/login`으로 명시했다.
  - API 체인, SQL, `application.yml`, Gradle, 의존성은 변경하지 않았다.
- 테스트 (이번 실행 결과)
  - `./gradlew clean test`, 테스트 DB 미설정: 536개 중 453 통과, 83 건너뜀(DB 필요), 실패 0.
  - `./gradlew clean test`, 테스트 DB 설정(MySQL 8.0.46, SQL 001~003 적용): 536개 모두 실행, 건너뜀 0, 실패 0. 새 DB 테스트 `MemberAccountDbTest` 7개에는 같은 비밀번호의 동시 설정 경합, 같은 Google sub의 동시 연결 경합, 연결 멱등성, 해제 영속성이 포함된다.
  - 새 단위·슬라이스 테스트: `MemberAccountServiceTest`(13), `GoogleAccountLinkFlowTest`(12), `MyPageHttpTest`(14). 콜백 state 불일치는 실제 Spring OAuth2 필터를 거쳐 `/mypage?error=google_failed`, 기존 로그인 유지, 쿠키 미발급을 확인했다.
  - `./gradlew bootJar` 성공. `git diff --check` 통과.
  - 실행 jar 점검(`local` 프로필, 테스트 DB, 테스트용 Google 설정, 실제 Google 호출 없음): 미로그인 `/mypage`는 `/login`으로 302. 회원가입 후 `/mypage` 200, 연결 폼과 CSRF 필드 존재. 연결 시작 10회는 Google 인증 URL로 302이고 `redirect_uri`는 `http://localhost:8080/login/oauth2/code/google`. 11회째는 429, `Retry-After: 900`, 안내 페이지. 429 뒤 `/mypage`는 여전히 200. 일반 Google 시작은 같은 카운터로 429 안내 페이지(전달 경로 `/rate-limited`).
- 알려진 제한과 미해결 사항
  - Google 실서버의 토큰 교환과 콜백 성공 경로는 자동 테스트하지 않았다. 실제 자격 증명과 네트워크가 필요하다. 성공 경로의 세션 보존은 핸들러 단위 테스트와 실패 경로의 HTTP 테스트로만 확인했다.
  - 연결 시 Google 이메일과 회원 이메일이 같은지는 검사하지 않는다. 명세는 이메일 기준 자동 연결만 금지하며, 연동 대상은 연동 시작 시점의 세션 회원이다. 이 정책은 확인이 필요하다.
  - 연동 콜백에서도 Spring OAuth2 클라이언트 저장소에 Google 액세스 토큰이 잠깐 저장된 뒤 제거된다. 일반 로그인의 저장 정책은 6.21 위험 항목과 같이 그대로 두었다.
  - 화면 안내 페이지의 HTML 렌더링은 실행 jar에 curl로 요청해 본문을 확인했다. 브라우저 화면 확인은 하지 않았다.
  - PR #30 보완: `MockMvc`의 `forward`는 대상 URL만 기록하므로, `ScreenRateLimitForwardTest`(내장 Tomcat, 실제 HTTP)로 POST `/signup`, POST `/signup/google/nickname`, GET Google 시작의 429 HTML 응답 본문과 `Retry-After`를 확인했다. API JSON 429, 직접 접근 리다이렉트, 세션·쿠키 미변경도 같은 테스트에서 확인했다. GET 전용 매핑으로 되돌리면 POST 두 건이 실패하는 것을 확인했다.
  - 테스트용 MySQL은 `/var/lib/busan-mysql-test`에 임시로 만들었다. 작업 후 중지했다.

### 6.23 PR #30 병합 상태 문서 동기화 (2026-11-19)

- 브랜치: `docs/sync-after-pr30` (기준: `main` `0577889`). 문서 전용 PR이며 `main` 병합 대기 상태다.
- 확인한 사실: PR #30(`feat/stage2-4-google-account-linking`)이 `main`에 병합되었다. merge commit `05778895c03da90ad56570e5ddae3485acc97ba9`. 병합 커밋 SHA와 코드 상태(`RateLimitedPageController`의 GET·POST 매핑, `/mypage/**` 인증 규칙)를 대조했다.
- 문서 변경: README 현재 상태, PROGRESS 요약·F1-01 행·6.22 상태·7절, API_SPEC 1.1·1.4·1.5·2.5~2.7.1·10 상태 문구, DEVELOPMENT_RULES 5.7.3·5.7.5 상태 문구를 `main` 병합 완료 기준으로 맞췄다. 과거 작업 기록(6.1~6.21)과 날짜, 단계, 순서는 바꾸지 않았다.
- PR #30 보고 테스트 결과(PR #30 최종 HEAD 기준, 작업 당시 보고값): MySQL 8.0.46 536/536 통과. DB 미설정 453 통과, 83 건너뜀, 실패 0. `bootJar` 성공. `git diff --check` 통과. 이번 문서 PR에서 다시 실행한 결과가 아니다.
- F1-01은 진행 중으로 유지한다. 남은 작업: 프런트엔드 Access Token 자동 갱신(T13), 429 응답의 프런트엔드 연계와 검증(T20), `/mypage` 외 보호 대상 화면, F1-02~F1-09.

### 6.24 T13 프런트엔드 Access Token 자동 갱신 (2026-11-19)

- 브랜치: `feat/stage2-4-access-token-auto-refresh` (기준: `main` `acdfa6e`, PR #31 병합 이후). PR #32로 `main` 병합 완료(merge commit `31525ba`).
- 구현 범위
  - 공통 클라이언트 `src/main/resources/static/js/api-client.js`: 동일 출처 `/api/**` 요청의 401을 처리한다. 가입, 로그인, 재발급, 로그아웃은 제외한다. 외부 출처와 `/api` 밖 요청은 입력과 옵션을 그대로 넘긴다.
  - 401이면 `GET /api/auth/me`를 Web Lock(`busan-auth-refresh`) 안에서 확인한다. 200이면 다른 탭이 갱신한 것이므로 Refresh를 호출하지 않고 원래 요청을 한 번 재시도한다. 401이면 `POST /api/auth/refresh`를 `X-XSRF-TOKEN`과 함께 한 번 호출한다.
  - 같은 탭의 동시 401은 진행 중인 복구 하나를 공유한다. 갱신이 끝난 뒤 늦게 도착한 401은 세대(generation) 비교로 Refresh 없이 재시도한다.
  - 재시도는 원래 요청당 한 번이다. 재시도도 401이면 `auth:reauth-required`를 낸다.
  - 재발급 429는 인증 상태를 바꾸지 않고 응답(`Retry-After` 포함)을 돌려준다. 재발급 403, 5xx, 네트워크 오류는 `auth:refresh-failed`를 낸다. 쿠키는 읽기만 하고 쓰거나 지우지 않는다.
  - `layout.html`의 공통 스크립트 목록에 `/js/api-client.js`를 넣었다. 현재 화면에서 보호 API를 직접 호출하는 곳은 없으므로, 이번 구현은 공통 기반이다. 로그아웃(`logout.js`)은 기존 T12 순서를 그대로 쓴다.
- 서버 변경 없음. Java 소스, SQL, 설정, Gradle, 인증 로직은 바꾸지 않았다.
- 검증 (이번 실행)
  - 자동 단위 테스트: `node --test src/test/js/api-client.test.js`, 29개 중 29개 통과(PR #32 보완 후). Node 내장 테스트 러너만 사용했다. 지시서의 15개 항목과 보완 항목(Request 입력의 credentials 보존, init 명시값 우선, 같은 탭 기본값, Request 입력 본문 스트림 재시도 금지)을 포함한다.
  - 변형 검증: 같은 탭 공유, me 200 단락, 재발급 429 처리, 재시도 401 이벤트, 스트림 재시도 금지, 세대 비교를 각각 제거하면 해당 테스트가 실패했다.
  - Java: `./gradlew clean test`, 테스트 DB 미설정 536개 중 83 건너뜀, 실패 0. 테스트 DB 설정(MySQL 8.0.46, SQL 001~003) 536개 모두 실행, 실패 0. `./gradlew bootJar` 성공. `git diff --check` 통과.
  - 브라우저 검증 (Playwright Chromium, 실제 jar, 액세스 토큰 수명 5초로 실행, 저장소 밖 스크립트, 커밋하지 않음): 19개 중 19개 통과. PR #32 보완 뒤에도 같은 스크립트를 다시 실행해 19/19 통과를 확인했다. 단일 요청 복구, 같은 탭 동시 5개(Refresh 1회), 두 탭 동시 요청(Refresh 1회), 화면 세션 유지, 재발급 429(Retry-After 유지, 재로그인 이벤트 없음), 재발급 401(재로그인 이벤트 1회, JSESSIONID 유지), 로그아웃 요청의 자동 갱신 없음, 페이지 스크립트 오류 없음.
  - 음성 대조군: Web Locks를 끄고 두 탭이 동시에 갱신하면 재발급 요청이 2회 나가고, 두 번째는 회전된 토큰을 재사용으로 판정받아 세션 계열이 폐기되었다(이후 `/api/auth/me` 401). Web Locks가 필요한 근거다.
- 미검증 항목
  - 브라우저에서 재발급 403(CSRF)은 재현하지 못했다. 클라이언트가 쿠키 값을 그대로 헤더에 싣기 때문이다. 단위 테스트로만 확인했다.
  - 네트워크 오류와 실제 5xx는 단위 테스트로만 확인했다.
  - Web Locks를 지원하지 않는 브라우저, Firefox, Safari, 실제 여러 브라우저 프로필은 확인하지 않았다.
  - 재발급 401은 서버가 기존 정책대로 JWT 쿠키를 만료시킨다. 클라이언트는 이 쿠키를 바꾸지 않으며, 바꿀 수도 없다.
- F1-01은 여전히 진행 중이다. 남은 작업: T20(429 프런트엔드 연계와 검증), `/mypage` 외 보호 대상 화면, F1-02~F1-09.

### 6.25 T20 프런트엔드 Rate Limit 429 처리 (2026-11-20)

- 브랜치: `feat/stage2-4-rate-limit-frontend` (기준: `main` `31525ba`, PR #32 병합 이후). PR #33으로 `main` 병합 완료. 병합 커밋 `d861d808f930d42519bb717ef978804460c7f24e`.
- PR #33 테스트 결과는 작업 당시 보고값이다. 아래 검증 항목은 그때 실행한 결과이며, 이후 문서 동기화 PR에서 재실행한 것이 아니다.
- 구현 범위
  - `api-client.js`: 같은 출처 `/api` 응답이 429로 호출자에게 돌아갈 때 `onRateLimited({ retryAfterSeconds })`를 응답 객체마다 한 번 부른다. 응답은 바꾸지 않고 그대로 돌려준다. 브라우저에서는 `api:rate-limited` 문서 이벤트로 전달된다. 재요청은 하지 않는다.
  - `Retry-After`는 정수 초(0 이상)만 해석한다. 누락, 빈 값, 음수, 소수, 날짜 형식은 `null`로 보고 대기 시간 없는 문구를 쓴다.
  - `app.js`: 안내 요소는 하나(`#api-rate-limit-notice`)만 쓴다. 다시 오면 메시지만 바꾼다. Bootstrap `alert-dismissible`의 닫기 버튼을 쓴다. 문구는 "요청이 너무 많습니다. 약 N분 후에 다시 시도해 주세요."(60초 이상은 올림 분), 초 단위는 "약 N초 후에 다시 시도해 주세요.", 값이 없으면 "잠시 후 다시 시도해 주세요."다.
  - 재발급 429(T13)도 원래 요청의 응답으로 돌아가므로 같은 경로로 한 번 안내된다.
- 서버 변경 없음. Java 인증·요청 제한, SQL, 설정, 템플릿, 의존성은 바꾸지 않았다. 이 PR은 정적 스크립트만 바꾼다.
- 검증 (이번 실행)
  - Node 단위 테스트: `node --test src/test/js/api-client.test.js` 39개 중 39개 통과(기존 29, T20 10). `node --test src/test/js/app.test.js` 7개 중 7개 통과.
  - 변형 검증(scratch 사본): 응답별 중복 제거를 빼면 T20-5가 실패했다. Retry-After 날짜 형식을 허용하면 T20-3이 실패했다.
  - 브라우저 검증 (Playwright Chromium, 실제 jar, 액세스 토큰 수명 5초, 저장소 밖 스크립트, 커밋하지 않음): 16개 중 16개 통과.
    - API 로그인 제한으로 429 발생(Retry-After 820), 안내 한 개 표시, 문구에 "약 14분" 반영
    - 추가 429에도 안내 요소는 하나
    - 429 뒤 페이지 이동 없음, 인증 이벤트 없음, 쿠키 이름과 값 유지
    - 재발급 429: 호출자에게 429와 Retry-After 전달, 안내 요소는 하나로 갱신, 재로그인 이벤트 없음, JSESSIONID 유지, 페이지 이동 없음
    - 회귀: 재발급 제한 창이 지난 뒤 401 자동 갱신 200, 재발급 1회
    - 화면 로그인 제한: 기존 `/rate-limited` 안내 페이지 본문과 429 응답
    - 페이지 스크립트 오류 없음
- 미검증 항목
  - 재발급 429 구간의 쿠키 비교는 이름 수준이다. 액세스 토큰은 수명이 지나 브라우저가 지웠고, 값 비교는 API 429 구간에서만 했다.
  - 안내의 육안 확인(스크린샷)은 하지 않았다. 문구와 요소는 DOM으로 확인했다.
  - Firefox, Safari, Web Locks 미지원 환경은 확인하지 않았다.
  - Retry-After가 날짜 형식으로 오는 경우는 확인하지 않았다. 서버는 정수 초만 보낸다.
  - 안내에는 재시도 버튼이 없다. 재요청 호출부가 없어서다. 사용자는 닫은 뒤 같은 동작을 다시 실행할 수 있다.
- F1-01은 여전히 진행 중이다. 남은 작업: `/mypage` 외 보호 대상 화면, F1-02~F1-09.

### 6.26 맛집 등록·수정 화면 세션 인증 보호 (2026-11-20)

- 브랜치: `feat/stage2-4-protected-restaurant-screens` (기준: `main` `6bf56f7`). PR #35로 `main` 병합 완료. 병합 커밋 `57a1bcef6c33779a351f053a0bce9d19c67aa00c`.
- 구현: 화면 체인의 인증 필수 경로에 `/restaurants/new`와 `/restaurants/*/edit`를 추가했다. 기존 `/mypage` 규칙, `anyRequest().permitAll()`, 공개 경로, API 체인, JWT, CSRF, 세션 정책은 바꾸지 않았다.
- 테스트: `ProtectedScreenHttpTest` 15개(MockMvc, 실제 SecurityConfig). 비로그인 GET과 POST는 유효한 CSRF가 있어도 `/login`으로 보낸다. 로그인 세션의 GET은 인증을 통과한다(맛집 핸들러가 없어 404). 로그인 세션의 POST는 CSRF 토큰이 없으면 403이다. JWT 쿠키만으로는 화면 인증되지 않는다. `/mypage` 보호와 공개 홈, API 인증 회귀도 확인했다. 보호 규칙을 빼면 비로그인 리다이렉트 테스트 5개가 실패함을 확인했다.
- 실행 결과(PR #35 당시 보고값, 이번 문서 PR에서 재실행하지 않음): DB 미설정 `./gradlew clean test` 551개 중 83 건너뜀, 실패 0. 테스트 DB 설정(MySQL 8.0.46, SQL 001~003) 551개 모두 실행, 실패 0. `./gradlew bootJar` 성공.
- 작성자 소유권 검사는 이번 범위에서 제외했다. 맛집 도메인과 서비스가 만들어지는 F1-02에서 서버 측으로 구현한다.
- 보호 경로 `/restaurants/new`와 `/restaurants/*/edit`는 화면 세션이 없으면 `/login`으로 보낸다. JWT 쿠키만으로는 화면 인증되지 않는다. 화면 POST의 CSRF 보호는 유지된다.
- F1-01은 진행 중으로 유지한다. 남은 작업: F1-02 맛집 등록·수정 기능과 서버 측 작성자 소유권 검사.

### 6.27 F1-02 맛집 관리 구현 (2026-11-27)

- 브랜치: `feat/f1-02-restaurant-management` (기준: `main` `db60e4313971e6bbb8def9e7bcf90815c7ed29e5`).
- SQL: `db/manual/004_create_category_and_restaurant.sql` 추가. `category`(시드 10개, `sort_order` 1~10), `restaurant`(DATABASE_ERD 2.3, `member_id`·`category_id` FK는 RESTRICT, CHECK 두 개, 인덱스 세 개). 001~003은 바꾸지 않았다. 적용 순서는 001 → 002 → 003 → 004.
- API: `GET /api/categories`(공개), `GET /api/restaurants`(공개, 카테고리·검색어 50자·`latest`/`rating`·페이지 기본 12, 최대 50), `GET /api/restaurants/{id}`(공개, 로그인하면 `isOwner` 계산), `POST /api/restaurants`(JWT 필수, 201), `PUT`·`DELETE /api/restaurants/{id}`(JWT 필수, 작성자 본인만, 관리자 우회 없음). 작성자 검사는 서비스 계층에서 한다. 없는 맛집은 404 `RESTAURANT_NOT_FOUND`, 작성자가 아니면 403 `FORBIDDEN`, 없는 카테고리는 400 `CATEGORY_NOT_FOUND`.
- 화면: `/`(맛집 목록), `/restaurants`(목록·검색·정렬·페이지), `/restaurants/{id}`(상세, 작성자에게만 수정·삭제 버튼), `/restaurants/new`·`/restaurants/{id}/edit`(등록·수정 폼, 화면 세션과 CSRF). 삭제 폼은 `data-confirm`으로 한 번 확인한다.
- 삭제는 DB 행을 완전히 지운다. 이미지 테이블이 아직 없으므로 삭제 결과의 이미지 수와 S3 실패 수는 0이다. S3 삭제 실제 수행과 `storage_delete_task` 기록은 F1-03에서 `RestaurantService.delete`에 넣는다. 가짜 성공 응답은 만들지 않았다.
- 보안 설정: API 체인에 GET 공개 경로(`/api/categories`, `/api/restaurants`, `/api/restaurants/*`)를 추가했다. 화면 체인에서 `/restaurants/new`, `/restaurants/*/edit`, `/restaurants/*/delete`는 인증 필수다.
- 설계 판단: `/`는 맛집 목록을 보여 준다(기존 홈 화면은 템플릿만 남겼다). 상세의 이미지는 빈 목록, 찜 수는 0, 찜 여부는 false다. 목록과 상세에는 썸네일 URL 필드를 넣지 않았다(F1-03). LIKE 검색은 `!`를 이스케이프 문자로 쓴다.
- 테스트 (신규 55개 + 기존 회귀):
  - 단위·슬라이스(DB 없이): `RestaurantInputRulesTest` 13개, `RestaurantSearchTest` 3개, `RestaurantServiceTest` 10개(작성자·없음·카테고리 분기), `RestaurantApiHttpTest` 19개(공개 조회, JWT 401, CSRF 403, 작성자 아님 403, 없음 404, 검증 400, 카테고리 없음 400, 잘못된 토큰).
  - DB(MySQL 8.0.46, SQL 001~004 적용): `RestaurantDbTest` 10개(시드 순서, 등록 기본값, 작성자 수정·삭제, 퍼센트·밑줄 검색, 카테고리 필터, 페이지, 평점 정렬, RESTRICT 외래키). 각 테스트 뒤 맛집과 테스트 회원 행을 지운다.
- 실행 결과 (환경별로 구분):
  - DB 미설정 `./gradlew --offline clean test`: 606개 실행, 건너뜀 93개(모두 DB 테스트), 실패 0개. DB 테스트는 통과로 세지 않았다.
  - 테스트 DB 설정(`TEST_DB_URL`에 `characterEncoding=UTF-8` 포함, MySQL 8.0.46 로컬, SQL 001~004 적용) `./gradlew --offline clean test`: 606개 실행, 건너뜀 0개, 실패 0개.
  - `./gradlew --offline bootJar`: 성공.
  - `git diff --check`: 통과.
- 발견과 조치: 004 시드를 클라이언트 문자셋 기본값(latin1)으로 넣으면 한글이 이중 인코딩되어 저장된다. 004 파일에 `SET NAMES utf8mb4;`를 넣고 README에 `--default-character-set=utf8mb4`를 적었다. `TEST_DB_URL`에는 `characterEncoding=UTF-8`을 넣어야 한다. 빠지면 DB 테스트가 한글을 잘못 읽어 실패한다.
- 알려진 차이와 남은 항목:
  - API_SPEC 예시의 오류 응답 `details` 배열은 아직 없다. 기존 `ApiErrorResponse` 형식을 유지했다.
  - 좌표는 위도·경도 중 하나만 보내도 저장된다. 명세의 짝 검증은 F1-02 범위에서 빠졌다.
  - 목록과 상세의 작성자 닉네임은 상세에서만 쓰므로 목록의 지연 로딩 추가 조회는 아직 줄이지 않았다.
  - 화면의 작성자 전용 버튼과 삭제 확인 흐름은 브라우저로 직접 확인하지 않았다(자동화 테스트 없음).
  - 미구현(범위 밖): F1-03 이미지와 S3 삭제, F1-04 리뷰, F1-05 AI 요약, F1-06 찜, F1-09 관리자.

### 6.28 F1-02 최종 보완 (2026-11-27)

- 브랜치: `feat/f1-02-restaurant-management` (같은 브랜치에 추가 커밋. 새 브랜치 없음). PR #37은 `main`에 병합되지 않은 상태다.
- 좌표 쌍 검증: 위도와 경도는 둘 다 입력하거나 둘 다 비워야 한다. 한쪽만 있으면 `VALIDATION_FAILED`다. API와 화면 입력이 같은 `RestaurantInputRules.validate`를 거친다. 범위(위도 ±90, 경도 ±180)와 소수 일곱 자리 규칙은 그대로 둔다. 단위 테스트 2개를 추가했다.
- 목록 페이지 이동: 이전·다음 링크가 `baseQuery`(카테고리, 검색어, 정렬, 크기)를 유지한다. 검색 폼에는 `page` 필드가 없어서 조건을 바꿔 검색하면 첫 페이지부터 조회한다. `RestaurantListPagingHttpTest` 2개로 확인했다.
- README: DB 연동 테스트 준비 절차에 SQL 001 → 002 → 003 → 004 순서를 적었고, 004에는 `--default-character-set=utf8mb4`를 붙였다. `TEST_DB_URL`의 `characterEncoding=UTF-8` 요구도 적었다.
- 6.27의 "좌표 쌍 검증 미적용" 항목은 이 보완으로 해결되었다. 6.27의 테스트 수치(606개, 93개 건너뜀)는 이 보완 전 기록이며, 아래 최종 수치로 대체된다.
- 실행 결과 (이 보완 커밋 기준, 실제 실행값):
  - DB 미설정 `./gradlew --offline clean test`: 610개 실행, 건너뜀 93개(모두 DB 테스트), 실패 0개.
  - 테스트 DB 설정(MySQL 8.0.46 로컬, SQL 001~004 적용, `TEST_DB_URL`에 `characterEncoding=UTF-8` 포함) `./gradlew --offline clean test`: 610개 실행, 건너뜀 0개, 실패 0개.
  - `./gradlew --offline bootJar`: 성공. `git diff --check`: 통과.
- 미검증: 화면의 작성자 전용 버튼, 삭제 확인 대화상자, 페이지 이동 클릭은 브라우저로 검증하지 않았다. 자동화된 화면 테스트는 링크 HTML과 폼 필드만 확인한다. 브라우저 검증을 완료했다고 기록하지 않는다.
- 미구현 (F1-02 범위 밖): 이미지와 S3 삭제(F1-03), 리뷰(F1-04), AI 요약(F1-05), 찜(F1-06), 관리자(F1-09). API_SPEC의 오류 `details` 배열은 여전히 없다.

### 6.29 PR #37 병합 후 문서 동기화 (2026-11-27)

- 브랜치: `docs/sync-after-pr37` (기준: `main` `1f885dd`, PR #37 squash 병합 커밋). 문서만 수정했다. 기능 코드, SQL 파일, 인증·보안 코드는 바꾸지 않았다.
- 갱신: README 현재 상태(F1-02 완료, 미구현 목록 F1-03~F1-09), 구현된 맛집 경로 요약. PROGRESS 0·1·2절, 7절. API_SPEC 4절 구현 상태 메모(차이 목록), 4.3 위도·경도 쌍 규칙. FUNCTION_SPEC F1-02 구현 상태 메모, 등록 흐름 위도·경도 쌍 규칙.
- 구현과 명세의 차이 (명세 본문은 바꾸지 않고 메모로만 기록):
  - 목록 `thumbnailUrl`, 삭제 응답 본문 설명의 `storageDeleted` 필드, 상세 `images`·`favoriteCount`·`isFavorited` 자리표시값, 공통 오류 `details` 배열.
  - 삭제 흐름의 자식 행 삭제, `storage_delete_task` 기록, S3 삭제 (F1-03 이후).
  - 목록 대표 이미지, 상세 갤러리·리뷰·찜·AI 요약 (F1-03~F1-06).
- DATABASE_ERD 2.2·2.3은 `db/manual/004_create_category_and_restaurant.sql`과 컬럼, 기본값, FK, CHECK, 인덱스 이름이 일치하여 수정하지 않았다.
- 검증: 변경 파일 목록 확인, `git diff --check`, 문서 내 F1-02 상태와 PR #37 번호 일관성 확인, 기능 코드와 SQL 변경 없음 확인. 이 문서 작업에서는 Gradle 테스트를 다시 실행하지 않았다. 테스트 수치는 6.27·6.28의 실행값을 그대로 인용했다.

### 6.30 F1-03 맛집 이미지와 S3 연동 구현 (2026-11-27)

- 브랜치: `feat/f1-03-restaurant-images-s3` (기준: `main` `16b9869`). PR 생성 후 병합 전이다. 병합 뒤 실제 AWS 버킷 검증이 필요하다.
- DB: `db/manual/005_create_restaurant_image_and_storage_delete_task.sql` 추가. `restaurant_image`(DATABASE_ERD 2.4, FK `ON DELETE CASCADE`, `uk_image_s3_key`, `idx_image_restaurant`)와 `storage_delete_task`(2.9, `uk_storage_task_s3_key`, `idx_storage_task_created`). 명세에 없던 CHECK 4개를 추가했다(이미지 크기·형식·순서, 재시도 횟수). SQL 001~004는 바꾸지 않았다.
- 의존성: `software.amazon.awssdk:s3`를 추가했다. 버전은 `software.amazon.awssdk:bom:2.31.78`으로 고정했다(`build.gradle` `dependencyManagement`). 다른 의존성은 추가하지 않았다.
- 설정: 환경 변수 `AWS_REGION`, `AWS_S3_BUCKET`(`application.yml`의 `app.aws.s3`). 자격 증명은 AWS 기본 체인이 찾는다(코드·설정에 키 없음). 설정이 없으면 항상 실패하는 저장소를 쓴다. 가짜 성공은 없다. 요청 크기는 파일 5MB, 요청 전체 6MB로 제한했다.
- 업로드(API 5.1, 화면 `POST /restaurants/{id}/images`): 작성자 확인과 개수 사전 확인 → 파일 검증(MIME 헤더, 확장자, 실제 파일 시그니처, 크기, 0바이트 금지) → S3 업로드 → DB 저장. DB 저장은 맛집 행을 잠근 뒤 다시 개수를 세므로 동시 요청도 5장을 넘지 않는다(설계 의도). DB 저장이 실패하면 올린 객체를 지우고, 지우기도 실패하면 `storage_delete_task`에 별도 트랜잭션으로 남긴다. S3 업로드 실패는 DB에 아무것도 쓰지 않고 502 `STORAGE_ERROR`다.
- 삭제(API 5.2, 화면 `POST /restaurants/{id}/images/{imageId}/delete`): DB 트랜잭션에서 이미지 행 삭제와 `storage_delete_task` 기록을 같이 하고 커밋한다. 커밋 뒤 S3를 지운다. 성공하면 기록을 지우고, 실패하면 기록을 남기고 `storageDeleted: false`를 준다. DB 트랜잭션 실패 시에는 S3를 건드리지 않는다.
- 맛집 삭제(`RestaurantService.delete`): 같은 방식으로 이미지 키를 기록하고 맛집과 이미지 행을 지운다. 커밋 뒤 S3를 지운다. 응답의 `deletedImageCount`와 `storageDeleteFailedCount`는 실제 처리 결과다. 이전 F1-02의 0 고정 값은 없어졌다.
- 조회: 목록 항목에 `thumbnailUrl`(대표 이미지 = `sort_order` 최소), 상세에 `images`. 수정 화면에 사진 목록, 업로드 폼, 삭제 버튼을 넣었다. 이미지가 없을 때의 기본 문구가 있다.
- 보안 유지: 화면은 세션과 CSRF, API는 JWT, 작성자 본인만. 관리자 우회 권한은 추가하지 않았다. 화면 보호 경로에 `/restaurants/*/images`, `/restaurants/*/images/**`를 추가했다. 기존 회원·인증·Google·Refresh Token·요청 제한 코드는 바꾸지 않았다.
- S3 오류 메시지: HTTP 상태와 AWS 오류 코드 중 영문·숫자·`._-`만 남기고 서명, 자격 증명, 요청 헤더는 넣지 않는다. 이 메시지가 `storage_delete_task.last_error`에 들어간다.
- 테스트 (신규): `ImagePolicyTest` 10, `S3ImageStorageTest` 6(S3Client 목), `RestaurantImageServiceTest` 15(목, 보상·기록 분기 포함), `RestaurantImageDbTest` 12(MySQL, SQL 001~005 적용), `RestaurantImageApiHttpTest` 11, `RestaurantImageScreenHttpTest` 7, `ImageUploadErrorAdviceTest` 3. 기존 `RestaurantDbTest`는 새 의존성에 맞춰 수정했다(10개 유지).
- 기존 테스트 수정: 맛집·회원 슬라이스 테스트에 `RestaurantImageService` 목을 추가했다. `RestaurantServiceTest`는 새 생성자에 맞췄다.
- 실행 결과 (실제 실행값, 이 커밋 기준):
  - DB 미설정 `./gradlew clean test`: 674개 실행, 건너뜀 105개(모두 DB 테스트), 실패 0개, 오류 0개.
  - 테스트 DB 설정(MySQL 8.0.46 로컬, SQL 001~005 적용, `TEST_DB_URL`에 `characterEncoding=UTF-8`) `./gradlew clean test`: 674개 실행, 건너뜀 0개, 실패 0개, 오류 0개.
  - `./gradlew bootJar`: 성공. 산출물에 `s3-2.31.78.jar` 포함을 확인했다.
  - `git diff --check`: 통과.
- 미검증 (중요):
  - 실제 AWS S3 버킷 업로드·삭제는 하지 않았다. S3 연동은 목으로만 확인했다.
  - 버킷 공개 읽기 정책과 CDN, 운영 IAM 권한은 확정하지 않았다. 이미지 URL이 브라우저에서 열리는지 확인하지 않았다.
  - 브라우저에서 수정 화면의 업로드·삭제, 목록 썸네일, 상세 갤러리를 직접 확인하지 않았다. 화면 테스트는 HTML 문자열만 확인한다.
  - 실제 Tomcat의 요청 크기 초과 응답은 확인하지 않았다. `ImageUploadErrorAdvice`는 단위 테스트와 MockMvc로만 확인했다.
  - 동시 업로드 경합은 실제 병렬 요청으로 테스트하지 않았다. 잠금 쿼리와 순차 DB 테스트만 있다.
  - 리뷰·찜·AI 요약 행의 삭제 연동은 해당 기능이 없어서 아직 없다(F1-04~F1-06).
  - 관리자 S3 삭제 재시도는 F1-09 범위라서 구현하지 않았다.
- DB 적용 방법: `db/manual/005_create_restaurant_image_and_storage_delete_task.sql`을 `--default-character-set=utf8mb4`로 한 번 실행한다. 순서는 001 → 002 → 003 → 004 → 005다. 재실행하면 실패한다.

#### 6.30 후속: 삭제 동시성 수정 (같은 PR #39)

- 수정: `RestaurantService.delete`와 `RestaurantImageService.delete`가 맛집 행을 일반 조회 대신 `findByIdForUpdate`로 잠근다. 잠근 뒤 작성자 확인, 이미지 목록 조회(맛집 삭제), 해당 이미지 확인(이미지 삭제), `storage_delete_task` 기록, 행 삭제 순서다. S3 삭제는 DB 커밋 뒤에만 한다. 같은 맛집에 대한 업로드(`saveMetadata`)도 같은 잠금을 쓰므로 세 경로는 한 맛집 행에서 직렬화된다.
- 발견 원인: 일반 조회로 이미지 목록을 읽은 뒤 커밋되지 않은 업로드가 끼어들면, 맛집 삭제가 그 이미지 행을 `ON DELETE CASCADE`로 지우면서 `storage_delete_task`에 기록하지 않아 S3 객체가 고아로 남을 수 있었다.
- 테스트: `RestaurantDeleteConcurrencyDbTest` 7개 (실제 MySQL 8.0.46, SQL 001~005). 잠금 대기(맛집 삭제가 다른 트랜잭션의 잠금이 풀릴 때까지 기다리는지), 업로드와 맛집 삭제 경합 25회(고아 객체 없음), 보상 경합(업로드 중 맛집 삭제, 객체는 보상으로 지움), 개별 이미지 삭제와 맛집 삭제 경합 25회(키마다 정확히 한 번 삭제, 추적 기록 중복 없음), 맛집 삭제 트랜잭션 중간 실패(S3 미호출, 행과 기록 롤백), 이미지 삭제 트랜잭션 실패(S3 미호출), 맛집이 먼저 지워진 뒤 이미지 삭제(RESTAURANT_NOT_FOUND, 기록 없음).
- 수정 전 코드 검증: 같은 테스트를 일반 조회 버전에 실행하면 업로드 경합에서 고아 객체가, 개별 삭제 경합에서 추적 기록 키 중복(`uk_storage_task_s3_key` 위반)이 실패했다. 잠금 대기 테스트는 수정 전후 모두 통과했다. 이 테스트는 `DELETE` 문이 잠금에 막힘을 확인하는 것이므로 경합의 판별은 앞의 두 테스트가 한다.
- 실행 결과 (수정 커밋 기준):
  - DB 미설정 `./gradlew clean test`: 681개 실행, 건너뜀 112개(모두 DB 테스트), 실패 0개.
  - 테스트 DB 설정 `./gradlew clean test`: 681개 실행, 건너뜀 0개, 실패 0개.
  - `./gradlew bootJar`: 성공. `git diff --check`: 통과.
- 미검증: 동시성 테스트는 로컬 MySQL 8.0.46에서만 실행했다. EC2에 설치될 운영 MySQL 설정(격리 수준, 잠금 대기 시간)에서는 확인하지 않았다. 실제 AWS S3 검증은 여전히 하지 않았다.

### 6.31 PR #39 병합 후 문서 동기화 (2026-11-27)

- 기준: `main` `123db18` (PR #39 squash 병합 커밋, `feat: F1-03 맛집 이미지와 AWS S3 연동 (#39)`). 브랜치 `docs/sync-after-pr39`. 문서만 수정했다. Java, 테스트 코드, SQL 파일은 바꾸지 않았다.
- 갱신: README 기준 커밋과 F1-03 상태. PROGRESS 0·1·2절, 7절(다음 개발 단계는 F1-04). API_SPEC 4·5절 구현 상태 메모와 화면 URL 표에 수정 화면의 사진 업로드·삭제 경로 두 개 추가. FUNCTION_SPEC F1-03 구현 상태. DATABASE_ERD 4.1 삭제 절차의 잠금 단계와 6.1 SQL 적용 순서(001~005).
- 코드와 대조한 결과: 업로드 검증(MIME, 확장자, 파일 시그니처, 크기, 0바이트), 개수 제한 5장(맛집 행 잠금 뒤 재확인), DB 저장 실패 시 보상 삭제, 보상 실패 시 `storage_delete_task` 기록, 삭제 실패 기록(`retry_count` 증가와 `last_error`)을 구현과 확인했다. 맛집 삭제, 이미지 업로드, 이미지 개별 삭제는 맛집 행을 `findByIdForUpdate`(PESSIMISTIC_WRITE)로 잠근다(`d316c94`). SQL 005의 테이블, FK `ON DELETE CASCADE`, UNIQUE, INDEX는 ERD 2.4·2.9와 일치하고, CHECK 4개는 구현 메모로 남겼다.
- 자동 재시도: 구현되어 있지 않다. 스케줄러나 재시도 작업이 없고, 실패 객체는 `storage_delete_task`에 남는다. 재시도 대기 목록과 재시도는 F1-09 범위다. 문서에서 완료로 표현하지 않았다.
- 클라이언트 측 검사: 파일 선택 `accept` 속성과 `required`만 있다. 크기와 형식을 미리 검사하는 스크립트는 없다. 서버 검사는 동작한다. 명세 본문은 바꾸지 않고 차이로만 기록했다.
- 테스트 수치: 6.30 후속 실행 기록을 그대로 인용했다. 이번 문서 작업에서는 Gradle 테스트를 새로 실행하지 않았다.
- 미검증: 실제 AWS S3 버킷 업로드·삭제, EC2 IAM 권한, 버킷 공개 정책과 CDN, 브라우저 화면 동작은 확인하지 않았다.

### 6.32 F1-04 리뷰 구현 (2026-11-27)

- 브랜치: `feat/f1-04-reviews` (기준: `main` `018be2e`). PR 생성 후 병합 전이다.
- 조사: `review` 테이블은 어떤 SQL에도 없었다. `004`의 `review_count`는 맛집 컬럼이다. `restaurant.average_rating`(DECIMAL(2,1))과 `review_count`는 004에 이미 있어 바꾸지 않았다. 리뷰 API와 화면 경로는 기존 구현과 겹치지 않았다.
- DB: `db/manual/006_create_review.sql` 추가. `review`(DATABASE_ERD 2.5, FK는 맛집 쪽 `ON DELETE CASCADE`, 회원 쪽 `ON DELETE RESTRICT`, `uk_review_member_restaurant`, `ck_review_rating`, `idx_review_restaurant_created`). SQL 001~005는 바꾸지 않았다. 적용 순서는 001 → 002 → 003 → 004 → 005 → 006이다.
- API: `GET /api/restaurants/{id}/reviews`(공개), `POST /api/restaurants/{id}/reviews`(JWT, 201), `DELETE /api/reviews/{reviewId}`(JWT, 200). 응답은 API_SPEC 6.1~6.3과 같다.
- 화면: 상세 화면의 리뷰 목록(최신순, 10개 단위 페이지), 로그인 회원의 작성 폼, 본인 리뷰의 삭제 버튼. 제출은 `POST /restaurants/{id}/reviews`, `POST /reviews/{reviewId}/delete`(세션과 CSRF). 비로그인은 작성 폼 대신 로그인 안내를 본다.
- 동시성: 작성과 삭제는 맛집 행을 `findByIdForUpdate`(PESSIMISTIC_WRITE)로 잠근 뒤 권한·중복을 확인하고, 리뷰 행에서 개수와 평점 합을 다시 계산해 맛집 값을 쓴다. 삭제는 리뷰가 속한 맛집을 먼저 찾고, 잠근 뒤 리뷰를 다시 읽는다. 맛집 삭제는 리뷰를 명시적으로 지운 뒤 맛집을 지운다. 잠금을 일반 조회로 바꾼 변형에서 동시성 테스트 4개가 교착·리뷰 수 불일치·중복 허용으로 실패함을 확인했고, 수정본을 복원했다.
- 평점: `BigDecimal`로 받아 정수인지 직접 확인한다. `Integer`로 받으면 Jackson이 5.5를 5로 바꿔 저장할 수 있어서 쓰지 않았다. 평균은 합계 나누기 개수를 소수 첫째 자리 HALF_UP으로 반올림한다.
- 설정: `SecurityConfig`에 공개 GET 경로 `/api/restaurants/*/reviews`를, 화면 인증 필수 경로에 `/restaurants/*/reviews`, `/reviews/*/delete`를 추가했다. 인증·JWT·세션 정책 자체는 바꾸지 않았다.
- 맛집 삭제 연동: `RestaurantService.delete`의 트랜잭션에 리뷰 삭제를 추가했다.
- FK 정책 수정: 회원 쪽 `fk_review_member`를 CASCADE에서 RESTRICT로 바꿨다. CASCADE이면 리뷰가 남은 회원을 지울 때 리뷰가 DB에서 조용히 지워지고, 맛집의 평균과 리뷰 수가 갱신되지 않아 집계가 어긋난다. 이제 DB가 삭제를 막는다. 회원 탈퇴 정책(T1)이 정해지면 서비스 경로로 처리한다. 맛집 쪽 CASCADE는 맛집 삭제 트랜잭션이 리뷰를 먼저 지우므로 그대로 둔다.
- 관련 테스트: `ReviewDbTest`에 FK 정책 검사(information_schema의 DELETE_RULE), 리뷰가 남은 회원 삭제 거부와 집계 유지, 리뷰가 없는 회원 삭제 허용을 추가했다. 회원 쪽 정책을 CASCADE로 되돌린 변형에서 FK 테스트 2개가 실패함을 확인하고 RESTRICT로 복원했다.
- 테스트 (신규 46개): `ReviewInputRulesTest` 7(단위), `ReviewDbTest` 17(MySQL, FK와 롤백 포함), `ReviewConcurrencyDbTest` 4(MySQL, 동시성), `ReviewApiHttpTest` 10, `ReviewScreenHttpTest` 8.
- 실행 결과 (이 브랜치, 이 세션에서 실제 실행):
  - DB 미설정 `./gradlew clean test`: 727개 실행, 건너뜀 133개(모두 DB 테스트), 실패 0개, 오류 0개.
  - 테스트 DB 설정(MySQL 8.0.46 로컬, SQL 001~006 적용) `./gradlew clean test`: 727개 실행, 건너뜀 0개, 실패 0개, 오류 0개.
  - 참고: 한 번의 DB 포함 실행에서 기존 `GlobalExceptionHandlerTest.rateLimitExceededIsRenderedAs429WithRetryAfterAndNoInternals`가 실패했다. 응답 타임스탬프의 초가 42일 때 본문에 `"42"`가 들어가 단정이 깨지는 기존 테스트의 불안정성이다. 이 PR의 범위가 아니므로 고치지 않았고, 단독 실행과 전체 재실행은 통과했다.
  - `./gradlew bootJar`: 성공. `git diff --check`: 통과.
- 미검증: 브라우저에서 리뷰 화면 동작, EC2 운영 MySQL의 격리 수준과 잠금 대기 설정에서의 동시성, 실제 Tomcat에서의 HTTP 요청(MockMvc와 DB 테스트로만 확인).
- 남은 문제: 맛집 행의 `updated_at`이 집계가 바뀔 때도 갱신된다(맛집 정보 수정 시각과 섞임, 후속 검토). 리뷰 수정 기능은 명세에 없어 만들지 않았다. API 오류 `details` 배열은 아직 없다. 내용 길이는 Java 문자열 길이 기준이라 이모지 같은 보조 평면 문자는 2자로 센다(후속 검토). AI 요약 캐시 무효화는 F1-05에서 추가한다. 관리자 리뷰 삭제는 F1-09 범위다.

### 6.33 PR #41 병합 후 문서 동기화 (2026-11-27)

- 기준: `main` `0f8b1f8` (PR #41 squash 병합 커밋, `feat: F1-04 맛집 리뷰 작성·목록·삭제와 평균 평점 동기화 (#41)`). 브랜치 `docs/sync-after-pr41`. 문서만 수정했다.
- 병합 내용 확인: `main`의 리뷰 코드와 SQL 006은 PR #41 마지막 커밋(`1e15d6a`)과 같다.
- 갱신: README 기준 커밋과 F1-04 상태, 리뷰 FK 제약. PROGRESS 0·1·2절과 7절(다음 개발 단계는 F1-05). API_SPEC 6절 상태 메모. FUNCTION_SPEC 리뷰 상태와 삭제 흐름 차이. DATABASE_ERD 6.1 제목(001~006).
- 대조: `fk_review_member`는 RESTRICT, `fk_review_restaurant`은 CASCADE. 리뷰 API 경로(GET·POST `/api/restaurants/{id}/reviews`, DELETE `/api/reviews/{reviewId}`)와 화면 경로(POST `/restaurants/{id}/reviews`, POST `/reviews/{reviewId}/delete`)가 코드와 같다. 오류 코드 `REVIEW_ALREADY_EXISTS`(409), `REVIEW_NOT_FOUND`(404)가 코드와 같다. 적용 순서는 001 → 002 → 003 → 004 → 005 → 006이다.
- 미구현으로 유지: AI 리뷰 요약(F1-05), 찜(F1-06), AI 맛집 추천(F1-07), 메인 페이지(F1-08), 관리자 기능(F1-09). 회원 탈퇴 API와 탈퇴 절차(T1)는 없다.
- 테스트: 이번 문서 작업에서는 Gradle 테스트를 새로 실행하지 않았다. 수치는 6.32의 실행 기록을 그대로 인용했다.
- 미검증: 브라우저 리뷰 화면, EC2 운영 MySQL의 동시성과 FK 동작, 실제 Tomcat의 HTTP 요청은 확인하지 않았다.

### 6.34 F1-05 AI 리뷰 요약·캐싱 구현 (2026-11-27)

- 브랜치: `feat/f1-05-ai-review-summary` (기준: `main` `6296a4e`). PR 생성 후 병합 전이다.
- 조사: 기존 코드에는 OpenRouter 설정, 호출 코드, AI 의존성이 없었다. `build.gradle`에 새 의존성을 추가하지 않았다. 외부 호출은 Spring Web의 `RestClient`로 한다.
- 설계 쟁점 (보고 필요): 개발 규칙은 AI 연동에 Spring AI를 쓰고 F1-05 단계에서 도입한다고 적었다. 이번 구현은 Spring AI를 쓰지 않았다. 이유: Spring AI의 OpenAI 자동 구성은 API 키가 없으면 기동 단계에서 실패할 수 있어, 키가 없는 로컬 개발과 기존 테스트 전체에 영향을 준다. 한 번의 chat completion 호출에 Spring AI 전체 구성은 필요하지 않다. 제한 시간을 HTTP 계층에서 직접 걸 수 있다. 검토 후 Spring AI로 바꾸는 것도 가능하다.
- DB: `db/manual/007_create_ai_review_summary.sql`. `ai_review_summary`(ERD 2.7, 맛집당 한 행 UNIQUE, `source_review_count >= 3` CHECK, FK CASCADE). SQL 001~006은 바꾸지 않았다. 적용 순서는 001 → 002 → 003 → 004 → 005 → 006 → 007이다.
- 설정: `app.ai.openrouter`(base-url 고정, `OPENROUTER_API_KEY`와 `OPENROUTER_MODEL`은 환경 변수, 제한 시간 10초). 모델 ID와 키는 Java 코드에 없다. 설정 객체의 문자열 표현은 키를 가린다. 키나 모델이 없으면 외부 호출 없이 UNAVAILABLE이다.
- 동작: 리뷰 3개 미만은 AI를 부르지 않고 안내한다. 캐시가 있고 리뷰 수가 같으면 재사용한다. 없으면 최신 리뷰 30개(본문 6000자 이내)로 프롬프트를 만든다. 작성자 이름과 ID는 보내지 않는다. 리뷰 내용은 데이터 구획 안에 넣고, 꺾쇠 괄호를 지운다. 응답은 1000자 이내 비어 있지 않은 본문만 저장한다.
- 무효화: 리뷰 작성과 삭제 트랜잭션이 맛집 행을 잠근 뒤 같은 트랜잭션에서 요약 행을 지운다. 맛집 삭제 트랜잭션도 요약 행을 지운다(FK CASCADE는 안전장치).
- 동시성: 같은 맛집의 동시 요청은 하나의 생성 작업을 공유한다. 외부 호출은 DB 잠금 밖에서 한다. 저장 직전에 맛집 행을 잠그고, 생성 시작 때의 리뷰 개수와 가장 큰 리뷰 ID를 다시 비교한다. 다르면 결과를 캐시하지 않는다. 여러 서버 인스턴스 사이의 중복 생성은 막지 않는다. 저장은 UNIQUE로 하나만 남긴다.
- 실패: 호출 실패, 제한 시간 초과, 설정 없음, 잘못된 응답은 모두 UNAVAILABLE이다. 예외로 나가지 않으며, 캐시하지 않고, 상세 화면은 정상 표시한다.
- 화면과 API: 상세 화면에 요약 블록을 넣었다. `GET /api/restaurants/{id}/summary`는 공개 경로다. 보안 설정에는 경로 항목만 추가했다.
- 테스트 (신규 33개): `OpenRouterPropertiesTest` 3, `OpenRouterChatClientTest` 6(MockRestServiceServer로 모킹, 제한 시간은 실제 로컬 서버로 확인), `ReviewSummaryPromptTest` 6, `ReviewSummaryDbTest` 12(MySQL, AI는 목), `ReviewSummaryApiHttpTest` 4, `ReviewScreenHttpTest` 2 추가.
- 변형 검증: 저장 직전 비교를 끄면 "생성 중 리뷰 변경" 테스트가 실패했다. 동시 요청 공유를 끄면 "동시 요청" 테스트가 실패했다. 두 경우 모두 원본을 복원했다.
- 실행 결과 (이 브랜치, 이 세션에서 실제 실행):
  - DB 미설정 `./gradlew clean test`: 760개 실행, 건너뜀 145개(모두 DB 테스트), 실패 0개, 오류 0개.
  - 테스트 DB 설정(MySQL 8.0.46 로컬, SQL 001~007 적용) `./gradlew clean test`: 760개 실행, 건너뜀 0개, 실패 0개, 오류 0개.
  - `./gradlew bootJar`: 성공. `git diff --check`: 통과.
- 미검증: 실제 OpenRouter 호출, 운영 키와 모델 설정, 브라우저 화면, 요약 API 요청 제한(현재 없음), 여러 인스턴스 사이의 중복 생성 방지.

### 6.35 F1-05 OpenRouter 호출을 Spring AI로 전환 (PR #43 보완, 2026-11-27)

- 브랜치: `feat/f1-05-ai-review-summary` (PR #43에 추가 커밋). 6.34의 설계 쟁점(Spring AI 미사용)은 이 항목에서 해결했다.
- 의존성: `build.gradle`에 `spring-ai-bom:1.1.8`(Spring Boot 3.5와 맞는 1.1.x 라인)과 `spring-ai-starter-model-openai`를 넣었다. 버전은 BOM이 정한다.
- 구조: `OpenAiApi` 빈(`AiConfig`)이 OpenRouter 주소(`app.ai.openrouter.base-url`), 키, 제한 시간 10초를 쓴다. `ChatModel`은 스타터 자동 구성이 만든다. `ChatCompletionClient`는 유지하고 구현을 `OpenRouterChatModelClient`(Spring AI `ChatModel` 호출)로 바꿨다. 직접 `RestClient`로 OpenRouter를 부르던 `OpenRouterChatClient`와 그 테스트는 삭제했다.
- 설정 (`application.yml`): `spring.ai.model.chat=openai`, 임베딩·이미지·음성(생성·전사)·모더레이션은 `none`. 켜 두면 키가 없을 때 기동이 실패한다. `spring.ai.retry.max-attempts=1`로 자동 재시도를 끈다. 모델 ID는 `OPENROUTER_MODEL`에서 읽어 호출 옵션으로 넘긴다. 코드에 없다.
- 기존 동작 유지: 3개 미만 안내, 최신 리뷰 30개, 글자 수 제한, 개인정보 미전달, 리뷰 내용과 시스템 지시 분리, 응답 검증, MySQL 캐싱, 캐시 재사용, 리뷰 작성·삭제 시 무효화, 동시 생성 공유, 생성 중 변경 시 저장 금지, 실패 시 UNAVAILABLE, 제한 시간 10초. 서비스의 캐시 정책과 트랜잭션 경계는 바꾸지 않았다.
- 발견: 스타터의 OpenAI 자동 구성 전부(음성, 이미지, 임베딩, 모더레이션)가 키가 없으면 기동을 막았다. 채팅만 끄거나 켜서는 해결되지 않아서, 나머지를 `none`으로 껐다. `@EnableAutoConfiguration`을 쓰고 `AiConfig`를 가져오지 않는 `ScreenRateLimitForwardTest`에는 `spring.ai.model.chat=none`을 넣었다(AI가 필요 없는 테스트).
- 테스트: `OpenRouterChatModelClientTest` 10개(요청 형식, 모델과 옵션 전달, Bearer 헤더, 서버 오류, 연결 실패, 빈 응답·본문 null, 예상 못한 예외, 키 없음·모델 없음 시 ChatModel 미호출, 제한 시간, HTTP 오류 시 재시도 없음 1회). `AiConfigWiringTest` 3개(키와 모델 없이 기동, 스타터 자동 구성 포함 기동, 자동 구성 ChatModel의 재시도 없음). 외부 OpenRouter에는 접속하지 않았다. 재시도 설정 변형 검증: `max-attempts`를 3으로 바꾸면 `AiConfigWiringTest`의 재시도 테스트가 실패했고, 원복 후 통과했다.
- 실행 결과 (이 브랜치, 이 세션에서 실제 실행):
  - DB 미설정 `./gradlew clean test`: 767개 실행, 건너뜀 145개(모두 DB 테스트), 실패 0개, 오류 0개.
  - 격리 MySQL 8.0.46 (SQL 001~007 적용, 테스트 전용 계정), `./gradlew clean test`: 767개 실행, 건너뜀 0개, 실패 0개, 오류 0개.
  - `./gradlew bootJar`: 성공. `git diff --check`: 통과.
- 미검증: 실제 OpenRouter 호출, 운영 키와 모델 설정, 브라우저 화면, 요약 API 요청 제한(현재 없음), 여러 인스턴스 사이의 중복 생성 방지. 사용하는 Spring AI 1.1.8 API 세부 동작은 모킹과 로컬 서버 테스트로만 확인했다.
- 남은 문제: 상세 화면의 캐시 미스는 요약 생성을 기다리므로 최대 10초까지 늦어질 수 있다. 요약 API에는 요청 제한이 없어 캐시 미스가 반복되면 AI 비용이 늘 수 있다. 회원 탈퇴 절차(T1)는 아직 없다.

### 6.36 PR #43 병합 후 F1-05 문서 동기화 (문서 전용)

- 병합: PR #43 (`feat/f1-05-ai-review-summary`)이 `main`에 병합되었다. merge commit `39532fa2a8439eb6834e87646fa2a6d95cb76e8b`, 기준 `main` `6296a4e`.
- 작업 브랜치: `docs/sync-after-pr43` (기준: `main` `39532fa`). 문서 전용 PR이다.
- 확인: SQL 007(`ai_review_summary`, FK CASCADE, UNIQUE, CHECK `source_review_count >= 3`)과 ERD 2.7이 일치함을 확인했다. 무효화 호출은 리뷰 작성·삭제와 맛집 삭제 세 곳뿐이며, 회원 탈퇴와 관리자 삭제 경로는 아직 없다.
- 문서 변경: README(기준 커밋, F1-05 병합 상태, 요약 API 경로), PROGRESS(요약 표, 1절, 2절 F1-05 행, 7절, 이 항목), API_SPEC 7절 상태 문구, FUNCTION_SPEC F1-05 상태와 무효화 범위, DATABASE_ERD 2.7 무효화 범위.
- 수정한 사실 오류: 무효화 범위에 "회원 삭제, 관리자 삭제"가 구현된 것처럼 적혀 있었다. 두 경로는 아직 없으므로 미구현으로 고쳤다.
- 테스트: 이번 문서 작업에서 테스트를 새로 실행하지 않았다. 6.35의 수치는 PR #43 보고값이다.
- 코드 변경: 없음. `src/`, `db/`, `build.gradle`은 변경하지 않았다.
- 미검증 유지: 실제 OpenRouter 호출, 운영 키·모델 설정, 브라우저 화면, 요약 API 요청 제한(없음), 여러 인스턴스의 중복 생성 방지.
- 다음 단계: F1-06 찜하기·찜 목록 (미착수).

### 6.37 F1-06 찜하기·찜 목록 구현 (2026-11-27)

- 브랜치: `feat/stage2-5-restaurant-favorites` (기준: `main` `238a14d`). PR #45로 `main` 병합 완료(merge commit `c9cb668`).
- DB: `db/manual/008_create_favorite.sql`. `favorite`(DATABASE_ERD 2.6). 회원·맛집 FK 모두 CASCADE, `uk_favorite_member_restaurant` UNIQUE, `idx_favorite_member_created`(내 찜 목록), `idx_favorite_restaurant`(맛집별 찜 수). SQL 001~007은 바꾸지 않았다. 적용 순서는 001 → 002 → 003 → 004 → 005 → 006 → 007 → 008이다.
- 서비스: `FavoriteService`. 찜하기는 맛집 행을 잠근 뒤 중복을 검사하고 저장한다. 동시 요청이 겹치면 UNIQUE가 최종으로 막는다. 찜 취소는 회원 ID로만 찾으므로 본인 찜만 지운다. 목록은 최신 찜 순, 페이지 크기 기본 12, 최대 50이다.
- API: `POST /api/restaurants/{id}/favorite`(201), `DELETE /api/restaurants/{id}/favorite`(200), `GET /api/me/favorites`(200). 모두 JWT가 필요하다. 응답은 기존 `ApiSuccessResponse`다.
- 화면: 맛집 상세에 찜 버튼과 찜 수를 넣었다. 비회원에게는 로그인 안내를 준다. `/favorites`에 찜 목록을 만들었다(빈 목록 안내, 페이지 이동, 찜 취소). 상단 메뉴에 `찜 목록` 링크를 넣었다. 제출 경로는 `POST /restaurants/{id}/favorite`, `POST /restaurants/{id}/favorite/delete`다. 화면 세션과 CSRF를 쓴다.
- 경로 결정: 요청서의 `/mypage/favorites`와 `/api/mypage/favorites`는 채택하지 않았다. 명세의 `/favorites`와 `/api/me/favorites`를 유지했다. 테이블 이름도 명세의 `favorite`을 썼다(요청서의 `restaurant_favorite` 대신).
- 기존 동작 변경: 맛집 상세 응답의 `favoriteCount`와 `isFavorited`가 실제 값이 되었다(이전에는 0과 false 자리표시). 맛집 삭제 트랜잭션은 찜 행을 리뷰보다 먼저 지운다. `RestaurantService` 생성자에 `FavoriteRepository`가 추가되었다. 보안 설정에는 화면 보호 경로 세 개(`/favorites`, `/restaurants/*/favorite`, `/restaurants/*/favorite/delete`)를 추가했다. API 체인 규칙은 바꾸지 않았다. 오류 코드 `FAVORITE_ALREADY_EXISTS`(409), `FAVORITE_NOT_FOUND`(404)를 추가했다.
- 테스트 수정: 새 찜 컨트롤러가 `@WebMvcTest` 슬라이스에 스캔되므로, 모든 WebMvc 테스트 클래스에 `FavoriteService` 목을 추가했다(테스트 설정만 바뀌고 검증 내용은 바뀌지 않았다).
- 테스트 (신규): `FavoriteDbTest` 16개(MySQL), `FavoriteApiHttpTest` 10개, `FavoriteScreenHttpTest` 12개. DB 테스트는 정상 등록, 중복, 서로 다른 회원, 취소, 이미 취소된 찜, 없는 맛집, 회원별 분리, 최신 찜 순, 페이지, 맛집 삭제 정리, 상세 찜 값, UNIQUE 제약, 회원 삭제 CASCADE, 목록 크기 검증, 같은 회원의 동시 찜 요청(10라운드, 성공 1과 중복 1, 행 1개)을 확인한다.
- 실행 결과 (이 브랜치, 이 세션에서 실제 실행):
  - 테스트 DB 설정(로컬 MySQL 8.0.46, SQL 001~008 적용, 테스트 전용 계정) `./gradlew clean test`: 805개 실행, 건너뜀 0개, 실패 0개, 오류 0개.
  - DB 미설정 `./gradlew clean test`: 805개 실행, 건너뜀 161개(모두 DB 테스트, 이전 145개 + 찜 16개), 실패 0개, 오류 0개.
  - `./gradlew bootJar`: 성공. `git diff --check`: 통과.
- 미검증: 브라우저에서 찜 버튼 상태와 찜 목록 화면, 목록 카드의 찜 버튼(미구현), 운영 MySQL 설정에서의 동시 찜, 회원 탈퇴 경로(T1 미정).

### 6.38 F1-06 보완: 맛집 목록 카드 찜 버튼, 기록 정정 (PR #45 추가 커밋)

- 목록 카드 찜 버튼: 맛집 목록(`/restaurants`)과 홈(`/`)의 카드에 찜하기·찜 취소 버튼을 넣었다. 로그인 회원은 찜 여부에 따라 버튼 하나만 본다. 비회원은 "로그인하고 찜하기" 링크를 본다. 6.37의 "카드 찜 버튼 미구현" 기록은 이 항목에서 해결했다.
- 찜 상태 조회: 목록에 보이는 맛집 ID들을 `FavoriteRepository.findFavoritedRestaurantIds`의 IN 쿼리 한 번으로 구한다. 맛집마다 조회하지 않는다. 비회원은 조회하지 않고 빈 집합이다. 상세 화면의 찜 여부와 같은 테이블을 읽는다.
- DTO: `RestaurantSummary`와 목록 API 응답은 바꾸지 않았다. 찜 상태는 화면 모델(`favoritedIds`)에만 둔다. 목록 API는 찜 정보를 주지 않는다(명세 밖).
- 복귀: 찜하기와 취소는 누른 화면으로 돌아간다. 목록은 검색어, 카테고리, 정렬, 페이지, 크기를 서버가 다시 조립해 유지한다. 홈은 `/`로 돌아간다. 복귀 대상은 `favorites`, `restaurants`, `home` 세 값만 인정하므로 외부 주소로 보내지 않는다. 카드는 맛집 위치로 스크롤하는 앵커(`#restaurant-{id}`)를 쓴다.
- 안내 문구: 목록에서 찜하기·취소·중복·찜 없음 결과를 안내한다.
- 보안: 카드 제출은 CSRF 토큰이 있어야 한다. 토큰이 없으면 403이다. 찜 경로는 세션이 있어야 하며 없으면 로그인으로 보낸다.
- 정정: SQL 순서 기록을 `001 → 002 → 003 → 004 → 005 → 006 → 007 → 008`로 고쳤다(6.37에 `001 → 008`로 잘못 적혀 있었다).
- 회귀 테스트: `FavoriteListButtonHttpTest` 11개(비회원 상태, 회원의 찜 여부별 버튼, 일괄 조회 1회, 찜하기 후 검색·페이지 유지 복귀, 찜 취소 후 홈 복귀, 중복 안내, CSRF 403, 열린 리다이렉트 차단, 검색·정렬·페이지가 서비스로 전달, 안내 문구, 홈 공유). `FavoriteDbTest`에 목록 찜 상태 일괄 조회 3개(쿼리 1회, 찜 없음과 빈 목록의 무조회, 상세와 일치, 다른 회원·삭제된 맛집 제외)를 추가했다.

### 6.39 F1-07 AI 맛집 추천 구현 (2026-11-27)

- 브랜치: `feat/stage2-5-ai-restaurant-recommendations` (기준: `main` `c9cb668`, PR #45 병합 뒤). PR 생성 후 병합 전이다.
- 기준 문서: `docs/DECISIONS.md`는 저장소에 없다. 명세는 FUNCTION_SPEC F1-07과 API_SPEC 9절을 따랐다.
- 구현: 취향 입력(1~200자) → 최근 등록 맛집 최대 100곳을 후보로 조회 → 후보가 없으면 AI를 부르지 않음 → F1-05와 같은 `ChatCompletionClient`(OpenRouter, `OPENROUTER_MODEL`, 타임아웃 10초)로 호출 → JSON 배열 `[{"restaurantId", "reason"}]` 파싱 → 후보에 있는 ID만 남기고 중복 제외 후 최대 5개 → 이름, 카테고리, 평점은 DB 후보에서 채움. 추천 결과는 저장하지 않음.
- AI 사용 방식: F1-05의 AI 설정과 클라이언트를 그대로 썼다. 새 설정과 새 클라이언트를 만들지 않았다. 프롬프트는 F1-05와 같은 방식으로 데이터 구획(`<candidates>`, `<preference>`)을 쓰고 구획 문자를 지운다. 취향 원문은 로그에 남기지 않는다.
- 오류: 취향 검증 실패 `400 VALIDATION_FAILED`, AI 호출 실패·타임아웃·설정 없음 `502 AI_UNAVAILABLE`, JSON이 아니거나 배열이 아니거나 필수 필드가 틀린 응답 `502 AI_RESPONSE_INVALID`. 두 오류 코드를 `ErrorCode`에 추가했다.
- 명세와 차이: (1) AI 응답은 순수 JSON 배열만 받는다. 마크다운 코드 블록으로 감싼 응답은 `AI_RESPONSE_INVALID`다. (2) 추천 이유는 300자까지만 쓴다. (3) 추천 결과가 없으면(후보는 있으나 AI가 고르지 않았거나 전부 후보에 없을 때) 안내 문구를 준다. 명세에는 후보가 0개일 때 문구만 있었다. (4) 화면 제출은 리다이렉트 없이 같은 화면에 결과를 그린다.
- 보안: `/recommend`는 세션 인증, `/api/recommendations`는 JWT 인증(USER, ADMIN 모두 허용). 기존 JWT 필터, SecurityFilterChain, OAuth2, Refresh Token 로직은 바꾸지 않았다. 화면 제출은 CSRF를 쓴다. F1-05 요약 동작은 바꾸지 않았다.
- 화면: `/recommend` (GET 입력 화면, POST 추천). 헤더에 `AI 추천` 링크를 넣었다. 카드는 이름, 카테고리, 평균 평점, 추천 이유, 상세 링크를 보인다.
- 테스트 (신규 33개): `RestaurantRecommendationServiceTest` 15(정상, 후보 0개 무호출, 100개 제한, 없는 ID 제외, 5개 제한, 중복 제외, 결과 없음 안내, 파싱 실패, 필수 필드 누락·형식 오류, AI 실패, AI 응답 검증 실패, 취향 검증, 200자 허용, 구획 탈출 차단, 추천 이유 300자 절단), `RecommendationApiHttpTest` 7(비인증 401, USER, ADMIN, 취향 누락과 길이 400, 알 수 없는 필드 400, AI_UNAVAILABLE 502, AI_RESPONSE_INVALID 502), `RecommendationScreenHttpTest` 9(비로그인 차단, 비로그인 CSRF 403, 입력 화면, 결과 카드, 후보 없음 안내, AI 실패 재시도 안내, AI 응답 오류 안내, 취향 검증 안내, 로그인 회원 CSRF 403), `RecommendationCandidateDbTest` 2(MySQL: 후보 없음 무호출, 최근 등록 순 최대 100개).
- 회귀: F1-05 AI 리뷰 요약의 테스트(`ai.*`, `review.*`)를 함께 실행해 통과했다. 모든 WebMvc 테스트 클래스에 추천 서비스 목을 추가했다(새 컨트롤러가 스캔되기 때문).
- 실행 결과 (이 브랜치, 이 세션에서 실제 실행):
  - 테스트 DB 설정 (로컬 MySQL 8.0.46, SQL 001~008 적용, 테스트 전용 계정) `./gradlew clean test`: 853개 실행, 건너뜀 0개, 실패 0개, 오류 0개 (두 번째 실행 결과). 첫 번째 실행에서는 기존 시각 의존 테스트 `GlobalExceptionHandlerTest.rateLimitExceededIsRenderedAs429WithRetryAfterAndNoInternals` 1건이 실패했다. 이 테스트는 응답 본문에 초 단위 숫자 `42`가 없어야 한다고 확인하며, 실행 시각의 초가 42일 때 실패한다(이전 기록과 같은 현상). 같은 테스트를 다시 실행하면 통과했고, 전체 실행도 두 번째에 통과했다.
  - DB 미설정 `./gradlew clean test`: 853개 실행, 건너뜀 167개(모두 DB 테스트), 실패 0개, 오류 0개.
  - `./gradlew bootJar`: 성공. `git diff --check`: 통과.
- 미검증: 실제 OpenRouter 호출과 추천 품질, 운영 키·모델 설정, 브라우저 화면(`/recommend`, 카드 표시), 추천 응답 지연 체감. 모든 AI 호출은 모킹 또는 로컬 서버로 확인했다.

### 6.40 F1-08 메인 홈페이지 완성 (2026-11-27)

- 브랜치: `feat/stage2-5-homepage-completion` (기준: `main` `9206026`, PR #46 병합 뒤). PR 생성 후 병합 전이다.
- 구현: `/`를 맛집 목록 재사용 화면에서 메인 홈 화면(`home.html`)으로 바꿨다. 서비스 소개와 검색 진입(`/restaurants?keyword=`), 카테고리 탐색(기존 DB 카테고리 10개, `/restaurants?category={id}`), 최신 등록 맛집 8곳(`RestaurantService.latest`)을 보여 준다. 맛집 목록과 검색 결과는 `/restaurants`가 맡는다. 비회원도 볼 수 있다.
- 최신 맛집 조회: `findByOrderByCreatedAtDescIdDesc(Pageable)`에 카테고리 엔티티 그래프를 붙여 맛집과 카테고리를 한 쿼리로 읽는다. 대표 이미지는 기존 한 번의 IN 쿼리를 재사용한다. 개수는 1~50으로 제한하며, 홈은 8이다. 맛집 전체를 읽지 않는다.
- 카드: 목록과 홈이 같은 카드 프래그먼트(`fragments/restaurant-card.html`)를 쓴다. 카드에는 이름, 카테고리, 주소, 평균 평점, 찜 버튼(회원), 상세 링크가 있다. 대표 이미지가 없으면 기존 회색 "사진 없음" 자리표시를 쓴다. 별도 이미지 파일은 추가하지 않았다.
- 반응형: 카드 그리드는 `row-cols-1 row-cols-sm-2 row-cols-lg-4`, 카테고리는 줄바꿈 가능한 버튼 묶음, 검색창은 모바일에서 한 줄로 쌓인다.
- 빈 상태: 맛집이 없으면 안내 문구를 보이고(회원에게는 등록 링크, 비회원에게는 로그인 링크), 카테고리는 계속 보인다.
- 찜 복귀: 홈에서 찜을 누르면 `/`로 돌아간다. 홈은 검색 조건이 없으므로 복귀 주소에서 목록 조건을 뺐다(`FavoriteScreenController`).
- 삭제·비공개: 맛집 테이블에 비공개나 소프트 삭제 열이 없다. 삭제는 행을 지우므로 홈 목록에서 자동으로 빠진다.
- API: 변경 없음. 홈은 서비스를 직접 호출하며 AI를 부르지 않는다. API_SPEC에 새 API를 추가하지 않았다.
- 테스트 (신규 12개): `HomePageHttpTest` 9(비회원 접근·검색 진입·카테고리 링크, 최신 순서와 상세 링크, 빈 상태, 사진 없는 카드, 대표 이미지, 비회원 찜 상태, 회원 찜 버튼, 반응형 클래스, 찜 안내 문구), `HomeLatestDbTest` 3(MySQL: 최근 등록 순과 8개 제한, 삭제된 맛집 제외, 맛집 2곳과 8곳의 쿼리 수가 같고 3 이하). 기존 테스트 3건은 새 홈 동작에 맞게 고쳤다(홈 뷰 이름, 홈의 최신 목록 스텁, 홈 복귀 주소).
- 실행 결과 (이 브랜치, 이 세션에서 실제 실행):
  - 테스트 DB 설정 (로컬 MySQL 8.0.46, SQL 001~008 적용, 테스트 전용 계정) `./gradlew clean test`: 두 번째 실행에서 865개 실행, 건너뜀 0개, 실패 0개, 오류 0개. 첫 실행에서는 기존 시각 의존 테스트 `GlobalExceptionHandlerTest.rateLimitExceededIsRenderedAs429WithRetryAfterAndNoInternals` 1건(응답 본문에 초 숫자 `42`가 나오는 경우)이 실패했다. 같은 테스트를 다시 실행해 통과했다.
  - DB 미설정 `./gradlew clean test`: 865개 실행, 건너뜀 170개(모두 DB 테스트), 실패 0개, 오류 0개.
  - `./gradlew bootJar`: 성공. `git diff --check`: 아래 PR 보고 참조.
- 미검증: 실제 브라우저에서 모바일과 데스크톱 레이아웃, 운영 데이터와 이미지 주소, 카드 이미지 로딩.


### 6.41 F1-09 관리자 기능 구현 (2026-11-27)

- 브랜치: `feat/stage2-5-admin-features` (기준: `main` `7b20233`, PR #47 병합 뒤). PR 생성 후 병합 전이다.
- DB: 변경 없음. SQL 001~008 그대로다. 신규 테이블과 관리자 전용 테이블은 없다.
- 권한: `AdminAccess.requireAdmin`이 인증 정보의 `ROLE_ADMIN`을 확인한다. 요청 파라미터와 본문의 role은 보지 않는다. 관리자 서비스 진입점마다 호출한다. SecurityConfig: API 체인에 `/api/admin/**` ADMIN 규칙과 JSON 403 핸들러(`ApiAccessDeniedHandler`), 화면 체인에 `/admin/**` ADMIN 규칙. 비로그인 API는 401, 비로그인 화면은 로그인으로 이동, USER는 403이다. JWT 필터, OAuth2, Refresh Token, CSRF 정책은 바꾸지 않았다.
- 재사용: `RestaurantService`(관리자 수정·삭제·수정 화면 진입은 일반 흐름 본문을 공유하고, 작성자 검사 인자만 다르다), `RestaurantImageService`(관리자 업로드·삭제, 같은 형식·크기·5장 제한·잠금·보상 삭제), `ReviewService`(관리자 삭제, 같은 집계 갱신과 요약 캐시 삭제), `StorageCleanup`(재시도 행마다 `deleteAfterCommit`), `RestaurantSearch`(목록 조건, LIKE 이스케이프 헬퍼를 공개), `RestaurantService.parseSort`(정렬 규칙 공개). 일반 회원 메서드의 작성자 검사는 그대로 남는다.
- 신규 클래스: `global/security/AdminAccess`, `global/config/ApiAccessDeniedHandler`, `admin/dto/AdminDtos`, `admin/service/AdminQueryService`, `admin/service/AdminStorageRetryService`, `admin/web/AdminApiController`, `admin/web/AdminRestaurantApiController`, `admin/web/AdminScreenController`.
- 신규 쿼리: 맛집 목록(`findAll(spec)`에 작성자·카테고리 엔티티 그래프, 일반 목록에도 같은 그래프가 적용되지만 결과는 같다), 맛집·리뷰 회원별 개수 집계 두 개, 이미지 개수 집계, 관리자 리뷰 목록(맛집·회원 필터, 최신순, 작성자와 맛집을 함께 읽음), 관리자 회원 검색, 재시도 대기 페이징.
- 화면: `/admin`(대시보드), `/admin/restaurants`(검색·카테고리·정렬·페이지, 수정·삭제 링크), `/admin/restaurants/{id}/edit`(정보 수정, 사진 올리기·지우기), `/admin/reviews`(맛집·회원 필터, 삭제), `/admin/members`(검색, 조회 전용), `/admin/members/{id}`(조회 전용, 비밀번호 해시와 Google 식별값 없음), `/admin/storage-deletions`(대기 목록, 수동 재시도 결과 표시). 공통 관리자 메뉴(`fragments/admin-nav`)를 둔다. `app.js`에 `data-once` 폼 처리를 추가해 재시도 중복 제출을 막는다.
- 명세와 구현 차이: (1) 최근 7일은 서비스 시간대 기준 오늘 포함 7일로 정했다. (2) S3 재시도는 한 프로세스에서 동시에 한 번만 돌고, 동시 요청은 409 `STORAGE_RETRY_RUNNING`이다(API_SPEC 표에 추가). (3) 관리자 맛집 수정 화면은 실패하면 입력값을 되살리지 않고 오류 문구만 보여 준다. (4) 역할 변경은 다음 로그인 또는 토큰 재발급부터 반영된다. 이미 발급된 세션과 토큰은 만료 전까지 이전 역할을 가진다. 이 부분은 실제 운영 전에 확인이 필요하다.
- 테스트 (신규 48개): `AdminAccessTest` 4(권한 판정), `AdminStorageRetryServiceTest` 5(여러 배치, 한 건 실패가 나머지를 막지 않음, 예상 밖 예외 기록, 회원 거부, 동시 실행 409), `AdminHttpTest` 18(화면 로그인 이동·USER 403·ADMIN 정상, API 401·USER 403 JSON·ADMIN 정상, role 파라미터로 권한 상승 없음, 작성자 필드와 알 수 없는 필드 400, 회원 역할 변경 경로 없음, CSRF 403, 민감 정보 미노출, 회원 조회 화면에 역할 변경 버튼 없음), `AdminServiceDbTest` 21(MySQL: 목록 검색·카테고리·정렬·페이지, 작성자와 이미지 수, 쿼리 수 일정, 관리자 수정의 작성자 유지, 타인 맛집 삭제와 정리, S3 실패 추적, 일반 회원 작성자 검사, 관리자 리뷰 삭제 집계와 요약 캐시 삭제, 리뷰 목록 필터, 회원 집계와 LIKE 특수문자, 회원 상세와 404, 대시보드 7일 경계, S3 재시도 행 처리와 기록, 관리자 이미지 업로드·삭제, 사진 5장 제한, 일반 회원 업로드 거부).
- 실행 결과 (이 브랜치, 이 세션에서 실제 실행):
  - 테스트 DB 설정 (로컬 MySQL 8.0.46, SQL 001~008 적용, 테스트 전용 계정) `./gradlew clean test`: 913개 실행, 건너뜀 0개, 실패 0개, 오류 0개.
  - DB 미설정 `./gradlew clean test`: 913개 실행, 건너뜀 191개(모두 DB 테스트), 실패 0개, 오류 0개.
  - `./gradlew bootJar`와 `git diff --check`: 본 PR 보고 참조.
- 미검증: 실제 AWS S3 삭제와 재시도, 실제 브라우저 화면(관리자 메뉴와 확인 대화상자), 운영 DB에서 초기 관리자 지정 SQL 실행, 여러 서버 인스턴스 사이의 재시도 동시 실행, 역할 변경의 실제 반영 시점(재로그인 후 화면 확인).


### 6.42 Stage 11 배포 기반 구축 (2026-11-27)

- 브랜치 `feat/stage11-deployment-foundation`, 기준 `main` `4a2a11c` (PR #48 병합 후). 배포 파일과 문서만 추가·수정했다. 애플리케이션 코드, 테스트, API, 보안 설정, DB 구조와 SQL 001~008은 바꾸지 않았다.
- 추가한 파일: `docs/DEPLOYMENT.md`(운영 가이드), `deploy/`(환경변수 예시, systemd 유닛 2개와 타이머, Nginx 설정, MySQL 설정·계정 예시, S3 수명 규칙, 스크립트 5개: `setup-ec2.sh`, `mount-data-volume.sh`, `deploy.sh`, `rollback.sh`, `backup-mysql.sh`), `.github/workflows/deploy.yml`(저장소의 첫 워크플로).
- T4(DB 백업)와 T5(MySQL·영속 저장소) 정책을 결정하고 절차를 작성했다. 백업은 `mysqldump --single-transaction` 후 gzip, 백업 전용 S3 버킷에 SSE-S3로 올리고 크기를 확인한다. 업로드 실패 시 로컬 파일을 지우지 않고 종료 코드 1로 끝낸다.
- 배포는 GitHub OIDC로 임시 자격 증명을 받아 SSM Run Command로 `deploy.sh`를 호출한다. 헬스 체크(GET /) 실패 시 직전 JAR로 자동 롤백한다.
- 발견한 충돌: (1) 이미지 URL이 공개 읽기를 전제한다. 비공개 원칙과 충돌하므로 T21로 기록하고 코드는 바꾸지 않았다. (2) 운영 프로필의 Secure 쿠키 때문에 도메인·HTTPS 전에는 화면 로그인이 동작하지 않는다. (3) 운영은 Google OAuth 값을 필수로 요구한다. 도메인 전에는 Google 로그인이 동작하지 않는다. (4) 배포 JAR 전달용 버킷이 추가로 필요하다 (T22).
- 검증: `./gradlew clean test bootJar`(DB 미설정) 성공, 셸 스크립트 `bash -n` 통과, systemd 유닛 `systemd-analyze verify` 통과(백업 스크립트 미설치 경고 외), 워크플로 YAML 파싱 통과, S3 수명 규칙 JSON 파싱 통과.
- 미검증: 실제 EC2·EBS·MySQL 설치, AppArmor 적용, S3 업로드·이미지 표시, IAM·OIDC·SSM 배포, 백업 실행과 복구, 브라우저 접속, Google 운영 콜백, HTTPS. 14절 참고.

## 7. 다음 작업

1. 완료 (`main` 병합: PR #17 `9f881a0cca892b28f342dec9fc399814b40001a8`, PR #19 `dd147e3f595825047d017ddcbb20df95f55ec40f`, PR #23 `56faff2566743c89911d4d87407fff5ac6c1732e`, PR #25 `d1b9e1d`):
   - 인증 요청 제한 R1~R10 서비스 구현 (PR #16, `50963bc`, 6.14)
   - 회원가입 요청 제한 HTTP 연결과 공통 429·`Retry-After`, fail-closed (PR #17, 6.15)
   - API JWT 인증 필터와 API·화면 SecurityFilterChain 분리, Bearer·쿠키 인증, API CSRF 정책 (PR #19, 6.16)
   - API 인증 HTTP 5종과 JWT 쿠키 발급·갱신·만료, 로그인·재발급 요청 제한 HTTP 연결 (PR #23, 6.18)
   - 화면 회원가입·로그인·로그아웃과 화면 세션, 세션 고정 방어 (PR #25, 6.19). T12 서버 측 로그아웃 승인과 `validation_failed` 명세 (PR #26 보완, 6.20; PR #25 브랜치에 병합되어 `main`에 반영)
2. `main` 병합 완료 (PR #28, merge commit `37df8e024ffea4065c6c1213265010527970dc48`, 6.21):
   - Google 로그인 시작 `GET /oauth2/authorization/google`과 콜백 `GET /login/oauth2/code/google`, state 검증은 Spring 기본 방식
   - Google 로그인 요청 제한: 시작과 콜백 각 1회, IP별 15분 10회, 초과 시 429와 `Retry-After`
   - 콜백 판정: 미검증 이메일 거부, sub 연결 회원 로그인, 같은 이메일 자동 연결 금지(`email_account_exists`), 신규 가입 진행
   - Google 신규 가입 닉네임 설정 `GET·POST /signup/google/nickname`, 닉네임 제출 요청 제한(회원가입 카운터 공유), 회원 생성과 토큰 발급의 한 트랜잭션 처리
   - PR #30 (`feat/stage2-4-google-account-linking`, merge commit `05778895c03da90ad56570e5ddae3485acc97ba9`, 6.22): Google 계정 연결 시작·콜백, 연결 해제, 로컬 비밀번호 설정, `/mypage`, 화면 429 안내 `/rate-limited`(POST forward 보완 포함), Google 연결 요청 제한 공유
   - PR #32 (`feat/stage2-4-access-token-auto-refresh`, merge commit `31525ba`, 6.24): T13 프런트엔드 Access Token 자동 갱신, 같은 탭 single-flight, Web Locks 여러 탭 조정, 재발급 1회
   - PR #33 (`feat/stage2-4-rate-limit-frontend`, merge commit `d861d808f930d42519bb717ef978804460c7f24e`, 6.25): T20 프런트엔드 429 처리. 429와 401 구분, `Retry-After` 기준 재시도 시점 안내, 자동 재요청 없음, 쿠키와 화면 세션 유지
   - PR #35 (`feat/stage2-4-protected-restaurant-screens`, merge commit `57a1bcef6c33779a351f053a0bce9d19c67aa00c`, 6.26): `/restaurants/new`, `/restaurants/*/edit` 화면 세션 인증 보호. JWT 쿠키 단독 인증 불가, CSRF 유지
3. F1-02 맛집 관리: `main` 병합 완료 (PR #37, merge commit `1f885dd`, 6.27·6.28·6.29). 구현 범위는 카테고리 시드와 조회, 맛집 등록·수정·삭제(작성자 본인만), 목록·검색·정렬·페이지, 상세, 위도·경도 쌍 검증, 페이지 이동 시 검색 조건 유지, API JWT 인증과 화면 세션 인증 구분이다. 브라우저 화면 검증은 하지 않았다. 자동화 테스트는 링크 HTML과 폼 필드만 확인한다.
   - F1-03 맛집 이미지: `main` 병합 완료 (PR #39, 6.30·6.31). 배포 단계에서 실제 AWS 버킷 업로드·삭제, EC2 IAM 권한, 버킷 공개 정책을 확인해야 한다. 삭제 실패 객체의 자동 재시도는 없다.
   - F1-04 리뷰: `main` 병합 완료 (PR #41, 6.32·6.33). 브라우저 화면과 EC2 MySQL 설정에서의 동작은 아직 확인하지 않았다.
   - F1-05 AI 리뷰 요약: `main` 병합 완료 (PR #43, merge commit `39532fa`, 6.34·6.35). 실제 OpenRouter 호출과 운영 키·모델 설정은 아직 검증하지 않았다.
   - F1-06 찜: `main` 병합 완료 (PR #45, merge commit `c9cb668`, 6.37·6.38).
   - F1-07 AI 맛집 추천: `main` 병합 완료 (PR #46, merge commit `9206026`, 6.39). 실제 OpenRouter 호출은 아직 검증하지 않았다.
   - F1-08 메인 홈페이지: `main` 병합 완료 (PR #47, merge commit `7b20233`, 6.40). 실제 브라우저 화면과 운영 데이터는 아직 검증하지 않았다.
   - F1-09 관리자 기능: `main` 병합 완료 (PR #48, merge commit `4a2a11c`, 6.41). 실제 AWS S3 재시도와 브라우저 화면은 아직 검증하지 않았다.
   - Stage 11 배포 기반: PR 병합 대기 (6.42, `docs/DEPLOYMENT.md`). 실제 AWS 리소스 생성, 배포, 백업·복구는 사용자 설정 뒤에 검증한다.
   - **다음 개발 단계: Stage 11 배포 기반 PR 병합 → 사용자가 AWS 설정(EC2·EBS·MySQL·S3·IAM·OIDC·Variables) → 실제 배포와 백업·복구 검증.** 회원 탈퇴(T1)는 A안으로 결정되어 이번 F1-09 범위에서 제외했다.
   - F1-02 구현 차이는 API_SPEC 4절과 FUNCTION_SPEC F1-02의 구현 상태 메모에 있다 (`thumbnailUrl`, `storageDeleted`, 이미지·찜 자리표시, 오류 `details`).
4. 확인 필요: 비밀번호 확인값 누락 처리, 닉네임 대소문자 구분 정책, 이메일 형식의 Unicode 공백 처리.
   - Google 액세스 토큰의 HttpSession 저장 정책 검토 (6.21 위험 항목)
5. 미확정 항목(T6, T10, T21, T22)은 각 단계 시작 전에 결정합니다. T21은 이미지 공개 방식, T22는 배포 아티팩트 버킷이다.
6. 실제 MySQL 스키마 적용은 사용자가 `db/manual/`의 SQL 파일을 직접 실행합니다. 이번 작업에서는 실행하지 않았습니다.
