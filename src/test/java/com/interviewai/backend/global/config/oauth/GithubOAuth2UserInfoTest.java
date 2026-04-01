package com.interviewai.backend.global.config.oauth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GithubOAuth2UserInfo 테스트")
class GithubOAuth2UserInfoTest {

    @Test
    @DisplayName("기능_테스트_getProviderId_id_값이_문자열로_반환된다")
    void 기능_테스트_getProviderId_id_값이_문자열로_반환된다() {
        GithubOAuth2UserInfo userInfo = new GithubOAuth2UserInfo(Map.of("id", 12345));

        assertThat(userInfo.getProviderId()).isEqualTo("12345");
    }

    @Test
    @DisplayName("기능_테스트_getEmail_email_값이_반환된다")
    void 기능_테스트_getEmail_email_값이_반환된다() {
        GithubOAuth2UserInfo userInfo = new GithubOAuth2UserInfo(
                Map.of("id", 1, "email", "github@example.com"));

        assertThat(userInfo.getEmail()).isEqualTo("github@example.com");
    }

    @Test
    @DisplayName("기능_테스트_getName_name이_있으면_name이_반환된다")
    void 기능_테스트_getName_name이_있으면_name이_반환된다() {
        GithubOAuth2UserInfo userInfo = new GithubOAuth2UserInfo(
                Map.of("id", 1, "name", "홍길동", "login", "honggildong"));

        assertThat(userInfo.getName()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("기능_테스트_getName_name이_null이면_login이_반환된다")
    void 기능_테스트_getName_name이_null이면_login이_반환된다() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", 1);
        attrs.put("name", null);
        attrs.put("login", "honggildong");
        GithubOAuth2UserInfo userInfo = new GithubOAuth2UserInfo(attrs);

        assertThat(userInfo.getName()).isEqualTo("honggildong");
    }

    @Test
    @DisplayName("기능_테스트_getName_name이_빈_문자열이면_login이_반환된다")
    void 기능_테스트_getName_name이_빈_문자열이면_login이_반환된다() {
        GithubOAuth2UserInfo userInfo = new GithubOAuth2UserInfo(
                Map.of("id", 1, "name", "  ", "login", "honggildong"));

        assertThat(userInfo.getName()).isEqualTo("honggildong");
    }

    @Test
    @DisplayName("기능_테스트_getProfileImageUrl_avatar_url_값이_반환된다")
    void 기능_테스트_getProfileImageUrl_avatar_url_값이_반환된다() {
        GithubOAuth2UserInfo userInfo = new GithubOAuth2UserInfo(
                Map.of("id", 1, "avatar_url", "https://avatars.githubusercontent.com/u/1"));

        assertThat(userInfo.getProfileImageUrl()).isEqualTo("https://avatars.githubusercontent.com/u/1");
    }
}
