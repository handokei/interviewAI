package com.interviewai.backend.client;

import com.interviewai.backend.global.config.JobCrawlerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@DisplayName("JobCrawlerClient 테스트")
@ExtendWith(MockitoExtension.class)
class JobCrawlerClientTest {

    @Mock
    private JobCrawlerProperties jobCrawlerProperties;

    private JobCrawlerClient jobCrawlerClient;

    @BeforeEach
    void setUp() {
        lenient().when(jobCrawlerProperties.getTimeoutMs()).thenReturn(5000);
        lenient().when(jobCrawlerProperties.getUserAgent()).thenReturn("TestAgent/1.0");
        lenient().when(jobCrawlerProperties.getMaxTextLength()).thenReturn(3000);
        jobCrawlerClient = new JobCrawlerClient(jobCrawlerProperties);
    }

    @Test
    @DisplayName("기능_테스트_null_URL이면_null을_반환한다")
    void 기능_테스트_null_URL이면_null을_반환한다() {
        String result = jobCrawlerClient.crawl(null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("기능_테스트_ftp_scheme_URL이면_null을_반환한다")
    void 기능_테스트_ftp_scheme_URL이면_null을_반환한다() {
        String result = jobCrawlerClient.crawl("ftp://bad-scheme.com/job");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("기능_테스트_file_scheme_URL이면_null을_반환한다")
    void 기능_테스트_file_scheme_URL이면_null을_반환한다() {
        String result = jobCrawlerClient.crawl("file:///etc/passwd");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("기능_테스트_연결할_수_없는_http_URL이면_예외를_잡아_null을_반환한다")
    void 기능_테스트_연결할_수_없는_http_URL이면_예외를_잡아_null을_반환한다() {
        String result = jobCrawlerClient.crawl("http://localhost:0/invalid-url");

        assertThat(result).isNull();
    }
}
