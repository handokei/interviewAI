package com.interviewai.backend.interview.service;

import com.interviewai.backend.interview.controller.dto.*;

import java.util.List;

public interface InterviewService {

    InterviewStartResponseDto startInterview(Long userId, InterviewStartRequestDto request);

    InterviewSendMessageResponseDto sendMessage(Long userId, Long sessionId, InterviewSendMessageRequestDto request);

    InterviewFeedbackResponseDto finishInterview(Long userId, Long sessionId);

    List<InterviewSessionResponseDto> findMySessions(Long userId);

    List<InterviewMessageResponseDto> findSessionMessages(Long userId, Long sessionId);

    InterviewFeedbackResponseDto findFeedback(Long userId, Long sessionId);
}
