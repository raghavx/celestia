package com.celestia.agent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.agent.persistence.CurrentEngineVersion;
import com.celestia.agent.persistence.store.BirthDataStore;
import com.celestia.agent.persistence.store.ChartCacheService;
import com.celestia.agent.persistence.store.NatalChartBundle;
import com.celestia.agent.persistence.store.UserAccountService;
import com.celestia.core.chart.NatalChart;
import com.celestia.core.chart.NatalChartFactory;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.HouseProvider;
import com.celestia.ephemeris.PositionProvider;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * SPEC-008 P1 — the cache path shares the Constitution III golden gate.
 *
 * <p>{@code EngineConfig} resolves {@link com.celestia.ephemeris.swisseph.SwissEphemerisConfig}
 * exactly as the golden-chart suite does, so a cold cache-compute must equal a
 * direct {@code NatalChartFactory.cast}, and the stamped {@code engine_version}
 * must equal the chart's own.
 */
@Transactional
class ChartCacheGoldenIT extends PersistenceIT {

    @Autowired
    ChartCacheService service;

    @Autowired
    BirthDataStore births;

    @Autowired
    UserAccountService users;

    @Autowired
    PositionProvider positionProvider;

    @Autowired
    HouseProvider houseProvider;

    @Autowired
    CurrentEngineVersion currentEngineVersion;

    @Test
    void aColdCacheComputeEqualsADirectCastAndStampsTheChartsOwnVersion() {
        long id = users.findOrCreate("+914444400001").id();
        births.save(id, "Golden", "Pune", BirthFixtures.pune1985());
        BirthData birth = BirthFixtures.pune1985().birthData();

        NatalChart direct = new NatalChartFactory(positionProvider, houseProvider).cast(birth);
        NatalChartBundle cached = service.natalChart(id);

        assertThat(cached.chart().positions()).isEqualTo(direct.positions());
        assertThat(cached.chart().cusps()).isEqualTo(direct.cusps());
        assertThat(cached.chart().ascendant()).isEqualTo(direct.ascendant());
        assertThat(cached.chart().engineVersion()).isEqualTo(direct.engineVersion());
        assertThat(cached.engineVersion())
                .isEqualTo(direct.engineVersion().id())
                .isEqualTo(currentEngineVersion.id(birth));
    }
}
