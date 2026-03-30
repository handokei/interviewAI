package com.interviewai.backend.interview.model;

import com.interviewai.backend.interview.enums.AnswerLevel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_feedback")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private InterviewSession session;

    @Enumerated(EnumType.STRING)
    private AnswerLevel overallLevel;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String improvements;

    @Column(columnDefinition = "TEXT")
    private String fullReport;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public InterviewFeedback(InterviewSession session, AnswerLevel overallLevel,
                             String strengths, String improvements, String fullReport) {
        this.session = session;
        this.overallLevel = overallLevel;
        this.strengths = strengths;
        this.improvements = improvements;
        this.fullReport = fullReport;
    }
}
