package com.interviewai.backend.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JobCrawlerProperties 테스트")
class JobCrawlerPropertiesTest {

    @Test
    @DisplayName("기능_테스트_기본값으로_JobCrawlerProperties가_초기화된다")
    void 기능_테스트_기본값으로_JobCrawlerProperties가_초기화된다() {
        JobCrawlerProperties properties = new JobCrawlerProperties();

        assertThat(properties.getTimeoutMs()).isEqualTo(10_000);
        assertThat(properties.getMaxTextLength()).isEqualTo(3000);
        assertThat(properties.getUserAgent()).isEqualTo("Mozilla/5.0 (compatible; InterviewAI/1.0)");
    }

    @Test
    @DisplayName("기능_테스트_값을_변경할_수_있다")
    void 기능_테스트_값을_변경할_수_있다() {
        JobCrawlerProperties properties = new JobCrawlerProperties();
        properties.setTimeoutMs(5_000);
        properties.setMaxTextLength(1000);
        properties.setUserAgent("CustomAgent/1.0");

        assertThat(properties.getTimeoutMs()).isEqualTo(5_000);
        assertThat(properties.getMaxTextLength()).isEqualTo(1000);
        assertThat(properties.getUserAgent()).isEqualTo("CustomAgent/1.0");
    }
}
