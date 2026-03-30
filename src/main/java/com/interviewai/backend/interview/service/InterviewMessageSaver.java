package com.interviewai.backend.interview.service;

import com.interviewai.backend.interview.enums.MessageRole;
import com.interviewai.backend.interview.model.InterviewMessage;
import com.interviewai.backend.interview.model.InterviewSession;
import com.interviewai.backend.interview.repository.InterviewMessageRepository;
import com.interviewai.backend.interview.repository.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewMessageSaver {

    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewMessageRepository interviewMessageRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveUserMessage(Long sessionId, String content) {
        InterviewSession sessionRef = interviewSessionRepository.getReferenceById(sessionId);
        interviewMessageRepository.save(InterviewMessage.builder()
                .session(sessionRef)
                .role(MessageRole.USER)
                .content(content)
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAiMessageAndComplete(SseEmitter emitter, Long sessionId,
                                         String aiContent, InterviewEvaluation eval) {
        InterviewSession sessionRef = interviewSessionRepository.getReferenceById(sessionId);
        interviewMessageRepository.save(InterviewMessage.builder()
                .session(sessionRef)
                .role(MessageRole.AI)
                .content(aiContent)
                .build());

        String qualityHintEscaped = eval.qualityHint().replace("\\", "\\\\").replace("\"", "\\\"");
        String doneData = "{\"suggestFinish\":" + eval.suggestFinish()
                + ",\"qualityScore\":" + eval.qualityScore()
                + ",\"qualityHint\":\"" + qualityHintEscaped + "\"}";

        try {
            emitter.send(SseEmitter.event()
                    .name("done")
                    .data(doneData));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }
}
