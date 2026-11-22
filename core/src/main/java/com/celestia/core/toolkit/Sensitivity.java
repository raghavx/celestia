package com.celestia.core.toolkit;

/**
 * How carefully a reply drawing on this content must be worded, and the one
 * fixed advisory sentence it ends with (SPEC-010 FR-010a).
 *
 * <p>Ordered least to most severe. A reply that draws on several classes carries
 * exactly one sentence — the {@link #max(Sensitivity) most severe} one used.
 *
 * <p><b>These constants are the only source of advisory copy in the product.</b>
 * The wording is set here, never generated per reply and never written by the
 * chat model (Constitution V). The output stage appends {@link #note()}; the
 * model is not asked to.
 */
public enum Sensitivity {
    /** Ordinary KP content. No advisory sentence. */
    NONE(""),

    /** Gems, yantras, Vastu, Feng Shui. */
    REMEDY("Traditional remedy — complementary to, never a replacement for, professional advice."),

    /** Share market, speculation, investment (FR-010). */
    FINANCE(
            "Astrological guidance only — not financial advice; consult a registered financial advisor before investing."),

    /** Disease, treatment, mental health, self-harm (FR-010, FR-011). */
    HEALTH("Astrological guidance only — please consult a qualified doctor for diagnosis or treatment."),

    /** Lifespan, danger to life, manner of death (FR-010). */
    LONGEVITY(
            "Astrological indications only — not a prediction of any certain event; please consult a doctor about any health concern.");

    private final String note;

    Sensitivity(String note) {
        this.note = note;
    }

    /**
     * The fixed advisory sentence for this class, or {@code ""} for {@link #NONE}.
     *
     * <p>Verbatim product copy: append it, never paraphrase it.
     */
    public String note() {
        return note;
    }

    /** The more severe of this and {@code other}. */
    public Sensitivity max(Sensitivity other) {
        return compareTo(other) >= 0 ? this : other;
    }
}
