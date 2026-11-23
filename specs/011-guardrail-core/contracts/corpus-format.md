# Contract: the guardrail corpora (SPEC-011 FR-019, FR-020)

Two curated, human-editable files under
`guardrail/src/test/resources/com/celestia/guardrail/`. They are a **standing CI
gate**, not fixtures: every build replays both, and adding a case requires no
code change.

Format matches the rest of the repository's reviewed data: UTF-8 TSV, line 1 a
`#` comment, line 2 the header, then one case per line. Cells escape `\\`, `\t`
and `\n`.

## `corpus-adversarial.tsv` — everything here must be refused

| Column | Meaning |
|--------|---------|
| `id` | stable, unique; a failure names this, so do not renumber |
| `message` | the input, verbatim, including any obfuscation |
| `expectedReason` | `HOSTILE`, `OVERSIZE`, or `OFF_TOPIC` |
| `expectedRule` | the hostile rule's name, or blank for `OFF_TOPIC` |
| `note` | why this case exists — written for whoever reads it after a failure |

Asserted per case: the outcome is a refusal, the reason is `expectedReason`, and
where given the matched rule is `expectedRule`. Asserting the rule and not just
"refused" is what stops one rule being credited for a refusal another produced.

Asserted once for the whole file: the `Answerer` was invoked **zero** times
(SC-001). That is the measurement Constitution V's promise reduces to.

Required coverage — at least one case for each:

- instruction override; prompt extraction; reasoning extraction; role
  impersonation; cross-user reference;
- each obfuscation: base64, hex, zero-width characters, homoglyphs;
- oversize (past 4,096 characters);
- benign off-topic (a recipe, a coding request) — refused with `OFF_TOPIC`, no
  hostile rule;
- a mixed message: a genuine astrology question **plus** an injection, which must
  be refused as hostile rather than allowed (spec Edge Cases).

## `corpus-in-scope.tsv` — everything here must be allowed

| Column | Meaning |
|--------|---------|
| `id` | stable, unique |
| `message` | the question as a user would really write it |
| `language` | `en` for now. The column is **coverage metadata only** and must never restrict which terms are matched — a message routinely mixes registers, and filtering the vocabulary by this column would refuse it (research §12) |
| `topicGroup` | what the question is about. Documentation, **not** an assertion — see below |
| `note` | why this case exists |

Asserted per case: the outcome is `ALLOWED`. That is the whole assertion.

**Nothing about the topic group is asserted, and that is deliberate.** Two
replays against the real toolkit established it (research §12 finding 1, taken to
its conclusion in §13): neither "the decision's group equals `topicGroup`" nor
the weaker "some matched term is attributable to `topicGroup`" is satisfiable.
The master rule book holds 1,598 of 2,001 rules and spans every subject, so its
vocabulary cannot carry a subject claim; excluding it leaves the specialised
sheets, whose titles are terse compounds (`ChildBirthTime`, `AnnualPrediction`)
that tokenise into almost nothing usable; and inflections diverge
(`child`/`children`, `marriage`/`married`). The group is therefore a best-effort
diagnostic label, and `topicGroup` in this file documents **what the question is
about** so a reader knows why the case exists. The gate's job is allow-or-refuse,
and that is what the corpus holds it to.

Asserted once for the whole file: **every topic group with answerable content in
the loaded toolkit appears at least once.** The group list is read from the
toolkit, not hard-coded, so adding an answerable sheet in a new group fails the
build until the corpus covers it. At the current release that is **15 cases
minimum** (SC-002).

Required coverage beyond that:

- a bare horary number with a question;
- a sensitive but in-scope question (disease, lifespan, speculation, a gemstone)
  — allowed, because the advisory sentence and not a refusal is how those are
  handled;
- a second person's **birth data** supplied in the message — allowed, and the
  counterpart to the `CROSS_USER` adversarial cases (SPEC-010 FR-012);
- a question using a compound or synonym the toolkit words differently
  ("gemstone" where it says "gems"), which is what the English supplementary
  terms exist for (research §12, finding 3).

Hindi and Hinglish cases are deferred to post-launch; the drafted set is parked
in `../deferred-multilingual/` and is not read by the build.

## Why these are a contract and not tests

A reviewer — including the owner, who knows how users really phrase their
questions — must be able to add a line and have it enforced. Cases as Java test
methods would make every addition a code change and put the corpus behind a
build-tool skill, which is the opposite of a living file (FR-020).

The in-scope corpus is also where fail-closed is kept honest. Fail-closed means
anything unrecognised is refused, so this file is the record of what the product
has promised it can recognise. Growing it is how the owner converts an
`UNMATCHED` count into coverage (research §3, SC-007). With the first cut being
English-only, this file is also the record of a known limitation: a user writing
in Hindi is refused until that is re-enabled.
