package com.interviewai.backend.interview.repository;

import com.interviewai.backend.interview.model.InterviewMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewMessageRepository extends JpaRepository<InterviewMessage, Long> {

    List<InterviewMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
}
