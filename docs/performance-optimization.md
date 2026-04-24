# InterviewAI — 인터뷰 시작 API 성능 최적화

## 개요

`POST /api/interviews` (인터뷰 시작) 요청이 최대 27초까지 블로킹되는 성능 이슈를 단계별로 최적화한다.

## 문제 분석

### 병목 구조

인터뷰 시작 시 3개 외부 시스템을 **동기 + 순차** 호출:

```
startInterview() — 단일 Tomcat 스레드에서 순차 블로킹
├─ jobCrawlerClient.crawl()              최대 10초 (Jsoup)
├─ githubApiClient.extractGithubInfo()   3~7초 (REST API × N)
│   ├─ GET /users/{username}
│   ├─ GET /users/{username}/repos
│   └─ GET /repos/{user}/{repo}/readme × 최대 10개 (순차)
└─ claudeAiClient.chat()                5~10초 (Gemini API 블로킹)
```

### 근본 원인

| 항목 | 상태 |
|------|------|
| @EnableAsync / CompletableFuture | 미사용 |
| Virtual Threads | 미활성화 |
| 외부 호출 병렬화 | 없음 |
| 첫 질문 스트리밍 | 미적용 (chatModel.call 동기) |

---

## 모니터링 인프라 (#47)

성능 전후 비교를 위한 측정 기반 구축.

### 기술 스택

- **Spring Boot Actuator** + **Micrometer** → `/actuator/prometheus` 메트릭 노출
- **AOP Aspect** → client 패키지 메서드 실행 시간 자동 측정 + Timer 메트릭 기록
- **Prometheus** → 5초 간격 메트릭 수집
- **Grafana** → Client Performance 대시보드 (프로비저닝 자동화)

### Grafana 대시보드

<!-- 스크린샷: Grafana Client Performance 대시보드 -->
![Grafana Dashboard](./screenshots/grafana-baseline.png)

---

## Baseline 측정 결과

| Client | Method | 평균 응답시간 | 비고 |
|--------|--------|-------------|------|
| ClaudeAiClient | chat | **5.51s** | 첫 질문 생성 (동기 블로킹) |
| ClaudeAiClient | streamChat | 2.49ms | 후속 대화 (스트리밍) |
| GithubApiClient | extractGithubInfo | **4.89s** | GitHub 정보 수집 (순차 REST) |

> AI 호출 + GitHub 호출만으로 **~10.4초** 소요. 채용공고 크롤링 추가 시 **~20초** 예상.

---

## 최적화 계획

### 1단계: GitHub README 병렬 fetch (#49) ✅

