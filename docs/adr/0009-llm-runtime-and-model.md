# ADR 0009: LLM runtime and model

- **Status:** proposed
- **Date:** 2026-09-02
- **Decision ref:** D9 (PROJECT_PLAN.md §2)

## Context

The product requirement is a **self-hosted** LLM — no external API. The model does
two jobs: (a) interpret KP tool output as prose, and (b) make tool calls and
structured-output extraction during onboarding. Llama 4 is the stated target; its
tool-calling reliability under Ollama is still maturing.

## Options considered

1. Ollama + **Llama 4 Scout** (109B MoE / 17B active; single 80 GB GPU or 64 GB+
   unified memory at 4-bit) with a configured fallback model
   (`llama3.3:70b` or `qwen3`) for tool-heavy turns.
2. Ollama + Llama 4 Maverick (~400B) — needs a multi-GPU host; higher quality.
3. vLLM / TGI instead of Ollama — higher throughput, more operational surface.

## Decision

_Proposed:_ Option 1. Ollama for operational simplicity; **Scout** as primary; a
`celestia.llm.fallback-model` used when the primary returns malformed tool JSON or
fails the guardrail's output checks repeatedly. All model ids are configuration,
not code.

## Consequences

- Drives the hardware decision (ADR-0010).
- The orchestrator must implement the fallback path and pre-fetch chart context so
  an answer is possible even with zero tool calls.
- The eval harness (SPEC-014 / SPEC-015) gates any model swap.
