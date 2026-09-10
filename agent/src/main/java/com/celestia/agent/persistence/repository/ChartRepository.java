package com.celestia.agent.persistence.repository;

import com.celestia.agent.persistence.entity.ChartEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChartRepository extends JpaRepository<ChartEntity, Long> {
    Optional<ChartEntity> findByUserIdAndKind(Long userId, String kind);
    void deleteByUserId(Long userId);
}
