# ephemeris test resources

## Ephemeris data for tests

Tests resolve the `.se1` directory the same way production does (see
`SwissEphemerisConfig`):

1. system property `celestia.ephe.path` or env var `CELESTIA_EPHE_PATH`
2. `ephemeris/src/main/resources/ephe/` (present after `scripts/fetch-ephe.sh`)
3. none found → Moshier model, every result flagged `Accuracy.REDUCED`

**Full-accuracy tests** (golden charts, arc-second checks) require the real data:
run `scripts/fetch-ephe.sh` first, or set `CELESTIA_EPHE_PATH`. CI does this and
caches the files.

**Moshier-only tests** (structure, lord chain, boundary, determinism) need no
data and run anywhere. They are tagged so they can run without the download.
