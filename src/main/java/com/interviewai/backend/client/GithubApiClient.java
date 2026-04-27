package com.interviewai.backend.client;

import com.interviewai.backend.global.config.GithubApiProperties;
import lombok.extern.slf4j.Slf4j;
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
import java.util.stream.Collectors;

@Slf4j
@Component
public class GithubApiClient {

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
        this.githubExecutor = Executors.newFixedThreadPool(4);
    }

    GithubApiClient(GithubApiProperties githubApiProperties, RestClient restClient) {
        this.githubApiProperties = githubApiProperties;
        this.restClient = restClient;
        this.githubExecutor = Executors.newFixedThreadPool(4);
    }

    public String extractGithubInfo(String githubUrl) {
        String username = extractUsername(githubUrl);
        if (username == null) {
            return MSG_USERNAME_EXTRACT_FAILED;
        }

        StringBuilder result = new StringBuilder();
        result.append(LABEL_USERNAME).append(username).append("\n\n");

        try {
            appendUserInfo(result, username);
            Map<String, Long> totalLanguages = new LinkedHashMap<>();
            appendRepositories(result, username, totalLanguages);
            appendTechStackSummary(result, totalLanguages);
        } catch (Exception e) {
            log.warn("GitHub API 호출 실패: {}", e.getMessage());
            result.append(MSG_API_FAILED);
        }

        return result.toString();
    }

    private void appendUserInfo(StringBuilder result, String username) {
        try {
            Map<?, ?> userInfo = restClient.get()
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

    private void appendRepositories(StringBuilder result, String username, Map<String, Long> totalLanguages) {
        try {
            List<?> repos = restClient.get()
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
                                () -> fetchReadme(username, repoName), githubExecutor));
                        languageFutures.put(repoName, CompletableFuture.supplyAsync(
                                () -> fetchLanguages(username, repoName), githubExecutor));
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

    Map<String, Long> fetchLanguages(String username, String repoName) {
        try {
            Map<?, ?> raw = restClient.get()
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

    String fetchReadme(String username, String repoName) {
        try {
            Map<?, ?> readme = restClient.get()
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
}
