package com.interviewai.backend.interview.repository;

import com.interviewai.backend.interview.model.InterviewSessionDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewSessionDocumentRepository extends JpaRepository<InterviewSessionDocument, Long> {

    List<InterviewSessionDocument> findBySessionId(Long sessionId);

    void deleteBySessionId(Long sessionId);

    void deleteAllBySessionIdIn(List<Long> sessionIds);
}
