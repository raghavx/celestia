package com.celestia.channel.whatsapp;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Logical template key → the actual Meta template name + language (ADR-0012).
 * Callers never hard-code Meta's naming. v1 ships one stub/fixture entry
 * ({@code generic-reply}) — real, approved templates are added by the specs
 * that need them.
 */
public final class WhatsAppTemplateCatalog {

    private static final String CLASSPATH_PATTERN = "classpath*:whatsapp-templates/*.properties";

    private final Map<String, ResolvedTemplate> byKey;

    WhatsAppTemplateCatalog(Map<String, ResolvedTemplate> byKey) {
        this.byKey = Map.copyOf(byKey);
    }

    public Optional<ResolvedTemplate> resolve(String templateKey) {
        return Optional.ofNullable(byKey.get(templateKey));
    }

    /** Loads every {@code whatsapp-templates/*.properties} file on the classpath. */
    public static WhatsAppTemplateCatalog loadFromClasspath() {
        try {
            return load(new PathMatchingResourcePatternResolver().getResources(CLASSPATH_PATTERN));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to load whatsapp-templates/*.properties", e);
        }
    }

    /**
     * Builds the catalog from an explicit resource list — {@link #loadFromClasspath()}
     * is a thin wrapper over this; split out so duplicate-key handling is
     * testable without real classpath scanning.
     *
     * @throws IllegalStateException if two resources declare the same {@code key} —
     *     a silent last-wins would let one template shadow another with no
     *     indication which one actually resolves at send time.
     */
    static WhatsAppTemplateCatalog load(Resource[] resources) throws IOException {
        Map<String, ResolvedTemplate> byKey = new HashMap<>();
        Map<String, String> sourceByKey = new HashMap<>();
        for (Resource r : resources) {
            Properties props = new Properties();
            try (InputStream in = r.getInputStream()) {
                props.load(in);
            }
            String key = props.getProperty("key");
            String name = props.getProperty("metaTemplateName");
            String lang = props.getProperty("languageCode");
            if (key != null && name != null && lang != null) {
                String existingSource = sourceByKey.get(key);
                if (existingSource != null) {
                    throw new IllegalStateException("duplicate whatsapp template key '" + key + "' in "
                            + r.getDescription() + " and " + existingSource);
                }
                byKey.put(key, new ResolvedTemplate(name, lang));
                sourceByKey.put(key, r.getDescription());
            }
        }
        return new WhatsAppTemplateCatalog(byKey);
    }
}
