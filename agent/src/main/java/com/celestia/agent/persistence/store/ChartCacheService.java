package com.celestia.agent.persistence.store;

import com.celestia.agent.persistence.CurrentEngineVersion;
import com.celestia.agent.persistence.entity.ChartEntity;
import com.celestia.agent.persistence.json.ChartCodec;
import com.celestia.agent.persistence.repository.ChartRepository;
import com.celestia.core.chart.NatalChart;
import com.celestia.core.chart.NatalChartFactory;
import com.celestia.core.dasha.DashaTimeline;
import com.celestia.core.judgement.SignificatorTable;
import com.celestia.ephemeris.Ayanamsa;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.EphemerisResult;
import com.celestia.ephemeris.Graha;
import com.celestia.ephemeris.HouseProvider;
import com.celestia.ephemeris.HouseResult;
import com.celestia.ephemeris.PositionProvider;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The engine-versioned natal-chart cache (SPEC-008 US2, Constitution III).
 *
 * <p>Serves the stored {@code chart} row only when its {@code engine_version}
 * equals what {@link CurrentEngineVersion} would stamp on a fresh result and the
 * JSON deserialises; otherwise it recomputes via the pure {@code core} engine,
 * overwrites the row, and returns fresh. Recompute-when-in-doubt.
 *
 * <p>Horary caching is SPEC-015's concern (it carries the judgment moment/place);
 * the {@code chart} table's {@code HORARY} kind + {@code horary_query} are ready.
 */
@Service
public class ChartCacheService {

    private static final String KIND = "NATAL";

    private final BirthDataStore birthDataStore;
    private final ChartRepository chartRepo;
    private final ChartCodec codec;
    private final CurrentEngineVersion currentEngineVersion;
    private final PositionProvider positionProvider;
    private final HouseProvider houseProvider;

    public ChartCacheService(BirthDataStore birthDataStore, ChartRepository chartRepo,
            ChartCodec codec, CurrentEngineVersion currentEngineVersion,
            PositionProvider positionProvider, HouseProvider houseProvider) {
        this.birthDataStore = birthDataStore;
        this.chartRepo = chartRepo;
        this.codec = codec;
        this.currentEngineVersion = currentEngineVersion;
        this.positionProvider = positionProvider;
        this.houseProvider = houseProvider;
    }

    @Transactional
    public NatalChartBundle natalChart(long userId) {
        BirthData birth = birthDataStore.load(userId)
                .orElseThrow(() -> new IllegalStateException("no birth_data for user " + userId))
                .birthData();
        String expected = currentEngineVersion.id(birth);

        Optional<ChartEntity> row = chartRepo.findByUserIdAndKind(userId, KIND);
        if (row.isPresent() && expected.equals(row.get().getEngineVersion())) {
            Optional<NatalChart> cached = codec.read(row.get().getComputedJson());
            if (cached.isPresent()) {
                return bundle(cached.get(), expected);
            }
        }
        return recompute(userId, birth, expected, row.orElse(null));
    }

    private NatalChartBundle recompute(long userId, BirthData birth, String expected, ChartEntity existing) {
        EphemerisResult ephemeris = positionProvider.positions(birth.instant());
        HouseResult houses = houseProvider.houses(birth);
        NatalChart chart = NatalChartFactory.assemble(birth, ephemeris, houses);
        String json = codec.write(ephemeris, houses);

        ChartEntity entity = existing != null
                ? existing
                : new ChartEntity(userId, KIND, Ayanamsa.KP_NEW.name(),
                        houses.houseSystem().name(), expected, json);
        entity.setEngineVersion(expected);
        entity.setComputedJson(json);
        try {
            chartRepo.save(entity);
        } catch (DataIntegrityViolationException race) {
            // A concurrent cold compute won the (user_id, kind) unique key; its
            // value is equivalent — read it back.
            ChartEntity winner = chartRepo.findByUserIdAndKind(userId, KIND).orElseThrow(() -> race);
            NatalChart cached = codec.read(winner.getComputedJson()).orElse(chart);
            return bundle(cached, winner.getEngineVersion());
        }
        return bundle(chart, expected);
    }

    private static NatalChartBundle bundle(NatalChart chart, String engineVersion) {
        SignificatorTable significators = SignificatorTable.of(chart);
        DashaTimeline dasha = DashaTimeline.from(
                chart.birthData().instant(),
                chart.positions().get(Graha.MOON).longitude(),
                chart.accuracy(),
                chart.engineVersion());
        return new NatalChartBundle(chart, significators, dasha, engineVersion);
    }
}
