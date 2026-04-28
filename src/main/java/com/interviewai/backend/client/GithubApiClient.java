package com.interviewai.backend.client;

import com.interviewai.backend.global.config.GithubApiProperties;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GithubApiClient {

    private static final Set<String> IMPORTANT_FILES = Set.of(
            "docker-compose.yml", "dockerfile", "build.gradle", "pom.xml",
            "package.json", "tsconfig.json", "makefile", ".github",
            "cargo.toml", "go.mod", "requirements.txt", "pyproject.toml"
    );

    private static final String GITHUB_ACCEPT_HEADER = "application/vnd.github.v3+json";

    private static final String LABEL_USERNAME = "GitHub 사용자: ";
    private static final String LABEL_NAME = "이름: ";
    private static final String LABEL_BIO = "바이오: ";
    private static final String LABEL_PUBLIC_REPOS = "공개 레포: ";
    private static final String LABEL_REPOS_SECTION = "주요 레포지토리:\n";
    private static final String LABEL_TECH_STACK = "주요 기술스택: ";
    private static final String LABEL_DESCRIPTION = "  설명: ";
    private static final String LABEL_TOPICS = "  토픽: ";
    private static final String LABEL_LANGUAGES = "  언어: ";
    private static final String LABEL_LAST_ACTIVITY = " — 마지막 활동: ";
    private static final String LABEL_README = "  README: ";

    private static final String MSG_USERNAME_EXTRACT_FAILED = "GitHub URL에서 사용자명을 추출할 수 없습니다.";
    private static final String MSG_API_FAILED = "GitHub 정보를 불러오는데 실패했습니다.";

    private final RestClient restClient;
    private final GithubApiProperties githubApiProperties;
    private final ExecutorService githubExecutor;

    @Autowired
    public GithubApiClient(GithubApiProperties githubApiProperties) {
        this.githubApiProperties = githubApiProperties;
        this.restClient = RestClient.builder()
                .baseUrl(githubApiProperties.getBaseUrl())
                .defaultHeader("Accept", GITHUB_ACCEPT_HEADER)
                .build();
        this.githubExecutor = Executors.newFixedThreadPool(githubApiProperties.getThreadPoolSize());
    }

    GithubApiClient(GithubApiProperties githubApiProperties, RestClient restClient) {
        this.githubApiProperties = githubApiProperties;
        this.restClient = restClient;
        this.githubExecutor = Executors.newFixedThreadPool(githubApiProperties.getThreadPoolSize());
    }

    @PreDestroy
    public void destroy() {
        githubExecutor.shutdown();
    }

    @Deprecated
    public String extractGithubInfo(String githubUrl) {
        return extractGithubInfo(githubUrl, null);
    }

    @Deprecated
    public String extractGithubInfo(String githubUrl, String accessToken) {
        String username = extractUsername(githubUrl);
        if (username == null) {
            return MSG_USERNAME_EXTRACT_FAILED;
        }

        RestClient client = buildClient(accessToken);

        StringBuilder result = new StringBuilder();
        result.append(LABEL_USERNAME).append(username).append("\n\n");

        try {
            appendUserInfo(result, username, client);
            Map<String, Long> totalLanguages = new LinkedHashMap<>();
            appendRepositories(result, username, totalLanguages, client);
            appendTechStackSummary(result, totalLanguages);
        } catch (Exception e) {
            log.warn("GitHub API 호출 실패: {}", e.getMessage());
            result.append(MSG_API_FAILED);
        }

        return result.toString();
    }

    RestClient buildClient(String accessToken) {
        if (accessToken == null) {
            return restClient;
        }
        return RestClient.builder()
                .baseUrl(githubApiProperties.getBaseUrl())
                .defaultHeader("Accept", GITHUB_ACCEPT_HEADER)
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .build();
    }

    private void appendUserInfo(StringBuilder result, String username, RestClient client) {
        try {
            Map<?, ?> userInfo = client.get()
                    .uri("/users/{username}", username)
                    .retrieve()
                    .body(Map.class);

            if (userInfo != null) {
                result.append(LABEL_NAME).append(userInfo.get("name")).append("\n");
                result.append(LABEL_BIO).append(userInfo.get("bio")).append("\n");
                result.append(LABEL_PUBLIC_REPOS).append(userInfo.get("public_repos")).append("개\n\n");
            }
        } catch (Exception e) {
            log.warn("GitHub 사용자 정보 조회 실패: {}", e.getMessage());
        }
    }

    private void appendRepositories(StringBuilder result, String username, Map<String, Long> totalLanguages, RestClient client) {
        try {
            List<?> repos = client.get()
                    .uri("/users/{username}/repos?sort=updated&per_page={maxRepos}", username,
                            githubApiProperties.getMaxRepos())
                    .retrieve()
                    .body(List.class);

            if (repos == null || repos.isEmpty()) return;

            result.append(LABEL_REPOS_SECTION);

            Map<String, CompletableFuture<String>> readmeFutures = new LinkedHashMap<>();
            Map<String, CompletableFuture<Map<String, Long>>> languageFutures = new LinkedHashMap<>();
            List<Map<?, ?>> repoList = new ArrayList<>();

            int timeoutSeconds = githubApiProperties.getParallelTimeoutSeconds();

            for (Object repoObj : repos) {
                if (repoObj instanceof Map<?, ?> repo) {
                    repoList.add(repo);
                    String repoName = (String) repo.get("name");
                    if (!Boolean.TRUE.equals(repo.get("fork"))) {
                        readmeFutures.put(repoName, CompletableFuture.supplyAsync(
                                () -> fetchReadme(username, repoName, client), githubExecutor));
                        languageFutures.put(repoName, CompletableFuture.supplyAsync(
                                () -> fetchLanguages(username, repoName, client), githubExecutor));
                    }
                }
            }

            CompletableFuture.allOf(
                    readmeFutures.values().toArray(new CompletableFuture[0])
            ).orTimeout(timeoutSeconds, TimeUnit.SECONDS).exceptionally(e -> null).join();

            CompletableFuture.allOf(
                    languageFutures.values().toArray(new CompletableFuture[0])
            ).orTimeout(timeoutSeconds, TimeUnit.SECONDS).exceptionally(e -> null).join();

            for (Map<?, ?> repo : repoList) {
                String repoName = (String) repo.get("name");
                String language = (String) repo.get("language");
                String pushedAt = formatDate((String) repo.get("pushed_at"));

                result.append("- ").append(repoName);
                if (language != null) {
                    result.append(" [").append(language).append("]");
                }
                result.append(" (⭐").append(repo.get("stargazers_count")).append(")");
                if (pushedAt != null) {
                    result.append(LABEL_LAST_ACTIVITY).append(pushedAt);
                }
                result.append("\n");

                if (repo.get("description") != null) {
                    result.append(LABEL_DESCRIPTION).append(repo.get("description")).append("\n");
                }

                appendTopics(result, repo);

                if (languageFutures.containsKey(repoName)) {
                    try {
                        Map<String, Long> languages = languageFutures.get(repoName).join();
                        if (languages != null && !languages.isEmpty()) {
                            appendLanguageBreakdown(result, languages);
                            mergeLanguages(totalLanguages, languages);
                        }
                    } catch (Exception e) {
                        // 언어 정보 실패 무시
                    }
                }

                if (readmeFutures.containsKey(repoName)) {
                    try {
                        String readme = readmeFutures.get(repoName).join();
                        if (readme != null) {
                            result.append(LABEL_README).append(readme).append("\n");
                        }
                    } catch (Exception e) {
                        // README 실패 무시
                    }
                }
            }
        } catch (Exception e) {
            log.warn("GitHub 레포지토리 조회 실패: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    void appendTopics(StringBuilder result, Map<?, ?> repo) {
        Object topicsObj = repo.get("topics");
        if (topicsObj instanceof List<?> topics && !topics.isEmpty()) {
            result.append(LABEL_TOPICS).append(
                    ((List<String>) topics).stream().collect(Collectors.joining(", "))
            ).append("\n");
        }
    }

    void appendLanguageBreakdown(StringBuilder result, Map<String, Long> languages) {
        long total = languages.values().stream().mapToLong(Long::longValue).sum();
        if (total == 0) return;

        String breakdown = languages.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(githubApiProperties.getMaxLanguages())
                .map(e -> e.getKey() + " " + (e.getValue() * 100 / total) + "%")
                .collect(Collectors.joining(", "));
        result.append(LABEL_LANGUAGES).append(breakdown).append("\n");
    }

    void appendTechStackSummary(StringBuilder result, Map<String, Long> totalLanguages) {
        if (totalLanguages.isEmpty()) return;

        long total = totalLanguages.values().stream().mapToLong(Long::longValue).sum();
        if (total == 0) return;

        String summary = totalLanguages.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(githubApiProperties.getMaxLanguages())
                .map(e -> e.getKey() + " (" + (e.getValue() * 100 / total) + "%)")
                .collect(Collectors.joining(", "));

        result.insert(result.indexOf(LABEL_REPOS_SECTION),
                LABEL_TECH_STACK + summary + "\n\n");
    }

    Map<String, Long> fetchLanguages(String username, String repoName, RestClient client) {
        try {
            Map<?, ?> raw = client.get()
                    .uri("/repos/{username}/{repo}/languages", username, repoName)
                    .retrieve()
                    .body(Map.class);
            if (raw == null) return Map.of();
            Map<String, Long> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                result.put((String) entry.getKey(), ((Number) entry.getValue()).longValue());
            }
            return result;
        } catch (Exception e) {
            return Map.of();
        }
    }

    String fetchReadme(String username, String repoName, RestClient client) {
        try {
            Map<?, ?> readme = client.get()
                    .uri("/repos/{username}/{repo}/readme", username, repoName)
                    .retrieve()
                    .body(Map.class);

            if (readme != null && readme.get("content") != null) {
                String content = new String(
                        java.util.Base64.getMimeDecoder().decode((String) readme.get("content"))
                );
                if (content.length() > githubApiProperties.getMaxReadmeLength()) {
                    content = content.substring(0, githubApiProperties.getMaxReadmeLength()) + "...";
                }
                return content.replaceAll("\n", " ");
            }
        } catch (Exception e) {
            // README 없는 경우 무시
        }
        return null;
    }

    void mergeLanguages(Map<String, Long> total, Map<String, Long> repoLanguages) {
        repoLanguages.forEach((lang, bytes) -> total.merge(lang, bytes, Long::sum));
    }

    String formatDate(String isoDate) {
        if (isoDate == null) return null;
        try {
            return LocalDate.parse(isoDate.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE).toString();
        } catch (Exception e) {
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // 심화 분석 — 레포 URL 직접 입력 기반
    // -----------------------------------------------------------------------

    private static final String LABEL_BRANCHES = "  브랜치: ";
    private static final String LABEL_RECENT_COMMITS = "  최근 커밋:\n";
    private static final String LABEL_STRUCTURE = "  구조: ";
    private static final String LABEL_PRS = "  PR: ";
    private static final String LABEL_ISSUES = "  이슈: ";

    public String extractRepoAnalysis(List<String> repoUrls, String accessToken) {
        if (repoUrls == null || repoUrls.isEmpty()) {
            return "";
        }

        int maxRepos = githubApiProperties.getMaxDetailRepos();
        if (repoUrls.size() > maxRepos) {
            repoUrls = repoUrls.subList(0, maxRepos);
        }

        RestClient client = buildClient(accessToken);
        String username = extractOwner(repoUrls.get(0));

        StringBuilder result = new StringBuilder();

        if (username != null) {
            result.append(LABEL_USERNAME).append(username).append("\n\n");
            appendUserInfo(result, username, client);
        }

        result.append("분석 대상 레포지토리:\n");

        int timeoutSeconds = githubApiProperties.getParallelTimeoutSeconds();

        Map<String, CompletableFuture<String>> detailFutures = new LinkedHashMap<>();
        for (String url : repoUrls) {
            String[] ownerRepo = extractOwnerAndRepo(url);
            if (ownerRepo == null) continue;
            String owner = ownerRepo[0];
            String repo = ownerRepo[1];

            detailFutures.put(repo, CompletableFuture.supplyAsync(
                    () -> buildRepoDetail(owner, repo, client), githubExecutor));
        }

        CompletableFuture.allOf(detailFutures.values().toArray(new CompletableFuture[0]))
                .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .exceptionally(e -> null)
                .join();

        for (Map.Entry<String, CompletableFuture<String>> entry : detailFutures.entrySet()) {
            try {
                String detail = entry.getValue().join();
                if (detail != null) {
                    result.append(detail);
                }
            } catch (Exception e) {
                result.append("- ").append(entry.getKey()).append(": 분석 실패\n");
            }
        }

        return result.toString();
    }

    private String buildRepoDetail(String owner, String repo, RestClient client) {
        StringBuilder detail = new StringBuilder();

        try {
            Map<?, ?> repoInfo = client.get()
                    .uri("/repos/{owner}/{repo}", owner, repo)
                    .retrieve()
                    .body(Map.class);

            if (repoInfo == null) return "- " + repo + ": 접근 불가\n";

            String language = (String) repoInfo.get("language");
            String pushedAt = formatDate((String) repoInfo.get("pushed_at"));

            detail.append("- ").append(repo);
            if (language != null) detail.append(" [").append(language).append("]");
            detail.append(" (⭐").append(repoInfo.get("stargazers_count")).append(")");
            if (pushedAt != null) detail.append(LABEL_LAST_ACTIVITY).append(pushedAt);
            detail.append("\n");

            if (repoInfo.get("description") != null) {
                detail.append(LABEL_DESCRIPTION).append(repoInfo.get("description")).append("\n");
            }

            appendTopics(detail, repoInfo);

            Map<String, Long> languages = fetchLanguages(owner, repo, client);
            if (!languages.isEmpty()) {
                appendLanguageBreakdown(detail, languages);
            }

            appendBranches(detail, owner, repo, client);
            appendRecentCommits(detail, owner, repo, client);
            appendDirectoryStructure(detail, owner, repo, client, repoInfo);
            appendPullRequests(detail, owner, repo, client);
            appendIssues(detail, owner, repo, client);

            String readme = fetchReadme(owner, repo, client);
            if (readme != null) {
                detail.append(LABEL_README).append(readme).append("\n");
            }

        } catch (Exception e) {
            log.warn("GitHub 레포 분석 실패 [{}/{}]: {}", owner, repo, e.getMessage());
            detail.append("- ").append(repo).append(": 접근 불가 (private 레포이거나 존재하지 않는 레포)\n");
        }

        return detail.toString();
    }

    void appendBranches(StringBuilder result, String owner, String repo, RestClient client) {
        try {
            List<?> branches = client.get()
                    .uri("/repos/{owner}/{repo}/branches?per_page={max}", owner, repo,
                            githubApiProperties.getMaxBranches())
                    .retrieve()
                    .body(List.class);

            if (branches == null || branches.isEmpty()) return;

            List<String> branchNames = new ArrayList<>();
            for (Object b : branches) {
                if (b instanceof Map<?, ?> branch) {
                    branchNames.add((String) branch.get("name"));
                }
            }

            int displayCount = Math.min(branchNames.size(), githubApiProperties.getMaxBranchDisplay());
            String display = branchNames.subList(0, displayCount).stream()
                    .collect(Collectors.joining(", "));
            if (branchNames.size() > displayCount) {
                display += " 외 " + (branchNames.size() - displayCount) + "개";
            }
            result.append(LABEL_BRANCHES).append(display).append("\n");
        } catch (Exception e) {
            // 브랜치 조회 실패 무시
        }
    }

    void appendRecentCommits(StringBuilder result, String owner, String repo, RestClient client) {
        try {
            List<?> commits = client.get()
                    .uri("/repos/{owner}/{repo}/commits?per_page={max}", owner, repo,
                            githubApiProperties.getMaxCommits())
                    .retrieve()
                    .body(List.class);

            if (commits == null || commits.isEmpty()) return;

            result.append(LABEL_RECENT_COMMITS);
            for (Object c : commits) {
                if (c instanceof Map<?, ?> commit) {
                    Map<?, ?> commitData = (Map<?, ?>) commit.get("commit");
                    if (commitData != null) {
                        String message = (String) commitData.get("message");
                        if (message != null) {
                            String firstLine = message.split("\n")[0];
                            Map<?, ?> author = (Map<?, ?>) commitData.get("author");
                            String date = author != null ? formatDate((String) author.get("date")) : null;
                            result.append("    - ").append(firstLine);
                            if (date != null) result.append(" (").append(date).append(")");
                            result.append("\n");
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 커밋 조회 실패 무시
        }
    }

    void appendDirectoryStructure(StringBuilder result, String owner, String repo,
                                   RestClient client, Map<?, ?> repoInfo) {
        try {
            String defaultBranch = (String) repoInfo.get("default_branch");
            if (defaultBranch == null) defaultBranch = "main";

            Map<?, ?> tree = client.get()
                    .uri("/repos/{owner}/{repo}/git/trees/{branch}?recursive=1", owner, repo, defaultBranch)
                    .retrieve()
                    .body(Map.class);

            if (tree == null || tree.get("tree") == null) return;

            List<?> treeItems = (List<?>) tree.get("tree");
            List<String> topLevel = new ArrayList<>();
            for (Object item : treeItems) {
                if (item instanceof Map<?, ?> treeItem) {
                    String path = (String) treeItem.get("path");
                    String type = (String) treeItem.get("type");
                    if (path != null && !path.contains("/")) {
                        if ("tree".equals(type)) {
                            topLevel.add(path + "/");
                        } else if (path.contains(".") && isImportantFile(path)) {
                            topLevel.add(path);
                        }
                    }
                }
            }

            if (!topLevel.isEmpty()) {
                result.append(LABEL_STRUCTURE).append(String.join(", ", topLevel)).append("\n");
            }
        } catch (Exception e) {
            // 디렉토리 구조 조회 실패 무시
        }
    }

    private boolean isImportantFile(String filename) {
        return IMPORTANT_FILES.contains(filename.toLowerCase());
    }

    void appendPullRequests(StringBuilder result, String owner, String repo, RestClient client) {
        try {
            List<?> prs = client.get()
                    .uri("/repos/{owner}/{repo}/pulls?state=closed&sort=updated&direction=desc&per_page={max}",
                            owner, repo, githubApiProperties.getMaxPullRequests())
                    .retrieve()
                    .body(List.class);

            if (prs == null || prs.isEmpty()) return;

            List<String> prSummaries = new ArrayList<>();
            for (Object p : prs) {
                if (p instanceof Map<?, ?> pr) {
                    String title = (String) pr.get("title");
                    if (title != null) {
                        prSummaries.add(title);
                    }
                }
            }

            if (!prSummaries.isEmpty()) {
                result.append(LABEL_PRS).append(String.join(", ", prSummaries)).append("\n");
            }
        } catch (Exception e) {
            // PR 조회 실패 무시
        }
    }

    void appendIssues(StringBuilder result, String owner, String repo, RestClient client) {
        try {
            List<?> issues = client.get()
                    .uri("/repos/{owner}/{repo}/issues?state=closed&sort=updated&direction=desc&per_page={max}",
                            owner, repo, githubApiProperties.getMaxIssues())
                    .retrieve()
                    .body(List.class);

            if (issues == null || issues.isEmpty()) return;

            List<String> issueSummaries = new ArrayList<>();
            for (Object i : issues) {
                if (i instanceof Map<?, ?> issue) {
                    if (issue.get("pull_request") != null) continue;
                    String title = (String) issue.get("title");
                    if (title != null) {
                        issueSummaries.add(title);
                    }
                }
            }

            if (!issueSummaries.isEmpty()) {
                result.append(LABEL_ISSUES).append(String.join(", ", issueSummaries)).append("\n");
            }
        } catch (Exception e) {
            // 이슈 조회 실패 무시
        }
    }

    private String extractUsername(String githubUrl) {
        if (githubUrl == null) return null;
        githubUrl = githubUrl.trim().replaceAll("/$", "");
        String[] parts = githubUrl.split("/");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals("github.com") && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }
        return null;
    }

    private String extractOwner(String repoUrl) {
        String[] result = extractOwnerAndRepo(repoUrl);
        return result != null ? result[0] : null;
    }

    String[] extractOwnerAndRepo(String repoUrl) {
        if (repoUrl == null) return null;
        repoUrl = repoUrl.trim().replaceAll("/$", "").replaceAll("\\.git$", "");
        String[] parts = repoUrl.split("/");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals("github.com") && i + 2 < parts.length) {
                return new String[]{parts[i + 1], parts[i + 2]};
            }
        }
        return null;
    }
}
