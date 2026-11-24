# ADR 0010: Ollama hosting and deployment

- **Status:** accepted
- **Date:** 2026-09-02 (proposed) · 2026-11-24 (accepted, option 3)
- **Decision ref:** D10 (PROJECT_PLAN.md §2)

## Context

Llama 4 Scout at 4-bit needs roughly 60–80 GB of GPU or unified memory. Cold
model load is tens of seconds; concurrent requests multiply KV-cache memory. The
inference host is separate from the application and the database.

## Options considered

1. Dedicated GPU server on the LAN (e.g. 1× 80 GB card, or 2× 48 GB) running
   `ollama serve` with a long `OLLAMA_KEEP_ALIVE` and a capped `OLLAMA_NUM_PARALLEL`.
2. Apple Silicon box (M-series, 64–128 GB unified memory) — cheaper, slower,
   adequate for low volume.
3. Rented cloud GPU VM — recurring cost, no capital outlay, available today.

## Decision

**Option 3, accepted 2026-11-24.** A rented cloud GPU VM runs `ollama serve`;
the application reaches it via `celestia.llm.base-url`. Model keep-alive is on;
the application enforces a hard concurrency cap (semaphore) and returns a
"busy, try again shortly" message rather than queueing unboundedly; a health
check trips the fallback model (ADR-0009).

Chosen over options 1 and 2 because it needs no procurement and so does not gate
Phase 6: the owner preview can run on real hardware now rather than waiting on a
purchase. Concurrency for the preview is one user.

## Consequences

### What this changes in the documents

- **"Self-hosted" now means *we run the weights*, not *the machine is ours*.**
  The constitution's Technology Constraints say "Ollama, self-hosted"
  (`.specify/memory/constitution.md`), and ADR-0009's Context says "no external
  API". Option 3 satisfies the substance both were protecting — no prompt or
  user data reaches a third-party inference API, the weights and model ids are
  ours, and the runtime is unchanged — but the wording is strained. **Amending
  that line is a separate, deliberate act** under the constitution's Governance
  section (dated ADR, version bump, PROJECT_PLAN update). This ADR is the dated
  record; the amendment is still outstanding.
- **PROJECT_PLAN D10 said "over the LAN" and "dedicated GPU host (or Apple
  Silicon 64GB+)".** Updated on the same date to match this decision.

### What this changes in the system

- **Personal data now leaves the premises.** Birth data, names and message text
  travel over the public internet to a machine in someone else's datacentre.
  Constitution VII governs that data, so this decision carries obligations it
  did not before: transport encryption to the inference host, a provider and
  **region** chosen with Indian users' data in mind, no prompt logging on the
  host, and the host treated as a processor rather than as part of the trust
  boundary. **These are open sub-decisions**, and they should be settled before
  real user traffic rather than before the owner preview.
- **Cost and cold starts pull against each other, and SPEC-012 SC-008 is the
  casualty.** An always-on 80 GB-class GPU VM is a continuing monthly cost for a
  preview serving one person. Stopping it between sessions removes most of that
  cost but adds VM boot plus the "tens of seconds" of cold model load named in
  the Context above — which would blow SC-008's 30-second reply budget on the
  first question of every session. The choice is explicit: keep it warm and pay,
  or accept that the first question of a session is slow and say so in SC-008.
  **Open.**
- The guardrail classifier and embedding models of ADR-0023 (SPEC-021) would run
  on this host too, so its sizing is not only about the answering model.
- Development does not need the VM: ADR-0009 makes model ids configuration, so a
  smaller local model serves until the VM is provisioned, exercising the fallback
  path as the primary path.

### Revisit when

Public traffic arrives. At that point concurrency stops being one, the recurring
cost scales with usage rather than with time, and the data-protection
obligations above stop being deferrable. Options 1 and 2 become comparable again
on cost at that volume.
