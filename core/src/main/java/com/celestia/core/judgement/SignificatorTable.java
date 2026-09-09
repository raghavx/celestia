package com.celestia.core.judgement;

import com.celestia.core.chart.NatalChart;
import com.celestia.core.lordage.KpLordage;
import com.celestia.core.lordage.LordChain;
import com.celestia.ephemeris.EngineVersion;
import com.celestia.ephemeris.Graha;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The four-step KP significators of a {@link NatalChart} — the per-house lists,
 * the per-graha transpose, and Rahu/Ketu agency.
 *
 * <p>Pure: derives per-graha lord chains from the chart's stored positions via
 * {@link KpLordage}. Algorithm and sources: {@code core/REFERENCES.md} and
 * {@code specs/003-significators-ruling-planets/research.md} §1.
 */
public final class SignificatorTable {

    private final Map<Integer, HouseSignificators> byHouse;
    private final Map<Graha, GrahaSignificators> byGraha;
    private final Map<Graha, NodeAgency> nodeAgencies;
    private final EngineVersion engineVersion;

    private SignificatorTable(
            Map<Integer, HouseSignificators> byHouse,
            Map<Graha, GrahaSignificators> byGraha,
            Map<Graha, NodeAgency> nodeAgencies,
            EngineVersion engineVersion) {
        this.byHouse = byHouse;
        this.byGraha = byGraha;
        this.nodeAgencies = nodeAgencies;
        this.engineVersion = engineVersion;
    }

    public static SignificatorTable of(NatalChart chart) {
        Map<Graha, LordChain> chains = new EnumMap<>(Graha.class);
        Map<Graha, Integer> bhava = new EnumMap<>(Graha.class);
        for (Graha g : Graha.values()) {
            chains.put(g, KpLordage.chainFor(chart.position(g).longitude()));
            bhava.put(g, chart.placement(g).bhava());
        }

        Map<Graha, NodeAgency> nodeAgencies = new EnumMap<>(Graha.class);
        for (Graha node : List.of(Graha.RAHU, Graha.KETU)) {
            Set<Graha> conjunct = EnumSet.noneOf(Graha.class);
            for (Graha g : Graha.values()) {
                if (g != node && !isNode(g) && bhava.get(g).equals(bhava.get(node))) {
                    conjunct.add(g);
                }
            }
            Graha signLord = chains.get(node).signLord();
            Graha starLord = chains.get(node).starLord();
            Set<Graha> agents = EnumSet.copyOf(conjunct);
            agents.add(signLord);
            agents.add(starLord);
            nodeAgencies.put(node, new NodeAgency(node, conjunct, signLord, starLord, agents));
        }

        Map<Integer, HouseSignificators> byHouse = new TreeMap<>();
        for (int house = 1; house <= 12; house++) {
            Graha owner = chart.cusp(house).lordChain().signLord();

            Set<Graha> effective = EnumSet.noneOf(Graha.class);
            for (Graha g : Graha.values()) {
                if (bhava.get(g) == house) {
                    effective.add(g);
                    if (isNode(g)) {
                        effective.addAll(nodeAgencies.get(g).agents());
                    }
                }
            }

            Map<Graha, EnumSet<Step>> steps = new EnumMap<>(Graha.class);
            for (Graha g : Graha.values()) {
                if (effective.contains(chains.get(g).starLord())) {
                    steps.computeIfAbsent(g, k -> EnumSet.noneOf(Step.class)).add(Step.STAR_OF_OCCUPANT);
                }
                if (chains.get(g).starLord() == owner) {
                    steps.computeIfAbsent(g, k -> EnumSet.noneOf(Step.class)).add(Step.STAR_OF_OWNER);
                }
            }
            for (Graha g : effective) {
                steps.computeIfAbsent(g, k -> EnumSet.noneOf(Step.class)).add(Step.OCCUPANT);
            }
            steps.computeIfAbsent(owner, k -> EnumSet.noneOf(Step.class)).add(Step.OWNER);

            List<Significator> list = new ArrayList<>();
            int h = house;
            steps.forEach((g, s) -> list.add(new Significator(g, h, s)));
            list.sort(Significator.BY_STRENGTH);
            byHouse.put(house, new HouseSignificators(house, list));
        }

        Map<Graha, GrahaSignificators> byGraha = new EnumMap<>(Graha.class);
        for (Graha g : Graha.values()) {
            Map<Integer, Set<Step>> houses = new TreeMap<>();
            for (HouseSignificators hs : byHouse.values()) {
                Set<Step> s = hs.stepsFor(g);
                if (!s.isEmpty()) {
                    houses.put(hs.house(), s);
                }
            }
            byGraha.put(g, new GrahaSignificators(g, houses));
        }

        return new SignificatorTable(
                Map.copyOf(byHouse), Map.copyOf(byGraha), Map.copyOf(nodeAgencies),
                chart.engineVersion());
    }

    public HouseSignificators houseSignificators(int house) {
        HouseSignificators hs = byHouse.get(house);
        if (hs == null) {
            throw new IllegalArgumentException("house out of 1..12: " + house);
        }
        return hs;
    }

    public GrahaSignificators grahaSignificators(Graha graha) {
        return byGraha.get(graha);
    }

    public NodeAgency nodeAgency(Graha node) {
        NodeAgency a = nodeAgencies.get(node);
        if (a == null) {
            throw new IllegalArgumentException("not a node: " + node);
        }
        return a;
    }

    public EngineVersion engineVersion() {
        return engineVersion;
    }

    private static boolean isNode(Graha g) {
        return g == Graha.RAHU || g == Graha.KETU;
    }
}
