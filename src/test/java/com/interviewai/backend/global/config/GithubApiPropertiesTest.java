package com.interviewai.backend.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GithubApiProperties 테스트")
class GithubApiPropertiesTest {

    @Test
    @DisplayName("기능_테스트_기본값으로_GithubApiProperties가_초기화된다")
    void 기능_테스트_기본값으로_GithubApiProperties가_초기화된다() {
        GithubApiProperties properties = new GithubApiProperties();

        assertThat(properties.getBaseUrl()).isEqualTo("https://api.github.com");
        assertThat(properties.getMaxRepos()).isEqualTo(10);
        assertThat(properties.getMaxReadmeLength()).isEqualTo(500);
    }

    @Test
    @DisplayName("기능_테스트_값을_변경할_수_있다")
    void 기능_테스트_값을_변경할_수_있다() {
        GithubApiProperties properties = new GithubApiProperties();
        properties.setBaseUrl("https://api.github.enterprise.com");
        properties.setMaxRepos(20);
        properties.setMaxReadmeLength(1000);

        assertThat(properties.getBaseUrl()).isEqualTo("https://api.github.enterprise.com");
        assertThat(properties.getMaxRepos()).isEqualTo(20);
        assertThat(properties.getMaxReadmeLength()).isEqualTo(1000);
    }
}
