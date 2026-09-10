package com.celestia.agent.persistence.repository;

import com.celestia.agent.persistence.entity.AppUserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUserEntity, Long> {
    Optional<AppUserEntity> findByPhoneE164(String phoneE164);
}
