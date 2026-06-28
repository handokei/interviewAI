package com.interviewai.backend.interview.service;

import com.interviewai.backend.client.dto.ChatMessage;
import com.interviewai.backend.interview.model.InterviewMessage;

import java.util.List;

public record TrimResult(
        List<ChatMessage> retained,
        List<InterviewMessage> trimmed,
        boolean wasTrimmed
) {}
