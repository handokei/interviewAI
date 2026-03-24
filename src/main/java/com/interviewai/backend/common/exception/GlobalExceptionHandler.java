package com.interviewai.backend.common.exception;

import com.interviewai.backend.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("BusinessException: {}", e.getMessage());
        return ResponseEntity
                .status(e.getErrorCode().getHttpStatus())
                .body(ApiResponse.fail(e.getErrorCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.warn("ValidationException: {}", message);
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(new ErrorCode() {
                    public String getCode() { return "VALIDATION_ERROR"; }
                    public String getMessage() { return message; }
                    public org.springframework.http.HttpStatus getHttpStatus() {
                        return org.springframework.http.HttpStatus.BAD_REQUEST;
                    }
                }));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("MethodArgumentTypeMismatchException: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(new ErrorCode() {
                    public String getCode() { return "INVALID_PARAMETER"; }
                    public String getMessage() { return "잘못된 파라미터 값입니다: " + e.getName(); }
                    public org.springframework.http.HttpStatus getHttpStatus() {
                        return org.springframework.http.HttpStatus.BAD_REQUEST;
                    }
                }));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("파일 크기 초과: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(new ErrorCode() {
                    public String getCode() { return "FILE_TOO_LARGE"; }
                    public String getMessage() { return "파일 크기가 너무 큽니다."; }
                    public org.springframework.http.HttpStatus getHttpStatus() {
                        return org.springframework.http.HttpStatus.BAD_REQUEST;
                    }
                }));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        log.warn("DataIntegrityViolationException: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(new ErrorCode() {
                    public String getCode() { return "DATA_INTEGRITY_ERROR"; }
                    public String getMessage() { return "데이터 처리 중 오류가 발생했습니다."; }
                    public org.springframework.http.HttpStatus getHttpStatus() {
                        return org.springframework.http.HttpStatus.BAD_REQUEST;
                    }
                }));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unexpected Exception: ", e);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.fail(new ErrorCode() {
                    public String getCode() { return "INTERNAL_SERVER_ERROR"; }
                    public String getMessage() { return "서버 내부 오류가 발생했습니다."; }
                    public org.springframework.http.HttpStatus getHttpStatus() {
                        return org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
                    }
                }));
    }
}
