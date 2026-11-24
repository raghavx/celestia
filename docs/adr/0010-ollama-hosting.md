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

**This is IaaS, and the distinction is the whole reason the constitution's
amendment works.** We rent the GPU and **we administer the Ollama server
ourselves** — the instance, the model weights, the version, the configuration.
We are not buying inference from anyone. A managed model API would put prompts
and user data in a vendor's hands and would be forbidden by Technology
Constraints as amended in v2.2.0; renting the metal under our own server is not,
because the thing the constraint protects — that no prompt reaches a third-party
inference API — still holds.

**Provider: AWS or Oracle Cloud** (decided 2026-11-24, narrowed from "a rented
cloud GPU VM"). Which of the two, and in which region, is still open — but the
region is no longer a free choice: v2.2.0 requires it be chosen for the personal
data the host will see, and the users are in India, so the realistic candidates
are the Mumbai regions (AWS `ap-south-1`, OCI Mumbai or Hyderabad). Picking a US
or EU region would need a reason beyond price.

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
  record, and **the amendment was made the same day: constitution v2.1.0 →
  v2.2.0** defines "self-hosted" as *we run the weights*, permits a rented host
  we control, and attaches three obligations to one — encrypted transport, a
  region chosen for the personal data it will see, and no prompt logging. The
  first two open sub-decisions below are therefore now constitutional
  requirements rather than good intentions.
- **PROJECT_PLAN D10 said "over the LAN" and "dedicated GPU host (or Apple
  Silicon 64GB+)".** Updated on the same date to match this decision.

### What this changes in the system

- **Personal data now leaves the premises.** Birth data, names and message text
  travel over the public internet to a machine in someone else's datacentre.
  Constitution VII governs that data, and **v2.2.0 turned three of these from
  intentions into requirements**: encrypted transport to the inference host, a
  region chosen for the personal data it will see, and no prompt logging on the
  host — which, since we administer the server, is ours to configure rather than
  a vendor's to promise. The host is a processor, not part of the trust boundary.
  **What remains open is the choice between AWS and Oracle Cloud and the exact
  region**, and that is a decision to make before real user traffic rather than
  before the owner preview.
- **The VM stays warm. Decided 2026-11-24.** Cost and cold starts pull against
  each other: stopping the VM between sessions saves most of the money but adds
  boot plus the "tens of seconds" of model load named in the Context above, which
  would break SPEC-012 SC-008's 30-second reply budget on the first question of
  every session. We pay for a continuously running 80 GB-class VM instead, so
  that the preview is judged on whether the answers are good rather than on
  whether the first one arrives. `OLLAMA_KEEP_ALIVE` is set so the model stays
  resident between requests as well.

  This makes the preview's cost a standing monthly figure rather than a usage
  one — the thing to watch while the audience is a single person, and the first
  reason to revisit options 1 and 2.
- The guardrail classifier and embedding models of ADR-0023 (SPEC-021) would run
  on this host too, so its sizing is not only about the answering model.
- Development does not need the VM: ADR-0009 makes model ids configuration, so a
  smaller local model serves until the VM is provisioned, exercising the fallback
  path as the primary path.

### Development host (decided 2026-11-24)

**Production as above; development runs `ollama serve` on the dev machine** — a
2019 Intel MacBook Pro (i7-9750H, 16 GB). Three things follow from that hardware,
and they are the reason development and production are described separately
rather than as one setup with a smaller model:

- **It is CPU-only.** Metal acceleration is Apple Silicon, and the Radeon Pro in
  a 2019 MacBook Pro is not a target Ollama supports. Inference runs on six cores
  of a mobile i7, which is why the model has to be tiny (ADR-0009).
- **`OLLAMA_KEEP_ALIVE` should be short here, not long.** The production reason
  for a long keep-alive — never pay the cold load — is a laptop's reason to keep
  a model resident in RAM it needs for everything else. The settings are
  deliberately opposite at the two ends.
- **SPEC-012 SC-008's 30-second budget does not apply to this machine and must
  not be "fixed" against it.** Latency here will be far worse and means nothing;
  SC-008 is measured on the warm VM (SPEC-012 T040).

16 GB is ample for a sub-1B model, so memory is not the constraint. Throughput
is, and the answer to it is the production VM rather than a bigger local model.

### Revisit when

Public traffic arrives. At that point concurrency stops being one, the recurring
cost scales with usage rather than with time, and the data-protection
obligations above stop being deferrable. Options 1 and 2 become comparable again
on cost at that volume.
