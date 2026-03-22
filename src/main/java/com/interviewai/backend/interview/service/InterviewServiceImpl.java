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
import com.interviewai.backend.interview.enums.InterviewMode;
import com.interviewai.backend.interview.enums.InterviewStatus;
import com.interviewai.backend.interview.enums.MessageRole;
import com.interviewai.backend.interview.model.InterviewFeedback;
import com.interviewai.backend.interview.model.InterviewMessage;
import com.interviewai.backend.interview.model.InterviewSession;
import com.interviewai.backend.interview.repository.InterviewFeedbackRepository;
import com.interviewai.backend.interview.repository.InterviewMessageRepository;
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

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterviewServiceImpl implements InterviewService {

    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewMessageRepository interviewMessageRepository;
    private final InterviewFeedbackRepository interviewFeedbackRepository;
    private final UserRepository userRepository;
    private final UserDocumentRepository userDocumentRepository;
    private final ClaudeAiClient claudeAiClient;
    private final GithubApiClient githubApiClient;
    private final JobCrawlerClient jobCrawlerClient;

    @Override
    @Transactional
    public InterviewStartResponseDto startInterview(Long userId, InterviewStartRequestDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        validateModeRequirements(request);

        UserDocument document = null;
        if (request.getDocumentId() != null) {
            document = userDocumentRepository.findByIdAndUserId(request.getDocumentId(), userId)
                    .orElseThrow(() -> new BusinessException(InterviewErrorCode.DOCUMENT_REQUIRED));
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
                .document(document)
                .jobPostingUrl(request.getJobPostingUrl())
                .jobPostingContent(jobPostingContent)
                .build();
        session = interviewSessionRepository.save(session);

        String systemPrompt = buildSystemPrompt(session, document, jobPostingContent, githubInfo);
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

        UserDocument document = session.getDocument();
        String systemPrompt = buildSystemPrompt(session, document, session.getJobPostingContent(), null);
        String aiResponse = claudeAiClient.chat(systemPrompt, history, request.getContent());

        interviewMessageRepository.save(InterviewMessage.builder()
                .session(session)
                .role(MessageRole.AI)
                .content(aiResponse)
                .build());

        return InterviewSendMessageResponseDto.builder()
                .aiResponse(aiResponse)
                .isCompleted(false)
                .build();
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

    private void validateModeRequirements(InterviewStartRequestDto request) {
        if (request.getMode() == InterviewMode.RESUME && request.getDocumentId() == null
                && request.getGithubUrl() == null) {
            throw new BusinessException(InterviewErrorCode.DOCUMENT_REQUIRED);
        }
        if (request.getMode() == InterviewMode.COMPANY && request.getDocumentId() == null
                && request.getGithubUrl() == null) {
            throw new BusinessException(InterviewErrorCode.DOCUMENT_REQUIRED);
        }
    }

    private String buildSystemPrompt(InterviewSession session, UserDocument document,
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
                2. 지원자의 답변을 경청하고 꼬리 질문을 적절히 던진다.
                3. 기술적 깊이를 파악하되 존중하는 태도로 진행한다.
                4. 답변이 완벽하지 않더라도 바로 정답을 알려주지 말고 더 생각해볼 기회를 준다.
                5. 면접 중에는 피드백이나 점수를 주지 않는다.
                """);

        if (document != null && document.getParsedText() != null) {
            prompt.append("\n=== 지원자 이력서/포트폴리오 ===\n");
            String parsedText = document.getParsedText();
            if (parsedText.length() > 2000) {
                parsedText = parsedText.substring(0, 2000) + "...";
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
