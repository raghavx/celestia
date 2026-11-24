# Data Model: Guardrail Core (SPEC-011)

Nothing here is persisted. Every type is an immutable value computed per message
or loaded once from a classpath resource — the guardrail holds no state between
messages (Constitution II, research §9).

## Entities

### GateRequest

What the gate is asked about. Deliberately not a channel type (Constitution IX).

| Field | Type | Rule |
|-------|------|------|
| text | text | the message body as received; may be blank |
| senderRef | text | an opaque reference to the sender, used only by `CROSS_USER` to tell "another account" from "birth data in the message". Never parsed as a phone number for any other purpose |

### GateDecision

| Field | Type | Rule |
|-------|------|------|
| outcome | `ALLOWED` · `REFUSED_OUT_OF_SCOPE` · `REFUSED_HOSTILE` | the three outcomes of FR-001 |
| reason | `Reason` | why; the operator's view |
| matchedRule | text | the name of the rule or the topic group that decided it; **never the message text** (research §9) |
| topicGroup | `TopicGroup` or absent | **best-effort diagnostic only.** Present only when allowed, naming the most specific group the matched vocabulary could be attributed to. Often absent or generic even for a clearly on-topic message, because the master rule book's vocabulary carries no subject claim (research §12, §13). No decision depends on it and nothing asserts it |

`Reason` — the four refusal classes FR-010 requires be countable apart, plus
`IN_SCOPE` for an allowed decision:

| Reason | Outcome it implies | Meaning |
|--------|--------------------|---------|
| `IN_SCOPE` | allowed | vocabulary for an answerable topic group matched |
| `HOSTILE` | refused, hostile | a hostile rule family matched |
| `OVERSIZE` | refused, hostile | past the 4,096-character bound (research §3) |
| `OFF_TOPIC` | refused, out of scope | recognised as off-topic — working as intended |
| `UNMATCHED` | refused, out of scope | could not be placed; **the false-refusal signal** (SC-007) |

`OFF_TOPIC` and `UNMATCHED` produce identical user-facing text (FR-008). They
are separate values so the second can be counted on its own.

### NormalisedMessage (internal to the module)

| Field | Type | Rule |
|-------|------|------|
| normalised | text | after the §2 pipeline: NFKC, invisibles stripped, confusables folded, lower-cased, whitespace collapsed |
| decodedCandidates | list of text | base64/hex substrings that decoded to printable text |

**Invariant**: `decodedCandidates` is consulted by hostile rules only. In-scope
evidence is sought in `normalised` alone, so that encoding a subject word cannot
buy passage through the topic gate (research §2).

### HostileRule

| Field | Type | Rule |
|-------|------|------|
| family | `INSTRUCTION_OVERRIDE` · `PROMPT_EXTRACTION` · `REASONING_EXTRACTION` · `ROLE_IMPERSONATION` · `CROSS_USER` | research §6 |
| name | text, unique | what a decision reports |
| phrases | list of text | loaded from `hostile-phrases.tsv`; matched against the normalised view and every decoded candidate |

### InScopeTerm

| Field | Type | Rule |
|-------|------|------|
| term | text, normalised | the evidence |
| group | `TopicGroup` | must be a group with answerable content (FR-007) |
| source | `KP_VOCABULARY` · `TOOLKIT_MATTER` · `SUPPLEMENT` | the first two are derived at load; `SUPPLEMENT` is the committed table of compounds and synonyms the toolkit's wording misses (research §12) |

**Built at load** (research §4, amended by §12): `KP_VOCABULARY` from the engine's
`Nakshatra`, `Graha` and `Sign` constants plus the toolkit's KP terms;
`TOOLKIT_MATTER` from every answerable sheet's title and its promise-rule
matters, minus the stopwords; `SUPPLEMENT` from `topic-terms-en.tsv`.

