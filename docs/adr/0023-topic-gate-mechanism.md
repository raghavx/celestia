# ADR 0023: Topic-gate mechanism

- **Status:** proposed
- **Date:** 2026-09-02
- **Decision ref:** D23 (PROJECT_PLAN.md §2)

## Context

Constitution principles V (and plan principles 14–15): every message passes a
deterministic astrology-only gate **before** the answering model. The gate does
two things — catch prompt-injection / jailbreak attempts, and classify the
message as in-scope or out-of-scope. It must be fast, independently testable, and
independent of the answering LLM.

## Options considered

For the classifier stage:

1. A small local model on Ollama (`llama3.2:3b` or a dedicated guard model)
   prompted for strict JSON `{in_scope, category, is_injection, reason}`. Best
   accuracy; one extra model on the host; ~100–300 ms.
2. Embedding similarity of the message against a curated in-scope corpus with a
   tuned threshold. Cheapest, lowest latency, no generative model; weaker against
   adversarial phrasing.
3. Pure keyword / rule heuristics. Fast, brittle, high error rate.

## Decision

_Proposed:_ layered —

1. Always-on **deterministic rules** (injection phrase list, encoded-payload
   detection, prompt/tool-probe patterns) as a hard pre-filter.
2. The **small-model classifier** (Option 1) as the primary intent check.
3. **Embedding similarity** (Option 2) as the low-latency fallback when the
   classifier model is unavailable.

The in-scope taxonomy (`natal-house`, `planet-nakshatra-sublord`, `dasha-timing`,
`ruling-planets`, `horary`, `life-event-prediction`, `kp-remedy`) is a config
file. A checked-in adversarial corpus is a CI gate (SPEC-014).

## Consequences

- The guardrail classifier and embedding models are added to the Ollama host
  (ADR-0009 / ADR-0010).
- Classifier precision/recall thresholds are tracked against a labelled set.
- `guardrail` stays Spring-free by taking the classifier client as an interface.
