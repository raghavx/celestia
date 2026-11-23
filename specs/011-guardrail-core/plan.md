# Implementation Plan: Guardrail Core

**Branch**: `011-guardrail-core` | **Date**: 2026-11-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/011-guardrail-core/spec.md`

## Summary

Make Constitution V's single guarded route a property of the code.

- **Inbound gate.** A pure `Gate` in the existing `guardrail` module normalises the
  message, runs the deterministic hostile rules, then looks for evidence that the
  message belongs to a toolkit topic group with answerable content. Anything it
  cannot place is refused (fail closed), and the reason is recorded so false
  refusals are countable.
- **In-scope vocabulary from the toolkit.** Derived at load from `core` — the
  toolkit's rule matters and sheet titles, plus the engine's own KP vocabulary
  (nakshatras, grahas, signs, "sub lord", "dasha", "horary") — so widening the
  toolkit widens the gate with no list to maintain (FR-006). About 1,700 terms at
  the current release. Only what the toolkit cannot supply is committed data: a
  stopword list and a small supplement for compounds and synonyms its wording
  misses ("gemstone" where it says "gems" — research §12). **English only for the
  first cut**; the matcher stays script-aware and table-driven so adding a
  language later is data, not surgery.
- **Output stage.** A pure pass over every reply, the refusal redirect included:
  the single advisory sentence for the most severe class used, the crisis support
  line when the user's own message showed intent, the fixed redirect for the
  out-of-scope marker, and a leak scan that withholds the whole reply rather than
  redacting it. The scan keys on the toolkit's own ids and titles, splitting the
  titles by prose class so `ReadyReckoner` is caught bare while `Relationship`
  and `Vimshottari` stay ordinary English. Routing refusals through the stage too
  is what makes the support line reach someone whose message the gate could not
  place (FR-013, FR-015).
- **The seam that makes it enforceable.** `GuardedPipeline` takes an `Answerer`
  and calls it *only* on an allowed decision. SPEC-012 supplies the LLM-backed
  `Answerer`; the adversarial corpus wires a counting fake and asserts zero
  invocations, so SC-001 is real today rather than when the model lands.

## Technical Context

**Language/Version**: Java 25 (ADR-0007). No new language or build tooling.

**Primary Dependencies**: none new. `guardrail` gains a dependency on `core`
(for `TopicGroup`, `Sensitivity`, `KpToolkit`) — both are pure domain modules and
`guardrail` already sits after `core` in the module order. No Spring, no Spring AI
in this module (the module's own README comment already anticipates this).

**Storage**: classpath resources under
`guardrail/src/main/resources/com/celestia/guardrail/` (small TSVs: hostile
phrases, off-topic phrases, stopwords, English supplementary terms, homoglyph
folds, sheet-title prose classes).
The two corpora live in `src/test/resources`. No database change.

**Testing**: JUnit 5 + AssertJ (already in `guardrail`'s pom). Parameterised tests
driven by the two corpus files, so adding a case needs no code change (FR-020).
ArchUnit rules in `agent`'s existing architecture test suite.

**Target Platform**: JVM. `guardrail` stays framework-free; `agent` wires it.

**Project Type**: library code in the existing `guardrail` module, plus one
ArchUnit rule in `agent`.

**Performance Goals**: a gate decision in well under 1 ms for a message at the
4,096-character bound — comfortably inside SC-008's "no delay a user could
notice". Vocabulary is built once per JVM from the already-loaded toolkit.

**Constraints**:
- Constitution II: pure and deterministic — no Spring, no database, no network,
  no clock, no ambient state. Enforced by the existing ArchUnit rules, which
  already name `com.celestia.guardrail..` among the pure packages.
- Constitution V: no code path may reach the chat model except through the gate,
  and that must be mechanically enforced rather than trusted.
- Constitution IX: `guardrail` must not import the WhatsApp adapter. The gate
  takes plain text and a sender reference, not an `InboundMessage`; `agent`
  adapts.
- Constitution X / ADR-0025: the output stage must catch any disclosure of the
  toolkit's identity, while not blocking an innocent reply that happens to use a
  word which is also a sheet title.

**Scale/Scope**: 5 hostile-rule families plus obfuscation handling, 15 answerable
topic groups, English only for the first cut, 2 corpus files (15 in-scope cases
plus the adversarial set), 1 new ArchUnit rule. The output stage carries 6 leak
signals across 7 leak families — the largest single mechanism here — over all 75
sheet titles split by prose class. 6 resource TSVs, 11 test classes. The
~1,700-term in-scope vocabulary and the title classification are both derived,
not authored; only the stopwords, the English supplement, the homoglyph folds and
the reviewed prose-class exceptions are committed data.

## Constitution Check

*GATE: checked before Phase 0 and re-checked after Phase 1 (v2.1.0).*

| Principle | How this plan complies | Status |
|-----------|------------------------|--------|
| I Spec-driven | spec → this plan → tasks; both clarifications resolved and recorded in spec.md | ✅ |
| II Deterministic core | `guardrail` stays pure: classpath resources only, immutable, no clock or network; same message → same decision (FR-002). Already covered by `LayeringArchitectureTest`'s pure-package list | ✅ |
| III Correctness gated | Both corpora run on every build; the adversarial corpus additionally asserts the answerer was never invoked (SC-001). The in-scope corpus must cover every answerable topic group in English (SC-002) | ✅ |
| IV LLM never calculates or matches | The gate is entirely deterministic — no model is consulted to decide scope, and the classifier that would is deliberately deferred to SPEC-021 | ✅ |
| V Toolkit-scoped single path (NON-NEGOTIABLE) | This feature *is* the principle: injection check and topic gate before the model, fixed refusal with no model call, advisory sentences, self-harm support line, leak scan. `GuardedPipeline` calls the `Answerer` only when allowed; a new ArchUnit rule forbids any other path to a chat model | ✅ |
| VI Deterministic flows | Not touched — onboarding and subscription FSMs are SPEC-017/018 | n/a |
| VII Data protection | The gate stores nothing. A refusal reason names the matched rule, not the message body; the message itself is never logged by this module (research §9) | ✅ |
| VIII Payments | Not touched. The entitlement check precedes the gate and is SPEC-012's | n/a |
| IX Adapters at the edge | `guardrail` depends only on `core`; it never imports `channel-whatsapp` or any provider type. The gate's input is plain text | ✅ |
| X Toolkit is the judgement | The in-scope taxonomy is derived from the toolkit, not restated (FR-006). The leak scan enforces ADR-0025: no toolkit name, sheet id/title or row number reaches a user | ✅ |

No violations, so no Complexity Tracking entries.

## Project Structure

### Documentation (this feature)

```text
specs/011-guardrail-core/
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── guardrail-api.md # the Java API agent and SPEC-012 consume
│   └── corpus-format.md # the two corpus files as a contract
├── checklists/requirements.md
├── deferred-multilingual/  # the drafted Hindi/Hinglish work, parked (not built)
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
guardrail/src/main/java/com/celestia/guardrail/
├── Gate.java                  # inbound decision; the only entry point
├── GateDecision.java          # outcome + reason + matched rule
├── GateRequest.java           # message text + sender reference
├── Normaliser.java            # NFKC, invisibles, homoglyphs, case, decoded views
├── HostileRule.java           # one named attack family
├── HostileRules.java          # the loaded rule set
├── InScopeVocabulary.java     # toolkit-derived + alias terms → TopicGroup
├── SelfHarmSignal.java        # intent vs inquiry about the chart
├── GuardedPipeline.java       # gate → Answerer (only if allowed) → output stage
├── Answerer.java              # the seam SPEC-012 implements
├── OutputStage.java           # advisory, support line, redirect, leak scan
├── OutputRequest.java         # draft reply + what the answer drew on + secrets
├── OutputResult.java          # delivered text, or withheld with a reason
├── LeakRule.java              # one named disclosure family
├── FixedMessages.java         # the product's own copy (refusal, safe, support)
└── Tsv.java                   # package-private reader for this module's resources

