# ADR 0009: LLM runtime and model

- **Status:** accepted
- **Date:** 2026-09-02 (proposed) · 2026-11-24 (accepted)
- **Decision ref:** D9 (PROJECT_PLAN.md §2)

## Context

The product requirement is a **self-hosted** LLM — the weights run on
infrastructure we control, and no prompt or user data is sent to a third-party
inference API. The model does two jobs: (a) interpret KP tool output as prose,
and (b) make tool calls and structured-output extraction during onboarding.
Llama 4 is the stated target; its tool-calling reliability under Ollama is still
maturing.

## Options considered

1. Ollama + **Llama 4 Scout** (109B MoE / 17B active; single 80 GB GPU or 64 GB+
   unified memory at 4-bit) with a configured fallback model
   (`llama3.3:70b` or `qwen3`) for tool-heavy turns.
2. Ollama + Llama 4 Maverick (~400B) — needs a multi-GPU host; higher quality.
3. vLLM / TGI instead of Ollama — higher throughput, more operational surface.

## Decision

**Option 1, accepted 2026-11-24.** Ollama for operational simplicity; **Llama 4
Scout** as primary; a `celestia.llm.fallback-model` used when the primary returns
malformed tool JSON or fails the guardrail's output checks repeatedly. All model
ids are configuration, not code.

Where it runs is ADR-0010, which accepted **option 3 (rented cloud GPU VM)** on
the same date. That does not change this decision — the runtime is still Ollama
and the weights are still ours — but it does mean "self-hosted" here should be
read as *we run the weights*, not *the machine is on our premises*. See
ADR-0010's Consequences.

## Development model

**Production is Llama 4 Scout on the rented GPU; development is the smallest
Qwen on the dev machine's CPU** (decided 2026-11-24). Model ids are
configuration, which is what makes this possible without a code change.

- **Which Qwen**: the smallest one that `ollama show <tag>` reports as having the
  **`tools`** capability — try `qwen2.5:0.5b`, step up (`1.5b`, `3b`) if it does
  not. A model with no tool template cannot exercise this feature's main path at
  all; it fails at the Ollama layer rather than producing bad arguments, which
  teaches nothing.
- **What a 0.5B model on a CPU can and cannot prove.** It can prove the round
  trip: the client connects, a tool call is parsed, results come back through the
  reference swap, the output stage decorates, a reply reaches the sender. It
  **cannot** prove the answer is good KP, and nobody should read it as evidence
  either way. With eleven tools and structured arguments, a model this size will
  emit malformed tool JSON often — which is the fallback path of this ADR being
  exercised as the normal path, exactly as ADR-0010 anticipated.
- **Therefore SC-001's practitioner review (SPEC-012 T041) needs the production
  VM**, not the laptop. Ten documents produced by a 0.5B model would waste a
  practitioner's time and discredit the product.

## Consequences

- The hosting decision (ADR-0010) is settled: option 3.
- **The fallback path is not optional.** SPEC-012 is almost entirely tool
  calling — a fixed tool set, five toolkit tools, and a hard rule that the model
  never does arithmetic — and this ADR's own Context warns that Llama 4's
  tool-calling under Ollama is immature. The orchestrator must implement the
  fallback and must pre-fetch chart context so an answer is possible even with
  zero tool calls (SPEC-012 FR-009, FR-010).
- The eval harness gates any model swap. SPEC-012 FR-021 delivers v1 of it: ten
  owner questions, practitioner-reviewed. A model change that drops below the
  SC-001 bar is a regression, not a preference.
- Model ids being configuration means development can run a smaller model
  locally without a code change, which is what makes SPEC-012 buildable before
  the GPU VM exists.

## Measured on the dev machine, 2026-11-24 (SPEC-012 T043, and what T044 is for)

The development story above was tested rather than assumed, and it is **worse than
this ADR expected**. Recorded so nobody repeats the experiment.

- **T043 is answered: `qwen2.5:0.5b` does report the `tools` capability** —
  494M parameters, 32k context, Q4_K_M. The pin stands; the model is not the
  problem.
