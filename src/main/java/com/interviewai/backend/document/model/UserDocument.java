package com.interviewai.backend.document.model;

import com.interviewai.backend.document.enums.DocumentType;
import com.interviewai.backend.user.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType documentType;

    private String originalFileName;

    @Column(columnDefinition = "TEXT")
    private String parsedText;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public UserDocument(User user, DocumentType documentType, String originalFileName, String parsedText) {
        this.user = user;
        this.documentType = documentType;
        this.originalFileName = originalFileName;
        this.parsedText = parsedText;
    }

    public void updateParsedText(String parsedText) {
        this.parsedText = parsedText;
    }

    public void updateDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }
}
