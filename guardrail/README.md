# guardrail

The deterministic gate every message passes before a model is invoked, and the
deterministic pass every reply passes before it reaches a user. Constitution V's
"exactly one route from a user message to an answer", as code.

Pure: no Spring, no database, no network, no clock, no ambient state. The same
message always produces the same decision. Depends only on `core`, for the
toolkit taxonomy, the advisory sentences and the imported workbook.

Built by [SPEC-011](../specs/011-guardrail-core/spec.md).

## The two halves

```
inbound   GateRequest ──► Gate.decide ──► GateDecision
outbound  OutputRequest ──► OutputStage.decorate ──► OutputResult
seam      GuardedPipeline.handle(request, context, answerer)
```

`GuardedPipeline` is the only type that calls an `Answerer`, and it calls it
only on an allowed decision. An ArchUnit rule in `agent`
(`DeterminismArchitectureTest.onlyTheGuardedPipelineMayReachAChatModel`) forbids
any other path to a chat model. It passes vacuously until SPEC-012 introduces
one — which is the point: the rule is in place before a second path can exist.

## Decision order

Evaluated in this order, stopping at the first that fires (research §3):

| # | Condition | Outcome | Reason |
|--:|-----------|---------|--------|
| 1 | longer than 4,096 characters | refused, hostile | `OVERSIZE` |
| 2 | a hostile rule matches the normalised view **or any decoded candidate** | refused, hostile | the rule's name |
| 3 | in-scope evidence in the normalised view | **allowed** | `IN_SCOPE` |
| 4 | a recognised off-topic phrase | refused, out of scope | `OFF_TOPIC` |
| 5 | nothing matched | refused, out of scope | `UNMATCHED` |

Hostile before topic, so a message that is both is recorded as the attack it is.
Rows 4 and 5 produce the **same** user-facing text; they are separate so the
operator can tell "recognised as off-topic" from "could not place it, possibly a
false refusal". That second count is the only instrument the product has for
judging what failing closed costs it.

**One matched term is enough.** The terms that survive the stopword filter are
subject matter, so requiring two would refuse "marriage?" — one word, and that
word is the entire question.

## Where the vocabulary comes from

| Source | How | Count |
|--------|-----|------:|
| KP vocabulary | the engine's `Nakshatra`, `Graha`, `Sign` constants, house ordinals, KP terms | derived |
| Toolkit matters | every answerable sheet's title and promise matters, minus stopwords | derived |
| Supplement | `topic-terms-en.tsv` — compounds and synonyms the toolkit's wording misses | hand-written, small |

About 2,400 terms at the current release, almost all derived: widening the
toolkit widens the gate with no list to maintain (FR-006).

Group attribution is **weak by construction**. A term takes the group of its
source sheet, except terms from the master rule book `1500-rules`, which holds
1,598 of the 2,001 rules and spans every subject. The group is a diagnostic
label, never a result — nothing asserts it.

## Resources, and how to extend them

Everything here is data. Adding a line needs no code change (FR-020).

| File | Holds | Add a line when |
|------|-------|-----------------|
| `hostile-phrases.tsv` | the five attack families | a new attack shape appears |
| `offtopic-phrases.tsv` | recognisably off-topic requests | you want a refusal counted as `OFF_TOPIC` rather than `UNMATCHED` |
| `stopwords.tsv` | English function words excluded from derivation | a generic word is admitting off-topic messages |
| `topic-terms-en.tsv` | compounds and synonyms | a real question was refused as `UNMATCHED` |
| `homoglyph-folds.tsv` | confusable → Latin | a new confusable is used to hide a phrase |
| `title-prose-classes.tsv` | reviewed exceptions to the prose classification | an innocent reply is withheld, or a title leaks |
| `corpus-adversarial.tsv` (test) | refusals, with their expected reason | you find a case that must always be refused |
| `corpus-in-scope.tsv` (test) | questions that must always be allowed | you find a real question the gate refuses |

Both corpora run on every build. The adversarial one additionally asserts the
answerer was invoked **zero** times, that every hostile family has a case, and
that as many cases execute as the file has rows.

## The leak scan

Six signals, **none of which is a bare common word or a bare number**:

1. **sheet ids** that carry a hyphen or digit (`1500-rules`, `ready-reckoner`) — keyed bare;
2. **sheet titles** that cannot occur in English (`ReadyReckoner`, `MarriageMatching`) — keyed bare;
3. **row references** (`row 1349`, `<slug>:r<n>`);
4. **a citation frame** around a prose-colliding title or id ("per the Relationship sheet");
5. **caller-supplied secrets** — the system prompt and tool names, which only SPEC-012 knows;
6. **structural reasoning markers** and **another party's identifier**.

The prose split is what lets "your relationship with your partner", "your
Vimshottari dasha" and "this is about property, not death" through while
catching "ReadyReckoner says…". It is derived mechanically from each title's own
shape, with reviewed exceptions recorded in `title-prose-classes.tsv`.

A reply that fails any signal is **withheld in full** and replaced by the fixed
safe message — never partly redacted, because a fragment of the content that
must not be sent is still that content, and a half-redacted reply tells the
attacker their probe landed.

## Deliberately not here

- **The small-model classifier and the embedding fallback** — ADR-0023 options 1
  and 3, deferred to SPEC-021. This module is ADR-0023's layer 1, the always-on
  deterministic rules.
- **The LLM and the reply wiring** — SPEC-012 supplies the `Answerer` and
  replaces the echo in `ReplyHandler`.
- **Repeated-abuse handling** — strike counts and cool-off periods, SPEC-021.
- **Hindi and Hinglish** — post-launch. The drafted work is parked in
  `specs/011-guardrail-core/deferred-multilingual/`. The matcher stays
  script-aware and table-driven so re-enabling it is data, not surgery.

## Two things to know before trusting it

**Fail closed cuts both ways.** Every gap in the in-scope vocabulary is a real
question refused. The `UNMATCHED` count is the instrument for watching that, and
`corpus-in-scope.tsv` is the record of what the product has promised it can
recognise.

**The gate leans permissive inside recognised vocabulary**, deliberately
(research §5). "Draft a cover letter for a job application" is allowed, because
`job` is genuine toolkit subject matter; the model then answers from the toolkit
or says it has no basis. Allowing an astrology-adjacent message costs a model
call; refusing a paying user's real question costs trust. There is no metric for
the first kind of error yet — worth adding with real traffic.
