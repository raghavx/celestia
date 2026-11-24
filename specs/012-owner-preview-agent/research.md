# Research: Owner-Preview Agent (SPEC-012)

Phase 0 decisions. Each records what was chosen, why, and what was rejected.
Written after ADR-0009 and ADR-0010 were accepted on 2026-11-24, so the runtime
is settled: Ollama + Llama 4 Scout on a warm rented cloud GPU VM.

## §1 Where the code lives, and what may touch Spring

- **Decision**: everything in this feature lives in `agent`, in three new
  packages — `llm` (the model, the prompt, the tools, the answerer), `entitlement`
  (who may be answered) and `eval` (the harness). `guardrail` and `core` are not
  modified.
- **Rationale**: Constitution II keeps `ephemeris`, `core`, `geo`, `billing` and
  `guardrail` pure, and names `agent` as the only place Spring belongs
  (Technology Constraints). Spring AI's `OllamaChatModel` is Spring, so it cannot
  live anywhere else. The seam already exists: SPEC-011's `Answerer` is a pure
  functional interface in `guardrail`, and `agent` implements it.
- **What this means for the ArchUnit rule**: the single-path rule added in
  SPEC-011 forbids any class outside `GuardedPipeline` from depending on a
  chat-model type, and it has passed vacuously because no chat model existed.
  This feature is the first time it bites. The implementation must therefore put
  the Spring AI types behind the `Answerer` such that only `GuardedPipeline`
  reaches them — see §2.
- **Alternatives**: a new `llm` Maven module (more layering to enforce, no
  benefit while there is one consumer); putting the answerer in `guardrail`
  (breaks Constitution II immediately).

## §2 Satisfying the single-path rule with a real chat model

- **Decision**: the ArchUnit rule stays as written, and the answerer is
  constructed so it does not violate it: `OwnerAnswerer implements Answerer`
  holds a `ChatClient`, and **`GuardedPipeline` is the only type that calls
  `Answerer.answer`**. The rule forbids classes *depending on a chat-model type*,
  which `OwnerAnswerer` necessarily does — so the rule must be amended to permit
  exactly one additional class, named explicitly, rather than loosened to a
  package.
- **Rationale**: the rule's purpose is that there be one route to the model, not
  that no class may name the model's type — which is impossible once a model
  exists. Naming the single permitted implementation keeps the rule as tight as
  it can be while remaining true: two names on the allow-list, not a wildcard.
- **How it must not be done**: widening the rule to
  `com.celestia.agent.llm..` would let any future class in that package reach
  the model, which is the second path the rule exists to prevent. The amendment
  must be `GuardedPipeline` **plus `OwnerAnswerer`**, by fully-qualified name.
- **Alternatives**: deleting the rule (throws away SPEC-011's structural
  guarantee); leaving it and reflectively constructing the client (defeats the
  rule while appearing to honour it — worse than deleting it, because it lies).

## §3 The opaque source reference (spec FR-013a–d)

- **Decision**: the toolkit tools return their results to the application, which
  replaces each `source` (`{sheetId, sheetTitle, row}`) with a **per-turn
  reference** of the form `s1`, `s2`, … before the result is serialised into the
  model's context. The mapping lives in the turn's record and nowhere else.
- **Rationale**: Constitution X forbids the reply naming the toolkit. Keeping the
  strings out of the model's context converts that from a prompt instruction into
  a structural property. Per-turn numbering is deliberate: a stable global
  handle would accumulate into a durable map of the toolkit's structure, which is
  more to protect than the problem needs, and a reference is only ever needed
  within the turn that produced it.
- **Why it must not be reversible**: encoding the sheet id into the handle (a
  hash, a slug, base64) puts the string back in the model's context by another
  route. The reference is an index into a per-turn list, nothing more.
