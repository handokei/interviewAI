package com.interviewai.backend.client;

import com.interviewai.backend.global.config.GithubApiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class GithubApiClient {

    private final RestClient restClient;
    private final GithubApiProperties githubApiProperties;

    @Autowired
    public GithubApiClient(GithubApiProperties githubApiProperties) {
        this.githubApiProperties = githubApiProperties;
        this.restClient = RestClient.builder()
                .baseUrl(githubApiProperties.getBaseUrl())
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .build();
    }

    GithubApiClient(GithubApiProperties githubApiProperties, RestClient restClient) {
        this.githubApiProperties = githubApiProperties;
        this.restClient = restClient;
    }

    public String extractGithubInfo(String githubUrl) {
        String username = extractUsername(githubUrl);
        if (username == null) {
            return "GitHub URL에서 사용자명을 추출할 수 없습니다.";
        }

        StringBuilder result = new StringBuilder();
        result.append("GitHub 사용자: ").append(username).append("\n\n");

        try {
            appendUserInfo(result, username);
            appendRepositories(result, username);
        } catch (Exception e) {
            log.warn("GitHub API 호출 실패: {}", e.getMessage());
            result.append("GitHub 정보를 불러오는데 실패했습니다.");
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
                result.append("이름: ").append(userInfo.get("name")).append("\n");
                result.append("바이오: ").append(userInfo.get("bio")).append("\n");
                result.append("공개 레포: ").append(userInfo.get("public_repos")).append("개\n\n");
            }
        } catch (Exception e) {
            log.warn("GitHub 사용자 정보 조회 실패: {}", e.getMessage());
        }
    }

    private void appendRepositories(StringBuilder result, String username) {
        try {
            List<?> repos = restClient.get()
                    .uri("/users/{username}/repos?sort=updated&per_page={maxRepos}", username,
                            githubApiProperties.getMaxRepos())
                    .retrieve()
                    .body(List.class);

            if (repos != null && !repos.isEmpty()) {
                result.append("주요 레포지토리:\n");

                Map<String, CompletableFuture<String>> readmeFutures = new LinkedHashMap<>();
                for (Object repoObj : repos) {
                    if (repoObj instanceof Map<?, ?> repo) {
                        String repoName = (String) repo.get("name");
                        result.append("- ").append(repoName);
                        if (repo.get("description") != null) {
                            result.append(": ").append(repo.get("description"));
                        }
                        result.append(" (⭐").append(repo.get("stargazers_count")).append(")\n");
                        if (!Boolean.TRUE.equals(repo.get("fork"))) {
                            readmeFutures.put(repoName, CompletableFuture.supplyAsync(
                                    () -> fetchReadme(username, repoName)
                            ));
                        }
                    }
                }

                CompletableFuture.allOf(readmeFutures.values().toArray(new CompletableFuture[0]))
                        .orTimeout(30, TimeUnit.SECONDS)
                        .join();

                for (Map.Entry<String, CompletableFuture<String>> entry : readmeFutures.entrySet()) {
                    String readme = entry.getValue().join();
                    if (readme != null) {
                        result.append("  README: ").append(readme).append("\n");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("GitHub 레포지토리 조회 실패: {}", e.getMessage());
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
