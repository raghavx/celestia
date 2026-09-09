package com.celestia.core.dasha;

import com.celestia.core.lordage.Longitudes;
import com.celestia.core.lordage.Nakshatra;
import com.celestia.ephemeris.Accuracy;
import com.celestia.ephemeris.EngineVersion;
import com.celestia.ephemeris.Graha;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    /**
     * The stack of active periods at {@code query}, from Mahadasha down to
     * {@code depth} (1..5). Half-open {@code [start, end)} at every level.
     *
     * @throws IllegalArgumentException if {@code query} is before the birth instant,
     *     or {@code depth} is outside 1..5
     */
    public RunningDasha running(Instant query, int depth) {
        if (query.isBefore(birthInstant)) {
            throw new IllegalArgumentException("query is before the birth instant: " + query);
        }
        if (depth < 1 || depth > 5) {
            throw new IllegalArgumentException("depth out of 1..5: " + depth);
        }

        // exact seconds of the query, measured from the start of the birth Mahadasha (>= 0)
        BigFraction q = elapsedSeconds.add(durationSeconds(Duration.between(birthInstant, query)));

        List<DashaPeriod> stack = new ArrayList<>(depth);

        // level 1 — the sequence from the birth-Maha start is periodic with a 120-year
        // period. Walk from a whole cycle before the query (so the chosen segment has a
        // real predecessor) and pick the first segment whose rounded end instant is
        // after the query — that guarantees the rounded [start, end) brackets the query
        // even when it lands sub-nanosecond from an exact boundary.
        BigInteger cycles = floor(q.divide(CYCLE_SECONDS));
        BigInteger walkFrom = cycles.signum() > 0 ? cycles.subtract(BigInteger.ONE) : BigInteger.ZERO;

        // walk up to three cycles (27 segments) from one cycle before the query, so the
        // selected segment always has a real predecessor even at a cycle boundary
        Graha lord = mahaLord;
        BigFraction cursor = CYCLE_SECONDS.multiply(BigFraction.of(walkFrom));
        BigFraction start = cursor;
        BigFraction end = cursor.add(CYCLE_SECONDS.multiply(BigFraction.of(lord.years(), Graha.CYCLE_YEARS)));
        for (int i = 0; i < 27; i++) {
            BigFraction next = cursor.add(
                    CYCLE_SECONDS.multiply(BigFraction.of(lord.years(), Graha.CYCLE_YEARS)));
            start = cursor;
            end = next;
            if (query.isBefore(instantAt(next))) {
                break;
            }
            cursor = next;
            lord = lord.next();
        }

        List<Graha> parentLords = List.of();
        stack.add(period(DashaLevel.MAHADASHA, lord, start, end, parentLords));

        // levels 2..depth — nine-way split of the parent; same "first child ending
        // after the query" selection. b[0] == parent.start, b[9] == parent.end.
        for (int rank = 2; rank <= depth; rank++) {
            parentLords = append(parentLords, lord);
            List<VimshottariSplit.Portion> parts = VimshottariSplit.of(end.subtract(start), lord);
            BigFraction childCursor = start;
            for (int i = 0; i < 9; i++) {
                BigFraction childEnd = childCursor.add(parts.get(i).span());
                if (query.isBefore(instantAt(childEnd)) || i == 8) {
                    lord = parts.get(i).lord();
                    start = childCursor;
                    end = childEnd;
                    break;
                }
                childCursor = childEnd;
            }
            stack.add(period(DashaLevel.ofRank(rank), lord, start, end, parentLords));
        }

        return new RunningDasha(query, stack);
    }

    /** {@code birthInstant + (offset - elapsed)}, rounded to the nearest nanosecond. */
    private Instant instantAt(BigFraction offsetFromMahaStart) {
        return plusSeconds(birthInstant, offsetFromMahaStart.subtract(elapsedSeconds));
    }

    /** Largest number of periods {@link #periods} will return before rejecting the window. */
    static final int MAX_PERIODS = 10_000;

    /**
     * Every period at {@code level} overlapping the window {@code [from, to)}, in
     * chronological order and contiguous ({@code periods[i].end() ==
     * periods[i+1].start()}), each carrying its {@code parentLords} chain.
     *
     * @throws IllegalArgumentException if {@code from} is after {@code to}, {@code
     *     from} is before the birth instant, or the window spans more than
     *     {@value #MAX_PERIODS} periods at {@code level} (mis-scaled for the level)
     */
    public List<DashaPeriod> periods(DashaLevel level, Instant from, Instant to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("window start after end: " + from + " .. " + to);
        }
        if (from.isBefore(birthInstant)) {
            throw new IllegalArgumentException("window start is before the birth instant: " + from);
        }
        List<DashaPeriod> out = new ArrayList<>();
        Instant cursor = from;
        while (cursor.isBefore(to)) {
            if (out.size() >= MAX_PERIODS) {
                throw new IllegalArgumentException(
                        "window " + from + " .. " + to + " spans more than " + MAX_PERIODS
                                + " " + level + " periods");
            }
            DashaPeriod p = running(cursor, level.rank()).period(level);
            out.add(p);
            // guarantee forward progress even if p.end() rounds a fraction of a ns backwards
            cursor = p.end().isAfter(cursor) ? p.end() : cursor.plusNanos(1);
        }
        return out;
    }

    /** A period given its bounds as exact seconds from the birth-Mahadasha start. */
    private DashaPeriod period(
            DashaLevel level, Graha lord, BigFraction startFromMahaStart, BigFraction endFromMahaStart,
            List<Graha> parentLords) {
        return new DashaPeriod(
                level, lord, instantAt(startFromMahaStart), instantAt(endFromMahaStart), parentLords);
    }

    private static List<Graha> append(List<Graha> lords, Graha lord) {
        List<Graha> out = new ArrayList<>(lords);
        out.add(lord);
        return out;
    }

    private static BigInteger floor(BigFraction f) {
        BigInteger[] qr = f.getNumerator().divideAndRemainder(f.getDenominator());
        return qr[1].signum() < 0 ? qr[0].subtract(BigInteger.ONE) : qr[0];
    }

    static BigFraction durationSeconds(Duration d) {
        return BigFraction.of(d.getSeconds()).add(BigFraction.of(d.getNano(), 1_000_000_000L));
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
