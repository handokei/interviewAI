# 기술적 의사결정: AI 면접 Context Window 관리

## 1. 문제 정의

### 1.1 현상

AI 면접에서 대화가 길어질수록 AI 호출 시간이 급격히 증가한다.

| 대화 길이 | `chat` 평균 시간 | `chat` 최대 시간 |
|-----------|-----------------|-----------------|
| 짧은 대화 (5턴 이내) | 7.65초 | 7.65초 |
| 긴 대화 (10턴 이상) | 9.98초 | 12~13초 |

> 이미지 필요: `screenshots/context-before-short.png` — 짧은 대화 시 Grafana 메트릭스
> 이미지 필요: `screenshots/context-before-long.png` — 긴 대화 시 Grafana 메트릭스 (응답 시간 증가 추이)

### 1.2 원인

모든 AI 호출이 전체 대화 히스토리를 무제한으로 포함한다. 대화가 30턴을 넘어가면 프롬프트 토큰이 수만 개에 달하고, AI 응답 시간은 입력 토큰 수에 비례하여 증가한다.

#### 무제한 히스토리 사용 지점

| 호출 지점 | 메서드 | 히스토리 사용 | 호출 빈도 |
|-----------|--------|-------------|----------|
| 메인 응답 | `claudeAiClient.chat/streamChat` | `List<ChatMessage>` 전체 전달 | 매 메시지 |
| 비동기 평가 | `InterviewEvaluationService.buildEvalPrompt` | 전체 히스토리를 문자열로 직접 조합 | 매 메시지 |
| 종료 피드백 | `InterviewServiceImpl.buildConversationText` | 전체 메시지 연결 | 세션당 1회 |

#### 현재 제한되는 것 vs 제한 없는 것

| 제한 있음 | 제한 없음 |
|----------|----------|
| 문서 길이 (`maxDocumentLength = 1500자`) | 대화 히스토리 |
| 채용공고 길이 (`maxJobPostingLength = 1500자`) | 메시지 개수 |
| SSE 타임아웃 (`120초`) | 메시지 길이 |
| | 평가 프롬프트 크기 |
| | 피드백 대화 크기 |

### 1.3 영향

| 영향 | 설명 |
|------|------|
| 응답 시간 증가 | 대화 10턴 이상에서 chat 호출 9.98초 → 12~13초 |
| 비용 증가 | 토큰 수 증가 = API 호출 비용 증가 |
| Context Window 초과 위험 | 극단적 케이스에서 모델 최대 토큰 초과 가능 |
| 평가 품질 저하 | 긴 프롬프트에서 AI가 핵심 정보를 놓칠 수 있음 (Lost in the Middle 문제) |

---

## 2. 해결 방안 비교

### 방안 1: 순수 Sliding Window

```
[System Prompt] + [첫 질문(예약)] + [최근 N턴 원문]
```

오래된 메시지를 버리고, 토큰 budget 내에서 최근 메시지만 유지한다.

| 항목 | 내용 |
|------|------|
| 구현 복잡도 | 낮음 — `TokenEstimator` 유틸리티 1개 |
| 추가 AI 호출 | 없음 |
| 추가 인프라 | 없음 |
| 맥락 보존 | 낮음 — budget 초과 시 오래된 대화 완전 소실 |
| 면접 품질 | AI가 이전 주제를 잊음. 연결 질문 불가 |

**트레이드오프**: 구현이 가장 단순하고 추가 비용이 없지만, 대화가 길어지면 AI 면접관이 이전 주제에 대한 기억을 완전히 잃는다.

---

### 방안 2: 중요도 기반 선별 (Evaluation-Aware Retention)

```
[System Prompt] + [첫 질문] + [STUDY_REQUIRED 턴 원문] + [최근 5턴 원문]
```

