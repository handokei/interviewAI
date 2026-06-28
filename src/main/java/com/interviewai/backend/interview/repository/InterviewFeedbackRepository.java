package com.interviewai.backend.interview.repository;

import com.interviewai.backend.interview.model.InterviewFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewFeedbackRepository extends JpaRepository<InterviewFeedback, Long> {

    Optional<InterviewFeedback> findBySessionId(Long sessionId);

    void deleteBySessionId(Long sessionId);

    void deleteAllBySessionIdIn(List<Long> sessionIds);
}
