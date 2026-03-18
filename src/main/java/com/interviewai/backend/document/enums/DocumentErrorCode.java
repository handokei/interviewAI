package com.interviewai.backend.document.enums;

import com.interviewai.backend.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DocumentErrorCode implements ErrorCode {

    DOCUMENT_NOT_FOUND("D001", "문서를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    UNSUPPORTED_FILE_TYPE("D002", "지원하지 않는 파일 형식입니다. PDF 파일만 허용됩니다.", HttpStatus.BAD_REQUEST),
    FILE_UPLOAD_FAILED("D003", "파일 업로드에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_PARSE_FAILED("D004", "파일 파싱에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
