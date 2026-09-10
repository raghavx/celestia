package com.celestia.agent.persistence.json;

import com.celestia.core.prediction.DailyPrediction;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** {@code prediction.payload_json} ⇄ {@link DailyPrediction}. Read failure ⇒ empty ⇒ recompute. */
@Component
public class PredictionCodec {

    private static final int SCHEMA = 1;

    public String write(DailyPrediction prediction) {
        try {
            ObjectNode root = PersistenceJson.mapper().createObjectNode();
            root.put("schema", SCHEMA);
            root.set("prediction", PersistenceJson.mapper().valueToTree(prediction));
            return PersistenceJson.mapper().writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialise prediction", e);
        }
    }

    public Optional<DailyPrediction> read(String json) {
        try {
            JsonNode root = PersistenceJson.mapper().readTree(json);
            if (root.path("schema").asInt(-1) != SCHEMA) {
                return Optional.empty();
            }
            return Optional.of(PersistenceJson.mapper()
                    .treeToValue(root.get("prediction"), DailyPrediction.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
