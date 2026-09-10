package com.celestia.agent.persistence.store;

import com.celestia.agent.persistence.CurrentEngineVersion;
import com.celestia.agent.persistence.entity.PredictionEntity;
import com.celestia.agent.persistence.json.PredictionCodec;
import com.celestia.agent.persistence.repository.PredictionRepository;
import com.celestia.core.prediction.DailyPrediction;
import com.celestia.core.prediction.DailyPredictionFactory;
import com.celestia.ephemeris.BirthData;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The engine-versioned daily-prediction cache (SPEC-008 US2). Keyed on
 * {@code (user, for_date, DAILY)}; recomputes on an engine-version change, a
 * different date, or a deserialise failure. The reading is placed at the birth
 * longitude by default (SPEC-017 may override).
 */
@Service
public class PredictionCacheService {

    private static final String KIND = "DAILY";

    private final BirthDataStore birthDataStore;
    private final ChartCacheService chartCacheService;
    private final PredictionRepository repo;
    private final PredictionCodec codec;
    private final CurrentEngineVersion currentEngineVersion;
    private final DailyPredictionFactory factory;

    public PredictionCacheService(BirthDataStore birthDataStore, ChartCacheService chartCacheService,
            PredictionRepository repo, PredictionCodec codec,
            CurrentEngineVersion currentEngineVersion, DailyPredictionFactory factory) {
        this.birthDataStore = birthDataStore;
        this.chartCacheService = chartCacheService;
        this.repo = repo;
        this.codec = codec;
        this.currentEngineVersion = currentEngineVersion;
        this.factory = factory;
    }

    @Transactional
    public DailyPrediction dailyPrediction(long userId, LocalDate date) {
        BirthData birth = birthDataStore.load(userId)
                .orElseThrow(() -> new IllegalStateException("no birth_data for user " + userId))
                .birthData();
        String expected = currentEngineVersion.id(birth);

        Optional<PredictionEntity> row = repo.findByUserIdAndForDateAndKind(userId, date, KIND);
        if (row.isPresent() && expected.equals(row.get().getEngineVersion())) {
            Optional<DailyPrediction> cached = codec.read(row.get().getPayloadJson());
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        DailyPrediction fresh = factory.predict(
                chartCacheService.natalChart(userId).chart(), date, birth.longitude());
        String json = codec.write(fresh);
        PredictionEntity entity = row.orElseGet(
                () -> new PredictionEntity(userId, date, KIND, expected, json));
        entity.setEngineVersion(expected);
        entity.setPayloadJson(json);
        try {
            repo.save(entity);
        } catch (DataIntegrityViolationException race) {
            return repo.findByUserIdAndForDateAndKind(userId, date, KIND)
                    .flatMap(e -> codec.read(e.getPayloadJson()))
                    .orElse(fresh);
        }
        return fresh;
    }
}