guardrail/src/main/resources/com/celestia/guardrail/
├── hostile-phrases.tsv        # rule family → phrase
├── offtopic-phrases.tsv       # diagnosis only: separates off-topic from unmatched
├── stopwords.tsv              # English function words excluded from derivation
├── topic-terms-en.tsv         # compounds/synonyms the toolkit's wording misses
├── homoglyph-folds.tsv        # confusable → Latin
└── title-prose-classes.tsv    # reviewed overrides: which sheet titles are prose

guardrail/src/test/resources/com/celestia/guardrail/
├── corpus-adversarial.tsv     # message → expected reason class
└── corpus-in-scope.tsv        # message → language, topic group

guardrail/src/test/java/com/celestia/guardrail/
├── GateTest.java                    # FR-001..FR-012, decision order
├── NormaliserTest.java              # FR-005, FR-011
├── InScopeVocabularyTest.java       # FR-006, FR-007, derivation + aliases
├── SelfHarmSignalTest.java          # FR-015, intent vs inquiry
├── OutputStageTest.java             # FR-013..FR-018
├── AdversarialCorpusTest.java       # SC-001, zero answerer invocations
├── InScopeCorpusTest.java           # SC-002, 15-question coverage
├── GuardedPipelineTest.java         # FR-003, the single route
├── LeakRuleTest.java                # FR-017, the title prose classification
├── LoadValidationTest.java          # fail fast on bad resource data
└── GatePerformanceTest.java         # SC-008, the 1 ms budget

