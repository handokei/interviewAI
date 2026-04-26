package com.interviewai.backend.interview.service;

import com.interviewai.backend.client.ClaudeAiClient;
import com.interviewai.backend.client.dto.ChatMessage;
import com.interviewai.backend.global.config.InterviewProperties;
import com.interviewai.backend.interview.enums.AnswerLevel;
import com.interviewai.backend.interview.enums.InterviewLevel;
import com.interviewai.backend.interview.model.InterviewMessage;
import com.interviewai.backend.interview.model.InterviewSession;
import com.interviewai.backend.interview.repository.InterviewMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewEvaluationService {

    private final InterviewMessageRepository interviewMessageRepository;
    private final ClaudeAiClient claudeAiClient;
    private final TokenEstimator tokenEstimator;
    private final InterviewProperties interviewProperties;

    @Async("evalExecutor")
    @Transactional
    public void evaluateAsync(Long aiMessageId, InterviewSession session,
                              List<ChatMessage> history, String aiResponse) {
        try {
            InterviewEvaluation eval = evaluateWithAi(session, history, aiResponse);
            InterviewMessage aiMessage = interviewMessageRepository.findById(aiMessageId)
                    .orElseThrow(() -> new IllegalStateException("AI message not found: " + aiMessageId));
            aiMessage.updateEvaluation(eval.answerLevel(), eval.suggestFinish(), eval.qualityHint());
        } catch (Exception e) {
            log.error("비동기 AI 평가 실패 (messageId={})", aiMessageId, e);
            interviewMessageRepository.findById(aiMessageId).ifPresent(aiMessage -> {
                InterviewEvaluation fallback = InterviewEvaluation.fallback();
                aiMessage.updateEvaluation(fallback.answerLevel(), fallback.suggestFinish(), fallback.qualityHint());
            });
        }
    }

    InterviewEvaluation evaluateWithAi(InterviewSession session, List<ChatMessage> history, String aiResponse) {
        String evalPrompt = buildEvalPrompt(session, history, aiResponse);
        String evalJson = claudeAiClient.chat(evalPrompt, List.of(), "위 지시에 따라 JSON으로만 응답해주세요.");
        return parseEvaluation(evalJson);
    }

    private String buildEvalPrompt(InterviewSession session, List<ChatMessage> history, String aiResponse) {
        List<ChatMessage> trimmedHistory = tokenEstimator.trimHistory(
                history, interviewProperties.getPrompt().getMaxEvalHistoryTokens());

        StringBuilder prompt = new StringBuilder();
        String levelDesc = session.getLevel() == InterviewLevel.JUNIOR ? "신입 개발자" : "경력 개발자";

        prompt.append("당신은 기술 면접 평가 전문가입니다.\n");
        prompt.append("아래 면접 대화에서 지원자의 마지막 답변을 평가하고, 면접 종료 시점인지 판단해주세요.\n\n");
        prompt.append("면접 레벨: ").append(levelDesc).append("\n");
        if (session.getJobTitle() != null) {
            prompt.append("지원 직무: ").append(session.getJobTitle()).append("\n");
        }
        String summary = session.getConversationSummary();
        if (summary != null && !summary.isBlank()) {
            prompt.append("\n=== 이전 대화 요약 (참고용) ===\n");
            prompt.append(summary).append("\n\n");
        }

        prompt.append("\n=== 면접 대화 ===\n");
        for (ChatMessage msg : trimmedHistory) {
            String role = "assistant".equals(msg.role()) ? "[면접관]" : "[지원자]";
            prompt.append(role).append("\n").append(msg.content()).append("\n\n");
        }
        prompt.append("[면접관]\n").append(aiResponse).append("\n\n");
        prompt.append("""
                위 대화를 바탕으로 다음 JSON 형식으로만 응답하세요:
                {
                  "suggestFinish": false,
                  "answerLevel": "NEEDS_IMPROVEMENT",
                  "qualityHint": "시간복잡도 설명은 좋았으나 공간복잡도 언급이 부족했어요."
                }

                - suggestFinish: 주요 기술 주제가 충분히 다루어졌으면 true, 아직 부족하면 false
                - answerLevel: 지원자 마지막 답변 수준 (반드시 다음 중 하나만 사용)
                  * PASS: 핵심 개념을 정확히 이해하고 설명할 수 있음
                  * NEEDS_IMPROVEMENT: 기본 개념은 알지만 중요한 부분이 빠짐
                  * STUDY_REQUIRED: 개념 이해가 부족하거나 핵심을 놓침
                - qualityHint: 한국어 한 줄 피드백 (지원자 마지막 답변에 대해, 100자 이내)
                """);
        return prompt.toString();
    }

    InterviewEvaluation parseEvaluation(String evalJson) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String jsonStr = evalJson;
            int jsonStart = jsonStr.indexOf('{');
            int jsonEnd = jsonStr.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd >= 0) {
                jsonStr = jsonStr.substring(jsonStart, jsonEnd + 1);
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(jsonStr);
                boolean suggestFinish = node.has("suggestFinish") && node.get("suggestFinish").asBoolean(false);
                String levelStr = node.has("answerLevel") ? node.get("answerLevel").asText() : "NEEDS_IMPROVEMENT";
                AnswerLevel answerLevel;
                try {
                    answerLevel = AnswerLevel.valueOf(levelStr);
                } catch (IllegalArgumentException ex) {
                    answerLevel = AnswerLevel.NEEDS_IMPROVEMENT;
                }
                String qualityHint = node.has("qualityHint") ? node.get("qualityHint").asText() : "답변이 접수되었습니다.";
                return new InterviewEvaluation(suggestFinish, answerLevel, qualityHint);
            }
        } catch (Exception e) {
            log.warn("평가 JSON 파싱 실패, fallback 사용: {}", e.getMessage());
        }
        return InterviewEvaluation.fallback();
    }
}
