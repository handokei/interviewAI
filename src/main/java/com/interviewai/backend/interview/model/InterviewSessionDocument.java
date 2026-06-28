package com.interviewai.backend.interview.model;

import com.interviewai.backend.document.model.UserDocument;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "interview_session_documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewSessionDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_session_id", nullable = false)
    private InterviewSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private UserDocument document;

    @Builder
    public InterviewSessionDocument(InterviewSession session, UserDocument document) {
        this.session = session;
        this.document = document;
    }
}
