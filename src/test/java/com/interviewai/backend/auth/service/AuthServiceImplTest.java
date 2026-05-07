package com.interviewai.backend.auth.service;

import com.interviewai.backend.auth.model.RefreshToken;
import com.interviewai.backend.auth.repository.RefreshTokenRepository;
import com.interviewai.backend.auth.service.dto.AuthTokenServiceDto;
import com.interviewai.backend.global.common.exception.BusinessException;
import com.interviewai.backend.global.config.JwtProperties;
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

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @InjectMocks
    private AuthServiceImpl authService;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtProperties jwtProperties;

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
        RefreshToken storedToken = RefreshToken.builder()
                .userId(userId)
                .token(refreshToken)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        given(jwtProvider.validateToken(refreshToken)).willReturn(true);
        given(jwtProvider.getUserId(refreshToken)).willReturn(userId);
        given(refreshTokenRepository.findByToken(refreshToken)).willReturn(Optional.of(storedToken));
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(jwtProvider.generateAccessToken(any(), any())).willReturn("new.access.token");
        given(jwtProvider.generateRefreshToken(any())).willReturn("new.refresh.token");
        given(jwtProperties.getRefreshExpiration()).willReturn(604800000L);
        given(refreshTokenRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        AuthTokenServiceDto result = authService.refreshToken(refreshToken);

        // then
        assertThat(result.getAccessToken()).isEqualTo("new.access.token");
        assertThat(result.getRefreshToken()).isEqualTo("new.refresh.token");
        verify(refreshTokenRepository).deleteByToken(refreshToken);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
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
        RefreshToken storedToken = RefreshToken.builder()
                .userId(nonExistentUserId)
                .token(refreshToken)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        given(jwtProvider.validateToken(refreshToken)).willReturn(true);
        given(jwtProvider.getUserId(refreshToken)).willReturn(nonExistentUserId);
        given(refreshTokenRepository.findByToken(refreshToken)).willReturn(Optional.of(storedToken));
        given(userRepository.findById(nonExistentUserId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.refreshToken(refreshToken))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("사용자");
    }

    @Test
    @DisplayName("예외_테스트_DB에_없는_리프레시_토큰이면_해당_유저의_모든_토큰을_삭제하고_예외가_발생한다")
    void 예외_테스트_DB에_없는_리프레시_토큰이면_해당_유저의_모든_토큰을_삭제하고_예외가_발생한다() {
        // given
        String stolenToken = "stolen.refresh.token";
        Long userId = 1L;

        given(jwtProvider.validateToken(stolenToken)).willReturn(true);
        given(jwtProvider.getUserId(stolenToken)).willReturn(userId);
        given(refreshTokenRepository.findByToken(stolenToken)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.refreshToken(stolenToken))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("유효하지 않은 토큰");
        verify(refreshTokenRepository).deleteAllByUserId(userId);
    }

}
