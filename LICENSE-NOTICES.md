# Third-party licence notices

## Swiss Ephemeris (`ephemeris` module)

- **Component:** Swiss Ephemeris Java port + `.se1` ephemeris data files.
  - Port: `com.github.krishnact:swisseph:master-6000e46cf8-1` (JitPack build of
    the `krishnact/swisseph` fork; package `de.thmac.swisseph`; SE version 2.01.00).
  - Data: `sepl_18.se1`, `semo_18.se1` from the `aloistr/swisseph` mirror
    (`scripts/fetch-ephe.sh`, checksummed in `scripts/ephe.sha256`; not committed).
- **Upstream licence:** AGPL-3.0 (Astrodienst AG). Covers both the code and the data.
- **Celestia's basis:** **Swiss Ephemeris Professional License** — see
  [ADR-0001](docs/adr/0001-swiss-ephemeris-licensing.md).
- **Status:** ⏳ **not yet purchased.** Development, testing, and internal staging
  proceed under AGPL-3.0. The Professional License **must be purchased from
  Astrodienst before the production service is exposed to any external user.**
- **Action owner:** _(assign)_ · **Due:** before first external-facing production deploy.

## commons-numbers-fraction (`core` module)

- `org.apache.commons:commons-numbers-fraction:1.2` — Apache License 2.0. No notice
  obligation beyond retaining the licence; listed here for completeness.

When the licence is acquired, update this entry with the purchase date and
reference, and remove the interim-AGPL caveat.

---

_Add further third-party components here as dependencies are introduced
(geocoder, `timeshape`, Razorpay SDK, WhatsApp client, model weights, etc.)._
