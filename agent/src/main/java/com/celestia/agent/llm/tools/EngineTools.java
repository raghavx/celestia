package com.celestia.agent.llm.tools;

import com.celestia.agent.llm.TurnScope;
import com.celestia.core.chart.Cusp;
import com.celestia.core.chart.NatalChart;
import com.celestia.core.dasha.DashaPeriod;
import com.celestia.core.dasha.RunningDasha;
import com.celestia.core.horary.HoraryChartFactory;
import com.celestia.core.judgement.RulingPlanet;
import com.celestia.core.judgement.RulingPlanets;
import com.celestia.core.judgement.RulingPlanetsFactory;
import com.celestia.core.judgement.Significator;
import com.celestia.core.judgement.SignificatorTable;
import com.celestia.core.lordage.LordChain;
import com.celestia.core.lordage.Nakshatra;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.Graha;
import com.celestia.ephemeris.GrahaPosition;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * The deterministic engine, as the model may reach it (SPEC-012 FR-007, FR-008,
 * contracts/llm-tools.md).
 *
 * <p><b>No method accepts a {@code userId}, a coordinate or a time.</b> All three
 * are injected from the authenticated sender through {@link TurnScope}, so a
 * model that tries to pass someone else's identity has nowhere to put it
 * (FR-006, Constitution V). This class and {@link ToolkitTools} are the one place
 * where Constitution IV is at risk, which is why they live together in a package
 * you can read as a list.
 *
 * <p>These tools exist so the model <b>never computes</b>: there is a tool for
 * every number an answer might state, and no arithmetic left for the model to do
 * instead (Constitution IV, FR-008).
 *
 * <p>They carry no toolkit source, so no reference applies — and because of that
 * an engine call alone never grounds an interpretation. SC-002 counts
 * {@link ToolkitTools} calls.
 */
@Component
public class EngineTools {

    private final RulingPlanetsFactory rulingPlanets;
    private final HoraryChartFactory horaryCharts;
    private final Clock clock;

    public EngineTools(RulingPlanetsFactory rulingPlanets, HoraryChartFactory horaryCharts, Clock clock) {
        this.rulingPlanets = rulingPlanets;
        this.horaryCharts = horaryCharts;
        this.clock = clock;
    }

    @Tool(description = "The querent's natal chart: the twelve house cusps with their KP lordage, "
            + "and the nine grahas with their sign, star, sub lord and house. Use this rather than "
            + "working anything out.")
    public ChartView getNatalChart() {
        TurnScope scope = TurnScope.current();
        ChartView view = chartView(scope.context().chartToJudge());
        scope.finishCall("getNatalChart", List.of(), false);
        return view;
    }

    @Tool(description = "The Vimshottari periods running now, Mahadasha down to Sookshma, "
            + "with their start and end dates. Never calculate a dasha date yourself.")
    public List<PeriodView> getRunningDasha() {
        TurnScope scope = TurnScope.current();
        RunningDasha running = scope.context().chart().dasha().running(clock.instant(), 4);
        List<PeriodView> periods = running.stack().stream().map(EngineTools::periodView).toList();
        scope.finishCall("getRunningDasha", List.of(), false);
        return periods;
    }

    @Tool(description = "The significators of one house (1..12) — the planets that signify its "
            + "matters, strongest first, with the KP steps that make them significators.")
    public List<SignificatorView> getSignificators(
            @ToolParam(description = "house number, 1 to 12") int house) {
        TurnScope scope = TurnScope.current();
        requireHouse(house);
        SignificatorTable table = scope.context().chart().significators();
        List<SignificatorView> view = table.houseSignificators(house).significators().stream()
                .sorted(Significator.BY_STRENGTH)
                .map(s -> new SignificatorView(
                        s.graha().name(),
                        s.steps().stream().map(Enum::name).sorted().toList()))
                .toList();
        scope.finishCall("getSignificators", List.of(), false);
        return view;
    }

    @Tool(description = "The ruling planets for this moment — the day lord, the Ascendant's sign "
            + "and star lords, and the Moon's. The moment is the server's; you cannot choose it.")
    public RulingPlanetsView getRulingPlanets() {
        TurnScope scope = TurnScope.current();
        BirthData now = atNow(scope.context().chartToJudge());
        RulingPlanets rp = rulingPlanets.at(now);
        RulingPlanetsView view = new RulingPlanetsView(
                rp.dayLord().name(),
                rp.weekday().name(),
                rp.planets().stream()
                        .sorted(Comparator.comparingInt(p -> p.graha().ordinal()))
                        .map(EngineTools::rulingPlanetView)
                        .toList());
        scope.finishCall("getRulingPlanets", List.of(), false);
        return view;
    }

