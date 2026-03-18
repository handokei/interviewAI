package com.interviewai.backend.interview.enums;

import com.interviewai.backend.common.exception.ErrorCode;
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
    DOCUMENT_REQUIRED("I005", "이 면접 모드에서는 문서가 필요합니다.", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
