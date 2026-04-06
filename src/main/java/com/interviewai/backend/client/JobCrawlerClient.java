package com.interviewai.backend.client;

import com.interviewai.backend.global.config.JobCrawlerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobCrawlerClient {

    private final JobCrawlerProperties jobCrawlerProperties;

    public String crawl(String url) {
        if (url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            log.warn("허용되지 않는 URL scheme: {}", url);
            return null;
        }
        try {
            Document document = Jsoup.connect(url) // NOSONAR - URL scheme validated above (http/https only)
                    .timeout(jobCrawlerProperties.getTimeoutMs())
                    .userAgent(jobCrawlerProperties.getUserAgent())
                    .get();

            String text = document.body().text();
            if (text.length() > jobCrawlerProperties.getMaxTextLength()) {
                text = text.substring(0, jobCrawlerProperties.getMaxTextLength()) + "...";
            }
            log.info("Job posting crawled from: {}, length: {}", url, text.length());
            return text;
        } catch (Exception e) {
            log.warn("채용공고 크롤링 실패 - URL: {}, 이유: {}", url, e.getMessage());
            return null;
        }
    }
}
