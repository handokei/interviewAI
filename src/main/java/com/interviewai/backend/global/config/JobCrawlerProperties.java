package com.interviewai.backend.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "job-crawler")
public class JobCrawlerProperties {

    private int timeoutMs = 10_000;
    private int maxTextLength = 3000;
    private String userAgent = "Mozilla/5.0 (compatible; InterviewAI/1.0)";
}
