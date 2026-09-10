package com.celestia.agent.persistence.json;

import com.celestia.core.chart.NatalChart;
import com.celestia.core.chart.NatalChartFactory;
import com.celestia.ephemeris.EphemerisResult;
import com.celestia.ephemeris.HouseResult;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code chart.computed_json} ⇄ {@link NatalChart}.
 *
 * <p>Stores the raw engine outputs ({@link EphemerisResult} + {@link HouseResult})
 * — simple maps of doubles, no lossy re-projection — and rebuilds the chart with
 * the <em>pure, no-I/O</em> {@link NatalChartFactory#assemble}. A read that fails
 * for any reason (shape drift, unknown {@code schema}) returns
 * {@link Optional#empty()} so the caller recomputes (research.md §3).
 */
@Component
public class ChartCodec {

    private static final int SCHEMA = 1;

    public String write(EphemerisResult ephemeris, HouseResult houses) {
        try {
            ObjectNode root = PersistenceJson.mapper().createObjectNode();
            root.put("schema", SCHEMA);
            root.set("ephemeris", PersistenceJson.mapper().valueToTree(ephemeris));
            root.set("houses", PersistenceJson.mapper().valueToTree(houses));
            return PersistenceJson.mapper().writeValueAsString(root);
        } catch (RuntimeException e) {
            throw new IllegalStateException("failed to serialise chart", e);
        }
    }

    public Optional<NatalChart> read(String json) {
        try {
            JsonNode root = PersistenceJson.mapper().readTree(json);
            if (root.path("schema").asInt(-1) != SCHEMA) {
                return Optional.empty();
            }
            EphemerisResult ephemeris =
                    PersistenceJson.mapper().treeToValue(root.get("ephemeris"), EphemerisResult.class);
            HouseResult houses =
                    PersistenceJson.mapper().treeToValue(root.get("houses"), HouseResult.class);
            return Optional.of(
                    NatalChartFactory.assemble(houses.birthData(), ephemeris, houses));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
