package com.interviewai.backend.interview.service;

import com.interviewai.backend.interview.enums.AnswerLevel;
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
    @DisplayName("기능_테스트_AI_메시지를_저장하고_suggestFinish_false로_완료한다")
    void 기능_테스트_AI_메시지를_저장하고_suggestFinish_false로_완료한다() throws IOException {
        // given
        Long sessionId = 1L;
        SseEmitter emitter = mock(SseEmitter.class);
        InterviewSession sessionRef = mock(InterviewSession.class);
        InterviewEvaluation eval = new InterviewEvaluation(false, AnswerLevel.NEEDS_IMPROVEMENT, "좋은 답변이었습니다.");

        given(interviewSessionRepository.getReferenceById(sessionId)).willReturn(sessionRef);
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null);

        // when
        interviewMessageSaver.saveAiMessageAndComplete(emitter, sessionId, "좋은 답변입니다.", eval);

        // then
        verify(emitter).send(argThat((SseEmitter.SseEventBuilder builder) -> true));
        verify(emitter).complete();
        verify(emitter, never()).completeWithError(any(Throwable.class));
    }

    @Test
    @DisplayName("기능_테스트_AI_메시지를_저장하고_suggestFinish_true로_완료한다")
    void 기능_테스트_AI_메시지를_저장하고_suggestFinish_true로_완료한다() throws IOException {
        // given
        Long sessionId = 1L;
        SseEmitter emitter = mock(SseEmitter.class);
        InterviewSession sessionRef = mock(InterviewSession.class);
        InterviewEvaluation eval = new InterviewEvaluation(true, AnswerLevel.PASS, "전반적으로 훌륭한 답변이었습니다.");

        given(interviewSessionRepository.getReferenceById(sessionId)).willReturn(sessionRef);
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null);

        // when
        interviewMessageSaver.saveAiMessageAndComplete(emitter, sessionId, "수고하셨습니다.", eval);

        // then
        verify(emitter).send(argThat((SseEmitter.SseEventBuilder builder) -> true));
        verify(emitter).complete();
        verify(emitter, never()).completeWithError(any(Throwable.class));
    }

    @Test
    @DisplayName("기능_테스트_answerLevel과_qualityHint가_SSE_done_이벤트에_포함된다")
    void 기능_테스트_answerLevel과_qualityHint가_SSE_done_이벤트에_포함된다() throws IOException {
        // given
        Long sessionId = 1L;
        SseEmitter emitter = mock(SseEmitter.class);
        InterviewSession sessionRef = mock(InterviewSession.class);
        InterviewEvaluation eval = new InterviewEvaluation(false, AnswerLevel.PASS, "시간복잡도 언급이 좋았어요.");

        given(interviewSessionRepository.getReferenceById(sessionId)).willReturn(sessionRef);
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null);

        // when
        interviewMessageSaver.saveAiMessageAndComplete(emitter, sessionId, "답변입니다.", eval);

        // then
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
    }

    @Test
    @DisplayName("예외_테스트_emitter_전송_실패시_completeWithError가_호출된다")
    void 예외_테스트_emitter_전송_실패시_completeWithError가_호출된다() throws IOException {
        // given
        Long sessionId = 1L;
        SseEmitter emitter = mock(SseEmitter.class);
        InterviewSession sessionRef = mock(InterviewSession.class);
        InterviewEvaluation eval = InterviewEvaluation.fallback();

        given(interviewSessionRepository.getReferenceById(sessionId)).willReturn(sessionRef);
        given(interviewMessageRepository.save(any(InterviewMessage.class))).willReturn(null);
        doThrow(IOException.class).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        // when
        interviewMessageSaver.saveAiMessageAndComplete(emitter, sessionId, "답변입니다.", eval);

        // then
        verify(emitter).completeWithError(any(IOException.class));
        verify(emitter, never()).complete();
    }
}
