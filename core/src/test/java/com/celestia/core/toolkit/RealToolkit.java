package com.celestia.core.toolkit;

/**
 * Gate for the assertions that need the real, imported toolkit (SPEC-010 T047).
 *
 * <p>The bundled resources are generated from the licensed workbook, which is
 * never committed. They are present in this repository because the owner has
 * run the import, but a checkout that deletes them — or a contributor working
 * before an import — must still get a green build rather than a wall of
 * failures about missing files. The same pattern as the ephemeris-gated golden
 * tests, which skip when the {@code .se1} data is absent.
 */
final class RealToolkit {

    private RealToolkit() {}

    /** True when the toolkit has been imported and loads. */
    static boolean isImported() {
        try {
            KpToolkit.bundled();
            return true;
        } catch (IllegalStateException notImported) {
            return false;
        }
    }
}
