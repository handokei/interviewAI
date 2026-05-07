package com.interviewai.backend.auth.service;

import com.interviewai.backend.auth.service.dto.AuthTokenServiceDto;
import com.interviewai.backend.global.common.exception.BusinessException;
import com.interviewai.backend.global.config.jwt.JwtProvider;
import com.interviewai.backend.user.enums.OAuthProvider;
import com.interviewai.backend.user.enums.UserRole;
import com.interviewai.backend.user.model.User;
import com.interviewai.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @InjectMocks
    private AuthServiceImpl authService;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("기능_테스트_유효한_리프레시_토큰으로_새_토큰을_발급한다")
    void 기능_테스트_유효한_리프레시_토큰으로_새_토큰을_발급한다() {
        // given
        String refreshToken = "valid.refresh.token";
        Long userId = 1L;
        User user = User.builder()
                .providerId("12345")
                .provider(OAuthProvider.GITHUB)
                .email("test@test.com")
                .name("테스트유저")
                .profileImageUrl(null)
                .role(UserRole.USER)
                .build();

        given(jwtProvider.validateToken(refreshToken)).willReturn(true);
        given(jwtProvider.getUserId(refreshToken)).willReturn(userId);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(jwtProvider.generateAccessToken(any(), any())).willReturn("new.access.token");
        given(jwtProvider.generateRefreshToken(any())).willReturn("new.refresh.token");

        // when
        AuthTokenServiceDto result = authService.refreshToken(refreshToken);

        // then
        assertThat(result.getAccessToken()).isEqualTo("new.access.token");
        assertThat(result.getRefreshToken()).isEqualTo("new.refresh.token");
    }

    @Test
    @DisplayName("예외_테스트_유효하지_않은_리프레시_토큰이면_예외가_발생한다")
    void 예외_테스트_유효하지_않은_리프레시_토큰이면_예외가_발생한다() {
        // given
        String invalidToken = "invalid.token";
        given(jwtProvider.validateToken(invalidToken)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.refreshToken(invalidToken))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("유효하지 않은 토큰");
    }

    @Test
    @DisplayName("예외_테스트_존재하지_않는_사용자의_토큰으로_갱신하면_예외가_발생한다")
    void 예외_테스트_존재하지_않는_사용자의_토큰으로_갱신하면_예외가_발생한다() {
        // given
        String refreshToken = "valid.refresh.token";
        Long nonExistentUserId = 999L;

        given(jwtProvider.validateToken(refreshToken)).willReturn(true);
        given(jwtProvider.getUserId(refreshToken)).willReturn(nonExistentUserId);
        given(userRepository.findById(nonExistentUserId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.refreshToken(refreshToken))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("사용자");
    }

    @Test
    @DisplayName("기능_테스트_이메일_회원가입에_성공한다")
    void 기능_테스트_이메일_회원가입에_성공한다() {
        // given
        String email = "user@test.com";
        String password = "password123";
        String name = "테스트유저";
        String encodedPassword = "encoded_password";

        given(userRepository.findByEmail(email)).willReturn(Optional.empty());
        given(passwordEncoder.encode(password)).willReturn(encodedPassword);
        given(userRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(jwtProvider.generateAccessToken(any(), any())).willReturn("access.token");
        given(jwtProvider.generateRefreshToken(any())).willReturn("refresh.token");

        // when
        AuthTokenServiceDto result = authService.signup(email, password, password, name);

        // then
        assertThat(result.getAccessToken()).isEqualTo("access.token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh.token");
    }

    @Test
    @DisplayName("예외_테스트_비밀번호_확인이_일치하지_않으면_예외가_발생한다")
    void 예외_테스트_비밀번호_확인이_일치하지_않으면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> authService.signup("user@test.com", "password123", "different", "유저"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("비밀번호 확인이 일치하지 않습니다");
    }

    @Test
    @DisplayName("예외_테스트_이메일이_중복되면_예외가_발생한다")
    void 예외_테스트_이메일이_중복되면_예외가_발생한다() {
        // given
        String email = "existing@test.com";
        User existingUser = User.builder()
                .providerId("12345")
                .provider(OAuthProvider.GITHUB)
                .email(email)
                .name("기존유저")
                .role(UserRole.USER)
                .build();
        given(userRepository.findByEmail(email)).willReturn(Optional.of(existingUser));

        // when & then
        assertThatThrownBy(() -> authService.signup(email, "password123", "password123", "새유저"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 등록된 이메일");
    }

    @Test
    @DisplayName("기능_테스트_이메일_로그인에_성공한다")
    void 기능_테스트_이메일_로그인에_성공한다() {
        // given
        String email = "user@test.com";
        String password = "password123";
        User user = User.builder()
                .provider(OAuthProvider.LOCAL)
                .email(email)
                .name("테스트유저")
                .password("encoded_password")
                .role(UserRole.USER)
                .build();

        given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
        given(passwordEncoder.matches(password, "encoded_password")).willReturn(true);
        given(jwtProvider.generateAccessToken(any(), any())).willReturn("access.token");
        given(jwtProvider.generateRefreshToken(any())).willReturn("refresh.token");

        // when
        AuthTokenServiceDto result = authService.login(email, password);

        // then
        assertThat(result.getAccessToken()).isEqualTo("access.token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh.token");
    }

    @Test
    @DisplayName("예외_테스트_존재하지_않는_이메일로_로그인하면_예외가_발생한다")
    void 예외_테스트_존재하지_않는_이메일로_로그인하면_예외가_발생한다() {
        // given
        given(userRepository.findByEmail("none@test.com")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.login("none@test.com", "password123"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이메일 또는 비밀번호가 올바르지 않습니다");
    }

    @Test
    @DisplayName("예외_테스트_비밀번호가_틀리면_예외가_발생한다")
    void 예외_테스트_비밀번호가_틀리면_예외가_발생한다() {
        // given
        User user = User.builder()
                .provider(OAuthProvider.LOCAL)
                .email("user@test.com")
                .name("테스트유저")
                .password("encoded_password")
                .role(UserRole.USER)
                .build();

        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong_password", "encoded_password")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.login("user@test.com", "wrong_password"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이메일 또는 비밀번호가 올바르지 않습니다");
    }

    @Test
    @DisplayName("예외_테스트_OAuth_사용자가_이메일_로그인을_시도하면_예외가_발생한다")
    void 예외_테스트_OAuth_사용자가_이메일_로그인을_시도하면_예외가_발생한다() {
        // given
        User oauthUser = User.builder()
                .providerId("12345")
                .provider(OAuthProvider.GITHUB)
                .email("github@test.com")
                .name("깃허브유저")
                .role(UserRole.USER)
                .build();

        given(userRepository.findByEmail("github@test.com")).willReturn(Optional.of(oauthUser));

        // when & then
        assertThatThrownBy(() -> authService.login("github@test.com", "password123"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이메일 또는 비밀번호가 올바르지 않습니다");
    }

    // Mockito argument matchers helper
    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
