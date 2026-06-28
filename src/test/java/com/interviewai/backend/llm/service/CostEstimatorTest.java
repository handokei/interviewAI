package com.interviewai.backend.llm.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CostEstimator 테스트")
class CostEstimatorTest {

    private CostEstimator estimator;

    @BeforeEach
    void setUp() {
        estimator = new CostEstimator();
    }

    @Test
    @DisplayName("기능_테스트_Gemini_2_5_Flash_input_1M_토큰은_0_30_USD로_환산된다")
    void 기능_테스트_Gemini_2_5_Flash_input_1M_토큰은_0_30_USD로_환산된다() {
        BigDecimal cost = estimator.estimate("gemini-2.5-flash", 1_000_000, 0);
        assertThat(cost.setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(new BigDecimal("0.30"));
    }

    @Test
    @DisplayName("기능_테스트_Gemini_2_5_Flash_output_1M_토큰은_2_50_USD로_환산된다")
    void 기능_테스트_Gemini_2_5_Flash_output_1M_토큰은_2_50_USD로_환산된다() {
        BigDecimal cost = estimator.estimate("gemini-2.5-flash", 0, 1_000_000);
        assertThat(cost.setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(new BigDecimal("2.50"));
    }

    @Test
    @DisplayName("기능_테스트_input_output_혼합_시_두_항목의_합이_반환된다")
    void 기능_테스트_input_output_혼합_시_두_항목의_합이_반환된다() {
        // 1000 input ($0.0003) + 500 output ($0.00125) = $0.00155
        BigDecimal cost = estimator.estimate("gemini-2.5-flash", 1000, 500);
        assertThat(cost).isEqualByComparingTo(new BigDecimal("0.00155000"));
    }

    @Test
    @DisplayName("기능_테스트_등록되지_않은_모델은_0_USD로_안전하게_떨어진다")
    void 기능_테스트_등록되지_않은_모델은_0_USD로_안전하게_떨어진다() {
        BigDecimal cost = estimator.estimate("unknown-model-x", 10_000, 5_000);
        assertThat(cost).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("기능_테스트_token이_모두_0이면_비용도_0이다")
    void 기능_테스트_token이_모두_0이면_비용도_0이다() {
        BigDecimal cost = estimator.estimate("gemini-2.5-flash", 0, 0);
        assertThat(cost).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("기능_테스트_반환되는_BigDecimal은_scale_8로_반올림된다")
    void 기능_테스트_반환되는_BigDecimal은_scale_8로_반올림된다() {
        BigDecimal cost = estimator.estimate("gemini-2.5-flash", 1, 1);
        assertThat(cost.scale()).isEqualTo(8);
    }
}
