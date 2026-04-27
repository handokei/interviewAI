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
        lenient().when(githubApiProperties.getMaxLanguages()).thenReturn(5);
        lenient().when(githubApiProperties.getParallelTimeoutSeconds()).thenReturn(30);
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

        String result = githubApiClient.fetchReadme("testuser", "testrepo", restClient);

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

        String result = githubApiClient.fetchReadme("testuser", "norepo", restClient);

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

        String result = githubApiClient.fetchReadme("testuser", "testrepo", restClient);

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

        String result = githubApiClient.fetchReadme("testuser", "testrepo", restClient);

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

        String result = githubApiClient.fetchReadme("testuser", "testrepo", restClient);

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

        String result = githubApiClient.fetchReadme("testuser", "testrepo", restClient);

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
        String result = githubApiClient.extractGithubInfo("https://github.com");

        assertThat(result).contains("사용자명을 추출할 수 없습니다");
    }

    // -----------------------------------------------------------------------
    // fetchLanguages 단위 테스트
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("기능_테스트_언어_비율이_정상_반환된다")
    void 기능_테스트_언어_비율이_정상_반환된다() {
        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(Class.class))).thenReturn(Map.of("Java", 50000, "Kotlin", 3000));

        Map<String, Long> result = githubApiClient.fetchLanguages("testuser", "testrepo", restClient);

        assertThat(result).containsEntry("Java", 50000L);
        assertThat(result).containsEntry("Kotlin", 3000L);
    }

    @Test
    @DisplayName("기능_테스트_언어_API_실패시_빈_맵이_반환된다")
    void 기능_테스트_언어_API_실패시_빈_맵이_반환된다() {
        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenThrow(new RuntimeException("API 실패"));

        Map<String, Long> result = githubApiClient.fetchLanguages("testuser", "testrepo", restClient);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("기능_테스트_언어_API가_null_반환시_빈_맵이_반환된다")
    void 기능_테스트_언어_API가_null_반환시_빈_맵이_반환된다() {
        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(Class.class))).thenReturn(null);

        Map<String, Long> result = githubApiClient.fetchLanguages("testuser", "testrepo", restClient);

        assertThat(result).isEmpty();
    }

    // -----------------------------------------------------------------------
    // 통합 테스트 — 언어, 토픽, 최근 활동 포함
    // -----------------------------------------------------------------------

    // -----------------------------------------------------------------------
    // appendTopics / appendLanguageBreakdown / appendTechStackSummary / mergeLanguages / formatDate 단위 테스트
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("기능_테스트_토픽이_있으면_결과에_포함된다")
    void 기능_테스트_토픽이_있으면_결과에_포함된다() {
        StringBuilder result = new StringBuilder();
        Map<String, Object> repo = new java.util.HashMap<>();
        repo.put("topics", List.of("spring-boot", "docker", "kubernetes"));

        githubApiClient.appendTopics(result, repo);

        assertThat(result.toString()).contains("토픽: spring-boot, docker, kubernetes");
    }

    @Test
    @DisplayName("기능_테스트_토픽이_없으면_결과에_추가되지_않는다")
    void 기능_테스트_토픽이_없으면_결과에_추가되지_않는다() {
        StringBuilder result = new StringBuilder();
        Map<String, Object> repo = new java.util.HashMap<>();

        githubApiClient.appendTopics(result, repo);

        assertThat(result.toString()).isEmpty();
    }

    @Test
    @DisplayName("기능_테스트_토픽이_빈_리스트이면_결과에_추가되지_않는다")
    void 기능_테스트_토픽이_빈_리스트이면_결과에_추가되지_않는다() {
        StringBuilder result = new StringBuilder();
        Map<String, Object> repo = Map.of("topics", List.of());

        githubApiClient.appendTopics(result, repo);

        assertThat(result.toString()).isEmpty();
    }

    @Test
    @DisplayName("기능_테스트_언어_비율이_퍼센트로_표시된다")
    void 기능_테스트_언어_비율이_퍼센트로_표시된다() {
        StringBuilder result = new StringBuilder();
        Map<String, Long> languages = new java.util.LinkedHashMap<>();
        languages.put("Java", 70000L);
        languages.put("Kotlin", 30000L);

        githubApiClient.appendLanguageBreakdown(result, languages);

        assertThat(result.toString()).contains("언어: Java 70%, Kotlin 30%");
    }

    @Test
    @DisplayName("기능_테스트_언어_합계가_0이면_결과에_추가되지_않는다")
    void 기능_테스트_언어_합계가_0이면_결과에_추가되지_않는다() {
        StringBuilder result = new StringBuilder();
        Map<String, Long> languages = Map.of("Java", 0L);

        githubApiClient.appendLanguageBreakdown(result, languages);

        assertThat(result.toString()).isEmpty();
    }

    @Test
    @DisplayName("기능_테스트_기술스택_요약이_레포_섹션_앞에_삽입된다")
    void 기능_테스트_기술스택_요약이_레포_섹션_앞에_삽입된다() {
        StringBuilder result = new StringBuilder("주요 레포지토리:\n- repo1\n");
        Map<String, Long> totalLanguages = new java.util.LinkedHashMap<>();
        totalLanguages.put("Java", 80000L);
        totalLanguages.put("Python", 20000L);

        githubApiClient.appendTechStackSummary(result, totalLanguages);

        String output = result.toString();
        assertThat(output).contains("주요 기술스택: Java (80%), Python (20%)");
        assertThat(output.indexOf("주요 기술스택")).isLessThan(output.indexOf("주요 레포지토리"));
    }

    @Test
    @DisplayName("기능_테스트_빈_언어_맵이면_기술스택_요약이_추가되지_않는다")
    void 기능_테스트_빈_언어_맵이면_기술스택_요약이_추가되지_않는다() {
        StringBuilder result = new StringBuilder("주요 레포지토리:\n");
        Map<String, Long> empty = Map.of();

        githubApiClient.appendTechStackSummary(result, empty);

        assertThat(result.toString()).doesNotContain("주요 기술스택");
    }

    @Test
    @DisplayName("기능_테스트_언어_병합이_정상_동작한다")
    void 기능_테스트_언어_병합이_정상_동작한다() {
        Map<String, Long> total = new java.util.LinkedHashMap<>();
        total.put("Java", 50000L);

        githubApiClient.mergeLanguages(total, Map.of("Java", 30000L, "Kotlin", 10000L));

        assertThat(total).containsEntry("Java", 80000L);
        assertThat(total).containsEntry("Kotlin", 10000L);
    }

    @Test
    @DisplayName("기능_테스트_ISO_날짜가_포맷된다")
    void 기능_테스트_ISO_날짜가_포맷된다() {
        assertThat(githubApiClient.formatDate("2026-04-25T10:00:00Z")).isEqualTo("2026-04-25");
    }

    @Test
    @DisplayName("기능_테스트_null_날짜는_null_반환한다")
    void 기능_테스트_null_날짜는_null_반환한다() {
        assertThat(githubApiClient.formatDate(null)).isNull();
    }

    @Test
    @DisplayName("기능_테스트_잘못된_날짜는_null_반환한다")
    void 기능_테스트_잘못된_날짜는_null_반환한다() {
        assertThat(githubApiClient.formatDate("invalid")).isNull();
    }

    @Test
    @DisplayName("기능_테스트_레포에_주_언어와_최근_활동_날짜가_포맷된다")
    void 기능_테스트_레포에_주_언어와_최근_활동_날짜가_포맷된다() {
        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);

        Map<String, Object> userInfo = Map.of(
                "name", "Test User", "bio", "Developer", "public_repos", 1);

        Map<String, Object> repo = new java.util.HashMap<>();
        repo.put("name", "my-project");
        repo.put("description", "Spring Boot 프로젝트");
        repo.put("stargazers_count", 5);
        repo.put("fork", true);
        repo.put("language", "Java");
        repo.put("pushed_at", "2026-04-25T10:00:00Z");

        // fork=true이므로 README/Language 병렬 호출 없음 → 순차 mock 가능
        when(responseSpec.body(any(Class.class)))
                .thenReturn(userInfo)
                .thenReturn(List.of(repo));

        String result = githubApiClient.extractGithubInfo("https://github.com/testuser");

        assertThat(result).contains("[Java]");
        assertThat(result).contains("마지막 활동: 2026-04-25");
        assertThat(result).contains("설명: Spring Boot 프로젝트");
    }
}