- **What the model still gets**: everything it needs to reason. Two findings
  resting on the same rule carry the same reference, so SPEC-010's
  `agent-tools.md` obligation 3 ("hits sharing a matterKey are reported once,
  with all their sources") is still expressible — the model can say two findings
  agree without knowing what they agree on.
- **Alternatives**: passing the real source and relying on the prompt (what the
  spec rejected in Q1); stripping sources entirely (loses obligation 3 and the
  model's ability to group).

## §4 Stateless turns (spec FR-018)

- **Decision**: each turn is assembled from the inbound message and the owner's
  chart alone. No prior message text, no carried subject, no `ChatMemory`.
- **Rationale**: Q2's answer. Spring AI offers conversation memory and it is
  deliberately not used — the preview's audience is one person who can repeat
  himself, and a question that names its own matter works anyway because the
  chart is already context.
- **Consequence for the implementation**: there is no `ChatMemory` bean, and the
  prompt is built fresh per turn. That also makes the turn trivially
  reproducible, which the eval harness depends on (§7).
- **What must be handled**: a message that cannot stand alone ("what about
  her?"). The prompt instructs the model to ask what is meant; FR-018b makes that
  a requirement rather than a style preference.

## §5 The hardened system prompt, and where it lives

- **Decision**: one classpath resource, `system-prompt.md`, loaded once and
  versioned with the code. Not a database row, not a configuration property.
- **Rationale**: the prompt is the only thing standing between the model and
  several NON-NEGOTIABLE obligations — speak as the astrologer, never name the
  toolkit, never state interpretation without a basis, emit
  `<<OUT_OF_SCOPE>>` rather than improvise. A change to it is a change to
  behaviour that the eval harness must re-gate, so it belongs in the same review
  and the same commit as code, under the same version control. A prompt that can
  be edited in production without review is a guardrail that can be edited in
  production without review.
- **What it must contain**, each traceable to a requirement: the astrologer voice
  (FR-012); the prohibition on naming the toolkit, a sheet or a row (FR-012); no
  interpretation without a tool result (FR-014); the answer obligations of
  SPEC-010 `agent-tools.md` (FR-015); the out-of-scope marker (spec FR-014, and
  SPEC-011 FR-016 recognises it); never write an advisory sentence or a crisis
  line, because the output stage owns both (FR-016); ask rather than guess when a
  message cannot stand alone (FR-018b); stay inside the reply budget (FR-017).
- **Alternatives**: prompt in a property file (invites production edits); prompt
  assembled from fragments per turn (harder to review, and the thing most worth
  reviewing).

## §6 Tool exposure, budgets and failure

- **Decision**: the five toolkit tools of SPEC-010's `agent-tools.md` plus the
  engine tools are exposed as Spring AI `@Tool` methods on two adapter classes in
  `agent.llm.tools`. Each adapter injects `userId`, coordinates and the clock
  server-side (FR-006) and never reads them from arguments.
- **Turn budget**: a cap on tool-call rounds and a wall-clock deadline for the
  whole turn. Exceeding either ends the turn in the fixed "could not complete"
  message (FR-010) and records a fault.
- **Why a round cap and not just a timeout**: a model that loops cheaply can
  consume the whole deadline without producing anything, and the resulting fault
  is indistinguishable from a slow model. Counting rounds separates "the model is
  confused" from "the model is slow", which matters because the first is a prompt
  defect and the second is a hosting one.
- **Concurrency**: one permit, per ADR-0010 — the preview has one user, and an
  unbounded queue in front of a single warm GPU is how a preview becomes an
  outage. A second concurrent turn gets "busy, try again shortly".
- **Fallback**: ADR-0009's fallback model is used when the primary returns
  malformed tool JSON or fails repeatedly. During development, before the VM
  exists, a small local model stands in — which exercises the fallback path as
  the primary path, exactly as ADR-0010 anticipated.

## §7 The eval harness, and what it can and cannot assert

- **Decision**: ten of the owner's questions in a committed file, run by a test
  that writes one reviewable document per question — the question, the delivered
  reply, the tool calls, and the **resolved** sources behind each reference.
  Output goes to `agent/target/owner-eval/`, mirroring how SPEC-010 presented
  promise readings for review.
- **What it asserts mechanically**: that every reply has at least one tool call
  behind it (SC-002); that no delivered reply names the toolkit, a sheet or a row
  (SC-003); that nothing was withheld by the leak scan (SC-004); that no tool
  result presented to the model contained a sheet id, title or row (SC-010); and
  that every reference resolves (SC-011).
- **What it cannot assert**: whether the answer is *good astrology*. Only a
  practitioner can mark that, which is why the output is a document rather than
  an assertion, and why SC-001's bar is a human verdict (≥ 9 of 10).
- **Why it must run against a real model**: a fake answerer proves the wiring and
  nothing about the prompt. The harness therefore requires a reachable model and
  is excluded from the default build, like the Testcontainers tests.

## §8 The entitlement check, placed before the gate

- **Decision**: a small `EntitlementService` in `agent.entitlement` with one
  method answering "may this sender be answered?", backed by an allow-list of
  E.164 numbers in configuration. It is consulted in the reply handler **before**
  the gate and before any chart work.
- **Rationale**: spec FR-001. Checking first means an unknown sender costs
  nothing — no normalisation, no vocabulary match, no chart, no model — and
  cannot reach any of it. It is also the seam SPEC-018 replaces with real plans
  and quotas without touching the reply path.
- **Fail closed**: an unreadable or empty allow-list denies everyone (FR-003).
  The alternative — an empty list meaning "allow all" — is the kind of default
  that turns a configuration slip into an open service.
- **Alternatives**: checking inside the gate (conflates two different questions,
  and SPEC-011 is pure so it cannot read configuration); checking after the gate
  (a stranger's hostile message would be gated and logged before being refused,
  which is work done on behalf of someone not entitled to it).

## §9 What replaces `EchoReplyHandler`

- **Decision**: a new `GuardedReplyHandler implements ReplyHandler` that runs
  entitlement → `GuardedPipeline.handle` → outbound message.
  `EchoReplyHandler` is deleted rather than kept behind a flag.
- **Rationale**: `ReplyHandler`'s own Javadoc anticipated exactly this
  replacement, and the webhook, worker and sender are untouched — SPEC-009's seam
  holds. Keeping the echo behind a toggle would leave a second reply path in the
  code, and "exactly one route" is the principle this whole feature serves.
- **Non-text messages**: `ReplyHandler` must not throw for unrecognised content
  (SPEC-009 FR-006). A non-text message gets a fixed "text only for now" reply
  without reaching the gate or the model.

## §11 Proving the guardrail did not regress (SC-006)

- **Decision**: SPEC-011's adversarial corpus is replayed through the **real**
  reply path — `GuardedReplyHandler` with the actual `OwnerAnswerer` bean in
  place — wrapped in a counting decorator, asserting zero invocations across the
  whole file. The corpus file itself is reused, not copied.
- **Rationale**: SPEC-011 proved zero invocations against a *fake* answerer,
  which was the best available proof before a model existed. It says nothing
  about whether wiring a real one introduced a bypass — and this feature adds
  two things in front of the gate (entitlement, pre-fetch) and one behind it
  (the answerer), any of which could. SC-006 is the regression check on the
  single property the whole guardrail exists to deliver, so it must run against
  the real composition.
- **Why a counting decorator rather than a mock model**: a mock `ChatModel`
  would prove the model was not *called*, while leaving `OwnerAnswerer` itself
  reachable. Counting at the `Answerer` boundary is what FR-003 is actually
  about: nothing reached the answerer, so nothing could have reached the model.
- **Where it runs**: in the default build, with no model required — the whole
  point is that no invocation happens, so there is nothing for a model to do.
  That keeps the property in CI rather than behind the eval profile.
- **Alternatives**: trusting SPEC-011's own run (proves the gate, not the
  wiring); asserting on Ollama request logs (needs a reachable model to prove a
  negative, and absence of a log line is weak evidence).

## §10 What is deliberately not here

- **Onboarding** — collecting name and birth details conversationally is
  SPEC-017. Here the owner's data is seeded by an admin command (FR-019).
- **Plans, quotas and payment** — SPEC-018 onward. The entitlement interface is
  the seam they plug into.
- **Rate limiting, abuse handling, model failover beyond the single fallback,
  observability** — SPEC-021 and SPEC-022.
- **Conversation continuity** — withdrawn in Q2; the spec records what a later
  attempt must resolve.
- **Streaming replies** — WhatsApp is not a streaming channel for this purpose,
  and a reply is produced synchronously within the turn.
