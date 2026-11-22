package com.celestia.core.toolkit;

import com.celestia.core.chart.NatalChart;
import com.celestia.core.judgement.SignificatorTable;
import com.celestia.core.judgement.Step;
import com.celestia.core.lordage.KpLordage;
import com.celestia.ephemeris.Graha;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Matches the toolkit's rule book against a chart (SPEC-010 FR-004/005).
 *
 * <p><b>This class is why Constitution IV holds.</b> Deciding which toolkit
 * rules a chart satisfies is code, deterministic and reviewable; the chat model
 * receives the result and explains it, never the raw rules to judge for itself.
 *
 * <p>Pure and stateless. Works for any {@link NatalChart} — the user's own, a
 * horary chart, or a second person's supplied in conversation — and stores
 * nothing, so supplied birth data lives no longer than the reply (FR-012,
 * Constitution VII).
 *
 * <h2>What "connected" means</h2>
 *
 * For each house the evaluator takes its cuspal sub lord (CSL), then that sub
 * lord's star lord, and asks which houses the CSL reaches <i>through</i> that
 * star lord: the houses the star lord occupies or owns. In the four-step
 * significator table those are exactly the CSL's {@link Step#STAR_OF_OCCUPANT}
 * and {@link Step#STAR_OF_OWNER} houses — the toolkit's ABCD "A + C" for the
 * CSL, and the literal reading of the rule book's own column header
 * (research.md &sect;3). Rahu/Ketu agency is already folded into
 * {@link SignificatorTable}.
 */
public final class PromiseEvaluator {

    private static final Set<Step> THROUGH_STAR_LORD = Set.of(Step.STAR_OF_OCCUPANT, Step.STAR_OF_OWNER);

    private final KpToolkit toolkit;

    public PromiseEvaluator(KpToolkit toolkit) {
        this.toolkit = toolkit;
    }

    /** An evaluator over the bundled toolkit. */
    public static PromiseEvaluator bundled() {
        return new PromiseEvaluator(KpToolkit.bundled());
    }

    /** All twelve houses, firm and condition-to-check hits only. */
    public List<HousePromise> evaluate(NatalChart chart) {
        return evaluate(chart, SignificatorTable.of(chart), false);
    }

    /**
     * All twelve houses.
     *
     * @param includePartial also report rules only some of whose houses connect.
     *     Useful context, but the answer must say "partly indicated" or stay
     *     quiet about them (research.md &sect;4).
     */
    public List<HousePromise> evaluate(NatalChart chart, SignificatorTable significators, boolean includePartial) {
        List<HousePromise> promises = new ArrayList<>(12);
        for (int house = 1; house <= 12; house++) {
            promises.add(evaluateHouse(chart, significators, house, includePartial));
        }
        return List.copyOf(promises);
    }

    /**
     * One house.
     *
     * @throws IllegalArgumentException if {@code house} is outside 1..12
     */
    public HousePromise evaluateHouse(
            NatalChart chart, SignificatorTable significators, int house, boolean includePartial) {
        if (house < 1 || house > 12) {
            throw new IllegalArgumentException("house out of 1..12: " + house);
        }

        Graha csl = chart.cuspSubLord(house);
        Graha starLord = KpLordage.chainFor(chart.position(csl).longitude()).starLord();
        Set<Integer> connected = connectedHouses(significators, csl);

        List<PromiseHit> hits = new ArrayList<>();
        Sensitivity sensitivity = Sensitivity.NONE;
        for (PromiseRule rule : toolkit.rulesFor(house)) {
            PromiseHit hit = judge(rule, connected, includePartial);
            if (hit != null) {
                hits.add(hit);
                sensitivity = sensitivity.max(hit.sensitivity());
            }
        }
        hits.sort(java.util.Comparator.comparingInt(h -> h.status().ordinal()));

        return new HousePromise(
                house,
                csl,
                chart.position(csl).retrograde(),
                starLord,
                chart.position(starLord).retrograde(),
                connected,
                hits,
                sensitivity);
    }

    /**
     * The houses {@code csl} reaches through its star lord — those the star lord
     * occupies ({@link Step#STAR_OF_OCCUPANT}) or owns ({@link Step#STAR_OF_OWNER}).
     */
    private static Set<Integer> connectedHouses(SignificatorTable significators, Graha csl) {
        Set<Integer> connected = new TreeSet<>();
        significators.grahaSignificators(csl).houses().forEach((house, steps) -> {
            if (steps.stream().anyMatch(THROUGH_STAR_LORD::contains)) {
                connected.add(house);
            }
        });
        return connected;
    }

    /**
     * Judge one rule, or {@code null} when it does not apply to this chart.
     *
     * <p>A rule naming no houses is never matched automatically: the toolkit's
     * author wrote a condition, not a formula, and inventing houses for it would
     * breach FR-003.
     */
    private static PromiseHit judge(PromiseRule rule, Set<Integer> connected, boolean includePartial) {
        if (rule.unmatchable()) {
            return null;
        }
        List<Integer> hit = new ArrayList<>();
        List<Integer> missing = new ArrayList<>();
        for (int house : rule.houses()) {
            if (connected.contains(house)) {
                hit.add(house);
            } else {
                missing.add(house);
            }
        }
        if (missing.isEmpty()) {
            PromiseHit.Status status =
                    rule.hasCondition() ? PromiseHit.Status.CONDITION_TO_CHECK : PromiseHit.Status.PROMISED;
            return new PromiseHit(rule, hit, missing, status);
        }
        if (includePartial && !hit.isEmpty()) {
            return new PromiseHit(rule, hit, missing, PromiseHit.Status.PARTLY_INDICATED);
        }
        return null;
    }
}
