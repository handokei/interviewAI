package com.interviewai.backend.interview.service;

import com.interviewai.backend.interview.enums.InterviewLevel;
import com.interviewai.backend.interview.enums.MessageRole;
import com.interviewai.backend.interview.model.InterviewMessage;
import com.interviewai.backend.interview.model.InterviewSession;
import com.interviewai.backend.interview.repository.InterviewMessageRepository;
import com.interviewai.backend.interview.repository.InterviewSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterviewMessageSaverTest {

    @InjectMocks
    private InterviewMessageSaver interviewMessageSaver;

    @Mock
    private InterviewSessionRepository interviewSessionRepository;

    @Mock
    private InterviewMessageRepository interviewMessageRepository;

    @Test
    @DisplayName("기능_테스트_유저_메시지를_저장한다")
    void 기능_테스트_유저_메시지를_저장한다() {
        // given
        Long sessionId = 1L;
        String content = "Java는 객체지향 언어입니다.";
        InterviewSession sessionRef = mock(InterviewSession.class);

        given(interviewSessionRepository.getReferenceById(sessionId)).willReturn(sessionRef);
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null);

        // when
        interviewMessageSaver.saveUserMessage(sessionId, content);

        // then
        verify(interviewSessionRepository).getReferenceById(sessionId);
        verify(interviewMessageRepository).save(any(InterviewMessage.class));
    }

    @Test
    @DisplayName("기능_테스트_JUNIOR_AI_메시지_5개_이하면_suggestFinish가_false다")
    void 기능_테스트_JUNIOR_AI_메시지_5개_이하면_suggestFinish가_false다() throws IOException {
        // given
        Long sessionId = 1L;
        SseEmitter emitter = mock(SseEmitter.class);
        InterviewSession sessionRef = mock(InterviewSession.class);

        List<InterviewMessage> messages = buildMessageList(5);

        given(interviewSessionRepository.getReferenceById(sessionId)).willReturn(sessionRef);
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null);
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(messages);

        // when
        interviewMessageSaver.saveAiMessageAndComplete(emitter, sessionId, InterviewLevel.JUNIOR, "좋은 답변입니다.");

        // then
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
        verify(emitter, never()).completeWithError(any(Throwable.class));
    }

    @Test
    @DisplayName("기능_테스트_JUNIOR_AI_메시지_6개_이상이면_suggestFinish가_true다")
    void 기능_테스트_JUNIOR_AI_메시지_6개_이상이면_suggestFinish가_true다() throws IOException {
        // given
        Long sessionId = 1L;
        SseEmitter emitter = mock(SseEmitter.class);
        InterviewSession sessionRef = mock(InterviewSession.class);

        List<InterviewMessage> messages = buildMessageList(6);

        given(interviewSessionRepository.getReferenceById(sessionId)).willReturn(sessionRef);
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null);
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(messages);

        // when
        interviewMessageSaver.saveAiMessageAndComplete(emitter, sessionId, InterviewLevel.JUNIOR, "수고하셨습니다.");

        // then
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
        verify(emitter, never()).completeWithError(any(Throwable.class));
    }

    @Test
    @DisplayName("기능_테스트_SENIOR_AI_메시지_7개_이하면_suggestFinish가_false다")
    void 기능_테스트_SENIOR_AI_메시지_7개_이하면_suggestFinish가_false다() throws IOException {
        // given
        Long sessionId = 1L;
        SseEmitter emitter = mock(SseEmitter.class);
        InterviewSession sessionRef = mock(InterviewSession.class);

        List<InterviewMessage> messages = buildMessageList(7);

        given(interviewSessionRepository.getReferenceById(sessionId)).willReturn(sessionRef);
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null);
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(messages);

        // when
        interviewMessageSaver.saveAiMessageAndComplete(emitter, sessionId, InterviewLevel.SENIOR, "좋은 답변입니다.");

        // then
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
        verify(emitter, never()).completeWithError(any(Throwable.class));
    }

    @Test
    @DisplayName("기능_테스트_SENIOR_AI_메시지_8개_이상이면_suggestFinish가_true다")
    void 기능_테스트_SENIOR_AI_메시지_8개_이상이면_suggestFinish가_true다() throws IOException {
        // given
        Long sessionId = 1L;
        SseEmitter emitter = mock(SseEmitter.class);
        InterviewSession sessionRef = mock(InterviewSession.class);

        List<InterviewMessage> messages = buildMessageList(8);

        given(interviewSessionRepository.getReferenceById(sessionId)).willReturn(sessionRef);
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null);
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(messages);

        // when
        interviewMessageSaver.saveAiMessageAndComplete(emitter, sessionId, InterviewLevel.SENIOR, "수고하셨습니다.");

        // then
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
        verify(emitter, never()).completeWithError(any(Throwable.class));
    }

    @Test
    @DisplayName("예외_테스트_emitter_전송_실패시_completeWithError가_호출된다")
    void 예외_테스트_emitter_전송_실패시_completeWithError가_호출된다() throws IOException {
        // given
        Long sessionId = 1L;
        SseEmitter emitter = mock(SseEmitter.class);
        InterviewSession sessionRef = mock(InterviewSession.class);

        List<InterviewMessage> messages = buildMessageList(3);

        given(interviewSessionRepository.getReferenceById(sessionId)).willReturn(sessionRef);
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null);
        given(interviewMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(messages);
        doThrow(IOException.class).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        // when
        interviewMessageSaver.saveAiMessageAndComplete(emitter, sessionId, InterviewLevel.JUNIOR, "답변입니다.");

        // then
        verify(emitter).completeWithError(any(IOException.class));
        verify(emitter, never()).complete();
    }

    private List<InterviewMessage> buildMessageList(int aiCount) {
        List<InterviewMessage> messages = new ArrayList<>();
        InterviewSession session = mock(InterviewSession.class);
        for (int i = 0; i < aiCount; i++) {
            messages.add(InterviewMessage.builder()
                    .session(session)
                    .role(MessageRole.AI)
                    .content("Q" + (i + 1))
                    .build());
            messages.add(InterviewMessage.builder()
                    .session(session)
                    .role(MessageRole.USER)
                    .content("A" + (i + 1))
                    .build());
        }
        return messages;
    }
}
