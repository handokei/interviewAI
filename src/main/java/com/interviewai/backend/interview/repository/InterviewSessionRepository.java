package com.interviewai.backend.interview.repository;

import com.interviewai.backend.interview.enums.InterviewLevel;
import com.interviewai.backend.interview.enums.InterviewMode;
import com.interviewai.backend.interview.enums.InterviewStatus;
import com.interviewai.backend.interview.model.InterviewSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    List<InterviewSession> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<InterviewSession> findByUserId(Long userId, Pageable pageable);

    Optional<InterviewSession> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, InterviewStatus status);

    long countByUserIdAndMode(Long userId, InterviewMode mode);

    long countByUserIdAndLevel(Long userId, InterviewLevel level);
}
