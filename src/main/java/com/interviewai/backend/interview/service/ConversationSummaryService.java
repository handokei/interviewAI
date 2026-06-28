package com.interviewai.backend.interview.service;

import com.interviewai.backend.client.ClaudeAiClient;
import com.interviewai.backend.global.config.InterviewProperties;
import com.interviewai.backend.interview.enums.MessageRole;
import com.interviewai.backend.interview.model.InterviewMessage;
import com.interviewai.backend.interview.model.InterviewSession;
import com.interviewai.backend.interview.repository.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationSummaryService {

    private final ClaudeAiClient claudeAiClient;
    private final InterviewSessionRepository interviewSessionRepository;
    private final TokenEstimator tokenEstimator;
    private final InterviewProperties interviewProperties;

    @Async("summaryExecutor")
    @Transactional
    public void generateSummaryAsync(Long sessionId, List<InterviewMessage> trimmedMessages) {
        if (trimmedMessages == null || trimmedMessages.isEmpty()) {
            return;
        }

        try {
            InterviewSession session = interviewSessionRepository.findById(sessionId).orElse(null);
            if (session == null) {
                log.warn("요약 생성 실패: 세션을 찾을 수 없음 (sessionId={})", sessionId);
                return;
            }

            String summary = generateSummary(session.getConversationSummary(), trimmedMessages);
            session.setConversationSummary(summary);
            log.debug("대화 요약 생성 완료 (sessionId={}, 요약 길이={}자)", sessionId, summary.length());
        } catch (Exception e) {
            log.error("비동기 대화 요약 생성 실패 (sessionId={})", sessionId, e);
        }
    }

    String generateSummary(String existingSummary, List<InterviewMessage> trimmedMessages) {
        String summaryPrompt = buildSummaryPrompt(existingSummary, trimmedMessages);
        String summary = claudeAiClient.chat(summaryPrompt, List.of(), "위 지시에 따라 요약만 작성해주세요.");

        int maxSummaryTokens = interviewProperties.getPrompt().getMaxSummaryTokens();
        int maxChars = maxSummaryTokens * interviewProperties.getPrompt().getCharsPerToken();
        if (summary.length() > maxChars) {
            summary = summary.substring(0, maxChars);
        }

        return summary;
    }

    String buildSummaryPrompt(String existingSummary, List<InterviewMessage> trimmedMessages) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("당신은 기술 면접 대화 요약 전문가입니다.\n");
        prompt.append("아래 면접 대화 내용을 요약해주세요. 요약은 다음 정보를 반드시 포함해야 합니다:\n");
        prompt.append("1. 다뤄진 기술 주제와 질문 목록\n");
        prompt.append("2. 지원자의 핵심 답변 내용 (잘한 점과 부족한 점)\n");
        prompt.append("3. 면접 진행 흐름\n\n");
        prompt.append("요약은 간결하게 300자 이내로 작성해주세요.\n\n");

        if (existingSummary != null && !existingSummary.isBlank()) {
            prompt.append("=== 기존 요약 ===\n");
            prompt.append(existingSummary).append("\n\n");
            prompt.append("위 기존 요약에 아래 새로운 대화 내용을 통합하여 누적 요약을 작성해주세요.\n\n");
        }

        prompt.append("=== 새로운 대화 내용 ===\n");
        for (InterviewMessage msg : trimmedMessages) {
            String roleLabel = msg.getRole() == MessageRole.AI ? "[면접관]" : "[지원자]";
            prompt.append(roleLabel).append("\n").append(msg.getContent()).append("\n\n");
        }

        return prompt.toString();
    }
}
