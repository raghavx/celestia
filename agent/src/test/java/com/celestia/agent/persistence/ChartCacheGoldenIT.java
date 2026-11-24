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
import com.celestia.ephemeris.EngineVersion;
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
 * direct {@code NatalChartFactory.cast}.
 *
 * <p><b>The stamped {@code engine_version} is deliberately not the chart's own.</b>
 * SPEC-010 composed the cache's stamp from the engine's rules <em>plus the bundled
 * toolkit's release</em> — {@code kp-1+toolkit-2026-09-12} rather than {@code kp-1}
 * — because {@code ephemeris} cannot depend on {@code core}, so the two are joined
 * in {@link CurrentEngineVersion} (SPEC-010 research.md &sect;7). A toolkit release
 * therefore invalidates every cached chart, which is what keeps a stored answer
 * explainable by the toolkit that produced it (Constitution III, SPEC-010 FR-009).
 * This test asserted equality with the chart's own version and had been failing
 * since that change; nobody saw it because it needs Docker.
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
    void aColdCacheComputeEqualsADirectCastAndStampsTheLiveComposedVersion() {
        long id = users.findOrCreate("+914444400001").id();
        births.save(id, "Golden", "Pune", BirthFixtures.pune1985());
        BirthData birth = BirthFixtures.pune1985().birthData();

        NatalChart direct = new NatalChartFactory(positionProvider, houseProvider).cast(birth);
        NatalChartBundle cached = service.natalChart(id);

        assertThat(cached.chart().positions()).isEqualTo(direct.positions());
        assertThat(cached.chart().cusps()).isEqualTo(direct.cusps());
        assertThat(cached.chart().ascendant()).isEqualTo(direct.ascendant());
        // The chart carries the engine's own version, unchanged by caching.
        assertThat(cached.chart().engineVersion()).isEqualTo(direct.engineVersion());

        // The ROW carries the live composed version: the engine's rules plus the
        // toolkit release. Asserted against CurrentEngineVersion rather than a
        // literal, so importing a new toolkit does not break this test — it is the
        // cache invalidation working, not a regression.
        assertThat(cached.engineVersion())
                .isEqualTo(currentEngineVersion.id(birth))
                .startsWith(EngineVersion.RULES);

        // And it extends the chart's own version rather than contradicting it: same
        // rules prefix, same ephemeris identity, with the toolkit release added when
        // one has been imported (absent on a fresh clone, which is also valid).
        assertThat(cached.engineVersion()).endsWith(direct.engineVersion().id().substring(
                EngineVersion.RULES.length()));
    }
}