    @Tool(description = "Cast the horary chart for a number the querent gave, 1 to 249, judged now.")
    public ChartView castHorary(
            @ToolParam(description = "the number the querent chose, 1 to 249") int number) {
        TurnScope scope = TurnScope.current();
        if (number < 1 || number > 249) {
            throw new IllegalArgumentException("a horary number must be between 1 and 249");
        }
        NatalChart chart = horaryCharts.cast(number, atNow(scope.context().chartToJudge()));
        ChartView view = chartView(chart);
        scope.finishCall("castHorary", List.of(), false);
        return view;
    }

    /**
     * A second person's chart, from birth details given in this message (FR-011).
     *
     * <p><b>Not available yet, and it says so rather than guessing.</b> Casting a
     * chart from a place name needs a live geocoder and a timezone resolution;
     * {@code geo} defines the port (SPEC-007) but no geocoder is wired into this
     * application, and the model must never be handed coordinates to supply
     * itself (FR-006). Until one exists this returns a refusal the model can read,
     * so it declines in its own voice instead of inventing a chart.
     */
    @Tool(description = "Cast a chart for another person from birth details the querent gave in "
            + "this message. If this returns unavailable, say you cannot read another person's "
            + "chart yet — do not guess at one.")
    public SuppliedChartResult castChartFor(
            @ToolParam(description = "birth date as the querent gave it, e.g. 1987-04-23") String birthDate,
            @ToolParam(description = "birth time as the querent gave it, e.g. 14:35") String birthTime,
            @ToolParam(description = "birth place as the querent gave it, e.g. Nagpur, India") String birthPlace) {
        TurnScope scope = TurnScope.current();
        scope.finishCall("castChartFor", List.of(), false);
        return new SuppliedChartResult(
                false,
                "Casting another person's chart is not available yet — no place lookup is "
                        + "configured. Tell the querent you cannot read that chart at the moment.");
    }

    // ---- views: flat, readable, and free of anything the reply must not contain ----

    private static ChartView chartView(NatalChart chart) {
        List<CuspView> cusps = new ArrayList<>(12);
        for (Cusp cusp : chart.cusps()) {
            LordChain chain = cusp.lordChain();
            cusps.add(new CuspView(
                    cusp.house(),
                    chain.sign().name(),
                    chain.signLord().name(),
                    chain.nakshatra().name(),
                    chain.pada(),
                    chain.starLord().name(),
                    chain.subLord().name(),
                    chain.subSubLord().name()));
        }
        List<PlanetView> planets = new ArrayList<>(Graha.values().length);
        for (Graha graha : Graha.values()) {
            GrahaPosition position = chart.position(graha);
            Nakshatra nakshatra = Nakshatra.at(position.longitude());
            planets.add(new PlanetView(
                    graha.name(),
                    com.celestia.core.lordage.Sign.at(position.longitude()).name(),
                    nakshatra.name(),
                    Nakshatra.padaAt(position.longitude()),
                    nakshatra.lord().name(),
                    chart.placement(graha).bhava(),
                    position.retrograde()));
        }
        return new ChartView(cusps, planets);
    }

    private static PeriodView periodView(DashaPeriod period) {
        return new PeriodView(
                period.level().name(),
                period.lord().name(),
                LocalDate.ofInstant(period.start(), ZoneOffset.UTC).toString(),
                LocalDate.ofInstant(period.end(), ZoneOffset.UTC).toString());
    }

    private static RulingPlanetView rulingPlanetView(RulingPlanet planet) {
        return new RulingPlanetView(
                planet.graha().name(),
                planet.sources().stream().map(Enum::name).sorted().toList());
    }

    /** "Now", at the querent's own birth place — the clock is the server's (FR-006). */
    private BirthData atNow(NatalChart chart) {
        return new BirthData(clock.instant(), chart.birthData().latitude(), chart.birthData().longitude());
    }

    private static void requireHouse(int house) {
        if (house < 1 || house > 12) {
            throw new IllegalArgumentException("a house must be between 1 and 12");
        }
    }

    public record ChartView(List<CuspView> cusps, List<PlanetView> planets) {}

    public record CuspView(
            int house,
            String sign,
            String signLord,
            String star,
            int pada,
            String starLord,
            String subLord,
            String subSubLord) {}

    public record PlanetView(
            String planet,
            String sign,
            String star,
            int pada,
            String starLord,
            int bhava,
            boolean retrograde) {}

    public record PeriodView(String level, String lord, String from, String to) {}

    public record SignificatorView(String planet, List<String> steps) {}

    public record RulingPlanetsView(String dayLord, String weekday, List<RulingPlanetView> planets) {}

    public record RulingPlanetView(String planet, List<String> sources) {}

    public record SuppliedChartResult(boolean available, String note) {}
}
