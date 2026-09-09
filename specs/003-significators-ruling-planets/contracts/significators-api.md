# Contract: `core.judgement` significators API

Consumers: SPEC-004 (dasha significators), SPEC-005 (horary), the LLM agent tools.

## `SignificatorTable`

```java
public final class SignificatorTable {
    public static SignificatorTable of(NatalChart chart);

    public HouseSignificators houseSignificators(int house);   // house in 1..12
    public GrahaSignificators grahaSignificators(Graha graha);
    public NodeAgency nodeAgency(Graha node);                  // RAHU or KETU
    public EngineVersion engineVersion();
}
```

### `of(chart)`

Pure — no I/O. Computes per-graha lord chains from `chart.position(g).longitude()`
via `KpLordage`. Deterministic: equal `NatalChart` ⇒ equal table.

### `houseSignificators(h)` → `HouseSignificators`

- `significators` — de-duplicated by `graha`, each carrying the non-empty set of
  qualifying `Step`s, ordered by `strongestStep().rank()` (1 strongest) then by
  `Graha` ordinal.
- The four steps (research.md §1): star of effective occupants / effective
  occupants / star of owner / owner. A node occupant folds its **agents**
  (conjunct non-node grahas, sign lord, star lord) into the effective occupants.
- Owner (`Step.OWNER`) is always exactly one non-node graha (`chart.cusp(h)
  .lordChain().signLord()`).
- An empty house has empty steps 1–2 and non-empty steps 3–4.

### `grahaSignificators(g)` → `GrahaSignificators`

The **exact transpose** of the twelve `houseSignificators`:
`grahaSignificators(g).signifies(h)` ⇔ `houseSignificators(h).signifies(g)`, with
identical step tags. No independent computation.

### `nodeAgency(node)` → `NodeAgency`

For `RAHU` / `KETU`: `conjunctGrahas` (non-node grahas sharing its bhava),
`signLord`, `starLord`, and `agents` = their union. `nodeAgency(g)` for a
non-node `g` throws `IllegalArgumentException`.

## Supporting types (`com.celestia.core.judgement`)

`Step` (ranked 1..4), `Significator`, `HouseSignificators`, `GrahaSignificators`,
`NodeAgency`.

## Versioning

`EngineVersion` (from the chart) changes if the four-step rule, the node-agency
rule, the owner/occupant definitions, or any upstream KP rule changes.
