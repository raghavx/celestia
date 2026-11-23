# Contract: `com.celestia.guardrail` public API (SPEC-011)

Pure Java, no Spring. Immutable records and enums. Every method deterministic:
the same input always gives the same result, with no clock, network or stored
state (Constitution II).

Consumed by `agent` (SPEC-012), which supplies the `Answerer` and the secrets to
scan for.

## `Gate`

| Method | Returns | Errors / notes |
|--------|---------|----------------|
| `static Gate bundled()` | the shared gate, built once from `KpToolkit.bundled()` | `IllegalStateException` if the toolkit cannot be loaded — the gate has no taxonomy without it and must not fall open (spec Edge Cases) |
| `GateDecision decide(GateRequest request)` | the decision | never throws on message content; a blank, emoji-only or oversize message is a decision, not an error |

`decide` evaluates in the fixed order of research §3 and stops at the first
match: oversize, hostile, in-scope, off-topic, unmatched.

## `GuardedPipeline`

The only type that calls an `Answerer`. This is what makes Constitution V's
single route structural rather than documentary.

| Method | Returns |
|--------|---------|
| `GuardedPipeline(Gate gate, OutputStage output)` / `static bundled()` | pipeline |
| `OutputResult handle(GateRequest request, AnswerContext context, Answerer answerer)` | what to send |

Behaviour:

1. `gate.decide(request)`.
2. If **not** `ALLOWED` — return the fixed refusal redirect. **`answerer` is not
   called.** This is the property SC-001 measures.
3. If `ALLOWED` — call `answerer.answer(...)` exactly once, then run the result
   through `OutputStage`.

## `Answerer`

```java
@FunctionalInterface
public interface Answerer {
    Answer answer(GateRequest request, AnswerContext context);
}
```

`Answer(String draft, Sensitivity sensitivityUsed)` — the draft reply and the
most severe sensitivity class it drew on. The class is **reported by the caller**,
from the toolkit tool results, not inferred from the draft text (data-model
`OutputRequest`).

`AnswerContext(String recipientRef, List<String> secrets)` — what the output
stage needs that only the caller knows: who the reply is going to, and the
system prompt and tool names to scan for.

SPEC-012 implements `Answerer` over the LLM. Nothing else may: the ArchUnit rule
below forbids a second path.

## `OutputStage`

| Method | Returns | Notes |
|--------|---------|-------|
| `static OutputStage bundled()` | the shared stage | |
| `OutputResult decorate(OutputRequest request)` | delivered text, or withheld | composes in the fixed order of data-model "Decoration order" |

Guarantees:

- At most **one** advisory sentence, always `sensitivityUsed.note()` verbatim,
  appended last (FR-014). `Sensitivity.NONE` adds nothing.
- The support line is prepended when and only when `selfHarm` is `INTENT`
  (FR-015).
- The out-of-scope marker and a leak are both **terminal**: the reply is replaced
  in full, never partly redacted (FR-016, FR-018).

## `SelfHarmSignal`

| Method | Returns |
|--------|---------|
| `static SelfHarmSignal of(String normalisedText)` | `NONE`, `INTENT` or `CHART_INQUIRY` |

`INTENT` requires a first-person volitional construction with a harm term, and
wins over `CHART_INQUIRY` when both appear (research §7). A question about what
the chart indicates is `CHART_INQUIRY` and is ordinary health-class content.

## `FixedMessages`

| Method | Returns |
|--------|---------|
| `static String refusalRedirect()` | the single refusal text, identical for hostile and out-of-scope (FR-008) |
| `static String safeReplacement()` | what replaces a withheld reply (FR-018) |
| `static String selfHarmSupportLine()` | the support line, naming Tele-MANAS 14416 |

These are the only source of this copy. The four **advisory** sentences are not
here — they belong to SPEC-010's `Sensitivity.note()` (Constitution V: product-set
in exactly one place).

## What this module deliberately does not expose

- No way to obtain an `ALLOWED` decision other than `Gate.decide`.
- No way to reach an `Answerer` other than `GuardedPipeline.handle`.
- No logging. A `GateDecision` is a value; `agent` decides what to record, and
  the decision carries the matched rule's name but never the message text
  (research §9).
- No channel types. The gate takes text and an opaque sender reference, so
  `guardrail` never imports `channel-whatsapp` (Constitution IX).

## Enforced elsewhere

A rule in `agent`'s `DeterminismArchitectureTest` forbids any class outside
`GuardedPipeline` from depending on a chat-model type. It passes vacuously until
SPEC-012 introduces one — which is the point: it is in place before a second
path can be written (research §11).

## Stability

Adding a `TopicGroup` to the toolkit widens the gate with no API change. Adding a
hostile phrase or a translated term is a resource change. Adding a `Reason`
constant is not breaking; removing one is. Renaming a hostile rule changes what
decisions report and so requires updating the adversarial corpus in the same
change.
