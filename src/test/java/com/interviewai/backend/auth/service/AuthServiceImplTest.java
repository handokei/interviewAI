package com.interviewai.backend.auth.service;

import com.interviewai.backend.auth.service.dto.AuthTokenServiceDto;
import com.interviewai.backend.common.exception.BusinessException;
import com.interviewai.backend.config.jwt.JwtProvider;
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

    // Mockito argument matchers helper
    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
