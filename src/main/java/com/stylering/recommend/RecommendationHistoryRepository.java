package com.stylering.recommend;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationHistoryRepository extends JpaRepository<RecommendationHistory, Long> {
    List<RecommendationHistory> findByUser_IdOrderByCreatedAtDesc(Long userId);
}
