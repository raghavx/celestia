package com.celestia.agent.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.celestia.agent.persistence.entity.ChartEntity;
import com.celestia.agent.persistence.repository.ChartRepository;
import com.celestia.agent.persistence.store.BirthDataStore;
import com.celestia.agent.persistence.store.ChartCacheService;
import com.celestia.agent.persistence.store.NatalChartBundle;
import com.celestia.agent.persistence.store.UserAccountService;
import com.celestia.ephemeris.HouseProvider;
import com.celestia.ephemeris.PositionProvider;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** SPEC-008 US2 / SC-002 — the engine-versioned chart cache. */
@Transactional
class ChartCacheServiceIT extends PersistenceIT {

    @MockitoSpyBean
    PositionProvider positionProvider;

    @MockitoSpyBean
    HouseProvider houseProvider;

    @Autowired
    ChartCacheService service;

    @Autowired
    BirthDataStore births;

    @Autowired
    UserAccountService users;

    @Autowired
    ChartRepository chartRepo;

    private long seedUser(String phone) {
        long id = users.findOrCreate(phone).id();
        births.save(id, "T", "Pune", BirthFixtures.pune1985());
        return id;
    }

    @Test
    void coldMissComputesOnceAndStoresTheRowWithTheCurrentVersion() {
        long id = seedUser("+912222200001");
        clearInvocations(positionProvider, houseProvider);

        NatalChartBundle bundle = service.natalChart(id);

        verify(positionProvider, times(1)).positions(any());
        verify(houseProvider, times(1)).houses(any());
        ChartEntity row = chartRepo.findByUserIdAndKind(id, "NATAL").orElseThrow();
        assertThat(row.getEngineVersion()).isEqualTo(bundle.engineVersion());
    }

    @Test
    void warmHitComputesZeroTimes() {
        long id = seedUser("+912222200002");
        service.natalChart(id); // populate
        clearInvocations(positionProvider, houseProvider);

        NatalChartBundle bundle = service.natalChart(id);

        verify(positionProvider, never()).positions(any());
        verify(houseProvider, never()).houses(any());
        assertThat(bundle.chart()).isNotNull();
        assertThat(bundle.significators()).isNotNull();
        assertThat(bundle.dasha()).isNotNull();
    }

    @Test
    void aStaleEngineVersionTriggersOneRecomputeAndOverwritesTheRow() {
        long id = seedUser("+912222200003");
        service.natalChart(id);
        ChartEntity row = chartRepo.findByUserIdAndKind(id, "NATAL").orElseThrow();
        String staleJson = row.getComputedJson();
        row.setEngineVersion("kp-0/old/old/old");
        chartRepo.save(row);
        clearInvocations(positionProvider, houseProvider);

        NatalChartBundle bundle = service.natalChart(id);

        verify(positionProvider, times(1)).positions(any());
        ChartEntity after = chartRepo.findByUserIdAndKind(id, "NATAL").orElseThrow();
        assertThat(after.getEngineVersion()).isEqualTo(bundle.engineVersion())
                .isNotEqualTo("kp-0/old/old/old");
        // The recomputed JSON is byte-identical (same birth, same engine) — only
        // the stale engine_version was wrong; the row is now consistent again.
        assertThat(after.getComputedJson()).isEqualTo(staleJson).contains("\"schema\"");
    }

    @Test
    void aCorruptComputedJsonForcesRecompute() {
        long id = seedUser("+912222200004");
        service.natalChart(id);
        ChartEntity row = chartRepo.findByUserIdAndKind(id, "NATAL").orElseThrow();
        row.setComputedJson("{\"schema\":1,\"ephemeris\":\"broken\"}");
        chartRepo.save(row);
        clearInvocations(positionProvider, houseProvider);

        service.natalChart(id);

        verify(positionProvider, times(1)).positions(any());
    }
}
