package com.interviewai.backend.client;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JobCrawlerClient {

    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_TEXT_LENGTH = 3000;

    public String crawl(String url) {
        try {
            Document document = Jsoup.connect(url)
                    .timeout(TIMEOUT_MS)
                    .userAgent("Mozilla/5.0 (compatible; InterviewAI/1.0)")
                    .get();

            String text = document.body().text();
            if (text.length() > MAX_TEXT_LENGTH) {
                text = text.substring(0, MAX_TEXT_LENGTH) + "...";
            }
            log.info("Job posting crawled from: {}, length: {}", url, text.length());
            return text;
        } catch (Exception e) {
            log.warn("채용공고 크롤링 실패 - URL: {}, 이유: {}", url, e.getMessage());
            return null;
        }
    }
}
