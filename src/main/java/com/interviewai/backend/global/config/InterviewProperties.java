package com.interviewai.backend.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "interview")
public class InterviewProperties {

    private Prompt prompt = new Prompt();
    private Sse sse = new Sse();

    @Getter
    @Setter
    public static class Prompt {
        private int maxDocumentLength = 5000;
        private int maxJobPostingLength = 1500;
        private int maxHistoryTokens = 8000;
        private int maxEvalHistoryTokens = 4000;
        private int maxFeedbackTokens = 30000;
        private int charsPerToken = 3;
        private int maxSummaryTokens = 500;
        private boolean summaryEnabled = true;
    }

    @Getter
    @Setter
    public static class Sse {
        private long timeoutMs = 120_000L;
    }
}
