package com.interviewai.backend.user.enums;

import com.interviewai.backend.global.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    USER_NOT_FOUND("U001", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_REGISTERED("U002", "이미 등록된 이메일입니다.", HttpStatus.CONFLICT),
    PASSWORD_CHANGE_NOT_SUPPORTED("U003", "비밀번호 변경을 지원하지 않는 계정입니다.", HttpStatus.BAD_REQUEST),
    CURRENT_PASSWORD_MISMATCH("U004", "현재 비밀번호가 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
    CONFIRM_PASSWORD_MISMATCH("U005", "비밀번호 확인이 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
    NEW_PASSWORD_SAME_AS_CURRENT("U006", "새 비밀번호가 현재 비밀번호와 동일합니다.", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
