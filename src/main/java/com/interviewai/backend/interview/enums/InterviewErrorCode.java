package com.interviewai.backend.interview.enums;

import com.interviewai.backend.global.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum InterviewErrorCode implements ErrorCode {

    SESSION_NOT_FOUND("I001", "면접 세션을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    SESSION_ALREADY_COMPLETED("I002", "이미 완료된 면접 세션입니다.", HttpStatus.BAD_REQUEST),
    INVALID_INTERVIEW_MODE("I003", "잘못된 면접 모드입니다.", HttpStatus.BAD_REQUEST),
    FEEDBACK_NOT_FOUND("I004", "피드백을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    DOCUMENT_REQUIRED("I005", "이 면접 모드에서는 문서가 필요합니다.", HttpStatus.BAD_REQUEST),
    SESSION_CANNOT_CANCEL("I006", "진행 중인 면접 세션만 취소할 수 있습니다.", HttpStatus.BAD_REQUEST),
    FIRST_QUESTION_ALREADY_SENT("I007", "첫 질문이 이미 전송되었습니다.", HttpStatus.CONFLICT),
    MESSAGE_NOT_FOUND("I008", "메시지를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    SSE_SEND_FAILED("I009", "SSE 토큰 전송에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    LLM_RATE_LIMITED("I010", "AI 요청이 일시적으로 혼잡합니다. 잠시 후 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS),
    LLM_UPSTREAM_ERROR("I011", "AI 서비스 호출 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.", HttpStatus.BAD_GATEWAY);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