- **이슈**: [#49](https://github.com/handokei/interviewAI/issues/49) / **PR**: [#50](https://github.com/handokei/interviewAI/pull/50)
- **대상**: `GithubApiClient.appendRepositories()` 내 README fetch 순차 루프

#### 변경 전 (순차)

```java
for (Object repoObj : repos) {
    if (!Boolean.TRUE.equals(repo.get("fork"))) {
        appendReadme(result, username, (String) repo.get("name"));  // 블로킹 × N
    }
}
```

- 최대 10개 repo의 README를 **한 번에 하나씩** HTTP 호출
- 각 호출 ~500ms → 총 **~5초 소요**

#### 변경 후 (병렬)

```java
// 1. non-fork repo별 CompletableFuture 생성 (LinkedHashMap으로 순서 보장)
Map<String, CompletableFuture<String>> readmeFutures = new LinkedHashMap<>();
for (...) {
    readmeFutures.put(repoName, CompletableFuture.supplyAsync(
        () -> fetchReadme(username, repoName)
    ));
}

// 2. 모든 fetch 완료 대기 (30초 타임아웃)
CompletableFuture.allOf(readmeFutures.values().toArray(new CompletableFuture[0]))
    .orTimeout(30, TimeUnit.SECONDS)
    .join();

// 3. 결과 수집 (순서 유지)
for (Map.Entry<String, CompletableFuture<String>> entry : readmeFutures.entrySet()) {
    String readme = entry.getValue().join();
    if (readme != null) {
        result.append("  README: ").append(readme).append("\n");
    }
}
```

#### 기술적 의사결정

| 결정 | 이유 |
|------|------|
| `CompletableFuture.supplyAsync()` (ForkJoinPool) | 별도 ExecutorService 관리 불필요, README fetch는 경량 I/O |
| `LinkedHashMap` | repo 순서 보장 (HashMap은 순서 미보장) |
| `orTimeout(30초)` | 개별 README가 hang 걸려도 전체 요청이 영원히 블로킹되지 않도록 |
| `fetchReadme()` → null 반환 | 개별 실패가 다른 fetch에 영향 없음 (graceful degradation) |
| `StringBuilder` 쓰레드 안전 | fetch는 String만 반환, StringBuilder는 메인 스레드에서만 조작 |

#### 측정 결과

| Client.Method | Before | After | 개선 |
|---------------|--------|-------|------|
| GithubApiClient.extractGithubInfo | **4.89s** | **1.57s** | **-68% (-3.32s)** |

![Before](./screenshots/grafana-baseline.png)
![After wt-1](./screenshots/grafana-after-wt1.png)

### 2단계: 외부 호출 병렬화 (#51) ✅

- **이슈**: [#51](https://github.com/handokei/interviewAI/issues/51) / **PR**: [#52](https://github.com/handokei/interviewAI/pull/52)
- **대상**: `InterviewServiceImpl.startInterview()` 내 Job Crawl + GitHub API 순차 호출

#### 변경 전 (순차)

```java
// 1. crawl 완료까지 대기 (최대 10초)
String jobPostingContent = null;
if (request.getMode() == InterviewMode.COMPANY && request.getJobPostingUrl() != null) {
    jobPostingContent = jobCrawlerClient.crawl(request.getJobPostingUrl());
}

// 2. crawl 끝난 후 github 시작 (3~7초)
String githubInfo = null;
if (request.getGithubUrl() != null) {
    githubInfo = githubApiClient.extractGithubInfo(request.getGithubUrl());
}
```

- crawl(최대 10초) **완료 후** github(1.5~7초) 시작 → 순차 합산 **~12~17초**

#### 변경 후 (병렬)

```java
// 1. 두 호출을 동시에 시작
CompletableFuture<String> crawlFuture = null;
if (request.getMode() == InterviewMode.COMPANY && request.getJobPostingUrl() != null) {
    crawlFuture = CompletableFuture.supplyAsync(
            () -> jobCrawlerClient.crawl(request.getJobPostingUrl()));
}

CompletableFuture<String> githubFuture = null;
if (request.getGithubUrl() != null) {
    githubFuture = CompletableFuture.supplyAsync(
            () -> githubApiClient.extractGithubInfo(request.getGithubUrl()));
}

// 2. 각각 타임아웃 + graceful degradation
String jobPostingContent = joinSafely(crawlFuture, 15);
String githubInfo = joinSafely(githubFuture, 30);
```

```java
// joinSafely — 실패 시 null 반환, 인터뷰 생성은 계속 진행
private String joinSafely(CompletableFuture<String> future, long timeoutSeconds) {
    if (future == null) return null;
    try {
        return future.orTimeout(timeoutSeconds, TimeUnit.SECONDS).join();
    } catch (Exception e) {
        log.warn("외부 호출 실패 (graceful degradation): {}", e.getMessage());
        return null;
    }
}
```

- crawl + github **동시 실행** → 가장 느린 호출 기준으로만 대기

#### 기술적 의사결정

| 결정 | 이유 |
|------|------|
| `CompletableFuture.supplyAsync()` | crawl과 github는 완전 독립적 — 동시 실행 가능 |
| `joinSafely()` + null 반환 | 외부 호출 실패가 인터뷰 생성을 막으면 안 됨. GitHub/채용공고 정보는 보조 컨텍스트이므로 없어도 AI가 질문 생성 가능 |
| `orTimeout(crawl 15초, github 30초)` | crawl은 Jsoup 자체 타임아웃 10초 + 여유, github는 내부 병렬 README fetch 포함 |
| `CompletionException` catch | `.join()`은 예외를 `CompletionException`으로 래핑 — 이를 잡지 않으면 500 에러 노출 |
| `@Transactional` 범위 유지 | 외부 호출은 DB 트랜잭션과 무관 (읽기 전용 데이터 수집), future는 `.join()` 후 결과만 사용 |

#### 측정 결과

| Client.Method | Baseline | After wt-1 | After wt-2 | 개선 |
|---------------|----------|------------|------------|------|
| GithubApiClient.extractGithubInfo | 4.89s | 1.57s | **1.83s** | -63% |
| JobCrawlerClient.crawl | 미측정 | 미측정 | **474ms** | 첫 측정 |
| ClaudeAiClient.chat | 5.51s | 3.05s | **5.59s** | AI 응답 변동 |

**핵심 개선**: crawl(474ms) + github(1.83s)가 **동시 실행**되므로 순차 대비 **~474ms 절약** (crawl이 github보다 빨리 끝남).
전체 옵션 사용 시 총 대기: github(1.83s) + chat(5.59s) = **~7.4초** (baseline ~15초 대비 -50%)

![After wt-2](./screenshots/grafana-after-wt2.png)

### 3단계: 첫 질문 스트리밍 전환

- **대상**: `claudeAiClient.chat()` 동기 블로킹
- **방법**: 세션 생성 즉시 반환 → 첫 질문은 SSE 스트리밍
- **예상 효과**: 체감 대기 ~2초 (세션 생성만)

### 예상 총 효과

| 단계 | 전체 소요 시간 (worst) |
|------|----------------------|
| Baseline (현재) | ~27초 |
| 1단계 후 | ~22초 (-5초) |
| 2단계 후 | ~12초 (-10초) |
| 3단계 후 | 체감 ~2초 |

---

## 전후 비교

> 각 단계 완료 시 동일 조건으로 API 호출 → Grafana 대시보드 캡처

| 단계 | 스크린샷 | 결과 |
|------|---------|------|
| Baseline | ![baseline](./screenshots/grafana-baseline.png) | extractGithubInfo **4.89s**, chat **5.51s** |
| 1단계 후 | ![after-wt1](./screenshots/grafana-after-wt1.png) | extractGithubInfo **1.57s** (-68%) |
| 2단계 후 | ![after-wt2](./screenshots/grafana-after-wt2.png) | crawl **474ms** + github **1.83s** 동시 실행, 총 **~7.4초** (-50%) |
| 3단계 후 | | 예정 |
