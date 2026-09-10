package com.celestia.core.prediction;

import com.celestia.ephemeris.Accuracy;
import com.celestia.ephemeris.EngineVersion;
import java.time.Instant;
import java.util.List;

/**
 * A structured KP daily reading (Constitution IV — no prose). Consumed by the
 * {@code getDailyPrediction} agent tool. Contract:
 * {@code specs/006-daily-prediction/contracts/daily-prediction-api.md}.
 *
 * @param referenceInstant local noon of the date
 * @param dasha the running lords and the activated house set
 * @param transit the transiting Moon / Sun contribution
 * @param verdicts one per {@link Matter}, in enum order
 * @param accuracy mirrors the transit positions
 * @param engineVersion the engine identity
 */
public record DailyPrediction(
        Instant referenceInstant,
        DashaSignificators dasha,
        TransitContribution transit,
        List<MatterVerdict> verdicts,
        Accuracy accuracy,
        EngineVersion engineVersion) {

    public DailyPrediction {
        verdicts = List.copyOf(verdicts);
    }

    public MatterVerdict verdictFor(Matter matter) {
        return verdicts.stream()
                .filter(v -> v.matter() == matter)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no verdict for " + matter));
    }
}
