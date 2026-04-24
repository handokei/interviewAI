package com.interviewai.backend.client;

import com.interviewai.backend.global.config.GithubApiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

    // -----------------------------------------------------------------------
    // fetchReadme 단위 테스트
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("기능_테스트_README가_존재하면_디코딩된_내용이_반환된다")
    void 기능_테스트_README가_존재하면_디코딩된_내용이_반환된다() {
        String rawContent = "Hello README";
        String encoded = Base64.getMimeEncoder().encodeToString(rawContent.getBytes());

        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(Class.class))).thenReturn(Map.of("content", encoded));

        String result = githubApiClient.fetchReadme("testuser", "testrepo");

        assertThat(result).isEqualTo("Hello README");
    }

    @Test
    @DisplayName("기능_테스트_README가_없으면_null이_반환된다")
    void 기능_테스트_README가_없으면_null이_반환된다() {
        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenThrow(new RuntimeException("404 Not Found"));

        String result = githubApiClient.fetchReadme("testuser", "norepo");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("기능_테스트_README_content_키가_없으면_null이_반환된다")
    void 기능_테스트_README_content_키가_없으면_null이_반환된다() {
        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(Class.class))).thenReturn(Map.of("type", "file"));

        String result = githubApiClient.fetchReadme("testuser", "testrepo");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("기능_테스트_API가_null을_반환하면_null이_반환된다")
    void 기능_테스트_API가_null을_반환하면_null이_반환된다() {
        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(Class.class))).thenReturn(null);

        String result = githubApiClient.fetchReadme("testuser", "testrepo");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("기능_테스트_README_내용이_maxReadmeLength를_초과하면_잘라내고_말줄임표가_붙는다")
    void 기능_테스트_README_내용이_maxReadmeLength를_초과하면_잘라내고_말줄임표가_붙는다() {
        // maxReadmeLength = 500 (setUp)
        String longContent = "A".repeat(600);
        String encoded = Base64.getMimeEncoder().encodeToString(longContent.getBytes());

        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(Class.class))).thenReturn(Map.of("content", encoded));

        String result = githubApiClient.fetchReadme("testuser", "testrepo");

        assertThat(result).endsWith("...");
        assertThat(result).hasSize(503); // 500 chars + "..."
    }

    @Test
    @DisplayName("기능_테스트_README_내용이_maxReadmeLength_이하이면_잘라내지_않는다")
    void 기능_테스트_README_내용이_maxReadmeLength_이하이면_잘라내지_않는다() {
        String shortContent = "B".repeat(500);
        String encoded = Base64.getMimeEncoder().encodeToString(shortContent.getBytes());

        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(Class.class))).thenReturn(Map.of("content", encoded));

        String result = githubApiClient.fetchReadme("testuser", "testrepo");

        assertThat(result).doesNotEndWith("...");
        assertThat(result).hasSize(500);
    }

    // -----------------------------------------------------------------------
    // appendRepositories — 병렬 README 수집 통합 테스트
    // -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private RestClient.RequestHeadersUriSpec<?> setupMockChain(
            RestClient.RequestHeadersSpec<?> headersSpec,
            RestClient.ResponseSpec responseSpec) {
        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        return uriSpec;
    }

    @Test
    @DisplayName("기능_테스트_fork가_아닌_레포는_README가_병렬로_수집되어_결과에_포함된다")
    void 기능_테스트_fork가_아닌_레포는_README가_병렬로_수집되어_결과에_포함된다() {
        // user-info response
        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);

        Map<String, Object> userInfo = Map.of(
                "name", "Test User",
                "bio", "Developer",
                "public_repos", 5
        );
        String encoded = Base64.getMimeEncoder().encodeToString("My README content".getBytes());
        Map<String, Object> readmeResponse = Map.of("content", encoded);

        Map<String, Object> repo = Map.of(
                "name", "my-repo",
                "description", "A test repo",
                "stargazers_count", 10,
                "fork", false
        );

        // First call: user info, second call: repo list, third call: readme
        when(responseSpec.body(any(Class.class)))
                .thenReturn(userInfo)
                .thenReturn(List.of(repo))
                .thenReturn(readmeResponse);

        String result = githubApiClient.extractGithubInfo("https://github.com/testuser");

        assertThat(result).contains("my-repo");
        assertThat(result).contains("README");
        assertThat(result).contains("My README content");
    }

    @Test
    @DisplayName("기능_테스트_fork_레포는_README를_수집하지_않는다")
    void 기능_테스트_fork_레포는_README를_수집하지_않는다() {
        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);

        Map<String, Object> userInfo = Map.of(
                "name", "Test User",
                "bio", "Developer",
                "public_repos", 1
        );
        Map<String, Object> forkRepo = Map.of(
                "name", "forked-repo",
                "stargazers_count", 0,
                "fork", true
        );

        // First call: user info, second call: repo list — README must NOT be fetched
        when(responseSpec.body(any(Class.class)))
                .thenReturn(userInfo)
                .thenReturn(List.of(forkRepo));

        String result = githubApiClient.extractGithubInfo("https://github.com/testuser");

        assertThat(result).contains("forked-repo");
        // README section should not appear for fork repos
        assertThat(result).doesNotContain("README");
    }

    @Test
    @DisplayName("기능_테스트_레포_목록이_비어있으면_README_수집을_시도하지_않는다")
    void 기능_테스트_레포_목록이_비어있으면_README_수집을_시도하지_않는다() {
        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);

        Map<String, Object> userInfo = Map.of(
                "name", "Test User",
                "bio", "Developer",
                "public_repos", 0
        );

        // First call: user info, second call: empty repo list
        when(responseSpec.body(any(Class.class)))
                .thenReturn(userInfo)
                .thenReturn(List.of());

        String result = githubApiClient.extractGithubInfo("https://github.com/testuser");

        assertThat(result).contains("testuser");
        assertThat(result).doesNotContain("주요 레포지토리");
    }

    @Test
    @DisplayName("기능_테스트_레포_목록이_null이면_README_수집을_시도하지_않는다")
    void 기능_테스트_레포_목록이_null이면_README_수집을_시도하지_않는다() {
        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);

        Map<String, Object> userInfo = Map.of(
                "name", "Test User",
                "bio", "Developer",
                "public_repos", 0
        );

        // First call: user info, second call: null repo list
        when(responseSpec.body(any(Class.class)))
                .thenReturn(userInfo)
                .thenReturn(null);

        String result = githubApiClient.extractGithubInfo("https://github.com/testuser");

        assertThat(result).contains("testuser");
        assertThat(result).doesNotContain("주요 레포지토리");
    }

    @Test
    @DisplayName("기능_테스트_README가_없는_non_fork_레포는_결과에_README_항목이_추가되지_않는다")
    void 기능_테스트_README가_없는_non_fork_레포는_결과에_README_항목이_추가되지_않는다() {
        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);

        Map<String, Object> userInfo = Map.of(
                "name", "Test User",
                "bio", "Developer",
                "public_repos", 1
        );
        Map<String, Object> repo = Map.of(
                "name", "no-readme-repo",
                "stargazers_count", 2,
                "fork", false
        );

        // user info → repo list → null (no README)
        when(responseSpec.body(any(Class.class)))
                .thenReturn(userInfo)
                .thenReturn(List.of(repo))
                .thenReturn(null);

        String result = githubApiClient.extractGithubInfo("https://github.com/testuser");

        assertThat(result).contains("no-readme-repo");
        assertThat(result).doesNotContain("README");
    }

    @Test
    @DisplayName("기능_테스트_레포_목록에_Map이_아닌_원소가_있으면_해당_원소를_건너뛴다")
    void 기능_테스트_레포_목록에_Map이_아닌_원소가_있으면_해당_원소를_건너뛴다() {
        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);

        Map<String, Object> userInfo = Map.of(
                "name", "Test User",
                "bio", "Developer",
                "public_repos", 1
        );

        // Raw list that includes a non-Map element (String)
        List<Object> mixedRepos = new java.util.ArrayList<>();
        mixedRepos.add("not-a-map");

        when(responseSpec.body(any(Class.class)))
                .thenReturn(userInfo)
                .thenReturn(mixedRepos);

        String result = githubApiClient.extractGithubInfo("https://github.com/testuser");

        // Header is appended before the loop, but no repo lines are added
        assertThat(result).contains("testuser");
    }

    @Test
    @DisplayName("기능_테스트_github_com이_마지막_토큰인_URL이면_사용자명_추출_불가_메시지가_반환된다")
    void 기능_테스트_github_com이_마지막_토큰인_URL이면_사용자명_추출_불가_메시지가_반환된다() {
        // URL like "https://github.com" — github.com is at parts[2], parts[3] doesn't exist
        String result = githubApiClient.extractGithubInfo("https://github.com");

        assertThat(result).contains("사용자명을 추출할 수 없습니다");
    }
}
