package com.interviewai.backend.interview.service;

import com.interviewai.backend.client.dto.ChatMessage;
import com.interviewai.backend.global.config.InterviewProperties;
import com.interviewai.backend.interview.enums.AnswerLevel;
import com.interviewai.backend.interview.enums.MessageRole;
import com.interviewai.backend.interview.model.InterviewMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
public class TokenEstimator {

    private final int charsPerToken;

    public TokenEstimator(InterviewProperties interviewProperties) {
        this.charsPerToken = interviewProperties.getPrompt().getCharsPerToken();
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

        log.debug("히스토리 trimming: {} → {} 메시지 ({}→{} 추정 토큰)",
                history.size(), result.size(), totalTokens, firstTokens + accumulated);
        return result;
    }

    public List<ChatMessage> trimHistoryWithPriority(List<InterviewMessage> messages, int maxTokens) {
        if (messages.isEmpty()) return List.of();

        int totalTokens = messages.stream()
                .mapToInt(m -> estimateTokens(m.getContent()))
                .sum();
        if (totalTokens <= maxTokens) return toChatMessages(messages);

        InterviewMessage first = messages.get(0);
        int firstTokens = estimateTokens(first.getContent());
        int remainingBudget = maxTokens - firstTokens;

        if (remainingBudget <= 0) return toChatMessages(List.of(first));

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

        long priorityKept = priority.stream().filter(included::contains).count();
        log.debug("중요도 기반 trimming: {} → {} 메시지 (STUDY_REQUIRED {} 보존)",
                messages.size(), result.size(), priorityKept);
        return toChatMessages(result);
    }

    public List<ChatMessage> toChatMessages(List<InterviewMessage> messages) {
        return messages.stream()
                .map(msg -> new ChatMessage(
                        msg.getRole() == MessageRole.AI ? "assistant" : "user",
                        msg.getContent()))
                .toList();
    }
}
