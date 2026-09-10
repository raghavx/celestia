package com.celestia.geo.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class QueryNormalizerTest {

    @Test
    void trimsCollapsesWhitespaceAndCaseFolds() {
        assertThat(QueryNormalizer.normalize("  New   York ")).isEqualTo("new york");
    }

    @Test
    void appliesTheAliasMapWholeToken() {
        assertThat(QueryNormalizer.normalize("Bombay")).isEqualTo("mumbai");
        assertThat(QueryNormalizer.normalize("Bandra, Bombay")).isEqualTo("bandra, mumbai");
        assertThat(QueryNormalizer.normalize("Calcutta")).isEqualTo("kolkata");
        assertThat(QueryNormalizer.normalize("POONA")).isEqualTo("pune");
    }

    @Test
    void isIdempotent() {
        for (String q : new String[] {"  Bombay ", "Bandra, Bombay", "peking", "Saigon", "Pune"}) {
            String once = QueryNormalizer.normalize(q);
            assertThat(QueryNormalizer.normalize(once)).as(q).isEqualTo(once);
        }
    }

    @Test
    void spellingVariantsSharingAnAliasTargetNormaliseEqual() {
        assertThat(QueryNormalizer.normalize("Bombay")).isEqualTo(QueryNormalizer.normalize("Mumbai"));
        assertThat(QueryNormalizer.normalize("Poona")).isEqualTo(QueryNormalizer.normalize("pune"));
    }

    @Test
    void aliasTargetsAreNotThemselvesAliasKeys() {
        for (String target : QueryNormalizer.aliases().values()) {
            assertThat(QueryNormalizer.aliases()).doesNotContainKey(target);
        }
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> QueryNormalizer.normalize(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
