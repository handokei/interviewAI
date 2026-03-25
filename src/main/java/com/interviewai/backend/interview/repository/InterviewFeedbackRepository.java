package com.interviewai.backend.interview.repository;

import com.interviewai.backend.interview.model.InterviewFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface InterviewFeedbackRepository extends JpaRepository<InterviewFeedback, Long> {

    Optional<InterviewFeedback> findBySessionId(Long sessionId);

    @Query("SELECT AVG(f.overallScore) FROM InterviewFeedback f WHERE f.session.user.id = :userId")
    Double findAverageScoreByUserId(Long userId);

    void deleteBySessionId(Long sessionId);

    void deleteAllBySessionIdIn(List<Long> sessionIds);
}
