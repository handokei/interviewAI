package com.interviewai.backend.llm.model;

import com.interviewai.backend.llm.enums.PromptType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * LLM 호출 1건의 운영 metric을 보존한다. metric service가 호출 종료 시 1건 저장한다.
 *
 * <p>읽기 위주: actuator endpoint가 24h aggregation에 사용. 쓰기 비용을 낮추기 위해
 * 필드는 모두 단순 scalar (FK 없음).</p>
 *
 * <p>인덱스: ({@code createdAt}) — 24h aggregation 범위 조회.
 * ({@code promptType}, {@code status}) — group-by aggregation.</p>
 */
@Entity
@Table(
        name = "llm_call_log",
        indexes = {
                @Index(name = "idx_llm_call_log_created_at", columnList = "createdAt"),
                @Index(name = "idx_llm_call_log_prompt_status", columnList = "promptType,status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LlmCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 호출 식별자 — UUID로 외부 trace ID와 매칭 가능. */
    @Column(nullable = false, length = 64)
    private String requestId;

    /** 모델명 (예: gemini-2.5-flash). 단가/엔진 변경 시 group-by 키. */
    @Column(nullable = false, length = 64)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PromptType promptType;

    /** 입력 token 추정량 — char/charsPerToken 기반. */
    @Column(nullable = false)
    private int inputTokens;

    /** 출력 token 추정량 — 응답 길이 기반. 실패 시 0. */
    @Column(nullable = false)
    private int outputTokens;

    @Column(nullable = false)
    private long durationMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LlmCallStatus status;

    /** 실패 분류 — TIMEOUT / RATE_LIMIT / SERVER_ERROR / PARSE_ERROR / OTHER. SUCCESS 시 null. */
    @Column(length = 32)
    private String failureReason;

    /** sanitize 휴리스틱 발동 여부 — markdown codeblock / trailing comma 등. */
    @Column(nullable = false)
    private boolean sanitizeApplied;

    /** 호출 시 적용된 retry 횟수. 0이면 단발 호출. */
    @Column(nullable = false)
    private int retryCount;

    /** Gemini 단가 표 기반 USD 추정 비용. precision 10 / scale 8. */
    @Column(nullable = false, precision = 10, scale = 8)
    private BigDecimal costEstimate;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public LlmCallLog(String requestId, String model, PromptType promptType,
                      int inputTokens, int outputTokens, long durationMs,
                      LlmCallStatus status, String failureReason,
                      boolean sanitizeApplied, int retryCount, BigDecimal costEstimate) {
        this.requestId = requestId;
        this.model = model;
        this.promptType = promptType;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.durationMs = durationMs;
        this.status = status;
        this.failureReason = failureReason;
        this.sanitizeApplied = sanitizeApplied;
        this.retryCount = retryCount;
        this.costEstimate = costEstimate;
    }
}
