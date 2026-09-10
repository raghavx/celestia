package com.celestia.agent.persistence.json;

import com.celestia.geo.place.GeocodeResult;
import com.celestia.geo.place.PlaceCandidate;
import com.celestia.geo.place.PlaceQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code geocode_cache.results_json} ⇄ {@link GeocodeResult}. The port
 * ({@code geo.GeocodeCache}) only promises the candidate list is equal on read,
 * so the codec rebuilds a {@link PlaceQuery} from the stored {@code raw}/{@code normalized}.
 */
@Component
public class GeocodeResultCodec {

    private static final int SCHEMA = 1;

    public String write(String queryNorm, GeocodeResult result) {
        ObjectNode root = PersistenceJson.mapper().createObjectNode();
        root.put("schema", SCHEMA);
        ObjectNode q = root.putObject("query");
        q.put("raw", result.query().raw());
        q.put("normalized", result.query().normalized());
        ArrayNode cs = root.putArray("candidates");
        for (PlaceCandidate c : result.candidates()) {
            ObjectNode n = cs.addObject();
            n.put("id", c.id());
            n.put("displayName", c.displayName());
            n.put("country", c.country());
            n.put("adminRegion", c.adminRegion());
            n.put("latitude", c.latitude());
            n.put("longitude", c.longitude());
            n.put("source", c.source());
        }
        try {
            return PersistenceJson.mapper().writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialise geocode result", e);
        }
    }

    public Optional<GeocodeResult> read(String json) {
        try {
            JsonNode root = PersistenceJson.mapper().readTree(json);
            if (root.path("schema").asInt(-1) != SCHEMA) {
                return Optional.empty();
            }
            JsonNode q = root.get("query");
            PlaceQuery query = new PlaceQuery(q.get("raw").asText(), q.get("normalized").asText());
            List<PlaceCandidate> candidates = new ArrayList<>();
            for (JsonNode n : root.get("candidates")) {
                candidates.add(new PlaceCandidate(
                        n.get("id").asText(),
                        n.get("displayName").asText(),
                        n.path("country").asText(""),
                        n.path("adminRegion").asText(""),
                        n.get("latitude").asDouble(),
                        n.get("longitude").asDouble(),
                        n.get("source").asText()));
            }
            return Optional.of(new GeocodeResult(query, candidates));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
