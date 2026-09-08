# Fallback: vendoring the Swiss Ephemeris Java port

The `ephemeris` module depends on the Swiss Ephemeris Java port via JitPack
(`com.github.krishnact:swisseph:master-6000e46cf8-1`, pinned in the parent POM).
JitPack builds are normally durable, but if the build is ever garbage-collected or
JitPack is unavailable, switch to a vendored copy of the source. No engine code
changes — the package (`de.thmac.swisseph`) and API are the same.

## Steps

1. Clone the pinned commit:
   ```bash
   git clone https://github.com/krishnact/swisseph /tmp/swisseph
   git -C /tmp/swisseph checkout 6000e46c
   ```
2. Copy the sources into the module:
   ```bash
   mkdir -p ephemeris/src/main/java/de
   cp -r /tmp/swisseph/src/main/java/de/thmac ephemeris/src/main/java/de/
   ```
3. Remove the JitPack pieces from `pom.xml`:
   - delete the `jitpack.io` `<repository>`
   - delete the `swisseph.version` property and the `com.github.krishnact:swisseph`
     entries in `dependencyManagement` and `ephemeris/pom.xml`
4. Exclude the vendored package from Spotless / coverage / ArchUnit as third-party
   code (it is not `com.celestia.*`, so the layering rules already ignore it; add a
   Spotless `<excludes>` for `de/thmac/**` if formatting rules tighten later).
5. Update `LICENSE-NOTICES.md`: the port is now in-tree (still AGPL, still covered
   by ADR-0001).
6. `./mvnw -pl ephemeris -am verify` — the `PositionProvider` tests should pass
   unchanged.

## Licence note

The port is AGPL-3.0. Vendoring the source is consistent with AGPL (which expects
source availability) and with ADR-0001 (Professional License purchased at
production go-live).
