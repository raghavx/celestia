package com.celestia.channel.whatsapp;

/** A logical template key resolved to Meta's actual template name + language. */
public record ResolvedTemplate(String metaTemplateName, String languageCode) {

    public ResolvedTemplate {
        if (metaTemplateName == null || metaTemplateName.isBlank()) {
            throw new IllegalArgumentException("metaTemplateName must not be blank");
        }
        if (languageCode == null || languageCode.isBlank()) {
            throw new IllegalArgumentException("languageCode must not be blank");
        }
    }
}
