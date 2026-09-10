package com.celestia.agent.persistence.repository;

import com.celestia.agent.persistence.entity.GeocodeCacheEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeocodeCacheRepository extends JpaRepository<GeocodeCacheEntity, Long> {
    Optional<GeocodeCacheEntity> findByQueryNorm(String queryNorm);
}
