package com.celestia.agent.persistence.repository;

import com.celestia.agent.persistence.entity.AppUserEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUserEntity, Long> {

    Optional<AppUserEntity> findByPhoneE164(String phoneE164);

    /** Write-once consent: sets {@code consent_at} only if it is still null (FR-009). */
    @Modifying(clearAutomatically = true)
    @Query("update AppUserEntity u set u.consentAt = :ts where u.id = :id and u.consentAt is null")
    int recordConsentIfAbsent(@Param("id") long id, @Param("ts") Instant ts);
}
