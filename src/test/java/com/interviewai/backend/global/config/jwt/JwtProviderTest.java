package com.interviewai.backend.global.config.jwt;

import com.interviewai.backend.auth.enums.AuthErrorCode;
import com.interviewai.backend.global.common.exception.BusinessException;
import com.interviewai.backend.global.config.JwtProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtProvider 테스트")
class JwtProviderTest {

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(Base64.getEncoder().encodeToString(
                "test-secret-32-bytes-long-key-ok!".getBytes()));
        props.setExpiration(86400000L);
        props.setRefreshExpiration(604800000L);
        jwtProvider = new JwtProvider(props);
    }

    @Test
    @DisplayName("기능_테스트_generateAccessToken_정상_토큰이_생성된다")
    void 기능_테스트_generateAccessToken_정상_토큰이_생성된다() {
        String token = jwtProvider.generateAccessToken(1L, "test@example.com");

        assertThat(token).isNotBlank();
        Claims claims = jwtProvider.parseClaims(token);
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("email", String.class)).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("기능_테스트_generateRefreshToken_정상_토큰이_생성된다")
    void 기능_테스트_generateRefreshToken_정상_토큰이_생성된다() {
        String token = jwtProvider.generateRefreshToken(1L);

        assertThat(token).isNotBlank();
        assertThat(jwtProvider.parseClaims(token).getSubject()).isEqualTo("1");
    }

    @Test
    @DisplayName("기능_테스트_getUserId_토큰에서_userId가_추출된다")
    void 기능_테스트_getUserId_토큰에서_userId가_추출된다() {
        String token = jwtProvider.generateAccessToken(42L, "user@example.com");

        assertThat(jwtProvider.getUserId(token)).isEqualTo(42L);
    }

    @Test
    @DisplayName("기능_테스트_validateToken_유효한_토큰이면_true를_반환한다")
    void 기능_테스트_validateToken_유효한_토큰이면_true를_반환한다() {
        String token = jwtProvider.generateAccessToken(1L, "test@example.com");

        assertThat(jwtProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("기능_테스트_validateToken_만료된_토큰이면_false를_반환한다")
    void 기능_테스트_validateToken_만료된_토큰이면_false를_반환한다() {
        JwtProperties expiredProps = new JwtProperties();
        expiredProps.setSecret(Base64.getEncoder().encodeToString(
                "test-secret-32-bytes-long-key-ok!".getBytes()));
        expiredProps.setExpiration(-1000L);
        expiredProps.setRefreshExpiration(-1000L);
        JwtProvider expiredJwtProvider = new JwtProvider(expiredProps);

        String token = expiredJwtProvider.generateAccessToken(1L, "test@example.com");

        assertThat(jwtProvider.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("기능_테스트_validateToken_잘못된_토큰이면_false를_반환한다")
    void 기능_테스트_validateToken_잘못된_토큰이면_false를_반환한다() {
        assertThat(jwtProvider.validateToken("invalid.token.value")).isFalse();
    }

    @Test
    @DisplayName("예외_테스트_parseClaims_만료된_토큰이면_EXPIRED_TOKEN_예외가_발생한다")
    void 예외_테스트_parseClaims_만료된_토큰이면_EXPIRED_TOKEN_예외가_발생한다() {
        JwtProperties expiredProps = new JwtProperties();
        expiredProps.setSecret(Base64.getEncoder().encodeToString(
                "test-secret-32-bytes-long-key-ok!".getBytes()));
        expiredProps.setExpiration(-1000L);
        expiredProps.setRefreshExpiration(-1000L);
        JwtProvider expiredJwtProvider = new JwtProvider(expiredProps);
        String token = expiredJwtProvider.generateAccessToken(1L, "test@example.com");

        assertThatThrownBy(() -> jwtProvider.parseClaims(token))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.EXPIRED_TOKEN));
    }

    @Test
    @DisplayName("예외_테스트_parseClaims_잘못된_토큰이면_INVALID_TOKEN_예외가_발생한다")
    void 예외_테스트_parseClaims_잘못된_토큰이면_INVALID_TOKEN_예외가_발생한다() {
        assertThatThrownBy(() -> jwtProvider.parseClaims("invalid.token.value"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_TOKEN));
    }
}
