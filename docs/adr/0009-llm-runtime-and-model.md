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
