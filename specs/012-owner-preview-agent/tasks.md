---

description: "Task list for SPEC-012: Owner-Preview Agent"
---

# Tasks: Owner-Preview Agent

**Input**: Design documents from `/specs/012-owner-preview-agent/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/answerer-api.md](contracts/answerer-api.md),
[contracts/llm-tools.md](contracts/llm-tools.md),
[contracts/system-prompt.md](contracts/system-prompt.md), [quickstart.md](quickstart.md)

**Tests**: included — not optional. Constitution III gates correctness, SC-002
through SC-012 are assertions rather than aspirations, and plan.md's Project
Structure names the test classes explicitly. Two kinds here, and the difference
matters: tests that run with a **stub model** belong in the default build, and
the **eval harness** needs a reachable model and does not.

**Organization**: grouped by user story (spec.md priorities P1/P1/P2/P2/P2/P3) so
each is independently testable. Where a story is not independent *in code*, the
Dependencies section says so plainly rather than pretending otherwise.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no unmet dependency)
- **[Story]**: US1 … US6, matching spec.md's user stories
- Every task names an exact file path

## A note on what already exists

- **Spring AI 2.0.1 is already in the parent pom's `dependencyManagement`**, and
  `agent/pom.xml` carries a comment anticipating the Ollama starter. T001 acts on
  that comment.
- **`BirthDataStore` and `ChartCacheService.natalChart(userId)` already exist**
  from SPEC-008. The pre-fetch of US5 is mostly wiring, not new computation.
- **`guardrail` is complete and must not be modified.** The whole point of
  building it first was that this feature plugs in without touching it. A task
  here that edits `guardrail/` is a task that has gone wrong.
- **`ReplyHandler`'s own Javadoc anticipated this replacement**, so SPEC-009's
  webhook, worker and sender are untouched.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: the dependency and the configuration surface, before any behaviour.

- [X] T001 Add `spring-ai-starter-model-ollama` to `agent/pom.xml`, replacing
      the "Phase 6+" comment that anticipates it. Its version comes from the
      parent's `dependencyManagement` (2.0.1), so no version here.
      **`core`, `guardrail` and `channel-whatsapp` are already dependencies** —
      verified, so the `Answerer` seam is reachable today and this is the only
      new one
- [X] T002 [P] `LlmProperties` record bound to `celestia.llm.*` — `base-url`,
      `model`, `fallback-model`, `max-tool-rounds`, `turn-deadline`,
      `max-concurrent-turns` — in
      `agent/src/main/java/com/celestia/agent/llm/LlmProperties.java`.
      **Validate at bind time** (data-model "Validation at load"): the bounds
      are positive — a misconfiguration must not silently mean "unbounded" — and
      `base-url`, `model` and `fallback-model` are all non-blank, so a missing
      model name fails at startup rather than on the owner's first question
- [X] T003 [P] `EntitlementProperties` bound to `celestia.entitlement.allow-list`
      in `agent/src/main/java/com/celestia/agent/entitlement/EntitlementProperties.java`
- [X] T004 Add the `celestia.llm.*` and `celestia.entitlement.*` keys to
      `agent/src/main/resources/application.yml` with **development** defaults
      (ADR-0009, ADR-0010): `base-url` a local `ollama serve`, `model`
      **`qwen2.5:0.5b`** (provisional — T043 verifies it can tool-call at all,
      and the answer may be another model entirely), the configured fallback, a **short** keep-alive because the dev machine is a
      16 GB laptop rather than a dedicated VM, and an **empty** allow-list —
      empty denies everyone (FR-003), the safe default for a key someone will
      forget to set. Production values are supplied per environment, not
      committed

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the model client, the turn record, the source-reference machinery and
the entitlement check — everything the reply path needs before any story can be
written.

**⚠️ CRITICAL**: no user story task may start until this phase is complete.

- [X] T005 `LlmConfig`: the `ChatClient` over `OllamaChatModel` from
      `LlmProperties`, a second client bound to the fallback model (ADR-0009),
      and the **turn permit** as a bean sized from `max-concurrent-turns`
      (**FR-010a**, ADR-0010). The permit is **the handler's to take, not the
      answerer's** — see T016 — so this task only provides it, in
      `agent/src/main/java/com/celestia/agent/llm/LlmConfig.java`.
      Keep-alive is the server's concern, not the client's — the VM stays warm
      (ADR-0010), so the client does not retry a cold start
- [X] T006 [P] The turn record, **in the layering SPEC-008 established and
      `PersistenceArchitectureTest` guards**: `OwnerTurnRecordEntity` in
      `agent/src/main/java/com/celestia/agent/persistence/entity/OwnerTurnRecordEntity.java`,
      `OwnerTurnRecordRepository` in
      `agent/src/main/java/com/celestia/agent/persistence/repository/OwnerTurnRecordRepository.java`,
      `TurnRecordStore` in
      `agent/src/main/java/com/celestia/agent/persistence/store/TurnRecordStore.java`,
      the `TurnDetail` value in
      `agent/src/main/java/com/celestia/agent/llm/TurnDetail.java`, and the
      migration in
      `agent/src/main/resources/db/migration/V8__owner_turn_record.sql`
      (**`V8`** — the existing migrations run `V1`…`V7`, the last two being
      `V6__async_io.sql` and `V7__whatsapp_channel.sql`; a duplicate version
      fails Flyway at startup, and a version-less `V__` name does not parse).
      `llm/` and `channel/` talk to the store, never
      to the entity. The record holds the **question** and the **delivered
      reply** — a practitioner cannot review an answer without knowing what was
      asked and what was received — and **nothing else about any message**: no
      normalised form, no intermediate draft, no earlier turn. SPEC-011 kept the
      gate's decisions free of message text and this must not add more back than
      those two columns (FR-020). Give **both** a column the **deferred
      column-encryption pass can reach without a schema change** — a sized
      `VARBINARY`-compatible shape rather than `TEXT` — which is the position
      SPEC-008 took for its own PII columns and all Constitution VII's
      "encrypted at rest where feasible" asks for while that pass is still
      outstanding (**FR-020c**). Both are covered by T006a's delete and T006b's
      purge. `outcome` carries the six values of data-model.md "TurnRecord", not
      a narrower set migrated later (see T029)
- [X] T007 [P] `SourceReferences`: the per-turn mapping from
      `{sheetId, sheetTitle, row}` to the **references** `s1`, `s2`, … — one
      word for this throughout, the one FR-013a uses — earlier drafts also said
      "label" and "handle" for it — in
      `agent/src/main/java/com/celestia/agent/llm/SourceReferences.java`.
      **An index, never an encoding** — no hash, no slug, no base64, because a
      reversible reference puts the sheet id back in the model's context by another
      route. The same source seen twice in a turn returns the **same** reference, so
      SPEC-010 `agent-tools.md` obligation 3 survives the swap (FR-013a–c,
      research.md §3)
- [X] T008 [P] `TurnContext` — `userId`, question, pre-fetched chart, running
      dasha, optional supplied chart — in
      `agent/src/main/java/com/celestia/agent/llm/TurnContext.java`. The supplied
      chart field is documented as **dying with the turn** (FR-011)
- [X] T006a `TurnRecordStore.deleteForUser(userId)`, wired into SPEC-008's
      "forget me" hard delete so `owner_turn_record` joins the rows FR-010 of
      that spec already removes (**FR-020a**), in
      `agent/src/main/java/com/celestia/agent/persistence/store/TurnRecordStore.java`
      and `.../UserAccountService.java`. **A deletion that leaves every question
      a user ever asked is not a deletion** — Constitution VII makes the hard
      delete a MUST, and a new table holding message bodies does not get to opt
      out (depends on T006)
- [X] T006b `TurnRecordStore.purgeOlderThan(...)` taking its "now" from an
      **injected `Clock`** so SC-013 is testable without waiting ninety days,
      plus a `@Scheduled` job — **reusing the `@EnableScheduling` already on
      `ChannelConfig`** for SPEC-009's worker rather than adding a second — and
      `celestia.turn-record.retention-days` (**default 90**) in
      `agent/src/main/java/com/celestia/agent/persistence/store/TurnRecordStore.java`
      and `agent/src/main/resources/application.yml`. **`0` means never purge
      on age** — implemented so it cannot be mistaken for disabling T006a, which
      no setting may switch off (**FR-020b**) (depends on T006)
- [X] T006c `TurnRecordRetentionIT`: a "forget me" request leaves **zero**
      turn records for that user; a record past the retention age is gone after a
      purge; one inside it survives; and with `retention-days: 0` **nothing is
      purged on age while forget-me still deletes everything** (**SC-013**) —
      driving the clock rather than the calendar, in
      `agent/src/test/java/com/celestia/agent/persistence/TurnRecordRetentionIT.java`
      (depends on T006a, T006b)
      → **Written and compiling; not executed here — needs Docker (ADR-0008).** Named `TurnRecordRetentionIT`, not `…Test`: a `Test` that silently requires Docker breaks the default build on a machine without it.
- [X] T009 `EntitlementService` + `EntitlementDecision` (`OWNER_ALLOW_LIST`,
      `DENIED_NOT_LISTED`, `DENIED_UNAVAILABLE`) in
      `agent/src/main/java/com/celestia/agent/entitlement/EntitlementService.java`.
      **Fails closed**: an unreadable or empty allow-list denies everyone
      (FR-003), and an entry that does not parse as an E.164 number makes the
      list **unusable** rather than merely shorter — the decision is
      `DENIED_UNAVAILABLE`, not `DENIED_NOT_LISTED`, because a typo in the
      owner's own number is a broken config and not a stranger at the door
      (data-model "Validation at load"). `DENIED_UNAVAILABLE` exists so a configuration fault is
      distinguishable from a stranger in the log while they get the same message
      — the same reasoning that separated `OFF_TOPIC` from `UNMATCHED`
      (depends on T003)
- [X] T010 [P] `AgentMessages`: the **four** fixed texts this feature adds —
      not open to you yet (FR-001); could not complete the reading (FR-010);
      busy, try again shortly (FR-010a); and **what is missing from your birth
      details** (US5 scenario 2), which T031 sends when no birth record exists
      and which an earlier draft of this task omitted — as constants in
      `agent/src/main/java/com/celestia/agent/AgentMessages.java`, with a Javadoc
      note that the **refusal redirect, safe replacement and crisis line belong
      to SPEC-011's `FixedMessages`** and must not be duplicated here

**Checkpoint**: a model client exists, turns can be recorded, references can be
swapped, and the reply path can tell who may be answered.

---

## Phase 3: User Story 1 - The owner gets a real answer (Priority: P1) 🎯 MVP

**Goal**: the owner asks a question the toolkit covers and receives a KP answer
in an astrologer's voice, grounded in at least one tool call.

**Independent Test**: send each of the ten eval questions from the owner's number
and read the replies; every one must be answerable from the toolkit and must read
as astrology rather than as a system report (spec.md US1).

### Implementation for User Story 1

- [X] T011 [P] [US1] `EngineTools`: `@Tool` methods over `core` for the natal
      chart, running dasha, significators, ruling planets, horary cast, and a
      chart cast from birth data supplied **this turn**, in
      `agent/src/main/java/com/celestia/agent/llm/tools/EngineTools.java`.
      **No method accepts a `userId`, a coordinate or a time** — all three are
      injected from the authenticated sender, so a model that tries to pass
      someone else's identity has nowhere to put it (FR-006, Constitution V)
- [X] T012 [US1] `ToolkitTools`: `@Tool` methods over SPEC-010's five toolkit
      tools, **each result passed through `SourceReferences` before
      serialisation**, in
      `agent/src/main/java/com/celestia/agent/llm/tools/ToolkitTools.java`.
      **`lookupToolkit` needs the most care of the five**: its SPEC-010 shape
      returns sheet `{id, title}`, which is exactly what must not reach the
      model, so this adapter returns the group, the kind and the matching rows
      with the identity behind a reference. One careless tool defeats the swap in a
      single call (contracts/llm-tools.md) (depends on T007)
- [X] T012a [US1] `ToolSetClosedTest`: the registered tool names equal an
      expected list exactly, so an accidentally-added `@Tool` fails the build
      rather than quietly widening what the model can reach (FR-007), in
      `agent/src/test/java/com/celestia/agent/llm/tools/ToolSetClosedTest.java`
      (depends on T011, T012)
- [X] T013 [US1] `system-prompt.md` — all nine obligations of
      contracts/system-prompt.md, each traceable to its requirement — in
      `agent/src/main/resources/com/celestia/agent/llm/system-prompt.md`.
      A classpath resource **versioned with the code**: a prompt editable in
      production is a guardrail editable in production (research.md §5).
      Obligation 5 writes `<<OUT_OF_SCOPE>>` as a **literal**, because
      `OutputStage.OUT_OF_SCOPE_MARKER` is package-private and unreachable from
      `agent`: the prompt and the guardrail agree by convention, not by
      compilation, and if SPEC-011 ever changes the marker every out-of-scope
      turn ships the raw text to the owner. `T026` pins the literal so the drift
      is at least a build failure here
- [X] T014 [P] [US1] `SystemPrompt`: load the resource once at startup and fail
      fast if it is missing or empty, in
      `agent/src/main/java/com/celestia/agent/llm/SystemPrompt.java`
- [X] T015 [US1] `OwnerAnswerer implements Answerer`: build the request from
      `TurnContext` and the prompt, run the tool loop against the `ChatClient`,
      and return `Answer(draft, sensitivityUsed)` where **`sensitivityUsed` is
      the maximum over the tool results actually used** — computed by the
      application, never read from the draft and never decided by the model
      (SPEC-011 FR-014). Enforce the **round cap and the deadline separately**,
      because a round-cap fault is a prompt defect and a timeout is a hosting one
      (research.md §6); switch to the fallback model on malformed tool JSON
      (ADR-0009); never throw for model trouble (FR-010). Fill `AnswerContext`
      with the owner's **full E.164 number** as `recipientRef` — not a hash or a
      token, whatever its javadoc suggests, because the leak scan compares its
      digits against the draft's (contracts/answerer-api.md) — in
      `agent/src/main/java/com/celestia/agent/llm/OwnerAnswerer.java`
      (depends on T005, T011, T012, T014)
- [X] T016 [US1] `GuardedReplyHandler implements ReplyHandler`, in the exact
      order of data-model.md "Order of operations": entitlement → non-text →
      **acquire the turn permit** → pre-fetch → `GuardedPipeline.gate` (for the
      record's `gateOutcome`/`gateReason`, which `handle` does not return) →
      `GuardedPipeline.handle` → outbound → turn record →
      **release the permit in a `finally`** (one fault
      otherwise locks the product permanently). The permit is taken **after
      entitlement so a sender who may not be answered cannot consume it, and
      before the gate so no chart or gate work is done for a message about to be
      turned away** (**FR-010b**). A message turned away for want of a permit
      gets "busy, try again shortly" and **no turn record** — no turn began
      (**FR-010c**) — but is counted: register **three `MeterRegistry` counters**
      (`celestia.agent.turned_away{reason=no_entitlement|not_text|no_permit}`)
      for exactly the three populations FR-020 says are "counted rather than
      recorded", using the `spring-boot-starter-actuator` already on the module.
      Three counters is the floor FR-010c requires, not an observability feature
      — the rest of that is SPEC-022 (research.md §10). Everything from the permit on is recorded
      whatever happened, faults included, in
      `agent/src/main/java/com/celestia/agent/channel/GuardedReplyHandler.java`.
      The order is the requirement, not an implementation detail (FR-001,
      data-model "Order of operations"). Wrap the answerer at the `Answerer`
      boundary before passing it to `handle`, so the handler can see whether it
      ran and which tools it called without reaching inside the pipeline — the
      same boundary T023's counting decorator uses for its own assertion
      (depends on T006, T009, T015)
- [X] T016a [US1] **Clamp the reply to the budget** in `GuardedReplyHandler`,
      after the output stage and before outbound, in
      `agent/src/main/java/com/celestia/agent/channel/GuardedReplyHandler.java`.
      Nothing enforces FR-017 today: SPEC-011's 4,096 is an **inbound** bound
      (`Gate.MAX_MESSAGE_LENGTH`, its FR-012), the output stage only ever
      *appends* — support line, then `sensitivityUsed.note()` — so a draft at
      4,090 characters becomes an over-budget reply, and `deliveredLength`
      merely observes it afterwards. SC-009 states a guarantee (**no** reply
      exceeds 4,096), so the guarantee needs a component. Trim at the **last
      sentence boundary that fits**, never mid-sentence (FR-017), and record the
      trim as a prompt defect on the turn — the same standing as a withheld
      reply, because the owner lost part of an answer. **Never trim the support
      line or the advisory**: if the decorated reply cannot fit with them, the
      body is what gives way, since Constitution V makes both obligatory.
      **4,096 is a literal here**, for the same reason the prompt's marker is:
      `Gate.MAX_MESSAGE_LENGTH` is package-private and unreachable from `agent`
      (research.md §12) (depends on T016)
- [X] T017 [US1] **Delete** `agent/src/main/java/com/celestia/agent/channel/EchoReplyHandler.java`
      and its test, rather than keeping it behind a flag. A second reply path in
      the code is the thing this whole feature exists to prevent (research.md §9)
- [X] T018 [US1] Replace the single-path ArchUnit rule in
      `agent/src/test/java/com/celestia/agent/architecture/DeterminismArchitectureTest.java`
      with **two** rules, because the existing one forbids any *dependency* on
      `org.springframework.ai..` and so cannot admit `LlmConfig` (which must
      construct the `ChatClient`) or the `@Tool`-annotated adapters (an
      annotation is a dependency in ArchUnit's model):
      **(a) `onlyTwoClassesMayInvokeAChatModel`** — no class may *call*
      `org.springframework.ai.chat.model.ChatModel.call(`,
      `…ChatModel.stream(` or `…chat.client.ChatClient.prompt(` except
      `com.celestia.guardrail.GuardedPipeline` and
      `com.celestia.agent.llm.OwnerAnswerer`. Use the `callCodeUnitWhere`
      predicate idiom this file already uses for `AMBIENT_CLOCK`, and **verify
      the resolved target names once against a real build** — a call through a
      subtype resolves to that subtype, not the interface.
      **(b) `springAiStaysInTheLlmPackage`** — no class outside
      `com.celestia.agent.llm..` and `GuardedPipeline` may depend on any package
      in `CHAT_MODEL_PACKAGES`, so no controller, worker or handler can hold a
      chat-model type.
      **Do not widen (b) to `com.celestia.agent..`** and do not collapse the two
      back into one: (a) is the single path, (b) is containment, and one
      predicate cannot say both (research.md §2)
- [X] T019 [US1] `OwnerAnswererTest` with a **stub `ChatModel`**: a tool loop
      that converges; the round cap ending the turn as `ROUND_CAP`; the deadline
      ending it as `TIMEOUT`; malformed tool JSON switching to the fallback;
      `sensitivityUsed` being the maximum over results used and not inferred from
      the draft; and model trouble producing a fixed message rather than an
      exception, in
      `agent/src/test/java/com/celestia/agent/llm/OwnerAnswererTest.java`
      (depends on T015)
- [X] T020 [US1] `GuardedReplyHandlerTest`: the order holds — a non-text message
      never reaches the gate **and takes no permit**, so a "text only" reply
      cannot consume the one the owner is waiting for; a second turn while one
      holds the permit gets "busy, try again shortly" with **no turn record** and
      a counter incremented (FR-010a–c); the permit is released even when the
      turn faults; an allowed question reaches the answerer exactly
      once; a `TurnRecord` is written for an answer, a refusal, a withheld reply
      and a fault; and a decorated reply that would exceed 4,096 characters is
      trimmed at a sentence boundary with the support line and advisory intact,
      the trim recorded as a defect — driven by a stub draft deliberately set a
      few characters under the bound, so the advisory is what pushes it over
      (FR-017, SC-009, T016a), in
      `agent/src/test/java/com/celestia/agent/channel/GuardedReplyHandlerTest.java`
      (depends on T016)

**Checkpoint**: the owner can be answered. US1 is independently demonstrable
with a stub model, and with a real one once T013's prompt is good.

---

## Phase 4: User Story 2 - A stranger gets nothing (Priority: P1)

**Goal**: a sender who is not the owner receives one fixed message, and no
chart, no gate decision and no model invocation happens on their behalf.

**Independent Test**: send messages from a number that is not on the allow-list
and confirm a fixed reply, zero model invocations, and no chart computation
(spec.md US2).

### Implementation for User Story 2

- [X] T021 [US2] `EntitlementServiceTest`: a listed number is allowed; an
      unlisted one is denied; **an empty or unreadable allow-list denies
      everyone** — the case worth re-reading, because an empty list meaning
      "allow all" is how a configuration slip becomes an open service (FR-003);
      **a list holding an unparseable number denies everyone as
      `DENIED_UNAVAILABLE`**, including the owner, rather than quietly dropping
      the bad entry; and `DENIED_UNAVAILABLE` is distinguishable from
      `DENIED_NOT_LISTED` in the decision while identical to the sender, in
      `agent/src/test/java/com/celestia/agent/entitlement/EntitlementServiceTest.java`
      (depends on T009)
- [X] T022 [US2] Extend `GuardedReplyHandlerTest` with the US2 scenarios: an
      unentitled sender gets the fixed not-open message, and **the gate is never
      consulted, no chart is computed and the answerer is never called** — work
      done for someone not entitled to it is work that should not have been done;
      an unentitled sender's *hostile* message produces the same not-open message,
      because entitlement runs first and the gate never sees it (SC-005), in
      `agent/src/test/java/com/celestia/agent/channel/GuardedReplyHandlerTest.java`
      (depends on T020)
- [X] T023 [US2] `GuardrailRegressionTest`: replay **SPEC-011's own**
      `corpus-adversarial.tsv` through the real reply path — the actual
      `OwnerAnswerer` bean in place, wrapped in a counting decorator at the
      `Answerer` boundary — asserting **zero invocations across the whole file**
      (SC-006), in
      `agent/src/test/java/com/celestia/agent/channel/GuardrailRegressionTest.java`.
      Reuse the corpus file; do not copy it. SPEC-011 proved this against a
      *fake* answerer, which says nothing about whether wiring a real one
      introduced a bypass — and this feature adds two things in front of the gate
      and one behind it. **No model is needed**: the assertion is that nothing was
      called, so it belongs in the default build (research.md §11)
      (depends on T016)

**Checkpoint**: both P1 stories hold. The owner is answered, nobody else is, and
the property SPEC-011 exists to deliver is proven against the real wiring.
**MVP complete.**

---

## Phase 5: User Story 3 - The answer never names where it came from (Priority: P2)

**Goal**: no delivered reply names the toolkit, a sheet id or title, or a row
number — and none is withheld by the leak scan either, because a withheld reply
is a prompt failure.

**Independent Test**: run the eval set and the adversarial corpus and confirm no
delivered reply names a source and nothing was withheld (spec.md US3).

### Implementation for User Story 3

- [X] T024 [US3] `SourceReferencesTest`: the same source twice in a turn returns
      the same reference; different sources get different references; **a reference cannot be
      reversed to its source** (no substring of any sheet id, title or row
      appears in a reference); and references restart per turn so no durable map of the
      toolkit's structure accumulates, in
      `agent/src/test/java/com/celestia/agent/llm/SourceReferencesTest.java`
      (depends on T007)
- [X] T025 [US3] `ModelContextLeakTest`: assert on **what the model was given**,
      not on what it replied — serialise the full request for a turn that used
      every toolkit tool and confirm it contains no sheet id, no sheet title and
      no row number (SC-010). This is the mechanical half of Constitution X and
      the reason the reference swap exists. **Also assert statelessness on the
      same evidence** (**FR-018**): run two turns for the same user and confirm
      the second request contains no substring of the first message. A seventh
      story, dropped when Q2 was revised to stateless, had asserted that — and it
      went with the story, leaving the decision's central claim untested, in
      `agent/src/test/java/com/celestia/agent/llm/ModelContextLeakTest.java`
      (depends on T012, T015)
- [X] T026 [US3] `SystemPromptTest`: every one of the nine obligations of
      contracts/system-prompt.md is present in the resource — including the
      prohibition on naming **references** as well as sources, which is a style rule
      rather than a secrecy one ("per s3" discloses nothing but reads as a system
      quoting a document), in
      `agent/src/test/java/com/celestia/agent/llm/SystemPromptTest.java`.
      A prompt missing its leak prohibition is a guardrail missing a wall, and
      this test is what makes that a build failure rather than a discovery
      (depends on T013)
- [X] T027 [US3] Extend `GuardedReplyHandlerTest`: a draft that names a sheet is
      **withheld** by SPEC-011's output stage and recorded as `WITHHELD` on the
      turn — correct behaviour and a prompt defect, counted so it cannot become
      normal operation (SC-004) (depends on T020)

**Checkpoint**: the toolkit's identity is held by two independent controls — out
of the model's context, and scanned on the way out.

---

## Phase 6: User Story 4 - The agent says when it has no basis (Priority: P2)

**Goal**: a question the toolkit does not cover is declined in the agent's own
voice, and a question it does cover is not.

**Independent Test**: ask questions chosen because the toolkit has no rule for
them and confirm the agent declines rather than improvising (spec.md US4).

### Implementation for User Story 4

- [X] T028 [US4] `NoBasisTest` with a stub model: a draft carrying
      `<<OUT_OF_SCOPE>>` is replaced in full by SPEC-011's fixed redirect
      (FR-016 of SPEC-011); a draft that declines in prose is delivered as
      written; and **a reply asserting astrological content with zero
      `ToolkitTools` calls behind it is a defect** — engine calls alone do not
      ground an interpretation — that the turn record makes visible (SC-002), in
      `agent/src/test/java/com/celestia/agent/llm/NoBasisTest.java`
      (depends on T015, T016)
- [X] T028a [US4] `AsksRatherThanGuessesTest`, **both directions**: a message
      that cannot be understood on its own — "what about her?", "and before
      2027?" — produces a reply that **asks what is meant** (FR-018b, SC-012);
      and a message that names its own matter — "and what about my career?" — is
      **answered without a clarifying question**, because the owner's chart is
      already context (**FR-018a**). The second case is what stops the first from
      being satisfied by a model that asks "what do you mean?" at everything —
      the same trap T029 closes for declines. And no reply is composed against
      context from an earlier turn, since none is kept, in
      `agent/src/test/java/com/celestia/agent/llm/AsksRatherThanGuessesTest.java`.
      SC-012 had no task at all until the first analysis pass, and quickstart
      step 3b claimed `GuardrailRegressionTest` covered it, which it does not
      (depends on T015, T016)
- [X] T029 [US4] Make the two directions derivable: `outcome` distinguishes
      `ANSWERED` from `AGENT_NO_BASIS`, `AGENT_REDIRECT` and `GATE_REFUSED`
      (data-model.md "TurnRecord"), and the eval harness pairs it against each
      question's `expectation` to count a false decline separately from a true
      one (SC-007) — a product that declines everything would pass an "invents
      nothing" test perfectly. Assert the four outcomes in `NoBasisTest` and the
      pairing in T035 (depends on T006, T028)

**Checkpoint**: the agent's silence is as reviewable as its answers.

---

## Phase 7: User Story 5 - The chart is ready before the model runs (Priority: P2)

**Goal**: the owner's chart and running dasha are computed before invocation and
handed to the model as context, and a missing birth record is reported rather
than worked around.

**Independent Test**: confirm a turn begins with the chart and running dasha in
hand, and that the model's tool calls are about judgement rather than
recomputing them (spec.md US5).

### Implementation for User Story 5

- [X] T030 [P] [US5] `SeedOwnerBirthDataCommand`: an administrative action that
      writes the owner's birth data through the existing `BirthDataStore`,
      without a conversational flow (FR-019), in
      `agent/src/main/java/com/celestia/agent/admin/SeedOwnerBirthDataCommand.java`
- [X] T031 [US5] Wire the pre-fetch into `GuardedReplyHandler`: build
      `TurnContext` from `ChartCacheService.natalChart(userId)` and the running
      dasha **before** `GuardedPipeline.handle`, and when no birth record exists,
      reply with **T010's "what is missing" constant** rather than an answer
      built on a default or assumed chart, and rather than prose invented in the
      handler (spec.md US5 scenario 2) (depends on T008, T010, T016, T030)
- [X] T032 [US5] `TurnContextTest` and an extension to `GuardedReplyHandlerTest`:
      the chart and dasha are present before the answerer is called; a missing
      birth record produces the "what is missing" reply and **no model
      invocation**; and a second person's supplied chart is present for the turn
      and **absent from anything persisted** (FR-011), in
      `agent/src/test/java/com/celestia/agent/llm/TurnContextTest.java`
      (depends on T031)

**Checkpoint**: the model is never asked to work out what the engine already knows.

---

## Phase 8: User Story 6 - A practitioner can judge the answers (Priority: P3)

**Goal**: ten of the owner's questions produce ten reviewable documents, each
with the resolved toolkit sources behind the answer.

**Independent Test**: produce the ten answers with their sources in a reviewable
form and have them marked (spec.md US6).

### Implementation for User Story 6

- [X] T033 [P] [US6] `owner-questions.tsv` — `id`, `question`, `expectation`
      (`ANSWERABLE` | `NO_BASIS`), `note` — in
      `agent/src/test/resources/com/celestia/agent/eval/owner-questions.tsv`.
      **Include `NO_BASIS` cases**: SC-007 counts both directions, and a set of
      only answerable questions cannot catch a model that declines too readily
- [X] T034 [US6] `OwnerEvalHarness`: run each question through the real reply
      path and write one document per question to `agent/target/owner-eval/` —
      the question, the delivered reply, the tool calls, and the **resolved**
      sources behind each reference, in
      `agent/src/test/java/com/celestia/agent/eval/OwnerEvalHarness.java`.
      Tagged so it is **excluded from the default build** (it needs a reachable
      model), like the Testcontainers tests. **It must fail loudly on its own
      preconditions**: the owner allow-listed (or every question returns "not
      open to you yet") and his birth data seeded (or every one returns what is
      missing). Either way ten documents appear and the practitioner reviews no
      astrology, so check both up front and refuse to run rather than writing
      useless output (depends on T009, T016, T030, T033)
      → **Written and compiling; not executed here — needs a reachable model and Docker.** Named `OwnerEvalHarness` (no `Test`/`IT` suffix) so neither surefire nor failsafe picks it up; run it with `-Dtest=OwnerEvalHarness`.
- [X] T035 [US6] The harness's mechanical assertions — every reply asserting KP
      judgement has at least one **`ToolkitTools`** call behind it, not merely
      any tool call (SC-002: an answer resting only on `EngineTools` — "your
      running bhukti is Saturn–Mercury" — is ungrounded interpretation under
      Constitution X, and engine results carry no source reference, which is
      also what would make SC-011 vacuous); no delivered reply names the toolkit, a
      sheet or a row (SC-003); nothing was withheld (SC-004); every reference
      resolves to a real source (SC-011); **no tool result the model received
      carried a sheet id, title or row across the whole run** (SC-010 — T025
      proves this on a stub, and SC-010 is written "across the whole evaluation
      run", so it is asserted here too); and answerable questions were answered
      while no-basis questions were declined, each paired against the question's
      `expectation` so the two directions are counted separately (SC-007) — in
      `agent/src/test/java/com/celestia/agent/eval/OwnerEvalHarness.java`.
      **What it cannot assert is whether the astrology is good**, which is why
      the output is a document and SC-001's bar is a human verdict
      (depends on T034)

**Checkpoint**: all six stories are independently demonstrable, and the one
thing code cannot judge is sitting in a folder waiting for a person.

---

## Phase 9: Polish & Cross-Cutting Concerns
      → **Written and compiling; not executed here — runs with T034.**
- [X] T036 [P] Add `agent/src/main/java/com/celestia/agent/llm/package-info.java`
      explaining the single-path obligation, the reference swap, and that the
      prompt is versioned with the code — so the next reader of this package
      learns the three non-obvious rules before changing anything
- [X] T037 [P] Extend `agent/README.md` (or add one) with the reply path as it
      now stands, the `celestia.llm.*`, `celestia.entitlement.*` and
      `celestia.turn-record.*` keys, the three `celestia.agent.turned_away`
      counter names an operator needs to see the busy path (T016), and how
      to point `base-url` at a local model for development
- [X] T038 Run quickstart.md steps 1, 1b, 2, 3, 3b and 5's prerequisites —
      `./mvnw -pl agent -am test` plus the ArchUnit and regression suites — and
      confirm green. `agent`'s Testcontainers tests need Docker (ADR-0008); the
      eval harness needs a model and is expected to be skipped here
      → **Run: 108 tests pass.** The one failure is `CelestiaApplicationTests`, which needs Docker and already did; the Testcontainers ITs and the eval harness were skipped as expected. `spotless:check` is clean.
- [X] T039 **Try to break both single-path rules** as quickstart.md step 3
      instructs. Rule (a): add a `ChatClient.prompt(...)` call to any class other
      than `OwnerAnswerer` — `LlmConfig` is the honest test, since it
      legitimately holds the client — and confirm the build fails. Rule (b): add
      a field of a Spring AI chat type to a class in `agent.channel` and confirm
      the build fails. Revert both. A rule that cannot be shown to fail is not
      known to work, and these two fail for different reasons
- [ ] T040 Measure SC-008 **on the real VM with the model warm** — not on a
      laptop — and record the figure. If 30 seconds does not hold, the honest
      move is to amend SC-008 with the measured number rather than to leave a
      criterion the product misses. Record the figure in
      `specs/012-owner-preview-agent/spec.md` (SC-008) so the claim and the
      measurement live together
- [ ] T041 **MANUAL — owner only**: run the eval harness **against the
      production VM and Llama 4 Scout — not the dev machine's Qwen** (ADR-0009:
      a 0.5B model on a 2019 Intel CPU proves the round trip and nothing about
      answer quality, and ten documents from it would waste a practitioner's time
      and discredit the product) and read the ten documents in
      `agent/target/owner-eval/`, marking each answer acceptable or not **as
      astrology** (SC-001, bar is 9 of 10). Watch
      for the two failure modes quickstart.md names: a confident answer resting
      on a thin basis, and a decline that should have been an answer. No test can
      do this, and the verdict is the precedent for every Stage B topic group
- [X] T042 **DECISION — owner only**: amend the constitution's "Ollama,
      self-hosted" wording, which ADR-0010 option 3 strained and left
      outstanding. **Done 2026-11-24: constitution v2.1.0 → v2.2.0** defines the
      term as *we run the weights*, permits a rented host we control, and
      attaches three obligations to one — encrypted transport, a region chosen
      for the data, no prompt logging. Propagated to `PROJECT_PLAN.md` (overview,
      D9, technology summary) and `README.md`; ADR-0010 updated to record that
      its outstanding item is closed

- [ ] T043 **Verify the dev model can tool-call before relying on it**: run
      `ollama show qwen2.5:0.5b` and look for `tools` among the capabilities. If
      it is absent, find the smallest model that has it — **it may not be a
      Qwen**, which is why plan.md names a capability rather than a family —
      change `model` in
      `agent/src/main/resources/application.yml`, and record the outcome in
      `docs/adr/0009-llm-runtime-and-model.md` (open item 1). Cheap, and worth
      doing before the first tier-2 smoke test rather than after an hour spent
      debugging the wrong layer: a model with **no tool template** fails inside
      Ollama and tells you nothing about this code, while one that is merely too
      small emits malformed arguments, which is the fallback path working
- [ ] T044 **OPEN ITEM — get a production-level host before the prompt and the
      tool surface are frozen.** Developing only against a 0.5B CPU model pulls
      two ways at once: you start adding workarounds for *its* failures that
      Llama 4 Scout does not need and may be hurt by, and you never meet the
      problems only a capable model creates — long tool chains, richer answers
      hitting the 4,096-character reply budget, leak phrasings a small model is
      not fluent enough to generate. Both surface late and expensively. Exercise
      `system-prompt.md` (T013) and the tool adapters (T011, T012) against Scout
      on the VM **before** T041 asks a practitioner to judge them, and record the
      date it happened in `docs/adr/0009-llm-runtime-and-model.md` (open item 2)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies — start immediately
- **Foundational (Phase 2)**: depends on Setup — BLOCKS every user story.
  **T006a/b/c (the delete, the purge, their test) are sequential on T006**, and
  they are in Foundational rather than a later phase deliberately: there must be
  no window in which the table accumulates records nothing can delete
- **User Stories (Phases 3–8)**:
  - **US1 and US2 are not independent in code**, though they are in testing.
    `GuardedReplyHandler` (T016) cannot be written without `EntitlementService`,
    so entitlement is Foundational (T009) and US2's phase holds its behaviour
    tests. US1 lands the path; US2 proves nobody else walks it.
  - **US5 is a prerequisite of US1's full behaviour**, not a successor. T031
    wires the pre-fetch into the same handler T016 creates. They are separated
    because the pre-fetch is independently testable, not because it can be
    deferred — an answer built without a chart is not US1 delivered.
  - **US3 owns no production code of its own.** The reference swap is
    Foundational (T007) and the prompt is US1 (T013), because US1 cannot produce
    an answer without them. US3 is the story that *proves* the property, which is
    why it is all tests.
  - **US4 and US6 depend on US1 and US2** being complete, since both exercise the
    whole path.
  - **T018 (the ArchUnit amendment) must land with T015**, not after. Between
    writing `OwnerAnswerer` and amending the rule, the build is red — which is
    correct, and is the rule working.
- **Polish (Phase 9)**: T036–T039 after the stories; T040 needs the VM; T041 and
  T042 are the owner's

### Within Each User Story

- Configuration before the beans that read it
- The reference swap before the tools that use it
- Tools and prompt before the answerer
- The answerer before the handler
- The handler before anything that exercises the whole path

### Parallel Opportunities

- Setup: T002 and T003 are independent files; T004 follows both
- Foundational: T006, T007, T008 and T010 are independent of each other; T005 and
  T009 each depend only on their properties. **T006a and T006b both follow T006**
  and can be written in parallel with each other; T006c follows both
- US1: T012a (the closed tool set) follows T011 and T012 and is independent of
  everything else in the phase
- US4: T028a is a separate test class from T028 and can be written alongside it
- US1: T011 and T014 are independent; T013 (the prompt) can be drafted alongside
  the code that will use it
- US5: T030 is independent of T031's wiring
- US6: T033 (the question file) is independent of T034's harness
- **Not parallel**: anything touching `GuardedReplyHandler.java` (T016, T016a,
  T022, T027, T031, T032), `system-prompt.md` (T013, and T026 reads it), or
  the turn record (T006, T006a, T006b, T029 — T029 fixes `outcome`'s values, so
  T006 must not ship a narrower enum and migrate it later)

---

## Implementation Strategy

### MVP (User Stories 1 and 2 — both P1)

1. Phase 1 and Phase 2 — blocking, and Phase 2 is most of the machinery.
2. Phase 3 (US1) — the owner can be answered, with a stub model proving the
   wiring and a real one proving the prompt.
3. Phase 4 (US2) — nobody else can be, and SC-006 proves the guardrail did not
   regress when a real model appeared.
4. **STOP and VALIDATE**: quickstart steps 1–3b green. At this point Stage A's
   exit criterion is reachable and the first real answer is a configuration away.

### Incremental Delivery

1. Setup + Foundational → a client, a turn record, a reference swap, an
   entitlement check.
2. US1 → the owner gets an answer.
3. US2 → only the owner does. **MVP.**
4. US3 → the toolkit's identity is held by two controls, both tested.
5. US4 → the agent's declines are as reviewable as its answers.
6. US5 → the model is never asked to compute what the engine knows.
7. US6 → a practitioner can judge the result.
8. Polish → docs, the deliberate attempt to break the ArchUnit rule, the real
   latency measurement, and the two owner-only items.

### Parallel Team Strategy

The file overlaps make this a poor feature to split finely — `GuardedReplyHandler`
is touched by five tasks across four stories. With two developers after
Foundational:

- Developer A: US1 then US5 (both own the handler and the answerer)
- Developer B: US2 then US3 then US4 (tests and the prompt, with T013 handed over
  once A has the answerer running)
- Either picks up US6 once US1 and US2 have landed

---

## Notes

- [P] tasks touch different files with no unmet dependency
- [Story] references trace every task back to its spec.md user story
- **`guardrail/` and `core/` are not modified by any task here.** If a task seems
  to need it, the design has drifted — raise it rather than editing them
- Tests split in two: the stub-model tests belong in the default build and prove
  the wiring; the eval harness needs a reachable model and proves the prompt.
  Only the second can be wrong in a way a test cannot see, which is why T041
  exists
- Commit after each task or logical group; stop at any checkpoint to validate a
  story independently
- T041 and T042 are the only tasks blocked on a human. Everything else is
  buildable today — T034's harness needs a model, but a small local one serves,
  which is ADR-0010's own development story
