# 🎙️ InterviewAI — AI 기반 실시간 모의 면접 서비스

> **"내 이력서와 지원 공고를 분석해 AI가 면접관이 되어주는 서비스"**

[![CI](https://github.com/handokei/interviewAI/actions/workflows/sonarcloud.yml/badge.svg)](https://github.com/handokei/interviewAI/actions/workflows/sonarcloud.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=handokei_interviewAI&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=handokei_interviewAI)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=handokei_interviewAI&metric=coverage)](https://sonarcloud.io/summary/new_code?id=handokei_interviewAI)
![Java](https://img.shields.io/badge/Java-17-007396?logo=java)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.0-6DB33F?logo=springboot)

---

# 🖥️ 프로젝트 소개

면접 준비를 혼자 할 때 가장 어려운 점은 **실전처럼 연습할 환경이 없다**는 것입니다.
모의 면접을 도와줄 사람을 구하기도 어렵고, 일반적인 예상 질문 목록은 내 이력서와 지원 직무에 맞지 않습니다.

InterviewAI는 이 문제를 해결합니다.

- 내가 올린 **이력서/포트폴리오 PDF**를 분석해 맞춤 질문을 생성합니다.
- **GitHub 프로필**을 자동으로 읽어 프로젝트 기반 기술 질문을 만듭니다.
- **채용공고 URL**을 입력하면 해당 직무에 특화된 질문이 나옵니다.
- 답변할 때마다 **실시간으로 평가**받고, 면접 종료 후 **종합 피드백**을 받습니다.

## 📢 _**면접관 없이, 내 상황에 맞는 모의 면접을**_

---

# 📌 주요 기능

- **면접 모드 3가지**
  - `BASIC` — 직무 기반 CS/기술 질문
  - `RESUME` — 이력서·포트폴리오 기반 맞춤 질문
  - `COMPANY` — 채용공고 URL 입력 → 직무 맞춤 질문

- **AI 실시간 스트리밍** — SSE로 AI 응답을 타이핑되듯 실시간 전달

- **답변 평가** — 매 답변마다 `PASS / NEEDS_IMPROVEMENT / STUDY_REQUIRED` 평가

- **종합 피드백** — 면접 종료 후 강점 3가지 / 개선점 3가지 / 총평 생성

- **문서 관리** — PDF 업로드 → 텍스트 추출 → AI 프롬프트에 자동 반영

- **GitHub 연동** — 공개 레포지토리 + README 자동 분석

- **채용공고 크롤링** — URL 입력 시 공고 내용 추출 → 직무 맞춤 질문

- **인증** — GitHub / Google OAuth2 로그인 + JWT (AccessToken / RefreshToken)

- **면접 통계** — 답변 수준 분포, 총 면접 횟수 조회

---

# 📈 기술적 고도화

- Spring AI + Gemini 2.5-Flash 연동으로 **실용적인 AI 기능** 구현
- **SSE 스트리밍**으로 AI 응답 실시간 전달 (TTFT 대폭 단축)
- **OAuth2 소셜 로그인** + JWT Stateless 인증으로 보안 설계
- **PDF 파싱** (원본 미저장, 텍스트만 DB 저장) — 개인정보 최소화
- **GitHub Actions + SonarCloud** — CI/CD 및 코드 품질 자동화
- **JaCoCo 커버리지 100%** — 비즈니스 로직 전 계층 테스트

---

# 📦 개발 환경

| 분류 | 상세 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.5.0 |
| AI | Spring AI + Gemini 2.5-Flash (OpenAI 호환 인터페이스) |
| Auth | Spring Security, OAuth2 (GitHub/Google), JWT (jjwt 0.12.6) |
| DB | PostgreSQL, H2 (테스트) |
| Streaming | SSE (Server-Sent Events) |
| PDF | Apache PDFBox 3.0.2 |
| Crawling | Jsoup 1.17.2 |
| Docs | SpringDoc OpenAPI 2.5.0 (Swagger UI) |
| Build | Gradle, JaCoCo |
| CI/CD | GitHub Actions, SonarCloud |

---

# 🗺️ 시스템 아키텍처

```
[React Client]
     │
     │ OAuth2 로그인 → JWT 발급
     │ Authorization: Bearer {token}
     ▼
[Spring Security — JwtAuthenticationFilter]
     │
     ▼
[Controller Layer]
     │
     ▼
[Service Layer]
     ├── ClaudeAiClient     → Gemini 2.5-Flash (Spring AI)
     ├── GithubApiClient    → GitHub REST API
     ├── JobCrawlerClient   → Jsoup 웹 크롤링
     └── PdfParserClient    → PDFBox 텍스트 추출
     │
     ▼
[Repository Layer — Spring Data JPA]
     │
     ▼
[PostgreSQL]
```

---

# 🔄 면접 플로우

```
① POST /api/interviews
     mode: RESUME | BASIC | COMPANY
     level: JUNIOR | SENIOR
     jobTitle, documentIds, githubUrl, jobPostingUrl
          │
          └─► AI: 이력서 + GitHub + 채용공고 분석 → 첫 질문 생성

② POST /api/interviews/{id}/messages/stream  (반복)
     content: "답변 내용"
          │
          └─► SSE: AI 응답 실시간 스트리밍
              answerLevel: PASS / NEEDS_IMPROVEMENT / STUDY_REQUIRED

③ POST /api/interviews/{id}/finish
          │
          └─► AI: 전체 대화 분석 → 최종 피드백 JSON 파싱
              overallLevel + strengths(3) + improvements(3) + fullReport
```

---

# 💡 기술적 의사결정

## #1 SSE vs WebSocket — AI 응답 스트리밍 방식

| 항목 | SSE | WebSocket |
|------|-----|-----------|
| 통신 방향 | 단방향 (서버 → 클라이언트) | 양방향 |
| 프로토콜 | HTTP | TCP 업그레이드 |
| Spring 지원 | `SseEmitter` 내장 | 별도 설정 필요 |
| 적합 용도 | AI 응답 스트리밍, 알림 | 채팅, 게임, 협업 |

**→ 선택: SSE**

AI 응답은 서버에서 클라이언트로의 단방향 전송이므로 WebSocket의 양방향 기능이 불필요합니다.
`SseEmitter`를 활용해 기존 HTTP 인프라를 그대로 사용하고 구현 복잡도를 낮췄습니다.

---

## #2 Spring AI + Gemini (OpenAI 호환) vs 직접 HTTP 호출

| 항목 | Spring AI | 직접 HTTP 호출 |
|------|-----------|--------------|
| 모델 교체 | `application.yml` 설정만 변경 | 코드 수정 필요 |
| 추상화 | `ChatClient` 프롬프트 빌더 패턴 | 직접 JSON 직렬화 |
| 의존성 | Spring AI BOM 관리 | 수동 관리 |

**→ 선택: Spring AI**

Spring AI의 OpenAI 호환 인터페이스를 사용하면 `base-url`과 `model`만 변경해 다른 AI 모델로 교체가 가능합니다.
실제로 초기 Claude 연동에서 Gemini로 전환 시 `application.yml` 설정만 바꿨습니다.

---

## #3 JWT Stateless vs 세션 기반 인증

| 항목 | JWT Stateless | 세션 |
|------|--------------|------|
| 서버 상태 | 없음 | 세션 스토어 필요 |
| 수평 확장 | 자유로움 | 세션 공유 필요 (Redis 등) |
| SPA 적합성 | ✅ 리다이렉트 후 토큰 전달 | 쿠키 의존 |
| CSRF | 불필요 (헤더 기반) | 필요 |

**→ 선택: JWT Stateless**

OAuth2 성공 핸들러에서 AccessToken + RefreshToken을 발급해 프론트엔드로 리다이렉트하는 SPA 구조에 최적합니다.
`SessionCreationPolicy.STATELESS`로 서버 상태 없이 운영하고, CSRF 보호 비활성화로 보안 설계를 단순화했습니다.

---

## #4 PDF 직접 파싱 (PDFBox) vs S3 원본 저장

| 항목 | PDFBox 파싱만 저장 | S3 원본 저장 |
|------|-----------------|------------|
| 인프라 | 없음 | S3 버킷 필요 |
| 개인정보 | 텍스트만 저장 | 원본 파일 보관 |
| AI 활용 | 텍스트를 바로 프롬프트에 삽입 | 별도 파싱 과정 필요 |

**→ 선택: PDFBox 텍스트만 저장**

AI 프롬프트에 필요한 것은 원본 파일이 아닌 텍스트 내용뿐입니다.
S3 없이 원본을 저장하지 않으므로 이력서 원본이 서버에 보관되지 않아 개인정보 부담이 줄어듭니다.

**인식한 한계**
`PDFTextStripper`는 **텍스트 레이어만 추출**합니다. 이력서에 포함된 이미지(사진, 차트, 스캔 기반 PDF)는 AI에 전달되지 않습니다.
이 한계를 인식하면서도 현재 방식을 선택한 이유:
- AI 면접 질문 생성에 필요한 핵심 정보(경력, 기술 스택, 프로젝트 설명)는 텍스트 레이어에 충분히 포함됨
- OCR 도입 시 처리 속도 저하 + 오인식 노이즈로 프롬프트 품질 오히려 하락 우려
- 향후 멀티모달 AI 전환 시 원본 저장 방식으로 개선 가능

---

## #5 AI 응답/평가 프롬프트 분리 설계

면접 답변에 대해 AI 응답(면접관 역할)과 답변 평가(평가자 역할)를 단일 호출로 처리할 수도 있었지만 분리했습니다.

| 항목 | 단일 응답 (응답 + 평가 혼합) | 응답/평가 분리 (2회 호출) |
|------|--------------------------|----------------------|
| AI 호출 횟수 | 1회 | 2회 |
| 면접관 응답 품질 | 평가 지시가 섞여 자연스러운 대화 저해 | 면접관 역할에만 집중 |
| 평가 JSON 안정성 | 대화 + JSON 혼합으로 파싱 복잡 | 평가 전용 프롬프트로 JSON만 반환 |

**→ 선택: 분리**

면접관 역할(자연스러운 대화 이어가기)과 평가자 역할(구조화된 JSON 데이터 반환)을 분리해
각각의 품질을 독립적으로 보장했습니다.
AI 호출이 1회 더 발생하지만, 면접 대화의 자연스러움과 평가 파싱 안정성이 훨씬 중요하다고 판단했습니다.

---

## #6 SSE 스트리밍과 ExecutorService 설계 — 동시성 고려

SSE 스트리밍은 HTTP 스레드를 즉시 반환하고 다른 스레드에서 비동기로 데이터를 푸시해야 합니다.

| 방식 | 설명 | 트레이드오프 |
|------|------|------------|
| 요청당 `newSingleThreadExecutor()` | 구현 단순 | 요청마다 스레드 생성/소멸 오버헤드 |
| Bean으로 관리하는 고정 스레드풀 | 재사용, 동시 스트림 수 제한 | 설정 복잡도 증가 |
| Reactor WebFlux 전환 | 논블로킹 완성 | 전체 스택 변경 필요 |

**→ 현재: 요청당 SingleThreadExecutor** (기능 완성 우선)

각 스트리밍 요청은 독립적인 단일 스레드에서 처리되므로 현재 구조에서 `StringBuilder` 데이터 경쟁은 발생하지 않습니다.
다만 동시 사용자 증가 시 스레드 생성 오버헤드가 누적될 수 있어, Bean으로 관리하는 고정 스레드풀로의 전환을 개선 계획에 포함했습니다.

---

## #7 @ConfigurationProperties vs @Value

| 항목 | @ConfigurationProperties | @Value |
|------|--------------------------|--------|
| 타입 안전성 | ✅ 클래스로 묶음 | 개별 주입 |
| 테스트 | 독립적으로 주입 가능 | SpEL 표현식 |
| 그룹화 | 관련 설정 클래스 단위 관리 | 흩어짐 |

**→ 선택: @ConfigurationProperties**

`InterviewProperties`, `GithubApiProperties`, `JobCrawlerProperties` 등 관련 설정을 클래스로 묶어
타입 안전성을 확보하고 테스트 시 독립적으로 주입할 수 있게 했습니다.
하드코딩 제거로 환경별 설정 분리도 용이합니다.

---

# 📊 성능 비교

## #1 SSE 스트리밍 vs 동기 응답 — 사용자 체감 응답 시간

AI 응답을 전체 생성 후 한 번에 반환하는 방식(동기)과
생성되는 즉시 클라이언트에 전달하는 방식(SSE 스트리밍)을 비교합니다.

**측정 조건**: Gemini 2.5-Flash, 동일 프롬프트 10회 평균, 로컬 환경

| 방식 | TTFT (첫 글자까지) | 전체 완료 | 사용자 체감 |
|------|-----------------|----------|------------|
| 동기 응답 | 전체 완료와 동일 | \_\_\_ms | 응답 전체가 한 번에 등장 |
| SSE 스트리밍 | \_\_\_ms | \_\_\_ms | 실시간으로 타이핑되듯 출력 |

> 전체 응답 시간은 동일하지만 **TTFT가 크게 단축**되어 면접 흐름이 끊기지 않는 체감 성능을 확보했습니다.

*(배포 후 실측값으로 업데이트 예정)*

---

## #2 N+1 쿼리 개선 — 문서 첨부 조회

면접 메시지 전송 시 첨부된 문서 목록을 프롬프트 컨텍스트로 구성하는 과정에서 N+1 쿼리가 발생했습니다.

**문제 코드**
```java
interviewSessionDocumentRepository.findBySessionId(sessionId)
    .stream()
    .map(InterviewSessionDocument::getDocument)  // ← 각 엔티티마다 SELECT 1회 추가
    .toList();
```

문서 3개 첨부 시 실행되는 SQL:
```sql
SELECT * FROM interview_session_documents WHERE session_id = ?   -- 1회
SELECT * FROM user_documents WHERE id = ?                        -- 3회 (문서 수만큼)
```

**개선 — JOIN FETCH**
```java
@Query("SELECT isd FROM InterviewSessionDocument isd JOIN FETCH isd.document WHERE isd.session.id = :sessionId")
List<InterviewSessionDocument> findBySessionIdWithDocuments(@Param("sessionId") Long sessionId);
```

| 문서 첨부 수 | 개선 전 쿼리 수 | 개선 후 쿼리 수 |
|-------------|--------------|--------------|
| 1개 | 2회 | 1회 |
| 3개 | 4회 | 1회 |
| 5개 | 6회 | 1회 |

---

# 🐞 트러블슈팅

## #1 LLM의 불안정한 JSON 응답으로 면접 평가 실패

**문제 인식**
면접 답변 평가 시 `answerLevel`이 항상 기본값(`NEEDS_IMPROVEMENT`)으로 표시되거나,
간헐적으로 면접이 비정상 종료되는 현상 발생. 로그에 `평가 JSON 파싱 실패` 경고 확인.

**원인 확인**
AI에게 JSON 형식만 반환하도록 지시했지만, LLM은 아래처럼 앞뒤로 자연어 텍스트를 붙이기도 합니다.
```
물론이죠! 다음은 평가 결과입니다:
{"suggestFinish": false, "answerLevel": "PASS", "qualityHint": "..."}
```
`ObjectMapper.readValue()`에 전체 문자열을 넣으면 `{` 앞의 텍스트 때문에 파싱 실패.

**해결 방안**
응답 문자열에서 첫 번째 `{`부터 마지막 `}`까지를 먼저 추출한 후 파싱.
각 필드는 `has()` 체크 후 기본값 제공, enum 변환 실패도 별도 `catch`로 fallback 처리.
```java
int start = response.indexOf('{');
int end   = response.lastIndexOf('}');
if (start >= 0 && end >= 0) {
    String json = response.substring(start, end + 1);
    // 추출된 JSON만 파싱
}
// 전체 실패 시 InterviewEvaluation.fallback() 반환
```

---

## #2 면접 메시지 전송 시 N+1 쿼리 발생

**문제 인식**
`spring.jpa.show-sql: true` 로그를 분석하던 중, 문서 3개를 첨부한 세션에서
메시지 1건 전송 시 `user_documents` 테이블 SELECT가 3번 연속 실행되는 것을 확인.

**원인 확인**
`findBySessionId()`로 `InterviewSessionDocument` 목록을 가져온 후,
`.map(InterviewSessionDocument::getDocument)`에서 각 엔티티의 LAZY 필드에 접근.
JPA가 엔티티마다 개별 SELECT를 발생시켜 문서 N개 → N+1 쿼리.
```
SELECT * FROM interview_session_documents WHERE session_id = ?    -- 1회
SELECT * FROM user_documents WHERE id = ?                         -- N회
```

**해결 방안**
Repository에 `JOIN FETCH` 쿼리를 추가해 연관 엔티티를 단일 쿼리로 로딩.
```java
@Query("SELECT isd FROM InterviewSessionDocument isd JOIN FETCH isd.document WHERE isd.session.id = :sessionId")
List<InterviewSessionDocument> findBySessionIdWithDocuments(@Param("sessionId") Long sessionId);
```
문서 수와 무관하게 항상 1회 쿼리로 해결.

---

## #3 SSE 연결 타임아웃으로 AI 스트리밍 중단

**문제 인식**
짧은 답변은 정상이지만 길고 복잡한 질문에 대한 AI 응답 중 연결이 끊기는 현상.
프론트엔드에서 SSE 스트림이 완료되지 않은 채 오류 이벤트 수신.

**원인 확인**
`new SseEmitter()`의 기본 타임아웃은 30초입니다.
Gemini API는 프롬프트 복잡도(이력서+채용공고+대화 이력)에 따라 응답 생성에 30초 이상 걸릴 수 있어,
생성 완료 전에 연결이 강제 종료됨.

**해결 방안**
타임아웃을 하드코딩하지 않고 `@ConfigurationProperties`로 외부 설정으로 분리.
```yaml
interview:
  sse:
    timeout-ms: 120000  # 2분
```
```java
SseEmitter emitter = new SseEmitter(interviewProperties.getSse().getTimeoutMs());
```
운영 환경에서 별도 조정 가능하며, AI 응답 특성에 맞게 2분으로 설정.

---

# 📋 API 목록

## Auth

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/auth/refresh` | RefreshToken → AccessToken + RefreshToken 재발급 |

## User

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/users/me` | 현재 로그인 사용자 프로필 조회 |

## Document

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/documents` | PDF 문서 업로드 (이력서/포트폴리오/자기소개서) |
| GET | `/api/documents` | 내 문서 목록 조회 (페이지네이션) |
| PATCH | `/api/documents/{id}/type` | 문서 유형 변경 |
| DELETE | `/api/documents/{id}` | 문서 삭제 |

## Interview

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/interviews` | 면접 세션 시작 + 첫 질문 생성 |
| POST | `/api/interviews/{id}/messages` | 답변 전송 (동기 응답) |
| POST | `/api/interviews/{id}/messages/stream` | 답변 전송 (SSE 스트리밍) |
| POST | `/api/interviews/{id}/finish` | 면접 종료 + 최종 피드백 생성 |
| GET | `/api/interviews` | 내 면접 이력 목록 (페이지네이션) |
| GET | `/api/interviews/{id}/messages` | 특정 면접 대화 내용 조회 |
| GET | `/api/interviews/{id}/feedback` | 특정 면접 피드백 조회 |
| GET | `/api/interviews/stats` | 면접 통계 (답변 수준 분포) |
| PATCH | `/api/interviews/{id}/cancel` | 진행 중 면접 취소 |
| DELETE | `/api/interviews/{id}` | 면접 삭제 |
| POST | `/api/interviews/batch-delete` | 면접 다중 삭제 |

> 전체 API 명세: 서버 실행 후 `http://localhost:8080/swagger-ui.html`

---

# 🔧 앞으로의 개선 계획

- **배포** — AWS EC2 또는 Railway를 통한 실서비스 환경 구축
- **스레드풀 Bean 관리** — SSE 스트리밍 시 요청마다 생성하는 ExecutorService를 Bean으로 관리하는 고정 스레드풀로 전환
- **맞춤형 피드백** — 면접 히스토리 기반 반복 취약 영역 개선 추적
- **WebClient 전환** — AI 및 외부 API 호출의 비동기 처리 개선
- **Redis 캐시** — 자주 조회되는 면접 통계 캐싱으로 DB 부하 감소

---

# 🏃 로컬 실행 방법

## 환경변수 설정

루트 경로에 `.env` 파일 또는 환경변수 설정:

```
# Database
DB_URL=jdbc:postgresql://localhost:5432/interviewai

# AI
GEMINI_API_KEY=your_gemini_api_key

# JWT
JWT_SECRET=your_jwt_secret_minimum_256bit

# OAuth2 - GitHub
GITHUB_CLIENT_ID=your_github_client_id
GITHUB_CLIENT_SECRET=your_github_client_secret

# OAuth2 - Google
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret
```

## 실행

```bash
./gradlew bootRun
```

## 테스트

```bash
./gradlew test
```

JaCoCo 리포트: `build/reports/jacoco/test/html/index.html`
