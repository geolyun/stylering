package com.stylering.chat;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySession_IdOrderByIdAsc(Long sessionId);
    List<ChatMessage> findBySession_IdOrderByIdAsc(Long sessionId, Pageable pageable);
    long countBySession_Id(Long sessionId);
    long countBySession_IdAndRole(Long sessionId, ChatMessageRole role);
    List<ChatMessage> findBySession_IdOrderByCreatedAtDesc(Long sessionId, Pageable pageable);
}
