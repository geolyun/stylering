package com.stylering.chat;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySession_IdOrderByIdAsc(Long sessionId);
    long countBySession_Id(Long sessionId);
    List<ChatMessage> findBySession_IdOrderByCreatedAtDesc(Long sessionId, Pageable pageable);
}
