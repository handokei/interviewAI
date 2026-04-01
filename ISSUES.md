# 코드 이슈 트래킹

진행 순서: 각 섹션 구현 완료 → `./gradlew test` → `./gradlew build` → PR → 다음 섹션

---

## 진행 현황

- [x] 섹션 1: 하드코딩 제거
- [ ] 섹션 2: 타입 안전성
- [ ] 섹션 3: 프론트엔드-백엔드 타입 불일치
- [ ] 섹션 4: 트랜잭션 경계 분리
- [ ] 섹션 5: 확장성·재사용성

---

## 섹션 1: 하드코딩 제거

매직 넘버/문자열이 코드에 직접 박혀 있어 환경별 설정 변경이 어렵고, 값의 의미를 코드만으로 파악하기 어렵다.

| # | 파일 | 하드코딩 값 | 이동할 위치 |
|---|------|------------|------------|
| 1 | `InterviewServiceImpl.java` | `1500` (문서/채용공고 최대 글자 수) | `application.yml` + `@ConfigurationProperties` |
| 2 | `GithubApiClient.java` | `"https://api.github.com"` | `application.yml` |
| 3 | `GithubApiClient.java` | `10` (최대 레포 수), `500` (README 최대 글자) | `application.yml` |
| 4 | `JobCrawlerClient.java` | `10_000` (타임아웃 ms), `3000` (최대 텍스트 길이) | `application.yml` |
| 5 | `JobCrawlerClient.java` | `"Mozilla/5.0 (compatible; InterviewAI/1.0)"` (User-Agent) | `application.yml` |
| 6 | `DocumentServiceImpl.java` | `"application/pdf"` | `MediaType.APPLICATION_PDF_VALUE` |
| 7 | `SecurityConfig.java` | `"http://localhost:5173"`, `"http://localhost:3000"`, `"https://*.cloudfront.net"` | `application.yml` `cors.allowed-origins` |
| 8 | `JwtAuthenticationFilter.java` | `"ROLE_USER"` | `UserRole` enum 활용 |

**개선 방향:** `@ConfigurationProperties`로 그룹핑하여 타입 안전하게 관리

```yaml
# application.yml 추가 예시
interview:
  prompt:
    max-document-length: 1500
    max-job-posting-length: 1500

github:
  api:
    base-url: https://api.github.com
    max-repos: 10
    max-readme-length: 500

job-crawler:
  timeout-ms: 10000
  max-text-length: 3000
  user-agent: "Mozilla/5.0 (compatible; InterviewAI/1.0)"

cors:
  allowed-origins:
    - http://localhost:5173
    - http://localhost:3000
    - https://*.cloudfront.net
```

---

## 섹션 2: 타입 안전성

코드 내 문자열 비교와 Jackson 키 이름 직접 사용으로 컴파일 타임 오류 탐지가 불가하다.

| # | 파일 | 문제 | 개선 방향 |
|---|------|------|----------|
| 1 | `ClaudeAiClient.java` | `ChatMessage(String role, ...)` — `"user".equals(msg.role())`로 문자열 비교 | `MessageRole` enum 타입으로 교체 |
| 2 | `InterviewServiceImpl.java` | `parseEvaluation` — `"overallLevel"`, `"suggestFinish"` 등 JSON 키를 문자열로 직접 파싱 | Jackson `@JsonProperty` 또는 record 매핑 활용 |
| 3 | `InterviewServiceImpl.java` | `parseFeedbackAndSave` — `"strengths"`, `"improvements"`, `"fullReport"` 키 문자열 하드코딩 | Jackson `@JsonProperty` 또는 record 매핑 활용 |

**개선 방향:**

```java
// Before
public record ChatMessage(String role, String content) {}
if ("user".equals(msg.role())) { ... }

// After
public record ChatMessage(MessageRole role, String content) {}
if (msg.role() == MessageRole.USER) { ... }
```

```java
// JSON 파싱 — Before (수동 substring + ObjectMapper)
int jsonStart = evalJson.indexOf('{');
String jsonStr = evalJson.substring(jsonStart, jsonEnd + 1);
JsonNode node = objectMapper.readTree(jsonStr);
String level = node.path("answerLevel").asText();

// After — record + ObjectMapper 직접 매핑
private record EvaluationResponse(
    @JsonProperty("suggestFinish") boolean suggestFinish,
    @JsonProperty("answerLevel") AnswerLevel answerLevel,
    @JsonProperty("qualityHint") String qualityHint
) {}
```

---

## 섹션 3: 프론트엔드-백엔드 타입 불일치

백엔드 리팩토링(`overallScore` → `overallLevel`) 이후 프론트엔드 타입이 미반영된 상태다.