agent/src/test/java/com/celestia/agent/architecture/
└── DeterminismArchitectureTest.java # + the single-path-to-the-model rule
```

**Structure Decision**: everything lands in the existing, currently empty
`guardrail` module, which the constitution already names as a pure domain module
and which `LayeringArchitectureTest` already guards. `guardrail` gains one
dependency, on `core`, for the toolkit taxonomy and the advisory sentences — the
alternative, copying either into `guardrail`, would have restated what
Constitution X says must come from the toolkit. `agent` changes only to add the
ArchUnit rule; wiring the gate into the reply path is SPEC-012's job, so
`EchoReplyHandler` is left alone here.

## Phase 0 — Research

All unknowns are resolved in [research.md](research.md): module boundary and the
`core` dependency (§1), the normalisation pipeline and why decoded text may only
ever add hostile evidence (§2), decision order under fail-closed (§3), deriving
in-scope vocabulary from the toolkit (§4), how much evidence is enough and the
deliberate asymmetry there (§5), the hostile rule families and how they avoid
refusing a legitimately supplied second chart (§6), distinguishing self-harm
intent from a question about the chart (§7), leak detection that does not trip on
common words (§8), what a refusal may record given Constitution VII (§9), the
corpus format and its CI gate (§10), the `Answerer` seam that makes "no model was
called" testable before the model exists (§11), and — found by drafting the
corpus against the real toolkit rather than reasoning about it — four corrections
to the sections above, including a broken topic-group attribution and a missing
tie-break (§12).

## Phase 1 — Design & Contracts

[data-model.md](data-model.md), [contracts/guardrail-api.md](contracts/guardrail-api.md),
[contracts/corpus-format.md](contracts/corpus-format.md), [quickstart.md](quickstart.md).

### Post-design Constitution re-check

Re-checked against v2.1.0 after the design above. All gates still pass, with
three points worth stating explicitly:

- **V:** the single route is now structural, not documentary. `GuardedPipeline` is
  the only type that calls an `Answerer`, and the new ArchUnit rule forbids any
  class outside it from depending on a chat-model type. The rule passes
  vacuously today — there is no chat model yet — which is the point: it is in
  place before SPEC-012 can introduce one on a second path.
- **VII:** a `GateDecision` carries the matched rule's *name* and never the
  message text, so the refusal log cannot become an unintended store of message
  bodies.
- **X:** the leak scan keys on sheet ids, the toolkit title inside a citation
  frame, and row references — never on a bare word — so "Relationship",
  "Aspects" and "Death" remain usable English in an answer (FR-017).

## Complexity Tracking

None.
