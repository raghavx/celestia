package com.celestia.agent.persistence.json;

import java.math.BigInteger;
import org.apache.commons.numbers.fraction.BigFraction;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * The one {@link ObjectMapper} the persistence-cache codecs share (Jackson 3).
 * {@code java.time} support is built in and auto-registered; this adds
 * {@code BigFraction} (canonical {@code "num/den"} string — it appears in
 * {@code core}'s public API). Cache JSON is an internal representation, never an
 * API contract.
 */
public final class PersistenceJson {

    private PersistenceJson() {}

    private static final ObjectMapper MAPPER = build();

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    private static ObjectMapper build() {
        SimpleModule bigFraction = new SimpleModule("celestia-bigfraction");
        bigFraction.addSerializer(BigFraction.class, new ValueSerializer<>() {
            @Override
            public void serialize(BigFraction value, JsonGenerator gen, SerializationContext ctx) {
                gen.writeString(value.getNumerator() + "/" + value.getDenominator());
            }
        });
        bigFraction.addDeserializer(BigFraction.class, new ValueDeserializer<>() {
            @Override
            public BigFraction deserialize(JsonParser p, DeserializationContext ctx) {
                String[] parts = p.getValueAsString().split("/", 2);
                return BigFraction.of(
                        new BigInteger(parts[0].trim()), new BigInteger(parts[1].trim()));
            }
        });

        return JsonMapper.builder()
                .addModule(bigFraction)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
    }
}
