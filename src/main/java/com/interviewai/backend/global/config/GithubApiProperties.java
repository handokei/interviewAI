package com.interviewai.backend.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "github.api")
public class GithubApiProperties {

    private String baseUrl = "https://api.github.com";
    private int maxRepos = 10;
    private int maxReadmeLength = 500;
    private int maxLanguages = 5;
    private int parallelTimeoutSeconds = 30;
    private int maxDetailRepos = 3;
    private int maxCommits = 5;
    private int maxBranches = 30;
    private int maxBranchDisplay = 5;
    private int maxPullRequests = 5;
    private int maxIssues = 5;
    private int threadPoolSize = 4;
}
