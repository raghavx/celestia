# Quickstart: validating SPEC-011

## Prerequisites

- JDK 25 and the Maven Wrapper.
- The toolkit imported — `core/src/main/resources/com/celestia/core/toolkit/`
  must hold `toolkit.properties` and `manifest.tsv`. The gate derives its
  taxonomy from there and refuses to build a gate without it (SPEC-010 T047; it
  is already imported in this repository).
- No ephemeris data, no Docker and no LLM are needed. This feature touches
  neither charts nor the model.

## 1. The gate's own tests

```bash
./mvnw -pl guardrail -am test
```

Expected: green. Covers the decision order, normalisation and obfuscation
handling, in-scope derivation, the self-harm distinction, and the output stage.

## 2. The two corpora (SC-001, SC-002)

```bash
./mvnw -pl guardrail -am test -Dtest='AdversarialCorpusTest,InScopeCorpusTest'
```

Expected: every adversarial case refused with its expected reason, **zero
`Answerer` invocations across the whole file**, every in-scope case allowed, and
the completeness check satisfied — one English question per answerable topic
group (15 at the current toolkit release).

To see the gate fail honestly, add a line to
`guardrail/src/test/resources/com/celestia/guardrail/corpus-in-scope.tsv` for a
question phrased in a way the vocabulary does not cover, and re-run: it should
fail as `UNMATCHED`. That is fail-closed working as designed, and the fix is a
term in `topic-terms-en.tsv` or a stopword removed — not a code change.

## 3. The single route (FR-003)

```bash
./mvnw -pl agent -am test -Dtest='DeterminismArchitectureTest'
```

Expected: green, including the new rule forbidding any class outside
`GuardedPipeline` from depending on a chat-model type. **It passes vacuously
today** — there is no chat model in the build until SPEC-012 — and that is
deliberate: the rule is in place before a second path to the model can be
written.

## 4. What the owner should check by hand

The corpora are judgement, not arithmetic, and two things are worth a human
read:

1. **`corpus-in-scope.tsv`.** Fifteen questions, one per topic group. Read them
   as a user would actually type them — a question nobody would write is not
   evidence the gate works. Fail-closed means every gap is a real question
   refused, so this is the highest-value review in the feature.
2. **`hostile-phrases.tsv`.** The plan assumes attacks arrive predominantly in
   English (research §6 and spec Assumptions). Worth a sceptical look.

## 5. Full build

```bash
./mvnw verify
```

Expected: green for `guardrail` and `core`, including Spotless and the ArchUnit
layering rules — which already list `com.celestia.guardrail..` among the pure
packages, so a stray Spring or network import in this module fails the build.

Two pre-existing failures in this environment are unrelated to this feature:
`HoraryHouseProviderContractTest.isDeterministic` in `ephemeris`, and `agent`'s
Testcontainers integration tests when Docker is not running (ADR-0008).

## What is deliberately not validated here

- Any end-to-end WhatsApp round trip through the gate: the reply path still runs
  `EchoReplyHandler`, and replacing it is SPEC-012.
- The small-model classifier and the embedding fallback: SPEC-021 (ADR-0023
  options 1 and 3).
- Hindi and Hinglish: deferred to post-launch. The drafted corpus and term table
  are in `deferred-multilingual/`, excluded from the build and unreviewed.
- Advisory-sentence wording: owned and tested by SPEC-010's `SensitivityTest`.
  This feature only asserts that exactly one is appended and that it is the most
  severe class used.
