package com.interviewai.backend.interview.service;

import com.interviewai.backend.interview.controller.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface InterviewService {

    InterviewStartResponseDto startInterview(Long userId, InterviewStartRequestDto request);

    InterviewSendMessageResponseDto sendMessage(Long userId, Long sessionId, InterviewSendMessageRequestDto request);

    SseEmitter streamMessage(Long userId, Long sessionId, InterviewSendMessageRequestDto request);

    InterviewFeedbackResponseDto finishInterview(Long userId, Long sessionId);

    Page<InterviewSessionResponseDto> findMySessions(Long userId, Pageable pageable);

    List<InterviewMessageResponseDto> findSessionMessages(Long userId, Long sessionId);

    InterviewFeedbackResponseDto findFeedback(Long userId, Long sessionId);

    void cancelInterview(Long userId, Long sessionId);

    void deleteInterview(Long userId, Long sessionId);

    void deleteInterviews(Long userId, List<Long> sessionIds);

    InterviewStatsResponseDto getMyStats(Long userId);
}