| # | 파일 | 백엔드 | 프론트엔드 (현재) | 수정 후 |
|---|------|--------|-----------------|--------|
| 1 | `types/interview.ts` | `overallLevel: AnswerLevel` | `overallScore: number` | `overallLevel: AnswerLevel` |
| 2 | `types/interview.ts` | `InterviewMode: GENERAL` | `InterviewMode: BASIC` | `GENERAL` 로 통일 |
| 3 | `interview.test.ts` | — | `overallScore: 85` (mock 데이터) | `overallLevel: 'PASS'` 로 수정 |

**영향 범위:** 피드백 페이지에서 `overallScore`를 숫자로 렌더링하고 있다면 런타임 오류 발생 가능.

---

## 섹션 4: 트랜잭션 경계 분리

DB 커넥션을 유지한 채로 외부 API(AI, GitHub, 크롤러)를 호출하여 커넥션 풀 낭비 및 AI 실패 시 의도치 않은 롤백이 발생한다.

| # | 메서드 | 문제 |
|---|--------|------|
| 1 | `startInterview` | 단일 `@Transactional` 안에서 GitHub API(~3s) + 크롤링(~10s) + Claude AI(~5s) 호출 → AI 실패 시 세션 자체가 저장 안 됨 |
| 2 | `sendMessage` | 유저 메시지 저장 + AI 호출 + 평가 저장이 동일 트랜잭션 → AI 타임아웃 시 유저 메시지도 롤백 |
| 3 | `finishInterview` | 세션 `COMPLETED` 상태 변경 + AI 피드백 생성 + 저장이 단일 트랜잭션 → AI 실패 시 상태도 롤백 |
| 4 | `InterviewMessageSaver` | `REQUIRES_NEW`로 분리되어 있으나 SSE emitter 이벤트 전송이 트랜잭션 내부에서 실행 |

**개선 방향: Facade 패턴으로 3단계 분리**

```
현재:
@Transactional  ← DB 커넥션 유지한 채로
  1. DB 읽기 (User, Document)
  2. 외부 API (GitHub, 크롤러, Claude) ← 최대 18초 커넥션 점유
  3. DB 쓰기 (Session, Message)

목표:
Phase 1: @Transactional(readOnly=true)  → DB 읽기만
Phase 2: 트랜잭션 없음                  → 외부 API 호출
Phase 3: @Transactional                 → DB 쓰기만

구조:
InterviewOrchestrationFacade  (트랜잭션 없음, 흐름 조율)
  ├── InterviewQueryService   (@Transactional readOnly)
  ├── ExternalCallService     (트랜잭션 없음)
  └── InterviewCommandService (@Transactional)
```

---

## 섹션 5: 확장성·재사용성

단일 클래스에 여러 책임이 집중되어 있고, 외부 클라이언트에 인터페이스가 없어 테스트 및 확장이 어렵다.

| # | 위치 | 문제 | 개선 방향 |
|---|------|------|----------|
| 1 | `InterviewServiceImpl` (644줄) | AI 호출·프롬프트 빌딩·JSON 파싱·평가 로직 혼재 (OCP 위반) | 섹션 4의 Facade 패턴 분리로 해결 |
| 2 | `ClaudeAiClient` | 인터페이스 없음, Spring AI OpenAI 구현에 직접 의존 | `AiChatClient` 인터페이스 추출 |
| 3 | `GithubApiClient` | 인터페이스 없음, `RestTemplate` 직접 사용 | `PortfolioClient` 인터페이스 추출 |
| 4 | `JobCrawlerClient` | 인터페이스 없음, `Jsoup` 직접 사용 | `JobPostingClient` 인터페이스 추출 |
| 5 | `InterviewMessageSaver` | 메시지 저장 + SSE 이벤트 전송 혼재 | 저장 책임만 담당하도록 분리 |
| 6 | `buildSystemPrompt` (private) | 면접 유형별 프롬프트 전략이 if-else로만 분기 가능 | `InterviewPromptStrategy` 인터페이스 + 유형별 구현체 |

**개선 방향:**

```java
// AI 클라이언트 인터페이스 추출
public interface AiChatClient {
    String chat(String systemPrompt, List<ChatMessage> history, String userMessage);
    Flux<String> streamChat(String systemPrompt, List<ChatMessage> history, String userMessage);
    String generateFeedback(String systemPrompt, String conversationText);
}

// 외부 클라이언트 인터페이스 추출
public interface PortfolioClient {
    Optional<String> fetchPortfolioInfo(String url);
}

public interface JobPostingClient {
    Optional<String> fetchJobPosting(String url);
}

// 프롬프트 전략 (면접 유형별)
public interface InterviewPromptStrategy {
    String buildSystemPrompt(InterviewSession session, PromptContext ctx);
}
```
