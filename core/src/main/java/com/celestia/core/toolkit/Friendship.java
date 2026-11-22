package com.celestia.core.toolkit;

/**
 * How one graha regards another, per the toolkit's Planet Friendship sheet
 * (SPEC-010 FR-006).
 *
 * <p>Not symmetric: A may be friendly to B while B is neutral to A, so the
 * look-up takes an ordered pair and all 72 of them must be present.
 */
public enum Friendship {
    FRIENDLY,
    NEUTRAL,
    ENEMY
}
