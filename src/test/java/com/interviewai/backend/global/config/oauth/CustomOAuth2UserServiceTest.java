package com.interviewai.backend.global.config.oauth;

import com.interviewai.backend.user.enums.OAuthProvider;
import com.interviewai.backend.user.enums.UserRole;
import com.interviewai.backend.user.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CustomOAuth2UserService 토큰 저장 로직 테스트")
class CustomOAuth2UserServiceTest {

    @Test
    @DisplayName("기능_테스트_GitHub_신규_사용자_생성시_토큰이_포함된다")
    void 기능_테스트_GitHub_신규_사용자_생성시_토큰이_포함된다() {
        User user = User.builder()
                .providerId("12345")
                .provider(OAuthProvider.GITHUB)
                .email("test@test.com")
                .name("testuser")
                .githubAccessToken("gho_test_token")
                .role(UserRole.USER)
                .build();

        assertThat(user.getGithubAccessToken()).isEqualTo("gho_test_token");
        assertThat(user.getProvider()).isEqualTo(OAuthProvider.GITHUB);
    }

    @Test
    @DisplayName("기능_테스트_기존_GitHub_사용자_재로그인시_토큰이_갱신된다")
    void 기능_테스트_기존_GitHub_사용자_재로그인시_토큰이_갱신된다() {
        User user = User.builder()
                .providerId("12345")
                .provider(OAuthProvider.GITHUB)
                .email("test@test.com")
                .name("testuser")
                .role(UserRole.USER)
                .build();

        assertThat(user.getGithubAccessToken()).isNull();

        user.updateGithubAccessToken("gho_new_token");

        assertThat(user.getGithubAccessToken()).isEqualTo("gho_new_token");
    }

    @Test
    @DisplayName("기능_테스트_Google_사용자는_githubAccessToken이_null이다")
    void 기능_테스트_Google_사용자는_githubAccessToken이_null이다() {
        User user = User.builder()
                .providerId("google123")
                .provider(OAuthProvider.GOOGLE)
                .email("google@test.com")
                .name("구글유저")
                .role(UserRole.USER)
                .build();

        assertThat(user.getGithubAccessToken()).isNull();
    }

    @Test
    @DisplayName("기능_테스트_토큰_갱신_후_다시_갱신할_수_있다")
    void 기능_테스트_토큰_갱신_후_다시_갱신할_수_있다() {
        User user = User.builder()
                .providerId("12345")
                .provider(OAuthProvider.GITHUB)
                .email("test@test.com")
                .name("testuser")
                .githubAccessToken("old_token")
                .role(UserRole.USER)
                .build();

        user.updateGithubAccessToken("new_token");
        assertThat(user.getGithubAccessToken()).isEqualTo("new_token");

        user.updateGithubAccessToken("newer_token");
        assertThat(user.getGithubAccessToken()).isEqualTo("newer_token");
    }

    @Test
    @DisplayName("예외_테스트_LOCAL_provider로_OAuth2_사용자_정보를_해석하면_예외가_발생한다")
    void 예외_테스트_LOCAL_provider로_OAuth2_사용자_정보를_해석하면_예외가_발생한다() {
        CustomOAuth2UserService service = new CustomOAuth2UserService(null);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service, "resolveOAuth2UserInfo", OAuthProvider.LOCAL, Map.<String, Object>of()))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }
}