**Group attribution** (research §12 finding 1, §13): a term takes its group from
its source sheet, **except** terms from the master rule book `1500+Rules`, which
spans every subject and would otherwise make its own group a universal
attractor. Those terms are evidence that the message is astrology at all and
carry no subject claim — which is why attribution is weak by construction and
the group is a label, not a result.

**Matching**: Latin-script terms match on word boundaries, never as bare
substrings (research §12, finding 4).

### SelfHarmSignal

| Value | Meaning |
|-------|---------|
| `NONE` | neither construction present |
| `INTENT` | a first-person volitional construction with a harm term — the support line is owed (FR-015) |
| `CHART_INQUIRY` | a question about what the chart indicates — ordinary health-class content, no support line |

**Rules** (research §7, refined during implementation):

1. Harm words alone are never enough — the Difficulty sheet is full of them.
2. `INTENT` needs a first-person volitional construction **and** the message must
   not be framed as a question about the future ("when will I die?").
3. A **reflexive** phrase (*kill myself, end my life, end it all*) means `INTENT`
   wherever it appears, interrogative or not.
4. `INTENT` wins when both it and a chart referent are present.

### OutputRequest

What the output stage is given. The caller supplies everything the stage must
not have to guess.

| Field | Type | Rule |
|-------|------|------|
| draft | text | what the answerer produced — or, on a refusal, the fixed redirect |
| sensitivityUsed | `Sensitivity` | the most severe class the answer drew on, reported by the toolkit tools — **not** inferred from the draft text. `NONE` on a refusal |
| selfHarm | `SelfHarmSignal` | computed by `GuardedPipeline` from the inbound message, for refused decisions as well as allowed ones |
| recipientRef | text | so another party's identifier in the draft is detectable |
| secrets | list of text | the system prompt and tool names, known only to SPEC-012 (research §8) |

**Refusal form.** `OutputRequest.refusal(SelfHarmSignal, String recipientRef)`
builds the refused case: `draft` is `FixedMessages.refusalRedirect()`,
`sensitivityUsed` is `NONE`, `secrets` is empty. The result is `delivered = true`
with no `withheldReason` — a refusal is a delivered reply, not a withheld one.

### OutputResult

| Field | Type | Rule |
|-------|------|------|
| delivered | bool | false when withheld |
| text | text | what the user receives: the decorated reply, the fixed redirect, or the fixed safe message |
| withheldReason | `LeakFamily` or absent | present only when `delivered` is false |

### LeakRule

One named family of disclosure the scan must catch (research §8).

| Field | Type | Rule |
|-------|------|------|
| family | `LeakFamily` | which of the seven below |
| name | text, unique | what a withheld result reports to the operator |

**What each family keys on is not modelled here**, deliberately: the six signals
are not one kind of thing. Sheet ids are literal slugs, row references are
patterns, a citation frame is a template wrapped around a title, secrets arrive
as a caller-supplied list, another party's identifier is a *shape test against
`recipientRef`*, and reasoning markers are structural tags. A single `signal`
field would mis-describe at least three of them. Research §8 carries the detail
per family, and that is the only place it belongs. The invariant that survives
modelling: **never a bare common word and never a bare number.**

`LeakFamily`: `SHEET_ID` · `SHEET_TITLE` · `ROW_REFERENCE` · `CITATION_FRAME` ·
`SECRET` · `OTHER_PARTY_IDENTIFIER` · `REASONING_MARKER`.

`SHEET_ID` and `SHEET_TITLE` are separate so the operator can tell a parroted
slug — which points at tool-result plumbing — from a parroted title, which points
at the prompt. `CITATION_FRAME` stays for a prose-colliding title caught inside a
frame. The three differ in what you fix, not in how severe they are.

**Prose class** — every sheet **title** and every sheet **id** is `NON_PROSE`
(keyed bare) or `PROSE_COLLIDING` (keyed only inside a citation frame). The class
is derived mechanically from the form's own shape — an internal capital, a digit
or a `+` for a title; a hyphen, an underscore or a digit for an id — with
`title-prose-classes.tsv` recording every reviewed departure from that default
(FR-017, research §8).

