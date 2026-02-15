package com.stylering.chat;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    Optional<ChatSession> findByIdAndUser_Id(Long id, Long userId);
    List<ChatSession> findByUser_FirebaseUid(String firebaseUid);
}
