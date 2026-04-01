package com.interviewai.backend.client;

import com.interviewai.backend.global.config.GithubApiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GithubApiClient 테스트")
@ExtendWith(MockitoExtension.class)
class GithubApiClientTest {

    @Mock
    private GithubApiProperties githubApiProperties;

    @Mock
    private RestClient restClient;

    private GithubApiClient githubApiClient;

    @BeforeEach
    void setUp() {
        lenient().when(githubApiProperties.getMaxRepos()).thenReturn(10);
        lenient().when(githubApiProperties.getMaxReadmeLength()).thenReturn(500);
        githubApiClient = new GithubApiClient(githubApiProperties, restClient);
    }

    @Test
    @DisplayName("기능_테스트_null_URL이면_사용자명_추출_불가_메시지가_반환된다")
    void 기능_테스트_null_URL이면_사용자명_추출_불가_메시지가_반환된다() {
        String result = githubApiClient.extractGithubInfo(null);

        assertThat(result).contains("사용자명을 추출할 수 없습니다");
    }

    @Test
    @DisplayName("기능_테스트_github_com이_없는_URL이면_사용자명_추출_불가_메시지가_반환된다")
    void 기능_테스트_github_com이_없는_URL이면_사용자명_추출_불가_메시지가_반환된다() {
        String result = githubApiClient.extractGithubInfo("https://notgithub.com/user");

        assertThat(result).contains("사용자명을 추출할 수 없습니다");
    }

    @Test
    @DisplayName("기능_테스트_유효한_GitHub_URL이면_결과에_사용자명이_포함된다")
    void 기능_테스트_유효한_GitHub_URL이면_결과에_사용자명이_포함된다() {
        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(Class.class))).thenReturn(null);

        String result = githubApiClient.extractGithubInfo("https://github.com/testuser");

        assertThat(result).contains("testuser");
    }

    @Test
    @DisplayName("기능_테스트_API_호출에서_예외가_발생해도_예외없이_사용자명이_포함된_결과가_반환된다")
    void 기능_테스트_API_호출에서_예외가_발생해도_예외없이_사용자명이_포함된_결과가_반환된다() {
        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenThrow(new RuntimeException("연결 실패"));

        String result = githubApiClient.extractGithubInfo("https://github.com/testuser");

        assertThat(result).contains("testuser");
    }
}
