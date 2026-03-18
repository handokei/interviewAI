package com.interviewai.backend.config.oauth;

import com.interviewai.backend.user.enums.OAuthProvider;
import com.interviewai.backend.user.enums.UserRole;
import com.interviewai.backend.user.model.User;
import com.interviewai.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuthProvider provider = OAuthProvider.valueOf(registrationId.toUpperCase());
        OAuth2UserInfo userInfo = resolveOAuth2UserInfo(provider, attributes);

        User user = userRepository.findByProviderAndProviderId(provider, userInfo.getProviderId())
                .map(existing -> {
                    existing.updateProfile(userInfo.getName(), userInfo.getEmail(), userInfo.getProfileImageUrl());
                    return existing;
                })
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .providerId(userInfo.getProviderId())
                                .provider(provider)
                                .email(userInfo.getEmail())
                                .name(userInfo.getName())
                                .profileImageUrl(userInfo.getProfileImageUrl())
                                .role(UserRole.USER)
                                .build()
                ));

        return new CustomOAuth2User(user, attributes);
    }

    private OAuth2UserInfo resolveOAuth2UserInfo(OAuthProvider provider, Map<String, Object> attributes) {
        return switch (provider) {
            case GITHUB -> new GithubOAuth2UserInfo(attributes);
            case GOOGLE -> new GoogleOAuth2UserInfo(attributes);
        };
    }
}
