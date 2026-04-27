package com.interviewai.backend.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InterviewProperties 테스트")
class InterviewPropertiesTest {

    @Test
    @DisplayName("기능_테스트_기본값으로_InterviewProperties가_초기화된다")
    void 기능_테스트_기본값으로_InterviewProperties가_초기화된다() {
        InterviewProperties properties = new InterviewProperties();

        assertThat(properties.getPrompt().getMaxDocumentLength()).isEqualTo(5000);
        assertThat(properties.getPrompt().getMaxJobPostingLength()).isEqualTo(1500);
        assertThat(properties.getSse().getTimeoutMs()).isEqualTo(120_000L);
    }

    @Test
    @DisplayName("기능_테스트_Prompt_값을_변경할_수_있다")
    void 기능_테스트_Prompt_값을_변경할_수_있다() {
        InterviewProperties properties = new InterviewProperties();
        properties.getPrompt().setMaxDocumentLength(2000);
        properties.getPrompt().setMaxJobPostingLength(3000);

        assertThat(properties.getPrompt().getMaxDocumentLength()).isEqualTo(2000);
        assertThat(properties.getPrompt().getMaxJobPostingLength()).isEqualTo(3000);
    }

    @Test
    @DisplayName("기능_테스트_Sse_타임아웃을_변경할_수_있다")
    void 기능_테스트_Sse_타임아웃을_변경할_수_있다() {
        InterviewProperties properties = new InterviewProperties();
        properties.getSse().setTimeoutMs(60_000L);

        assertThat(properties.getSse().getTimeoutMs()).isEqualTo(60_000L);
    }
}
