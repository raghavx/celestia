package com.celestia.ephemeris;

/**
 * Thrown only for genuinely unusable input or an unrecoverable backend failure
 * (null instant, Swiss Ephemeris failing to initialise). An out-of-range date is
 * <b>not</b> an error — it yields a result with {@link Accuracy#REDUCED}.
 */
public class EphemerisException extends RuntimeException {

    public EphemerisException(String message) {
        super(message);
    }

    public EphemerisException(String message, Throwable cause) {
        super(message, cause);
    }
}
