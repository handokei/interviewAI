package com.interviewai.backend.interview.service;

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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
    @DisplayName("기능_테스트_AI_메시지를_저장하고_done_이벤트로_즉시_완료한다")
    void 기능_테스트_AI_메시지를_저장하고_done_이벤트로_즉시_완료한다() throws IOException {
        // given
        Long sessionId = 1L;
        SseEmitter emitter = mock(SseEmitter.class);
        InterviewSession sessionRef = mock(InterviewSession.class);
        InterviewMessage savedMessage = InterviewMessage.builder()
                .session(sessionRef).role(MessageRole.AI).content("좋은 답변입니다.").build();
        ReflectionTestUtils.setField(savedMessage, "id", 100L);

        given(interviewSessionRepository.getReferenceById(sessionId)).willReturn(sessionRef);
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(savedMessage);

        // when
        Long result = interviewMessageSaver.saveAiMessageAndComplete(emitter, sessionId, "좋은 답변입니다.");

        // then
        assertThat(result).isEqualTo(100L);
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
        verify(emitter, never()).completeWithError(any(Throwable.class));
    }

    @Test
    @DisplayName("기능_테스트_저장된_AI_메시지의_ID를_반환한다")
    void 기능_테스트_저장된_AI_메시지의_ID를_반환한다() throws IOException {
        // given
        Long sessionId = 1L;
        SseEmitter emitter = mock(SseEmitter.class);
        InterviewSession sessionRef = mock(InterviewSession.class);
        InterviewMessage savedMessage = InterviewMessage.builder()
                .session(sessionRef).role(MessageRole.AI).content("수고하셨습니다.").build();
        ReflectionTestUtils.setField(savedMessage, "id", 42L);

        given(interviewSessionRepository.getReferenceById(sessionId)).willReturn(sessionRef);
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(savedMessage);

        // when
        Long result = interviewMessageSaver.saveAiMessageAndComplete(emitter, sessionId, "수고하셨습니다.");

        // then
        assertThat(result).isEqualTo(42L);
    }

    @Test
    @DisplayName("예외_테스트_emitter_전송_실패시_completeWithError가_호출된다")
    void 예외_테스트_emitter_전송_실패시_completeWithError가_호출된다() throws IOException {
        // given
        Long sessionId = 1L;
        SseEmitter emitter = mock(SseEmitter.class);
        InterviewSession sessionRef = mock(InterviewSession.class);
        InterviewMessage savedMessage = InterviewMessage.builder()
                .session(sessionRef).role(MessageRole.AI).content("답변입니다.").build();
        ReflectionTestUtils.setField(savedMessage, "id", 1L);

        given(interviewSessionRepository.getReferenceById(sessionId)).willReturn(sessionRef);
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(savedMessage);
        doThrow(IOException.class).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        // when
        interviewMessageSaver.saveAiMessageAndComplete(emitter, sessionId, "답변입니다.");

        // then
        verify(emitter).completeWithError(any(IOException.class));
        verify(emitter, never()).complete();
    }

    @Test
    @DisplayName("기능_테스트_saveFirstQuestionMessage_AI_메시지를_저장하고_done_이벤트로_완료한다")
    void 기능_테스트_saveFirstQuestionMessage_AI_메시지를_저장하고_done_이벤트로_완료한다() throws IOException {
        // given
        Long sessionId = 1L;
        SseEmitter emitter = mock(SseEmitter.class);
        InterviewSession sessionRef = mock(InterviewSession.class);
        String aiContent = "안녕하세요! 첫 번째 질문입니다.";

        given(interviewSessionRepository.getReferenceById(sessionId)).willReturn(sessionRef);
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null);

        // when
        interviewMessageSaver.saveFirstQuestionMessage(emitter, sessionId, aiContent);

        // then
        verify(interviewSessionRepository).getReferenceById(sessionId);
        verify(interviewMessageRepository).save(argThat(msg ->
                msg.getRole() == MessageRole.AI && msg.getContent().equals(aiContent)));
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
        verify(emitter, never()).completeWithError(any(Throwable.class));
    }

    @Test
    @DisplayName("예외_테스트_saveFirstQuestionMessage_emitter_전송_실패시_completeWithError가_호출된다")
    void 예외_테스트_saveFirstQuestionMessage_emitter_전송_실패시_completeWithError가_호출된다() throws IOException {
        // given
        Long sessionId = 1L;
        SseEmitter emitter = mock(SseEmitter.class);
        InterviewSession sessionRef = mock(InterviewSession.class);
        String aiContent = "첫 번째 질문입니다.";

        given(interviewSessionRepository.getReferenceById(sessionId)).willReturn(sessionRef);
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null);
        doThrow(IOException.class).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        // when
        interviewMessageSaver.saveFirstQuestionMessage(emitter, sessionId, aiContent);

        // then
        verify(emitter).completeWithError(any(IOException.class));
        verify(emitter, never()).complete();
    }
}
