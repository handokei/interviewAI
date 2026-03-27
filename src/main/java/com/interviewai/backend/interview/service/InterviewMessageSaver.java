package com.interviewai.backend.interview.service;

import com.interviewai.backend.interview.enums.InterviewLevel;
import com.interviewai.backend.interview.enums.MessageRole;
import com.interviewai.backend.interview.model.InterviewMessage;
import com.interviewai.backend.interview.model.InterviewSession;
import com.interviewai.backend.interview.repository.InterviewMessageRepository;
import com.interviewai.backend.interview.repository.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewMessageSaver {

    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewMessageRepository interviewMessageRepository;

    @Transactional
    public void saveUserMessage(Long sessionId, String content) {
        InterviewSession sessionRef = interviewSessionRepository.getReferenceById(sessionId);
        interviewMessageRepository.save(InterviewMessage.builder()
                .session(sessionRef)
                .role(MessageRole.USER)
                .content(content)
                .build());
    }

    @Transactional
    public void saveAiMessageAndComplete(SseEmitter emitter, Long sessionId,
                                         InterviewLevel level, String aiContent) {
        InterviewSession sessionRef = interviewSessionRepository.getReferenceById(sessionId);
        interviewMessageRepository.save(InterviewMessage.builder()
                .session(sessionRef)
                .role(MessageRole.AI)
                .content(aiContent)
                .build());

        List<InterviewMessage> allMessages = interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        long aiMessageCount = allMessages.stream()
                .filter(m -> m.getRole() == MessageRole.AI)
                .count();
        boolean suggestFinish = switch (level) {
            case JUNIOR -> aiMessageCount >= 5;
            case SENIOR -> aiMessageCount >= 7;
        };

        try {
            emitter.send(SseEmitter.event()
                    .name("done")
                    .data("{\"suggestFinish\":" + suggestFinish + "}"));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }
}
