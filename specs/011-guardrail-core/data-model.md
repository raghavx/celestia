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

`Reason` — the three classes FR-010 requires be countable apart, plus the
oversize case:

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

**Rule**: `INTENT` wins when both are present (research §7).

### OutputRequest

What the output stage is given. The caller supplies everything the stage must
not have to guess.

| Field | Type | Rule |
|-------|------|------|
| draft | text | what the answerer produced |
| sensitivityUsed | `Sensitivity` | the most severe class the answer drew on, reported by the toolkit tools — **not** inferred from the draft text |
| selfHarm | `SelfHarmSignal` | carried forward from the inbound message |
| recipientRef | text | so another party's identifier in the draft is detectable |
| secrets | list of text | the system prompt and tool names, known only to SPEC-012 (research §8) |

### OutputResult

| Field | Type | Rule |
|-------|------|------|
| delivered | bool | false when withheld |
| text | text | what the user receives: the decorated reply, the fixed redirect, or the fixed safe message |
| withheldReason | `LeakFamily` or absent | present only when `delivered` is false |

`LeakFamily`: `SHEET_ID` · `ROW_REFERENCE` · `CITATION_FRAME` · `SECRET` ·
`OTHER_PARTY_IDENTIFIER`.

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
| expected | `Reason` (adversarial) or `TopicGroup` (in-scope) | |
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

## Validation at load (fail fast)

- every `InScopeTerm.group` has answerable content in the loaded toolkit;
- `topic-terms-en.tsv` names only known `TopicGroup` values;
- no hostile phrase is empty, and every phrase belongs to a known family;
- no stopword appears as a `SUPPLEMENT` term (it would silence that term);
- the toolkit is loadable at all — if it is not, the gate has no taxonomy and
  refuses everything, reporting a fault rather than user error (spec Edge Cases).

## Relationships

`GateRequest → NormalisedMessage → GateDecision`;
`HostileRule *—1 family`; `InScopeTerm *—1 TopicGroup`;
`GuardedPipeline: GateRequest → GateDecision → (Answerer only if ALLOWED) → OutputRequest → OutputResult`.
