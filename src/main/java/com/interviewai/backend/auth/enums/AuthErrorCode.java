package com.interviewai.backend.auth.enums;

import com.interviewai.backend.global.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    INVALID_TOKEN("A001", "유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED),
    EXPIRED_TOKEN("A002", "만료된 토큰입니다.", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_NOT_FOUND("A003", "리프레시 토큰을 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED("A004", "인증이 필요합니다.", HttpStatus.UNAUTHORIZED),
    CONFIRM_PASSWORD_MISMATCH("A005", "비밀번호 확인이 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS("A006", "이메일 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
