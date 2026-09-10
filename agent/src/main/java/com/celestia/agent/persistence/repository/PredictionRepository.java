package com.celestia.agent.persistence.repository;

import com.celestia.agent.persistence.entity.PredictionEntity;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PredictionRepository extends JpaRepository<PredictionEntity, Long> {
    Optional<PredictionEntity> findByUserIdAndForDateAndKind(Long userId, LocalDate forDate, String kind);
    void deleteByUserId(Long userId);
}
