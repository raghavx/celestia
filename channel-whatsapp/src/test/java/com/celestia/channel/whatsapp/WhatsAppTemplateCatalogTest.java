package com.celestia.channel.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

class WhatsAppTemplateCatalogTest {

    private final WhatsAppTemplateCatalog catalog = WhatsAppTemplateCatalog.loadFromClasspath();

    @Test
    void resolvesTheGenericReplyStubTemplate() {
        ResolvedTemplate t = catalog.resolve("generic-reply").orElseThrow();
        assertThat(t.metaTemplateName()).isEqualTo("celestia_generic_reply");
        assertThat(t.languageCode()).isEqualTo("en");
    }

    @Test
    void anUnknownKeyResolvesToEmpty() {
        assertThat(catalog.resolve("does-not-exist")).isEmpty();
    }

    // Post-implementation analyze finding L3: two resources declaring the same
    // key must fail fast, not silently last-wins.
    @Test
    void twoResourcesDeclaringTheSameKeyFailFast() {
        Resource first = namedResource(
                "first.properties", "key=dup\nmetaTemplateName=tpl_one\nlanguageCode=en\n");
        Resource second = namedResource(
                "second.properties", "key=dup\nmetaTemplateName=tpl_two\nlanguageCode=en\n");

        assertThatThrownBy(() -> WhatsAppTemplateCatalog.load(new Resource[] {first, second}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dup");
    }

    private static Resource namedResource(String filename, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getDescription() {
                return filename;
            }
        };
    }
}
