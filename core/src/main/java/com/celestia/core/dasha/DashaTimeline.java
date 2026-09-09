package com.celestia.core.dasha;

import com.celestia.core.lordage.Longitudes;
import com.celestia.core.lordage.Nakshatra;
import com.celestia.ephemeris.Accuracy;
import com.celestia.ephemeris.EngineVersion;
import com.celestia.ephemeris.Graha;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import org.apache.commons.numbers.fraction.BigFraction;

/**
 * The Vimshottari dasha timeline for one birth: the balance at birth, the running
 * period stack for any instant, and the period list for any level and window.
 *
 * <p>Pure. Time is exact rational seconds internally (1 Vimshottari year =
 * 365.25 days = 31&#8239;557&#8239;600 s); {@link Instant}s are produced by
 * rounding a cumulative offset from the birth instant <b>once</b> per boundary.
 * Source and rules: {@code core/REFERENCES.md};
 * {@code specs/004-vimshottari-dasha/research.md}.
 */
public final class DashaTimeline {

    /** 365.25 days, exactly. */
    static final BigFraction YEAR_SECONDS = BigFraction.of(365L * 86_400L + 21_600L);

    static final BigFraction CYCLE_SECONDS = YEAR_SECONDS.multiply(Graha.CYCLE_YEARS);

    private static final BigFraction NAK_WIDTH = BigFraction.of(40, 3);
    private static final BigInteger BILLION = BigInteger.valueOf(1_000_000_000L);

    private final Instant birthInstant;
    private final Accuracy accuracy;
    private final EngineVersion engineVersion;

    private final Graha mahaLord;
    private final BigFraction elapsedFraction; // of the birth Mahadasha, [0, 1)
    /** Exact seconds from the birth instant back to the start of the birth Mahadasha (>= 0). */
    private final BigFraction elapsedSeconds;

    private DashaTimeline(
            Instant birthInstant, Accuracy accuracy, EngineVersion engineVersion,
            Graha mahaLord, BigFraction elapsedFraction, BigFraction elapsedSeconds) {
        this.birthInstant = birthInstant;
        this.accuracy = accuracy;
        this.engineVersion = engineVersion;
        this.mahaLord = mahaLord;
        this.elapsedFraction = elapsedFraction;
        this.elapsedSeconds = elapsedSeconds;
    }

    public static DashaTimeline from(
            Instant birthInstant, double moonLongitude, Accuracy accuracy, EngineVersion engineVersion) {
        // classify the nakshatra and the traversed fraction from one exact quantity,
        // so the maha lord and the fraction can never disagree at a ULP boundary
        BigFraction lambda = BigFraction.from(Longitudes.normalize(moonLongitude));
        BigFraction pos = lambda.divide(NAK_WIDTH); // position in nakshatra units, [0, 27)
        BigInteger nakIndex = pos.getNumerator().divide(pos.getDenominator()); // floor, pos >= 0
        BigFraction fraction = pos.subtract(BigFraction.of(nakIndex));
        Nakshatra nakshatra = Nakshatra.values()[nakIndex.intValueExact() % 27];
        Graha mahaLord = nakshatra.lord();

        BigFraction elapsedSeconds = fraction.multiply(mahaLord.years()).multiply(YEAR_SECONDS);
        return new DashaTimeline(
                birthInstant, accuracy, engineVersion, mahaLord, fraction, elapsedSeconds);
    }

    public Instant birthInstant() {
        return birthInstant;
    }

    public Accuracy accuracy() {
        return accuracy;
    }

    public EngineVersion engineVersion() {
        return engineVersion;
    }

    public DashaBalance balanceAtBirth() {
        BigFraction mahaSeconds = BigFraction.of(mahaLord.years()).multiply(YEAR_SECONDS);
        BigFraction balanceSeconds = mahaSeconds.subtract(elapsedSeconds);
        Instant mahaStart = plusSeconds(birthInstant, elapsedSeconds.negate());
        Instant mahaEnd = plusSeconds(birthInstant, balanceSeconds);
        return new DashaBalance(
                mahaLord, elapsedFraction.doubleValue(),
                toDuration(elapsedSeconds), toDuration(balanceSeconds), mahaStart, mahaEnd);
    }

    // --- exact-seconds <-> Instant / Duration -------------------------------------------------

    /** {@code base + seconds}, seconds exact (may be negative), rounded to the nearest nanosecond. */
    static Instant plusSeconds(Instant base, BigFraction seconds) {
        BigInteger totalNanos = roundToNanos(seconds);
        BigInteger[] sec = totalNanos.divideAndRemainder(BILLION);
        return base.plusSeconds(sec[0].longValueExact()).plusNanos(sec[1].longValueExact());
    }

    /** An exact-seconds amount (>= 0) as a {@link Duration}, rounded to the nearest nanosecond. */
    static Duration toDuration(BigFraction seconds) {
        BigInteger totalNanos = roundToNanos(seconds);
        BigInteger[] sec = totalNanos.divideAndRemainder(BILLION);
        return Duration.ofSeconds(sec[0].longValueExact(), sec[1].longValueExact());
    }

    private static BigInteger roundToNanos(BigFraction seconds) {
        BigInteger num = seconds.getNumerator().multiply(BILLION);
        BigInteger den = seconds.getDenominator(); // always positive
        BigInteger half = den.shiftRight(1);
        return num.signum() >= 0
                ? num.add(half).divide(den)
                : num.subtract(half).divide(den);
    }

    // package-private accessors for the running / window logic (Phase 4 / 5)

    Graha mahaLord() {
        return mahaLord;
    }

    BigFraction elapsedSeconds() {
        return elapsedSeconds;
    }
}
