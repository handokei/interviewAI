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

### 1단계: GitHub README 병렬 fetch

- **대상**: `GithubApiClient.appendReadme()` 순차 루프
- **방법**: `CompletableFuture.allOf()`로 병렬 실행
- **예상 효과**: 5초 → 500ms

### 2단계: 외부 호출 병렬화

- **대상**: `InterviewServiceImpl.startInterview()` 내 crawl + github 순차 호출
- **방법**: `CompletableFuture`로 동시 실행
- **예상 효과**: 순차 13초 → 병렬 10초 (가장 느린 호출 기준)

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
| Baseline | ![baseline](./screenshots/grafana-baseline.png) | 측정 완료 |
| 1단계 후 | | 예정 |
| 2단계 후 | | 예정 |
| 3단계 후 | | 예정 |
