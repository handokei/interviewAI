package com.interviewai.backend.global.config.oauth;

import com.interviewai.backend.user.enums.OAuthProvider;
import com.interviewai.backend.user.enums.UserRole;
import com.interviewai.backend.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CustomOAuth2User 테스트")
class CustomOAuth2UserTest {

    private User user;
    private Map<String, Object> attributes;
    private CustomOAuth2User customOAuth2User;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .providerId("12345")
                .provider(OAuthProvider.GITHUB)
                .email("test@example.com")
                .name("테스트유저")
                .profileImageUrl("https://example.com/avatar.png")
                .role(UserRole.USER)
                .build();
        attributes = Map.of("id", 12345, "login", "testuser");
        customOAuth2User = new CustomOAuth2User(user, attributes);
    }

    @Test
    @DisplayName("기능_테스트_getAttributes_주입한_속성_맵이_반환된다")
    void 기능_테스트_getAttributes_주입한_속성_맵이_반환된다() {
        assertThat(customOAuth2User.getAttributes()).isEqualTo(attributes);
    }

    @Test
    @DisplayName("기능_테스트_getAuthorities_ROLE_USER_권한이_포함된다")
    void 기능_테스트_getAuthorities_ROLE_USER_권한이_포함된다() {
        assertThat(customOAuth2User.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
    }

    @Test
    @DisplayName("기능_테스트_getName_유저_이름이_반환된다")
    void 기능_테스트_getName_유저_이름이_반환된다() {
        assertThat(customOAuth2User.getName()).isEqualTo("테스트유저");
    }

    @Test
    @DisplayName("기능_테스트_getUserId_유저_ID가_반환된다")
    void 기능_테스트_getUserId_유저_ID가_반환된다() {
        assertThat(customOAuth2User.getUserId()).isNull();
    }

    @Test
    @DisplayName("기능_테스트_getEmail_유저_이메일이_반환된다")
    void 기능_테스트_getEmail_유저_이메일이_반환된다() {
        assertThat(customOAuth2User.getEmail()).isEqualTo("test@example.com");
    }
}
