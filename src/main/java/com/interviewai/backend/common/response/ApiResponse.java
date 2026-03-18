package com.interviewai.backend.common.response;

import com.interviewai.backend.common.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private String message;
    private String code;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = true;
        response.data = data;
        response.message = "성공";
        return response;
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = true;
        response.data = data;
        response.message = message;
        return response;
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode) {
        ApiResponse<Void> response = new ApiResponse<>();
        response.success = false;
        response.message = errorCode.getMessage();
        response.code = errorCode.getCode();
        return response;
    }
}
