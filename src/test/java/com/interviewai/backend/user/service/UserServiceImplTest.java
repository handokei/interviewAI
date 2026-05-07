package com.interviewai.backend.user.service;

import com.interviewai.backend.global.common.exception.BusinessException;
import com.interviewai.backend.user.controller.dto.UserProfileResponseDto;
import com.interviewai.backend.user.enums.OAuthProvider;
import com.interviewai.backend.user.enums.UserErrorCode;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @InjectMocks
    private UserServiceImpl userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("기능_테스트_유저_프로필_조회에_성공한다")
    void 기능_테스트_유저_프로필_조회에_성공한다() {
        // given
        Long userId = 1L;
        User user = User.builder()
                .providerId("12345")
                .provider(OAuthProvider.GITHUB)
                .email("test@test.com")
                .name("테스트유저")
                .profileImageUrl("https://example.com/image.png")
                .role(UserRole.USER)
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        UserProfileResponseDto result = userService.findUserProfile(userId);

        // then
        assertThat(result.getEmail()).isEqualTo("test@test.com");
        assertThat(result.getName()).isEqualTo("테스트유저");
        assertThat(result.getProvider()).isEqualTo(OAuthProvider.GITHUB);
        assertThat(result.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    @DisplayName("예외_테스트_유저가_존재하지_않으면_USER_NOT_FOUND_예외가_발생한다")
    void 예외_테스트_유저가_존재하지_않으면_USER_NOT_FOUND_예외가_발생한다() {
        // given
        Long nonExistentUserId = 999L;
        given(userRepository.findById(nonExistentUserId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.findUserProfile(nonExistentUserId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(UserErrorCode.USER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("기능_테스트_비밀번호_변경에_성공한다")
    void 기능_테스트_비밀번호_변경에_성공한다() {
        // given
        Long userId = 1L;
        User user = User.builder()
                .provider(OAuthProvider.LOCAL)
                .email("user@test.com")
                .name("테스트유저")
                .password("encoded_old_password")
                .role(UserRole.USER)
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("oldPassword1", "encoded_old_password")).willReturn(true);
        given(passwordEncoder.encode("newPassword1")).willReturn("encoded_new_password");

        // when
        userService.changePassword(userId, "oldPassword1", "newPassword1", "newPassword1");

        // then
        verify(passwordEncoder).encode("newPassword1");
        assertThat(user.getPassword()).isEqualTo("encoded_new_password");
    }

    @Test
    @DisplayName("예외_테스트_새_비밀번호_확인이_일치하지_않으면_예외가_발생한다")
    void 예외_테스트_새_비밀번호_확인이_일치하지_않으면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> userService.changePassword(1L, "oldPassword1", "newPassword1", "different"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("비밀번호 확인이 일치하지 않습니다");
    }

    @Test
    @DisplayName("예외_테스트_OAuth_사용자가_비밀번호_변경을_시도하면_예외가_발생한다")
    void 예외_테스트_OAuth_사용자가_비밀번호_변경을_시도하면_예외가_발생한다() {
        // given
        Long userId = 1L;
        User oauthUser = User.builder()
                .providerId("12345")
                .provider(OAuthProvider.GITHUB)
                .email("github@test.com")
                .name("깃허브유저")
                .role(UserRole.USER)
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(oauthUser));

        // when & then
        assertThatThrownBy(() -> userService.changePassword(userId, "password", "newPass1", "newPass1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("비밀번호 변경을 지원하지 않는 계정");
    }

    @Test
    @DisplayName("예외_테스트_현재_비밀번호가_틀리면_예외가_발생한다")
    void 예외_테스트_현재_비밀번호가_틀리면_예외가_발생한다() {
        // given
        Long userId = 1L;
        User user = User.builder()
                .provider(OAuthProvider.LOCAL)
                .email("user@test.com")
                .name("테스트유저")
                .password("encoded_password")
                .role(UserRole.USER)
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrongPassword", "encoded_password")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> userService.changePassword(userId, "wrongPassword", "newPass1", "newPass1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("현재 비밀번호가 일치하지 않습니다");
    }

    @Test
    @DisplayName("예외_테스트_새_비밀번호가_현재_비밀번호와_동일하면_예외가_발생한다")
    void 예외_테스트_새_비밀번호가_현재_비밀번호와_동일하면_예외가_발생한다() {
        // given
        Long userId = 1L;
        User user = User.builder()
                .provider(OAuthProvider.LOCAL)
                .email("user@test.com")
                .name("테스트유저")
                .password("encoded_password")
                .role(UserRole.USER)
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("samePass1", "encoded_password")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.changePassword(userId, "samePass1", "samePass1", "samePass1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("새 비밀번호가 현재 비밀번호와 동일");
    }

    @Test
    @DisplayName("예외_테스트_비밀번호_변경_시_사용자가_존재하지_않으면_예외가_발생한다")
    void 예외_테스트_비밀번호_변경_시_사용자가_존재하지_않으면_예외가_발생한다() {
        // given
        Long nonExistentUserId = 999L;
        given(userRepository.findById(nonExistentUserId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.changePassword(nonExistentUserId, "old", "newPass1", "newPass1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }
}
