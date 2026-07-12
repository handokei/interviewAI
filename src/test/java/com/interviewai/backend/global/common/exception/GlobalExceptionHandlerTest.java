package com.interviewai.backend.global.common.exception;

import com.interviewai.backend.global.common.response.ApiResponse;
import com.interviewai.backend.interview.enums.InterviewErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GlobalExceptionHandler 테스트")
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("기능_테스트_BusinessException_처리_시_에러코드_HttpStatus와_코드와_메시지가_반환된다")
    void 기능_테스트_BusinessException_처리_시_에러코드_HttpStatus와_코드와_메시지가_반환된다() {
        BusinessException ex = new BusinessException(InterviewErrorCode.SESSION_NOT_FOUND);

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getCode()).isEqualTo("I001");
        assertThat(response.getBody().getMessage()).isEqualTo("면접 세션을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("기능_테스트_MethodArgumentNotValidException_처리_시_BAD_REQUEST와_VALIDATION_ERROR가_반환된다")
    void 기능_테스트_MethodArgumentNotValidException_처리_시_BAD_REQUEST와_VALIDATION_ERROR가_반환된다() {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getAllErrors()).thenReturn(List.of(new ObjectError("field", "검증 오류입니다")));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("검증 오류입니다");
    }

    @Test
    @DisplayName("기능_테스트_MethodArgumentTypeMismatchException_처리_시_BAD_REQUEST와_INVALID_PARAMETER가_반환된다")
    void 기능_테스트_MethodArgumentTypeMismatchException_처리_시_BAD_REQUEST와_INVALID_PARAMETER가_반환된다() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("interviewId");

        ResponseEntity<ApiResponse<Void>> response = handler.handleTypeMismatchException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("INVALID_PARAMETER");
        assertThat(response.getBody().getMessage()).contains("interviewId");
    }

    @Test
    @DisplayName("기능_테스트_MaxUploadSizeExceededException_처리_시_BAD_REQUEST와_FILE_TOO_LARGE가_반환된다")
    void 기능_테스트_MaxUploadSizeExceededException_처리_시_BAD_REQUEST와_FILE_TOO_LARGE가_반환된다() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(20 * 1024 * 1024L);

        ResponseEntity<ApiResponse<Void>> response = handler.handleMaxUploadSizeExceededException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    @DisplayName("기능_테스트_DataIntegrityViolationException_처리_시_BAD_REQUEST와_DATA_INTEGRITY_ERROR가_반환된다")
    void 기능_테스트_DataIntegrityViolationException_처리_시_BAD_REQUEST와_DATA_INTEGRITY_ERROR가_반환된다() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("constraint violation");

        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrityViolationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("DATA_INTEGRITY_ERROR");
    }

    @Test
    @DisplayName("기능_테스트_Exception_처리_시_INTERNAL_SERVER_ERROR가_반환된다")
    void 기능_테스트_Exception_처리_시_INTERNAL_SERVER_ERROR가_반환된다() {
        Exception ex = new RuntimeException("예상치 못한 오류");

        ResponseEntity<ApiResponse<Void>> response = handler.handleException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_SERVER_ERROR");
    }

    @Test
    @DisplayName("기능_테스트_WebClientResponseException_429_처리_시_TOO_MANY_REQUESTS와_LLM_RATE_LIMITED가_반환된다")
    void 기능_테스트_WebClientResponseException_429_처리_시_TOO_MANY_REQUESTS와_LLM_RATE_LIMITED가_반환된다() {
        WebClientResponseException ex = WebClientResponseException.create(
                429, "Too Many Requests", null, null, null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleWebClientResponseException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody().getCode()).isEqualTo("I010");
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    @DisplayName("기능_테스트_WebClientResponseException_TooManyRequests_서브타입_처리_시_429가_반환된다")
    void 기능_테스트_WebClientResponseException_TooManyRequests_서브타입_처리_시_429가_반환된다() {
        // create()는 429에 대해 TooManyRequests 서브타입 인스턴스를 반환한다
        WebClientResponseException ex = WebClientResponseException.create(
                429, "Too Many Requests", null, null, null);
        assertThat(ex).isInstanceOf(WebClientResponseException.TooManyRequests.class);

        ResponseEntity<ApiResponse<Void>> response = handler.handleWebClientResponseException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody().getCode()).isEqualTo("I010");
    }

    @Test
    @DisplayName("기능_테스트_WebClientResponseException_5xx_처리_시_BAD_GATEWAY와_LLM_UPSTREAM_ERROR가_반환된다")
    void 기능_테스트_WebClientResponseException_5xx_처리_시_BAD_GATEWAY와_LLM_UPSTREAM_ERROR가_반환된다() {
        WebClientResponseException ex = WebClientResponseException.create(
                503, "Service Unavailable", null, null, null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleWebClientResponseException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().getCode()).isEqualTo("I011");
    }
}
