package com.celestia.agent.llm;

import com.celestia.agent.persistence.store.NatalChartBundle;
import com.celestia.agent.persistence.store.StoredBirth;
import com.celestia.core.chart.NatalChart;
import com.celestia.core.chart.NatalChartFactory;
import com.celestia.core.dasha.DashaTimeline;
import com.celestia.core.dasha.DashaTimelineFactory;
import com.celestia.core.judgement.SignificatorTable;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import com.celestia.geo.BirthMomentFlag;
import com.celestia.geo.DatasetVersions;
import com.celestia.geo.ResolvedBirth;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;

/**
 * A real chart for the stub-model tests. The engine is deterministic and the
 * ephemeris data is in the tree, so a test can have an actual chart rather than a
 * mock of one — which matters here, because what the model is shown is derived
 * from it and {@code ModelContextLeakTest} asserts on exactly that.
 */
public final class TurnFixtures {

    /** Pune, 1985-06-15 14:30 IST — the same birth SPEC-008's fixtures use. */
    public static final BirthData PUNE_1985 =
            new BirthData(Instant.parse("1985-06-15T09:00:00Z"), 18.5204, 73.8567);

    public static final String OWNER = "+919876543210";

    private static NatalChartBundle cached;

    private TurnFixtures() {}

    public static synchronized NatalChartBundle bundle() {
        if (cached == null) {
            SwissEphemerisConfig config = SwissEphemerisConfig.resolve();
            var positions = new SwissEphemerisPositionProvider(config);
            var houses = new SwissEphemerisHouseProvider(config);
            NatalChart chart = new NatalChartFactory(positions, houses).cast(PUNE_1985);
            DashaTimeline dasha = new DashaTimelineFactory(positions).at(PUNE_1985);
            cached = new NatalChartBundle(chart, SignificatorTable.of(chart), dasha, "test-engine");
        }
        return cached;
    }

    /** A seeded birth, so a handler test can have one without a database. */
    public static StoredBirth storedBirth() {
        return new StoredBirth(
                "Owner",
                "Pune, Maharashtra, India",
                new ResolvedBirth(
                        LocalDateTime.of(1985, 6, 15, 14, 30),
                        ZoneId.of("Asia/Kolkata"),
                        Instant.parse("1985-06-15T09:00:00Z"),
                        ZoneOffset.ofHoursMinutes(5, 30),
                        18.5204,
                        73.8567,
                        "Pune, Maharashtra, India",
                        "India",
                        "fixture",
                        null,
                        true,
                        EnumSet.noneOf(BirthMomentFlag.class),
                        new DatasetVersions("2026a", "2025b.26")));
    }

    public static TurnContext context(String question) {
        return TurnContext.of(1L, OWNER, question, bundle());
    }

    /** Properties naming the self-hosted provider — the default a clone builds with. */
    public static LlmProperties ollamaProperties() {
        return new LlmProperties(
                LlmProperties.OLLAMA, "http://localhost:11434", "primary-model", "fallback-model",
                "5m", 6, Duration.ofMinutes(5), 1, 16000, "", "");
    }

    /** Properties naming a third-party provider, where ADR-0026 condition 2 bites. */
    public static LlmProperties anthropicProperties() {
        return new LlmProperties(
                LlmProperties.ANTHROPIC, "", "claude-sonnet-5", "claude-sonnet-5",
                "5m", 6, Duration.ofMinutes(5), 1, 16000, "", "test-key");
    }

    /** A clock inside the chart's dasha timeline, so "running now" always resolves. */
    public static Clock clock() {
        return Clock.fixed(Instant.parse("2026-11-24T08:00:00Z"), ZoneOffset.UTC);
    }

    /**
     * A clock that jumps an hour every time it is read, so a turn can outlive its
     * deadline inside one test without the test sleeping.
     */
    public static Clock tickingClock() {
        return new Clock() {
            private Instant now = Instant.parse("2026-11-24T08:00:00Z");

            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                Instant reading = now;
                now = now.plus(Duration.ofHours(1));
                return reading;
            }
        };
    }

    /** A scope with generous bounds — the budget tests set their own. */
    public static TurnScope scope(String question) {
        return new TurnScope(context(question), clock().instant(), Duration.ofMinutes(5), 6);
    }
}