이전 이슈(#56)에서 이미 `InterviewMessage`에 `answerLevel`(PASS, NEEDS_IMPROVEMENT, STUDY_REQUIRED)을 저장하고 있으므로, 평가 결과를 활용하여 중요한 턴만 선별 유지한다.

| 항목 | 내용 |
|------|------|
| 구현 복잡도 | 낮음~중간 — 기존 eval 데이터 활용, trimming 로직 확장 |
| 추가 AI 호출 | 없음 (기존 평가 결과 재사용) |
| 추가 인프라 | 없음 (#56에서 이미 `answerLevel` DB 저장) |
| 맥락 보존 | 중간~높음 — 약점 턴이 보존되어 면접 연결성 유지 |
| 면접 품질 | 약점 기반 꼬리질문 가능 |

**트레이드오프**: #56의 eval 인프라를 재활용하므로 추가 비용 없이 맥락 품질을 올릴 수 있다. 다만 `PASS`인 턴을 제거하면 "잘한 부분"에 대한 심화 질문 기회를 잃을 수 있다.

---

### 방안 3: 3-Layer Context (Persistent + Summary + Recent)

```
[System Prompt + 누적 요약] + [최근 5-10턴 원문]
```

히스토리가 budget을 초과할 때 AI로 요약을 생성하고, 시스템 프롬프트에 누적 저장한다.

| 항목 | 내용 |
|------|------|
| 구현 복잡도 | 중간 — 요약 생성/저장/갱신 로직 |
| 추가 AI 호출 | budget 초과 시점에 1회 (요약 생성) |
| 추가 인프라 | `InterviewSession.conversationSummary` 컬럼 1개 |
| 맥락 보존 | 높음 — 핵심 정보가 요약에 보존 |
| 면접 품질 | 요약 품질에 의존. 요약이 왜곡되면 면접 흐름 왜곡 |

**트레이드오프**: 맥락 보존이 가장 우수하지만, 요약 품질을 제어할 수 없다는 근본적 리스크가 있다. AI가 부정확하게 요약하면 이후 면접관이 잘못된 전제로 질문한다.

---

### 방안 4: RAG 기반 검색 (Vector Retrieval)

```
[System Prompt] + [관련 과거 턴 검색 결과] + [최근 3-5턴 원문]
```

모든 메시지를 벡터 DB(Embedding)에 저장하고, 현재 대화와 의미적으로 관련된 과거 턴을 검색하여 포함한다.

| 항목 | 내용 |
|------|------|
| 구현 복잡도 | 높음 — 임베딩 생성 + 벡터 DB + 검색 로직 |
| 추가 AI 호출 | 매 메시지마다 임베딩 생성 1회 |
| 추가 인프라 | 벡터 DB (pgvector, Pinecone 등) |
| 맥락 보존 | 최고 — 주제별로 관련 대화를 정확히 검색 |
| 면접 품질 | 가장 우수 |

**트레이드오프**: 기술적으로 가장 우아하지만, 면접 대화는 20-30턴 수준의 짧은 대화이므로 벡터 DB 도입이 과잉이다.

---

### 방안 5: Map-Reduce 요약 (Chunked Summarization)

```
[System Prompt] + [초반 요약] + [중반 요약] + [최근 원문]
```

히스토리를 청크(5턴 단위)로 나눠 각각 요약하고 이어붙인다.

| 항목 | 내용 |
|------|------|
| 구현 복잡도 | 높음 — 청크 분할 + 다중 요약 + 병합 |
| 추가 AI 호출 | 청크당 1회 (5턴마다) |
| 맥락 보존 | 높음 — 시간순 맥락이 구간별로 보존 |
| 면접 품질 | 양호 |

**트레이드오프**: 100턴 이상의 장기 대화에 적합한 전략이다. 20-30턴 면접에서는 비용 대비 효과가 방안 3보다 낮다.

---

## 3. 기술적 의사결정

### 3.1 비교 요약

| 기준 | 방안 1 | 방안 2 | 방안 3 | 방안 4 | 방안 5 |
|------|--------|--------|--------|--------|--------|
| 구현 비용 | ★ | ★~★★ | ★★ | ★★★★ | ★★★ |
| 추가 AI 비용 | 0 | 0 | 낮음 | 중간 | 높음 |
| 맥락 보존 | ★ | ★★★ | ★★★ | ★★★★ | ★★★ |
| 면접 품질 | ★★ | ★★★ | ★★★ | ★★★★ | ★★★ |
| 기존 인프라 활용 | - | ★★★★ | - | - | - |

### 3.2 채택: 방안 1 + 방안 2 조합

```
[System Prompt]
+ [첫 질문 (예약)]
+ [STUDY_REQUIRED 턴 원문 (약점 보존)]
+ [최근 N턴 원문]
```

### 3.3 채택 이유

**1. 아키텍처 일관성 — #56 eval 인프라 재활용**

이전 이슈(#56)에서 `evaluateWithAi()`를 비동기 분리하면서 `InterviewMessage` 테이블에 `answerLevel`을 저장하는 인프라를 구축했다. 이 평가 결과를 Context 관리에까지 활용하면, 평가 → Context 선별 → 면접 품질 향상이라는 **파이프라인 일관성**이 확보된다. 새 인프라 없이 기존 데이터를 재활용하는 것은 비용 효율적이면서도 아키텍처적으로 정당한 선택이다.

**2. 면접 도메인 최적화 — 약점 우선 보존**

실제 면접관은 지원자의 약점을 기억하고 후속 질문으로 연결한다. `STUDY_REQUIRED` 턴을 우선 보존하면 AI 면접관이 "아까 인덱스를 잘 모르셨는데, 쿼리 최적화는 어떻게 접근하시나요?"처럼 약점 기반 꼬리질문을 자연스럽게 이어갈 수 있다. 단순 시간 기반 sliding window로는 불가능한 **도메인 특화 맥락 관리**이다.

**3. 추가 비용 제로**

방안 3/4/5는 추가 AI 호출이나 인프라(벡터 DB, 요약 저장)가 필요하다. 방안 1+2는 기존 평가 결과를 trimming 로직에서 참조하는 것뿐이므로 추가 비용이 없다.

**4. RAG/WebSocket은 현 시점에서 과잉**

면접 대화는 20-30턴 수준이다. 벡터 DB 도입은 면접 데이터가 수만 건 축적되어 "과거 면접 참조" 기능이 필요해질 때 검토할 가치가 있다.

**5. 요약 레이어는 후속 확장**

방안 1+2로 성능 문제를 해결한 후, 방안 3(누적 요약)을 별도 이슈로 추가하여 맥락 보존을 더 강화할 수 있다. 현재 아키텍처(`TokenEstimator`)가 요약 레이어 추가의 기반이 된다.

---

## 4. 구현 상세

### 4.1 토큰 budget 설정

| 설정 | 값 | 적용 대상 | 근거 |
|------|-----|----------|------|
| `maxHistoryTokens` | 8000 | `chat/streamChat` 메인 응답 | 1턴 ~200-400토큰 × 10-20턴. 충분한 맥락 + 응답 시간 제어 |
| `maxEvalHistoryTokens` | 4000 | `evaluateWithAi` 평가 프롬프트 | 최근 답변 품질 판단이므로 5-10턴이면 충분 |
| `maxFeedbackTokens` | 30000 | 면접 종료 피드백 | 전체 대화가 필요하지만 극단적 케이스 방지 (약 50-75턴) |
| `charsPerToken` | 3 | 토큰 추정 비율 | 한글 1글자 ≈ 1-2토큰, 보수적으로 3글자당 1토큰 |

모든 값은 `application.yml`로 외부화하여 운영 중 재시작 없이 조정 가능하다.

### 4.2 TokenEstimator 핵심 알고리즘

**기본 Sliding Window (`trimHistory`)**:
1. 전체 토큰이 budget 이내면 그대로 반환
2. 첫 메시지(AI 첫 질문) 토큰을 budget에서 예약
3. 나머지 메시지를 역순으로 순회하며 budget 채움
4. 시간순(정순)으로 반환

**중요도 기반 Sliding Window (`trimHistoryWithPriority`)**:
1. 전체 토큰이 budget 이내면 그대로 반환
2. 첫 메시지 예약
3. `STUDY_REQUIRED` 턴을 우선 포함 (오래된 순)
4. 남은 budget으로 최근 일반 메시지 포함 (역순)
5. 시간순 정렬 후 반환

### 4.3 적용 지점

| 호출 지점 | 적용 메서드 | budget |
|-----------|-----------|--------|
| `sendMessage()` | `trimHistoryWithPriority` | `maxHistoryTokens` (8000) |
| `streamMessage()` | `trimHistoryWithPriority` | `maxHistoryTokens` (8000) |
| `buildEvalPrompt()` | `trimHistory` | `maxEvalHistoryTokens` (4000) |
| `finishInterview()` | `trimHistory` (초과 시만) | `maxFeedbackTokens` (30000) |

### 4.4 변경 파일

| 파일 | 변경 내용 |
|------|-----------|
| `InterviewProperties.java` | `maxHistoryTokens`, `maxEvalHistoryTokens`, `maxFeedbackTokens`, `charsPerToken` 추가 |
| `application.yml` | 신규 설정 기본값 명시 |
| `TokenEstimator.java` (신규) | 토큰 추정 + 중요도 기반 sliding window |
| `InterviewServiceImpl.java` | `sendMessage`, `streamMessage`에 `trimHistoryWithPriority` 적용, `finishInterview`에 feedback budget 적용 |
| `InterviewEvaluationService.java` | `buildEvalPrompt`에 `trimHistory` 적용 |

---

## 5. 검증 계획

### 5.1 필요한 Grafana 메트릭스

현재 Grafana 대시보드(`client-performance.json`)는 `client.call.duration` 하나의 메트릭만 보여준다. Context Window 변경의 효과를 정확히 측정하려면 추가 메트릭이 필요하다.

#### 현재 대시보드 패널

| 패널 | 쿼리 | 용도 |
|------|------|------|
| Client Call Duration (avg) | `client_call_duration_seconds_sum / count` | AI 호출 평균 시간 |
| Client Call Total Count | `client_call_duration_seconds_count` | 호출 횟수 |
| Client Call Duration (max) | `client_call_duration_seconds_max` | 최대 응답 시간 |
| Client Call Summary | instant 테이블 | 요약 |

#### 추가 필요한 패널

| 패널 | 쿼리 | 용도 |
|------|------|------|
| History Trimming Rate | `interview_history_trimmed_total` | trimming이 발생한 횟수 (budget 초과 빈도) |
| Trimmed vs Original Message Count | `interview_history_original_count` vs `interview_history_trimmed_count` | 몇 개의 메시지가 잘렸는지 |
| Prompt Token Estimate | `interview_prompt_estimated_tokens` | 추정 토큰 수 추이 (budget 이내인지) |
| Chat Duration by Conversation Length | 기존 쿼리 + 턴 수 태그 | 대화 길이별 응답 시간 분포 |

### 5.2 필요한 스크린샷

| 스크린샷 | 파일명 | 용도 |
|---------|--------|------|
| 수정 전 짧은 대화 메트릭스 | `screenshots/context-before-short.png` | 기준선 (7.65초) |
| 수정 전 긴 대화 메트릭스 | `screenshots/context-before-long.png` | 문제 증거 (9.98초, max 12-13초) |
| 수정 후 짧은 대화 메트릭스 | `screenshots/context-after-short.png` | 단축 확인 |
| 수정 후 긴 대화 메트릭스 | `screenshots/context-after-long.png` | 대화 길이 무관하게 일정한지 확인 |
| Trimming 발생 현황 | `screenshots/context-trimming-rate.png` | trimming이 실제로 동작하는지 확인 |

### 5.3 기능 검증 항목

- [ ] 대화가 길어져도 응답 시간이 일정한지 확인
- [ ] 첫 질문 맥락이 유지되는지 확인 (AI가 면접 주제를 기억하는지)
- [ ] `STUDY_REQUIRED` 턴의 맥락이 보존되어 꼬리질문이 이어지는지 확인
- [ ] 면접 종료 피드백이 정상 생성되는지 확인
- [ ] Grafana 대시보드에서 `chat` 평균 시간이 대화 길이에 관계없이 안정적인지 확인

---

## 6. 예상 효과

### 6.1 성능

| 항목 | 수정 전 | 수정 후 (예상) |
|------|---------|---------------|
| 짧은 대화 chat 시간 | 7.65초 | 7~8초 (변화 없음) |
| 긴 대화 chat 시간 | 9.98초 (max 12-13초) | 7~8초 (대화 길이 무관) |
| 프롬프트 최대 크기 | 무제한 | ~10,000 토큰 이내 |
| 평가 프롬프트 크기 | 무제한 | ~4,000 토큰 이내 |

### 6.2 비용

토큰 수 감소 = API 호출 비용 감소. 특히 긴 면접에서 효과가 크다.

### 6.3 면접 품질

- `STUDY_REQUIRED` 턴 보존으로 약점 기반 꼬리질문 가능
- 첫 질문 예약으로 면접 주제/역할 맥락 유지
- `PASS` 턴은 제거되지만, 면접 특성상 최근 주제가 가장 중요

---

## 7. 후속 로드맵

| 단계 | 내용 | 설명 |
|------|------|------|
| 1단계 (이번 이슈) | Sliding Window + 중요도 선별 | TokenEstimator + answerLevel 기반 trimming |
| 2단계 | 누적 요약 레이어 | AI 요약 → `InterviewSession.conversationSummary`에 저장 |
| 3단계 | 외부 데이터 품질 개선 | 문서 스마트 잘라내기, GitHub 코드 분석, 공고 이미지 OCR |
| 4단계 | RAG 도입 | 면접 데이터 축적 시 벡터 DB + 임베딩 검색 |
| 5단계 | AI 호출 Resilience | retry + circuit breaker 패턴 |

---

## 8. Grafana 대시보드 개선 제안

### 8.1 현재 한계

현재 대시보드는 `client.call.duration`만 보여주므로:
- **대화 길이별 응답 시간 차이**를 구분할 수 없음
- **trimming 동작 여부**를 확인할 수 없음
- **토큰 수 추이**를 모니터링할 수 없음

### 8.2 추가 필요한 커스텀 메트릭

`PerformanceLoggingAspect` 또는 `TokenEstimator`에서 다음 메트릭을 기록해야 한다:

| 메트릭 이름 | 타입 | 태그 | 설명 |
|------------|------|------|------|
| `interview.history.original.count` | Gauge | `method` | trimming 전 메시지 수 |
| `interview.history.trimmed.count` | Gauge | `method` | trimming 후 메시지 수 |
| `interview.history.estimated.tokens` | Gauge | `method` | 추정 토큰 수 |
| `interview.history.trimmed` | Counter | `method` | trimming 발생 횟수 |

### 8.3 대시보드 패널 구성 (제안)

**Row 1: AI 호출 성능 (기존)**
- Client Call Duration (avg) — 기존 유지
- Client Call Duration (max) — 기존 유지

**Row 2: Context Window 모니터링 (신규)**
- History Message Count (original vs trimmed) — Gauge 비교
- Estimated Token Usage — 시간 추이
- Trimming Rate — 발생 빈도

**Row 3: 요약 테이블 (기존 + 확장)**
- Client Call Summary — 기존 유지
- Context Window Summary — 평균 메시지 수, 평균 토큰 수, trimming 비율
