package com.interviewai.backend.interview.service;

import com.interviewai.backend.client.dto.ChatMessage;
import com.interviewai.backend.global.config.InterviewProperties;
import com.interviewai.backend.interview.enums.AnswerLevel;
import com.interviewai.backend.interview.enums.MessageRole;
import com.interviewai.backend.interview.model.InterviewMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class TokenEstimator {

    private final int charsPerToken;
    private final MeterRegistry meterRegistry;
    private final Counter trimCounter;

    public TokenEstimator(InterviewProperties interviewProperties, MeterRegistry meterRegistry) {
        this.charsPerToken = interviewProperties.getPrompt().getCharsPerToken();
        this.meterRegistry = meterRegistry;
        this.trimCounter = Counter.builder("interview.history.trimmed")
                .description("Number of times history trimming was triggered")
                .register(meterRegistry);
    }

    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return Math.max(1, text.length() / charsPerToken);
    }

    public int estimateTokens(List<ChatMessage> messages) {
        return messages.stream()
                .mapToInt(msg -> estimateTokens(msg.content()))
                .sum();
    }

    public List<ChatMessage> trimHistory(List<ChatMessage> history, int maxTokens) {
        if (history.isEmpty()) return history;

        int totalTokens = estimateTokens(history);
        if (totalTokens <= maxTokens) return history;

        ChatMessage first = history.get(0);
        int firstTokens = estimateTokens(first.content());
        int remainingBudget = maxTokens - firstTokens;

        if (remainingBudget <= 0) return List.of(first);

        List<ChatMessage> rest = history.subList(1, history.size());
        List<ChatMessage> included = new ArrayList<>();
        int accumulated = 0;

        for (int i = rest.size() - 1; i >= 0; i--) {
            int tokens = estimateTokens(rest.get(i).content());
            if (accumulated + tokens > remainingBudget) break;
            accumulated += tokens;
            included.add(0, rest.get(i));
        }

        List<ChatMessage> result = new ArrayList<>();
        result.add(first);
        result.addAll(included);

        trimCounter.increment();
        recordMetrics("trimHistory", history.size(), result.size(), firstTokens + accumulated);
        log.debug("히스토리 trimming: {} → {} 메시지 ({}→{} 추정 토큰)",
                history.size(), result.size(), totalTokens, firstTokens + accumulated);
        return result;
    }

    public List<ChatMessage> trimHistoryWithPriority(List<InterviewMessage> messages, int maxTokens) {
        return trimHistoryWithPriorityAndReport(messages, maxTokens).retained();
    }

    public TrimResult trimHistoryWithPriorityAndReport(List<InterviewMessage> messages, int maxTokens) {
        if (messages.isEmpty()) return new TrimResult(List.of(), List.of(), false);

        int totalTokens = messages.stream()
                .mapToInt(m -> estimateTokens(m.getContent()))
                .sum();
        if (totalTokens <= maxTokens) return new TrimResult(toChatMessages(messages), List.of(), false);

        InterviewMessage first = messages.get(0);
        int firstTokens = estimateTokens(first.getContent());
        int remainingBudget = maxTokens - firstTokens;

        if (remainingBudget <= 0) {
            List<InterviewMessage> trimmedMessages = messages.subList(1, messages.size());
            return new TrimResult(toChatMessages(List.of(first)), List.copyOf(trimmedMessages), true);
        }

        List<InterviewMessage> rest = messages.subList(1, messages.size());

        List<InterviewMessage> priority = new ArrayList<>();
        List<InterviewMessage> normal = new ArrayList<>();
        for (InterviewMessage msg : rest) {
            if (msg.getAnswerLevel() == AnswerLevel.STUDY_REQUIRED) {
                priority.add(msg);
            } else {
                normal.add(msg);
            }
        }

        List<InterviewMessage> included = new ArrayList<>();
        int accumulated = 0;

        for (InterviewMessage msg : priority) {
            int tokens = estimateTokens(msg.getContent());
            if (accumulated + tokens > remainingBudget) break;
            accumulated += tokens;
            included.add(msg);
        }

        for (int i = normal.size() - 1; i >= 0; i--) {
            int tokens = estimateTokens(normal.get(i).getContent());
            if (accumulated + tokens > remainingBudget) break;
            accumulated += tokens;
            included.add(normal.get(i));
        }

        included.sort(Comparator.comparing(InterviewMessage::getCreatedAt));

        List<InterviewMessage> result = new ArrayList<>();
        result.add(first);
        result.addAll(included);

        Set<InterviewMessage> retainedSet = new HashSet<>(result);
        List<InterviewMessage> trimmedMessages = messages.stream()
                .filter(m -> !retainedSet.contains(m))
                .toList();

        long priorityKept = priority.stream().filter(included::contains).count();
        trimCounter.increment();
        int trimmedTokens = result.stream().mapToInt(m -> estimateTokens(m.getContent())).sum();
        recordMetrics("trimHistoryWithPriority", messages.size(), result.size(), trimmedTokens);
        log.debug("중요도 기반 trimming: {} → {} 메시지 (STUDY_REQUIRED {} 보존, {} 메시지 요약 대상)",
                messages.size(), result.size(), priorityKept, trimmedMessages.size());
        return new TrimResult(toChatMessages(result), trimmedMessages, true);
    }

    public List<ChatMessage> toChatMessages(List<InterviewMessage> messages) {
        return messages.stream()
                .map(msg -> new ChatMessage(
                        msg.getRole() == MessageRole.AI ? "assistant" : "user",
                        msg.getContent()))
                .toList();
    }

    private void recordMetrics(String method, int originalCount, int trimmedCount, int estimatedTokens) {
        meterRegistry.gauge("interview.history.original.count",
                io.micrometer.core.instrument.Tags.of("method", method), originalCount);
        meterRegistry.gauge("interview.history.trimmed.count",
                io.micrometer.core.instrument.Tags.of("method", method), trimmedCount);
        meterRegistry.gauge("interview.history.estimated.tokens",
                io.micrometer.core.instrument.Tags.of("method", method), estimatedTokens);
    }
}