Ids are split for the same reason titles are: 16 of the 75 are single ordinary
English words (`numerology`, `death`, `relationship`), so keying every id bare
withholds innocent replies. **Invariant**: the product's own fixed messages must
themselves survive the scan, or every refusal becomes the safe message — which is
what happened on the first implementation run, because the refusal copy names
"numerology".

### FixedMessage

Product copy, set here and never generated (FR-008, FR-016, FR-018).

| Key | Used for |
|-----|----------|
| refusal redirect | every refusal, hostile or out of scope, identically |
| safe replacement | a reply withheld by the leak scan |
| self-harm support line | prepended on `INTENT`, naming Tele-MANAS 14416 |

The four advisory sentences are **not** here: they come from SPEC-010's
`Sensitivity.note()`, which is their single source (Constitution V).

### Corpus case

| Field | Type | Rule |
|-------|------|------|
| id | text, unique | stable, so a failure names a case |
| message | text | the input |
| expectedReason | `Reason`, adversarial file only | asserted per case |
| expectedRule | text, adversarial file only | the hostile rule's name, or blank where no rule decided it (`OFF_TOPIC`, `UNMATCHED`); asserted where given, so one rule is not credited for another's refusal |
| language | `en`, in-scope file only | coverage metadata only. It MUST never restrict which terms are matched (research §12) |
| topicGroup | `TopicGroup`, in-scope file only | **documentation, not an assertion** — what the question is about, so the completeness check can see a group is covered. Nothing asserts the decision's own group (research §13, contracts/corpus-format.md) |
| note | text | why the case exists, for the next reader |

## Decoration order

The output stage composes in exactly this order, so the result is deterministic:

1. If the draft carries the out-of-scope marker → return the fixed redirect, stop.
2. Run the leak scan → if anything fires, return the fixed safe message, stop.
3. Prepend the support line when `selfHarm` is `INTENT`.
4. Append `sensitivityUsed.note()` when it is not `NONE`, exactly once.

Steps 1 and 2 are terminal: a withheld reply is never decorated, because
decorating it would mean assembling a message around content already judged
undeliverable.

This same order is the refusal path (`OutputRequest.refusal`). Steps 1, 2 and 4
are vacuous there — product copy carries no marker, no secret and no sensitivity
— so the only decoration a refusal can receive is step 3's support line. That is
what spec.md's edge case "a refusal and a self-harm support line are both
warranted" requires: the support line is sent, the astrological content is not.

An invariant follows, and is asserted in `GuardedPipelineTest`: the fixed
redirect and the fixed safe replacement must both survive their own leak scan
unchanged. If a future copy edit made one of them trip the scan, every refusal
would silently become the safe message instead.

## Validation at load (fail fast)

- every `InScopeTerm.group` has answerable content in the loaded toolkit;
- `topic-terms-en.tsv` names only known `TopicGroup` values;
- no hostile phrase is empty, and every phrase belongs to a known family;
- no stopword appears as a `SUPPLEMENT` term (it would silence that term);
- every title in `title-prose-classes.tsv` is a real sheet title in the loaded
  manifest, so a renamed sheet cannot leave a stale override behind;
- the toolkit is loadable at all, checked at **both** entry points — without it
  `Gate.bundled()` has no taxonomy and `OutputStage.bundled()` has no leak
  vocabulary, so each fails rather than falling open, reporting a fault rather
  than user error (spec Edge Cases, FR-017).

## Relationships

`GateRequest → NormalisedMessage → GateDecision`;
`HostileRule *—1 family`; `InScopeTerm *—1 TopicGroup`;
`LeakRule *—1 LeakFamily`; `sheet title —1 prose class`;
`GuardedPipeline: GateRequest → GateDecision → (Answerer only if ALLOWED) → OutputRequest → OutputResult`.