- **But it cannot drive this tool surface at all.** Eleven tool definitions, a
  hardened prompt and twelve cusps of pre-fetched context put the first token
  far beyond the HTTP read timeout on a 2019 Intel CPU. Four questions of the
  evaluation set took **~24 minutes each**: three ended in
  `FAULT`/`MODEL_UNREACHABLE` after Spring AI retried the primary, then the
  fallback, each to a `ReadTimeoutException`. The fourth answered and its draft
  was **withheld by the leak scan**.
- **So the prediction in "Development model" above was half right.** The failure
  mode is not malformed tool JSON exercising the fallback as the normal path — it
  is *no response at all* inside any sane timeout. The fallback cannot help,
  because a second slow model is still slow.
- **What the run did prove**, which is what tier 2 is for: the round trip works
  end to end — client connects, prompt goes out, a draft comes back, the output
  stage judges it, the turn is recorded, the document is written — and the turn
  **budget does not bound a turn** (SPEC-012 tasks T045). That is a real defect,
  found by a model too slow to hide it.
- **Conclusion**: local Ollama is for compiling and for the stub-model tier, not
  for exercising the agent. Everything about prompt and tool-surface behaviour
  waits for the rented GPU VM (**T044**), which is this ADR's own advice taken
  seriously rather than worked around.

## Amendment, 2026-11-24: development runs against the Claude API

The "Development model" section above prescribes a local `ollama serve` with the
smallest tool-capable Qwen. **That prescription is withdrawn.** It was measured the
same day it was written and it does not work — see "Measured on the dev machine"
above: ~24 minutes a question, three faults in four, nothing learned about the
prompt or the tools. Keeping a development story the project has disproved is the
kind of drift Constitution I calls a defect.

**Decision.** Development and local testing call the Claude API —
`claude-sonnet-5` via Spring AI's Anthropic model — selected by
`celestia.llm.provider=anthropic`. Ollama remains fully wired and is the
committed default, so a clone with no credentials still builds and tests.

### Why this is not a constitution amendment

The Technology Constraint reads: *"what no **deployment** may give up: no prompt
and no user data reaches a third-party inference API."* It is scoped to
deployments, and this is not one:

- **Nothing is deployed.** Development and the evaluation harness run on a
  developer machine against a database that is thrown away.
- **The only personal data in flight is the owner's own** — his birth details and
  his own questions. The preview has an allow-list of one and no other user
  exists, so there is no third party's data to protect and the owner consents for
  himself.
- **Constitution VII is unaffected**: nothing changes about what is stored, what
  "forget me" deletes, or the retention purge.

**Serving anyone other than the owner from a third-party API remains
prohibited** and still requires a constitution amendment with its own ADR. This
amendment does not grant it, and the hosting decision for production is
deliberately left open — see below.

### Why the API rather than renting the GPU now

- **It de-risks the hosting decision instead of pre-committing to it.** As things
  stand, the only way to learn whether the prompt and the tool surface work is to
  rent a machine ADR-0010 itself calls "a standing monthly figure". The API costs
  about **$0.05–0.08 a question** at `claude-sonnet-5` ($3/M input, $15/M output),
  so the ten-question evaluation set runs well under a dollar. Learn first, then
  choose production hosting with evidence.
- **It removes this ADR's own largest risk from the development loop.** The
  Context above warns that Llama 4's tool-calling under Ollama is immature, and
  that is why the fallback model was made mandatory. On a capable API model the
  fallback stops being load-bearing. It stays configured.
- **The architecture already made this a configuration choice.** Model ids are
  configuration (see Consequences), `OwnerAnswerer` holds a `ChatModel`, and both
  single-path ArchUnit rules are expressed over chat-model packages and call
  sites. Swapping the provider adds one bean and changes no architecture.

### Reconciled with constitution v2.3.0

v2.3.0 (the same day) codified this amendment and **defined "deployment"** in the
process: *any run reachable by an inbound message from outside the developer's
machine*. Two consequences follow, and the second is stricter than this
amendment's first draft implied:

- **The evaluation harness is development.** It runs as a test on a developer's
  machine against a throwaway database, with no webhook and no queue. Nothing
  inbound can reach it. The permission above covers it.
