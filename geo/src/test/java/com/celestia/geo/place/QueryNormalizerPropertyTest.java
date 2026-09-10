package com.celestia.geo.place;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;

class QueryNormalizerPropertyTest {

    @Property(tries = 500)
    void normalizeIsIdempotent(@ForAll @StringLength(max = 40) String raw) {
        String once = QueryNormalizer.normalize(raw);
        assertThat(QueryNormalizer.normalize(once)).isEqualTo(once);
    }

    @Property(tries = 300)
    void normalizedIsLowercaseAndHasNoRepeatedOrEdgeSpaces(
            @ForAll @NotBlank @AlphaChars @StringLength(min = 1, max = 30) String word) {
        String n = QueryNormalizer.normalize("  " + word.toUpperCase() + "   x  ");
        assertThat(n).isEqualTo(n.toLowerCase());
        assertThat(n).doesNotContain("  ");
        assertThat(n).isEqualTo(n.strip());
    }
}
