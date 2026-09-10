package com.celestia.geo.time;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Properties;
import net.iakovlev.timeshape.TimeZoneEngine;

/**
 * {@link TimeZoneResolver} backed by {@code timeshape}'s embedded timezone
 * polygons (ADR-0014). Offline — the engine is built once from data bundled in
 * the {@code timeshape} jar; no network, no clock.
 *
 * <p>The engine build (~50 MB, ~1–3 s) is deferred to first use behind the
 * initialization-on-demand holder idiom, so it never runs in a constructor or a
 * hot path and is paid at most once per JVM.
 *
 * <p>No {@code net.iakovlev} type appears in any public member — {@code timeshape}
 * stays entirely behind this class (DeterminismArchitectureTest).
 */
public final class TimeshapeTimeZoneResolver implements TimeZoneResolver {

    /** The pinned timezone-boundary dataset version (see {@code geo-build.properties}). */
    public static final String DATASET_VERSION = loadDatasetVersion();

    private static final class Holder {
        private static final TimeZoneEngine ENGINE = TimeZoneEngine.initialize();
    }

    @Override
    public ZoneResolution resolve(double latitude, double longitude) {
        if (!(Math.abs(latitude) <= 90.0)) {
            throw new IllegalArgumentException("latitude out of [-90, 90]: " + latitude);
        }
        if (!(Math.abs(longitude) <= 180.0)) {
            throw new IllegalArgumentException("longitude out of [-180, 180]: " + longitude);
        }
        Optional<ZoneId> hit = Holder.ENGINE.query(latitude, longitude);
        if (hit.isPresent()) {
            ZoneId zone = hit.get();
            // The dataset carries fixed-offset ocean tiles ("Etc/GMT±h"). Those
            // are not civil zones — surface them as approximated so the caller
            // can confirm the place (research.md §2).
            boolean approximated = zone.getId().startsWith("Etc/");
            return new ZoneResolution(zone, approximated, DATASET_VERSION);
        }
        // Defensive: the current dataset always returns a zone within valid
        // lat/lon, but a future one might not.
        return new ZoneResolution(etcGmtFor(longitude), true, DATASET_VERSION);
    }

    /**
     * The {@code Etc/GMT} zone whose fixed offset is closest to the local mean
     * time of {@code longitude}. Note {@code Etc/GMT+5} is UTC−05:00 — the sign is
     * inverted — so a positive (east) longitude maps to {@code Etc/GMT-h}.
     */
    static ZoneId etcGmtFor(double longitude) {
        int h = (int) Math.round(longitude / 15.0); // −12..12
        if (h == 0) {
            return ZoneId.of("Etc/GMT");
        }
        String sign = h > 0 ? "-" : "+";
        return ZoneId.of("Etc/GMT" + sign + Math.abs(h));
    }

    private static String loadDatasetVersion() {
        try (InputStream in =
                TimeshapeTimeZoneResolver.class.getResourceAsStream("/geo-build.properties")) {
            if (in == null) {
                throw new IllegalStateException("geo-build.properties missing from the classpath");
            }
            Properties p = new Properties();
            p.load(in);
            String v = p.getProperty("timeshape.version");
            if (v == null || v.isBlank() || v.startsWith("$")) {
                throw new IllegalStateException("timeshape.version not filtered into geo-build.properties");
            }
            return v;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
