package com.celestia.agent.persistence.repository;

import com.celestia.agent.persistence.entity.HoraryQueryEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoraryQueryRepository extends JpaRepository<HoraryQueryEntity, Long> {
    Optional<HoraryQueryEntity> findByChartId(Long chartId);
}
