package com.interviewai.backend.interview.repository;

import com.interviewai.backend.interview.model.InterviewMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import com.interviewai.backend.interview.enums.MessageRole;

import java.util.List;
import java.util.Optional;

public interface InterviewMessageRepository extends JpaRepository<InterviewMessage, Long> {

    List<InterviewMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    boolean existsBySessionIdAndRole(Long sessionId, MessageRole role);

    void deleteBySessionId(Long sessionId);

    void deleteAllBySessionIdIn(List<Long> sessionIds);

    Optional<InterviewMessage> findTopBySessionIdAndRoleOrderByCreatedAtDesc(Long sessionId, MessageRole role);
}