- **The owner preview is a deployment, so it may not run on a third-party API.**
  Entitlement limits who it answers, not what it is, and the allow-list is one
  property edit from admitting someone else. **Shipping the preview therefore
  still needs a self-hosted model** — the rented GPU VM of ADR-0010 — or a
  further amendment making the case for a single consenting user on a live
  channel. That case is arguable and is not made here.

So the API buys development speed and de-risks the hosting decision. It does not,
on its own, let the preview go live.

### What this does not decide, and the trap to avoid

**Production hosting is still open** between self-hosted options (own hardware or
rented, per ADR-0010); it is **not** open to a third-party API without the further
amendment named above. But the two questions interact, and the
interaction has a sharp edge: **a practitioner's verdict is tied to a model.**
Tuning `system-prompt.md` against Sonnet 5 and then deploying Llama 4 Scout is
T044's own warning in reverse — fitting one model's behaviour — and SC-001's
verdict would not transfer.

So the order is: develop against the API, decide production hosting, and get
**T041's verdict on whatever production actually runs**. If production becomes the
API too, T044 stops being a risk item and the verdict earned in development is the
verdict that counts.

### Obligations that come with it

- **An API key, not a subscription.** Spring AI wants
  `spring.ai.anthropic.api-key` / `ANTHROPIC_API_KEY`. A Claude Code or claude.ai
  plan is not an API credential and is not licensed to serve an application's
  users.
- **Check the account's data-retention setting.** It is the substance the
  Technology Constraint protects; zero-retention is available to API customers on
  request, and it is worth having before the evaluation set carries real birth
  data.
- **Model pinning changes meaning.** A weights file is pinned for ever; an API
  model has a deprecation schedule. Constitution III makes a model change that
  drops below SC-001's bar a regression, so a retirement means re-running the
  verdict, not editing a property.
- **No `budget_tokens`.** Sonnet 5 rejects the fixed thinking-budget parameter
  outright (HTTP 400). Adaptive thinking is the only on-mode; the configuration
  below uses it, and `celestia.llm.effort` carries the depth knob instead.

## Open item 2 closed: the prompt exercised against the deployed model, 2026-11-24

SPEC-012 T044, against `claude-sonnet-5` on the fixture chart (no personal data).
Four questions, two or three tool rounds each, 13–17 seconds a turn.

**Two integration defects, both found only by a real model:**

1. **Spring AI 2.0.1 does not surface the response content when thinking is
   enabled.** With `thinkingAdaptive()` the reply lands in a package-private
   `AnthropicAssistantMessage` and both `getText()` and `getToolCalls()` return
   empty — measured at `end_turn` with **1,877 output tokens and zero readable
   characters**. The tool loop still ran, because `executeToolCalls` reads the raw
   response; only the accessors are blind. With thinking disabled the same question
   returned 2,183 characters and the tool calls became visible. There is no public
   accessor to work around it, so thinking is off on this provider until a later
   release fixes the mapping.
2. **Spring AI's chat models do not run the tool loop.** They return a tool-call
   response — `hasToolCalls()`, finish `tool_use`, empty text — and leave the round
   to the caller. The stub had *emulated* a loop the framework does not provide, so
   116 tests passed while production could not have worked at all. The loop now
   lives in `OwnerAnswerer`, which is also where the round cap and the deadline can
   actually be enforced.

**What the prompt got right**, which is what this item was for: no reply was
withheld by the leak scan, every KP judgement rested on toolkit calls (41–73
sources behind references), no reply named a source, replies ran 2,172–2,936
characters against a 4,096 budget, and the toolkit's answer obligations held — the
retrograde sub lord was reported as weakening and delaying, and partly-indicated
matters were said to be partial rather than promised.

**Two product defects for the owner to judge** (recorded in SPEC-012's tasks):
the advisory sentence is chosen from every sensitivity the tools *returned* rather
than what the answer used, so a marriage reading ended with a medical disclaimer;
and the out-of-scope marker fired for a speculation question the toolkit does
cover, which the thinking-enabled run had answered better.
