package com.interviewai.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    private final InterviewProperties interviewProperties;

    public AsyncConfig(InterviewProperties interviewProperties) {
        this.interviewProperties = interviewProperties;
    }

    @Bean("evalExecutor")
    public Executor evalExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("eval-");
        executor.initialize();
        return executor;
    }

    @Bean("summaryExecutor")
    public Executor summaryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("summary-");
        executor.initialize();
        return executor;
    }

    @Bean("sseStreamingExecutor")
    public Executor sseStreamingExecutor() {
        InterviewProperties.Pool pool = interviewProperties.getSse().getPool();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(pool.getCoreSize());
        executor.setMaxPoolSize(pool.getMaxSize());
        executor.setQueueCapacity(pool.getQueueCapacity());
        executor.setThreadNamePrefix("sse-stream-");
        executor.initialize();
        return executor;
    }
}
