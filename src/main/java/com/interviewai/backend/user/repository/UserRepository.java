package com.interviewai.backend.user.repository;

import com.interviewai.backend.user.enums.OAuthProvider;
import com.interviewai.backend.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderId(OAuthProvider provider, String providerId);

    Optional<User> findByEmail(String email);
}
