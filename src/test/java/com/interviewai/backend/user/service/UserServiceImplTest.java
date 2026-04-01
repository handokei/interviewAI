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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @InjectMocks
    private UserServiceImpl userService;

    @Mock
    private UserRepository userRepository;

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
}
