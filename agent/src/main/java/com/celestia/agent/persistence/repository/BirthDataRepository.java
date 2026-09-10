package com.celestia.agent.persistence.repository;

import com.celestia.agent.persistence.entity.BirthDataEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BirthDataRepository extends JpaRepository<BirthDataEntity, Long> {
    Optional<BirthDataEntity> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
