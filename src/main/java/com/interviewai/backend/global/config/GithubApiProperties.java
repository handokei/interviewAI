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
}
