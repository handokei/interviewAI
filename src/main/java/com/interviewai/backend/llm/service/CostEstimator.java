package com.interviewai.backend.llm.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 모델별 input/output token 단가를 기반으로 1 호출의 USD 비용을 추정한다.
 *
 * <p>출처: <a href="https://ai.google.dev/gemini-api/docs/pricing">Gemini API 가격 페이지</a>
 * (2026-06 기준).</p>
 *
 * <p>단위: 1,000,000 token당 USD. 미등록 모델은 안전 fallback(0.00)으로 처리해
 * costEstimate가 NPE/throw 없이 항상 BigDecimal을 돌려준다.</p>
 */
@Component
public class CostEstimator {

    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");

    /** Gemini 2.5-Flash 표준 단가. ≤200k context tier. */
    static final BigDecimal GEMINI_25_FLASH_INPUT_USD_PER_MTOK = new BigDecimal("0.30");
    static final BigDecimal GEMINI_25_FLASH_OUTPUT_USD_PER_MTOK = new BigDecimal("2.50");

    /** 모델명 → (input USD/Mtok, output USD/Mtok). */
    private final Map<String, BigDecimal[]> pricingTable = Map.of(
            "gemini-2.5-flash", new BigDecimal[]{
                    GEMINI_25_FLASH_INPUT_USD_PER_MTOK,
                    GEMINI_25_FLASH_OUTPUT_USD_PER_MTOK
            }
    );

    /**
     * 비용 추정 — 모델 단가 × token. 단가 미등록 모델은 0으로 떨어진다 (장애 무전파).
     */
    public BigDecimal estimate(String model, int inputTokens, int outputTokens) {
        BigDecimal[] pricing = pricingTable.get(model);
        if (pricing == null) {
            return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        }
        BigDecimal inputCost = pricing[0]
                .multiply(BigDecimal.valueOf(inputTokens))
                .divide(ONE_MILLION, 8, RoundingMode.HALF_UP);
        BigDecimal outputCost = pricing[1]
                .multiply(BigDecimal.valueOf(outputTokens))
                .divide(ONE_MILLION, 8, RoundingMode.HALF_UP);
        return inputCost.add(outputCost).setScale(8, RoundingMode.HALF_UP);
    }
}
