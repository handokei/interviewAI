package com.interviewai.backend.llm.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PromptType enum 테스트")
class PromptTypeTest {

    @Test
    @DisplayName("기능_테스트_프롬프트_유형은_INTERVIEWER_EVALUATOR_SUMMARIZER_세_개로_정의된다")
    void 기능_테스트_프롬프트_유형은_INTERVIEWER_EVALUATOR_SUMMARIZER_세_개로_정의된다() {
        PromptType[] values = PromptType.values();
        assertThat(values).containsExactly(
                PromptType.INTERVIEWER,
                PromptType.EVALUATOR,
                PromptType.SUMMARIZER
        );
    }

    @Test
    @DisplayName("기능_테스트_valueOf로_각_상수를_역조회_가능하다")
    void 기능_테스트_valueOf로_각_상수를_역조회_가능하다() {
        assertThat(PromptType.valueOf("INTERVIEWER")).isEqualTo(PromptType.INTERVIEWER);
        assertThat(PromptType.valueOf("EVALUATOR")).isEqualTo(PromptType.EVALUATOR);
        assertThat(PromptType.valueOf("SUMMARIZER")).isEqualTo(PromptType.SUMMARIZER);
    }
}
