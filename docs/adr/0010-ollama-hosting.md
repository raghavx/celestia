# ADR 0010: Ollama hosting and deployment

- **Status:** proposed
- **Date:** 2026-09-02
- **Decision ref:** D10 (PROJECT_PLAN.md §2)

## Context

Llama 4 Scout at 4-bit needs roughly 60–80 GB of GPU or unified memory. Cold model
load is tens of seconds; concurrent requests multiply KV-cache memory. The
inference host is separate from the application and the database.

## Options considered

1. Dedicated GPU server on the LAN (e.g. 1× 80 GB card, or 2× 48 GB) running
   `ollama serve` with a long `OLLAMA_KEEP_ALIVE` and a capped `OLLAMA_NUM_PARALLEL`.
2. Apple Silicon box (M-series, 64–128 GB unified memory) — cheaper, slower,
   adequate for low volume.
3. Rented cloud GPU VM — arguably not "self-hosted"; recurring cost.

## Decision

_Proposed:_ Option 1 or 2 depending on expected concurrency. The application
reaches it via `celestia.llm.base-url`. Model keep-alive is on; the application
enforces a hard concurrency cap (semaphore) and returns a "busy, try again
shortly" message rather than queueing unboundedly; a health check trips the
fallback model (ADR-0009).

## Consequences

- This is a procurement / capital decision that gates Phases 6–8.
- Until hardware exists, development uses a smaller local model and the fallback
  path is exercised as the primary path.
- The guardrail classifier and embedding models (ADR-0023) also run on this host.
