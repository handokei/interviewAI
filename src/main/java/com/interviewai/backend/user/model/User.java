package com.interviewai.backend.user.model;

import com.interviewai.backend.user.enums.OAuthProvider;
import com.interviewai.backend.user.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = {"githubAccessToken", "password"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OAuthProvider provider;

    @Column(unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    private String profileImageUrl;

    private String password;

    @Convert(converter = com.interviewai.backend.global.config.EncryptedStringConverter.class)
    private String githubAccessToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public User(String providerId, OAuthProvider provider, String email, String name,
                String profileImageUrl, String password, String githubAccessToken, UserRole role) {
        this.providerId = providerId;
        this.provider = provider;
        this.email = email;
        this.name = name;
        this.profileImageUrl = profileImageUrl;
        this.password = password;
        this.githubAccessToken = githubAccessToken;
        this.role = role;
    }

    public void updateProfile(String name, String email, String profileImageUrl) {
        this.name = name;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
    }

    public void updateGithubAccessToken(String githubAccessToken) {
        this.githubAccessToken = githubAccessToken;
    }

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }
}
