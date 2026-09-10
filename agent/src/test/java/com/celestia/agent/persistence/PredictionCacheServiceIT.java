package com.celestia.agent.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.celestia.agent.persistence.entity.PredictionEntity;
import com.celestia.agent.persistence.repository.PredictionRepository;
import com.celestia.agent.persistence.store.BirthDataStore;
import com.celestia.agent.persistence.store.PredictionCacheService;
import com.celestia.agent.persistence.store.UserAccountService;
import com.celestia.core.prediction.DailyPrediction;
import com.celestia.ephemeris.PositionProvider;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** SPEC-008 US2 — the engine-versioned daily-prediction cache. */
@Transactional
class PredictionCacheServiceIT extends PersistenceIT {

    @MockitoSpyBean
    PositionProvider positionProvider;

    @Autowired
    PredictionCacheService service;

    @Autowired
    BirthDataStore births;

    @Autowired
    UserAccountService users;

    @Autowired
    PredictionRepository repo;

    private static final LocalDate DATE = LocalDate.of(2025, 6, 15);

    private long seedUser(String phone) {
        long id = users.findOrCreate(phone).id();
        births.save(id, "T", "Pune", BirthFixtures.pune1985());
        return id;
    }

    @Test
    void warmHitDoesNoFurtherTransitLookups() {
        long id = seedUser("+913333300001");
        DailyPrediction first = service.dailyPrediction(id, DATE);
        clearInvocations(positionProvider);

        DailyPrediction second = service.dailyPrediction(id, DATE);

        verify(positionProvider, never()).positions(any());
        assertThat(second.verdicts()).isEqualTo(first.verdicts());
    }

    @Test
    void aDifferentDateRecomputes() {
        long id = seedUser("+913333300002");
        service.dailyPrediction(id, DATE);
        clearInvocations(positionProvider);

        service.dailyPrediction(id, DATE.plusDays(1));

        verify(positionProvider, org.mockito.Mockito.atLeastOnce()).positions(any());
        assertThat(repo.findByUserIdAndForDateAndKind(id, DATE.plusDays(1), "DAILY")).isPresent();
    }

    @Test
    void aStaleEngineVersionRecomputes() {
        long id = seedUser("+913333300003");
        service.dailyPrediction(id, DATE);
        PredictionEntity row =
                repo.findByUserIdAndForDateAndKind(id, DATE, "DAILY").orElseThrow();
        row.setEngineVersion("kp-0/old/old/old");
        repo.save(row);
        clearInvocations(positionProvider);

        service.dailyPrediction(id, DATE);

        verify(positionProvider, org.mockito.Mockito.atLeastOnce()).positions(any());
        assertThat(repo.findByUserIdAndForDateAndKind(id, DATE, "DAILY").orElseThrow()
                .getEngineVersion()).isNotEqualTo("kp-0/old/old/old");
    }
}
