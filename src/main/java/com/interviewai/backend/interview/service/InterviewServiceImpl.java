package com.interviewai.backend.interview.service;

import com.interviewai.backend.client.ClaudeAiClient;
import com.interviewai.backend.client.GithubApiClient;
import com.interviewai.backend.client.JobCrawlerClient;
import com.interviewai.backend.client.dto.ChatMessage;
import com.interviewai.backend.common.exception.BusinessException;
import com.interviewai.backend.document.model.UserDocument;
import com.interviewai.backend.document.repository.UserDocumentRepository;
import com.interviewai.backend.interview.controller.dto.*;
import com.interviewai.backend.interview.enums.InterviewErrorCode;
import com.interviewai.backend.interview.enums.InterviewLevel;
import com.interviewai.backend.interview.enums.InterviewMode;
import com.interviewai.backend.interview.enums.InterviewStatus;
import com.interviewai.backend.interview.enums.MessageRole;
import com.interviewai.backend.interview.model.InterviewFeedback;
import com.interviewai.backend.interview.model.InterviewMessage;
import com.interviewai.backend.interview.model.InterviewSession;
import com.interviewai.backend.interview.model.InterviewSessionDocument;
import com.interviewai.backend.interview.repository.InterviewFeedbackRepository;
import com.interviewai.backend.interview.repository.InterviewMessageRepository;
import com.interviewai.backend.interview.repository.InterviewSessionDocumentRepository;
import com.interviewai.backend.interview.repository.InterviewSessionRepository;
import com.interviewai.backend.user.enums.UserErrorCode;
import com.interviewai.backend.user.model.User;
import com.interviewai.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterviewServiceImpl implements InterviewService {

    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewSessionDocumentRepository interviewSessionDocumentRepository;
    private final InterviewMessageRepository interviewMessageRepository;
    private final InterviewFeedbackRepository interviewFeedbackRepository;
    private final UserRepository userRepository;
    private final UserDocumentRepository userDocumentRepository;
    private final ClaudeAiClient claudeAiClient;
    private final GithubApiClient githubApiClient;
    private final JobCrawlerClient jobCrawlerClient;
    private final InterviewMessageSaver interviewMessageSaver;

    @Override
    @Transactional
    public InterviewStartResponseDto startInterview(Long userId, InterviewStartRequestDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        validateModeRequirements(request);

        List<UserDocument> documents = new ArrayList<>();
        if (request.getDocumentIds() != null && !request.getDocumentIds().isEmpty()) {
            documents = userDocumentRepository.findAllByIdInAndUserId(request.getDocumentIds(), userId);
        }

        String jobPostingContent = null;
        if (request.getMode() == InterviewMode.COMPANY && request.getJobPostingUrl() != null) {
            jobPostingContent = jobCrawlerClient.crawl(request.getJobPostingUrl());
        }

        String githubInfo = null;
        if (request.getGithubUrl() != null) {
            githubInfo = githubApiClient.extractGithubInfo(request.getGithubUrl());
        }

        InterviewSession session = InterviewSession.builder()
                .user(user)
                .mode(request.getMode())
                .level(request.getLevel())
                .jobTitle(request.getJobTitle())
                .jobPostingUrl(request.getJobPostingUrl())
                .jobPostingContent(jobPostingContent)
                .build();
        session = interviewSessionRepository.save(session);

        for (UserDocument document : documents) {
            interviewSessionDocumentRepository.save(
                    InterviewSessionDocument.builder()
                            .session(session)
                            .document(document)
                            .build()
            );
        }

        String systemPrompt = buildSystemPrompt(session, documents, jobPostingContent, githubInfo);
        String firstQuestion = claudeAiClient.chat(systemPrompt, List.of(),
                "면접을 시작해주세요. 첫 번째 질문을 해주세요.");

        interviewMessageRepository.save(InterviewMessage.builder()
                .session(session)
                .role(MessageRole.AI)
                .content(firstQuestion)
                .build());

        return InterviewStartResponseDto.of(session, firstQuestion);
    }

    @Override
    @Transactional
    public InterviewSendMessageResponseDto sendMessage(Long userId, Long sessionId,
                                                       InterviewSendMessageRequestDto request) {
        InterviewSession session = interviewSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(InterviewErrorCode.SESSION_NOT_FOUND));

        if (session.getStatus() != InterviewStatus.IN_PROGRESS) {
            throw new BusinessException(InterviewErrorCode.SESSION_ALREADY_COMPLETED);
        }

        interviewMessageRepository.save(InterviewMessage.builder()
                .session(session)
                .role(MessageRole.USER)
                .content(request.getContent())
                .build());

        List<InterviewMessage> messages = interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        List<ChatMessage> history = messages.stream()
                .map(msg -> new ChatMessage(
                        msg.getRole() == MessageRole.AI ? "assistant" : "user",
                        msg.getContent()))
                .toList();

        List<UserDocument> documents = interviewSessionDocumentRepository.findBySessionId(sessionId)
                .stream()
                .map(InterviewSessionDocument::getDocument)
                .toList();
        String systemPrompt = buildSendMessageSystemPrompt(session, documents, session.getJobPostingContent());
        String aiResponse = claudeAiClient.chat(systemPrompt, history, request.getContent());

        interviewMessageRepository.save(InterviewMessage.builder()
                .session(session)
                .role(MessageRole.AI)
                .content(aiResponse)
                .build());

        InterviewEvaluation eval = evaluateWithAi(session, history, aiResponse);

        return InterviewSendMessageResponseDto.builder()
                .aiResponse(aiResponse)
                .isCompleted(false)
                .suggestFinish(eval.suggestFinish())
                .qualityScore(eval.qualityScore())
                .qualityHint(eval.qualityHint())
                .build();
    }

    @Override
    public SseEmitter streamMessage(Long userId, Long sessionId, InterviewSendMessageRequestDto request) {
        InterviewSession session = interviewSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(InterviewErrorCode.SESSION_NOT_FOUND));

        if (session.getStatus() != InterviewStatus.IN_PROGRESS) {
            throw new BusinessException(InterviewErrorCode.SESSION_ALREADY_COMPLETED);
        }

        interviewMessageSaver.saveUserMessage(sessionId, request.getContent());

        List<InterviewMessage> messages = interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        List<ChatMessage> history = messages.stream()
                .map(msg -> new ChatMessage(
                        msg.getRole() == MessageRole.AI ? "assistant" : "user",
                        msg.getContent()))
                .toList();

        List<UserDocument> documents = interviewSessionDocumentRepository.findBySessionId(sessionId)
                .stream()
                .map(InterviewSessionDocument::getDocument)
                .toList();
        String systemPrompt = buildSendMessageSystemPrompt(session, documents, session.getJobPostingContent());

        SseEmitter emitter = new SseEmitter(120_000L);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            StringBuilder aiContent = new StringBuilder();
            try {
                claudeAiClient.streamChat(systemPrompt, history, request.getContent())
                        .doOnNext(token -> {
                            aiContent.append(token);
                            sendTokenToEmitter(emitter, token);
                        })
                        .doOnComplete(() -> {
                            try {
                                InterviewEvaluation eval = evaluateWithAi(session, history, aiContent.toString());
                                interviewMessageSaver.saveAiMessageAndComplete(emitter, sessionId, aiContent.toString(), eval);
                            } catch (Exception e) {
                                log.error("AI 메시지 저장 실패, emitter 강제 종료", e);
                                emitter.completeWithError(e);
                            }
                        })
                        .doOnError(e -> emitter.completeWithError(e))
                        .subscribe();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        executor.shutdown();
        return emitter;
    }

    @Override
    @Transactional
    public InterviewFeedbackResponseDto finishInterview(Long userId, Long sessionId) {
        InterviewSession session = interviewSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(InterviewErrorCode.SESSION_NOT_FOUND));

        if (session.getStatus() != InterviewStatus.IN_PROGRESS) {
            throw new BusinessException(InterviewErrorCode.SESSION_ALREADY_COMPLETED);
        }

        session.complete();

        List<InterviewMessage> messages = interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        String conversationText = buildConversationText(messages);

        String feedbackSystemPrompt = buildFeedbackSystemPrompt(session);
        String feedbackJson = claudeAiClient.generateFeedback(feedbackSystemPrompt, conversationText);

        InterviewFeedback feedback = parseFeedbackAndSave(session, feedbackJson);
        return InterviewFeedbackResponseDto.from(feedback);
    }

    @Override
    @Transactional
    public void cancelInterview(Long userId, Long sessionId) {
        InterviewSession session = interviewSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(InterviewErrorCode.SESSION_NOT_FOUND));

        if (session.getStatus() != InterviewStatus.IN_PROGRESS) {
            throw new BusinessException(InterviewErrorCode.SESSION_CANNOT_CANCEL);
        }

        session.cancel();
    }

    @Override
    @Transactional
    public void deleteInterview(Long userId, Long sessionId) {
        InterviewSession session = interviewSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(InterviewErrorCode.SESSION_NOT_FOUND));

        interviewMessageRepository.deleteBySessionId(sessionId);
        interviewSessionDocumentRepository.deleteBySessionId(sessionId);
        interviewFeedbackRepository.deleteBySessionId(sessionId);
        interviewSessionRepository.delete(session);
    }

    @Override
    @Transactional
    public void deleteInterviews(Long userId, List<Long> sessionIds) {
        List<Long> uniqueIds = sessionIds.stream().distinct().toList();

        List<InterviewSession> sessions = interviewSessionRepository.findAllByIdInAndUserId(uniqueIds, userId);
        if (sessions.size() != uniqueIds.size()) {
            throw new BusinessException(InterviewErrorCode.SESSION_NOT_FOUND);
        }

        interviewMessageRepository.deleteAllBySessionIdIn(uniqueIds);
        interviewSessionDocumentRepository.deleteAllBySessionIdIn(uniqueIds);
        interviewFeedbackRepository.deleteAllBySessionIdIn(uniqueIds);
        interviewSessionRepository.deleteAll(sessions);
    }

    @Override
    public Page<InterviewSessionResponseDto> findMySessions(Long userId, Pageable pageable) {
        return interviewSessionRepository.findByUserId(userId, pageable)
                .map(InterviewSessionResponseDto::from);
    }

    @Override
    public List<InterviewMessageResponseDto> findSessionMessages(Long userId, Long sessionId) {
        interviewSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(InterviewErrorCode.SESSION_NOT_FOUND));

        return interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                .stream()
                .map(InterviewMessageResponseDto::from)
                .toList();
    }

    @Override
    public InterviewFeedbackResponseDto findFeedback(Long userId, Long sessionId) {
        interviewSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(InterviewErrorCode.SESSION_NOT_FOUND));

        InterviewFeedback feedback = interviewFeedbackRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException(InterviewErrorCode.FEEDBACK_NOT_FOUND));

        return InterviewFeedbackResponseDto.from(feedback);
    }

    @Override
    public InterviewStatsResponseDto getMyStats(Long userId) {
        long totalCount = interviewSessionRepository.countByUserId(userId);
        long completedCount = interviewSessionRepository.countByUserIdAndStatus(userId, InterviewStatus.COMPLETED);
        long cancelledCount = interviewSessionRepository.countByUserIdAndStatus(userId, InterviewStatus.CANCELLED);
        Double averageScore = interviewFeedbackRepository.findAverageScoreByUserId(userId);

        java.util.Map<String, Long> modeDistribution = new java.util.LinkedHashMap<>();
        for (InterviewMode mode : InterviewMode.values()) {
            modeDistribution.put(mode.name(), interviewSessionRepository.countByUserIdAndMode(userId, mode));
        }

        java.util.Map<String, Long> levelDistribution = new java.util.LinkedHashMap<>();
        for (InterviewLevel level : InterviewLevel.values()) {
            levelDistribution.put(level.name(), interviewSessionRepository.countByUserIdAndLevel(userId, level));
        }

        return InterviewStatsResponseDto.builder()
                .totalCount(totalCount)
                .completedCount(completedCount)
                .cancelledCount(cancelledCount)
                .averageScore(averageScore)
                .modeDistribution(modeDistribution)
                .levelDistribution(levelDistribution)
                .build();
    }

    private void validateModeRequirements(InterviewStartRequestDto request) {
        boolean hasDocument = request.getDocumentIds() != null && !request.getDocumentIds().isEmpty();
        if (request.getMode() == InterviewMode.RESUME && !hasDocument && request.getGithubUrl() == null) {
            throw new BusinessException(InterviewErrorCode.DOCUMENT_REQUIRED);
        }
        if (request.getMode() == InterviewMode.COMPANY && !hasDocument && request.getGithubUrl() == null) {
            throw new BusinessException(InterviewErrorCode.DOCUMENT_REQUIRED);
        }
    }

    private String buildSystemPrompt(InterviewSession session, List<UserDocument> documents,
                                     String jobPostingContent, String githubInfo) {
        StringBuilder prompt = new StringBuilder();
        String levelDescription = switch (session.getLevel()) {
            case JUNIOR -> "신입 개발자 (CS 기초, 프로젝트 경험, 성장 가능성 중심)";
            case SENIOR -> "경력 개발자 (기술 깊이, 아키텍처 경험, 리더십/협업 중심)";
        };

        prompt.append("당신은 전문 기술 면접관입니다. ");
        prompt.append("지금은 ").append(levelDescription).append(" 대상의 면접을 진행합니다.\n\n");

        if (session.getJobTitle() != null) {
            prompt.append("지원 직무: ").append(session.getJobTitle()).append("\n\n");
        }

        prompt.append("""
                면접 진행 방식:
                1. 질문은 하나씩 명확하게 한다.
                2. 꼬리 질문은 한 주제당 최대 1~2번으로 제한한다. 지원자가 '모르겠다', '잘 모른다', '모릅니다' 등의 답변을 하거나 충분한 답변이 없으면 추가 꼬리 질문 없이 바로 새로운 주제로 넘어간다.
                3. 기술적 깊이를 파악하되 존중하는 태도로 진행한다.
                4. 답변이 완벽하지 않더라도 바로 정답을 알려주지 말고 더 생각해볼 기회를 준다.
                5. 면접 중에는 피드백이나 점수를 주지 않는다.
                6. 지원자의 이름이 제공된 문서(이력서, 포트폴리오 등)에서 확인되면 그 이름으로 호칭하고, 확인되지 않는 경우 '지원자님'으로 호칭한다.
                """);

        for (UserDocument doc : documents) {
            if (doc.getParsedText() == null) continue;
            String docLabel = switch (doc.getDocumentType()) {
                case RESUME -> "이력서";
                case PORTFOLIO -> "포트폴리오";
                case CAREER_STATEMENT -> "경력기술서";
            };
            prompt.append("\n=== 지원자 ").append(docLabel).append(" ===\n");
            String parsedText = doc.getParsedText();
            if (parsedText.length() > 1500) {
                parsedText = parsedText.substring(0, 1500) + "...";
            }
            prompt.append(parsedText).append("\n");
        }

        if (githubInfo != null) {
            prompt.append("\n=== 지원자 GitHub 정보 ===\n");
            prompt.append(githubInfo).append("\n");
        }

        if (jobPostingContent != null) {
            prompt.append("\n=== 채용공고 내용 ===\n");
            String content = jobPostingContent;
            if (content.length() > 1500) {
                content = content.substring(0, 1500) + "...";
            }
            prompt.append(content).append("\n");
        }

        return prompt.toString();
    }

    private String buildSendMessageSystemPrompt(InterviewSession session, List<UserDocument> documents,
                                                 String jobPostingContent) {
        String base = buildSystemPrompt(session, documents, jobPostingContent, null);
        return base + "\n다음 면접 질문만 텍스트로 답변해주세요. JSON 형식 불필요.";
    }

    private String buildFeedbackSystemPrompt(InterviewSession session) {
        return """
                당신은 기술 면접을 평가하는 전문가입니다.
                다음 면접 대화를 분석하여 구조화된 피드백을 제공해주세요.

                다음 형식으로 정확히 응답해주세요 (JSON 형식):
                {
                  "overallScore": 75,
                  "strengths": "잘한 점 1.\\n잘한 점 2.\\n잘한 점 3.",
                  "improvements": "개선점 1.\\n개선점 2.\\n개선점 3.",
                  "fullReport": "종합 평가 내용..."
                }

                점수는 0-100 사이의 정수로, 다음 기준으로 평가합니다:
                - 기술 지식의 정확성 (40점)
                - 의사소통 능력 (20점)
                - 문제 해결 접근법 (20점)
                - 경험 및 예시의 적절성 (20점)
                """ + "대상: " + (session.getLevel() == com.interviewai.backend.interview.enums.InterviewLevel.JUNIOR
                ? "신입 개발자" : "경력 개발자");
    }

    private String buildConversationText(List<InterviewMessage> messages) {
        StringBuilder sb = new StringBuilder("=== 면접 대화 내용 ===\n\n");
        for (InterviewMessage message : messages) {
            String roleLabel = message.getRole() == MessageRole.AI ? "면접관" : "지원자";
            sb.append("[").append(roleLabel).append("]\n");
            sb.append(message.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    InterviewEvaluation evaluateWithAi(InterviewSession session, List<ChatMessage> history, String aiResponse) {
        String evalPrompt = buildEvalPrompt(session, history, aiResponse);
        String evalJson = claudeAiClient.chat(evalPrompt, List.of(), "위 지시에 따라 JSON으로만 응답해주세요.");
        return parseEvaluation(evalJson);
    }

    private String buildEvalPrompt(InterviewSession session, List<ChatMessage> history, String aiResponse) {
        StringBuilder prompt = new StringBuilder();
        String levelDesc = session.getLevel() == InterviewLevel.JUNIOR ? "신입 개발자" : "경력 개발자";

        prompt.append("당신은 기술 면접 평가 전문가입니다.\n");
        prompt.append("아래 면접 대화에서 지원자의 마지막 답변을 평가하고, 면접 종료 시점인지 판단해주세요.\n\n");
        prompt.append("면접 레벨: ").append(levelDesc).append("\n");
        if (session.getJobTitle() != null) {
            prompt.append("지원 직무: ").append(session.getJobTitle()).append("\n");
        }
        prompt.append("\n=== 면접 대화 ===\n");
        for (ChatMessage msg : history) {
            String role = "assistant".equals(msg.role()) ? "[면접관]" : "[지원자]";
            prompt.append(role).append("\n").append(msg.content()).append("\n\n");
        }
        prompt.append("[면접관]\n").append(aiResponse).append("\n\n");
        prompt.append("""
                위 대화를 바탕으로 다음 JSON 형식으로만 응답하세요:
                {
                  "suggestFinish": false,
                  "qualityScore": 75,
                  "qualityHint": "시간복잡도 설명은 좋았으나 공간복잡도 언급이 부족했어요."
                }

                - suggestFinish: 주요 기술 주제가 충분히 다루어졌으면 true, 아직 부족하면 false
                - qualityScore: 0-100 정수 (지원자 마지막 답변의 기술 정확성과 완성도)
                - qualityHint: 한국어 한 줄 피드백 (지원자 마지막 답변에 대해, 100자 이내)
                """);
        return prompt.toString();
    }

    private InterviewEvaluation parseEvaluation(String evalJson) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String jsonStr = evalJson;
            int jsonStart = jsonStr.indexOf('{');
            int jsonEnd = jsonStr.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd >= 0) {
                jsonStr = jsonStr.substring(jsonStart, jsonEnd + 1);
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(jsonStr);
                boolean suggestFinish = node.has("suggestFinish") && node.get("suggestFinish").asBoolean(false);
                int qualityScore = node.has("qualityScore") ? node.get("qualityScore").asInt(50) : 50;
                String qualityHint = node.has("qualityHint") ? node.get("qualityHint").asText() : "답변이 접수되었습니다.";
                return new InterviewEvaluation(suggestFinish, qualityScore, qualityHint);
            }
        } catch (Exception e) {
            log.warn("평가 JSON 파싱 실패, fallback 사용: {}", e.getMessage());
        }
        return InterviewEvaluation.fallback();
    }

    void sendTokenToEmitter(SseEmitter emitter, String token) {
        try {
            emitter.send(SseEmitter.event().data(token));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private InterviewFeedback parseFeedbackAndSave(InterviewSession session, String feedbackJson) {
        int overallScore = 70;
        String strengths = "분석 중...";
        String improvements = "분석 중...";
        String fullReport = feedbackJson;

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String jsonStr = feedbackJson;
            int jsonStart = jsonStr.indexOf('{');
            int jsonEnd = jsonStr.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd >= 0) {
                jsonStr = jsonStr.substring(jsonStart, jsonEnd + 1);
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(jsonStr);
                overallScore = node.has("overallScore") ? node.get("overallScore").asInt(70) : 70;
                strengths = node.has("strengths") ? node.get("strengths").asText() : strengths;
                improvements = node.has("improvements") ? node.get("improvements").asText() : improvements;
                fullReport = node.has("fullReport") ? node.get("fullReport").asText() : feedbackJson;
            }
        } catch (Exception e) {
            log.warn("피드백 JSON 파싱 실패, 원본 텍스트 사용: {}", e.getMessage());
        }

        InterviewFeedback feedback = InterviewFeedback.builder()
                .session(session)
                .overallScore(overallScore)
                .strengths(strengths)
                .improvements(improvements)
                .fullReport(fullReport)
                .build();

        return interviewFeedbackRepository.save(feedback);
    }
}
