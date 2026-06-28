package com.interviewai.backend.document.repository;

import com.interviewai.backend.document.model.UserDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserDocumentRepository extends JpaRepository<UserDocument, Long> {

    List<UserDocument> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<UserDocument> findByUserId(Long userId, Pageable pageable);

    Optional<UserDocument> findByIdAndUserId(Long id, Long userId);

    List<UserDocument> findAllByIdInAndUserId(List<Long> ids, Long userId);
}
