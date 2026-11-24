# Implementation Plan: Owner-Preview Agent

**Branch**: `012-owner-preview-agent` | **Date**: 2026-11-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/012-owner-preview-agent/spec.md`

## Summary

Wire the model in, between two halves of a path that already exist.

- **Entitlement first.** A small check in `agent` answers "may this sender be
  answered?" from an allow-list, before the gate and before any chart work. An
  unknown sender costs nothing and reaches nothing (FR-001, research §8).
- **The answerer.** `OwnerAnswerer` implements SPEC-011's `Answerer` over Spring
  AI's `OllamaChatModel`, and `GuardedPipeline` is the only thing that calls it.
  The single-path ArchUnit rule is amended to name exactly one more class rather
  than loosened to a package — the tightest form that stays true once a model
  exists (research §2).
- **The model sees no toolkit strings.** Tool results pass through the
  application, which swaps each `{sheetId, sheetTitle, row}` for a per-turn
  reference (`s1`, `s2`) before serialisation. The mapping lives in the turn
  record. Constitution X stops resting on the prompt and starts resting on the
  wiring (FR-013a–d, research §3).
- **Stateless turns.** No `ChatMemory`, no carried subject, prompt rebuilt per
  turn — which is also what makes a turn reproducible for the eval harness
  (FR-018, research §4).
- **One prompt, versioned with the code.** `system-prompt.md` on the classpath,
  not a property and not a database row: it is the only thing holding several
  NON-NEGOTIABLE obligations, so it belongs under the same review as code
  (research §5).
- **The eval harness is a document, not an assertion.** Ten questions produce ten
  reviewable files with their resolved sources. What can be checked mechanically
  is; whether the astrology is good is a practitioner's verdict (research §7).

## Technical Context

**Language/Version**: Java 25 (ADR-0007). No new language or build tooling.

**Primary Dependencies**: `spring-ai-starter-model-ollama` (Spring AI 2.0.1,
already in the parent pom's `dependencyManagement`; `agent/pom.xml` anticipates
it in a comment). **`agent` already depends on `core`, `guardrail` and
`channel-whatsapp`** — verified, so the `Answerer` seam is reachable today and
the only new dependency is the Spring AI starter.

**Storage**: MySQL via the existing stores — `BirthDataStore` for the owner's
seeded birth data, `ChartCacheService.natalChart(userId)` for the pre-fetched
chart. One new table, `owner_turn_record` (Flyway `V6__`), holding the decision,
the tool calls and the resolved sources, reached through a new `TurnRecordStore`
in `agent.persistence.store` with its entity in `agent.persistence.entity` —
the layering SPEC-008 established and `PersistenceArchitectureTest` guards.

**It holds a message body, so it is deleted.** The question is personal data
under Constitution VII: `owner_turn_record` joins the rows SPEC-008 FR-010's
"forget me" removes (FR-020a), and records are purged past a configurable
retention age, default 90 days, where `0` means never purge on age without ever
disabling the deletion obligation (FR-020b). The reference→source mapping is
per-turn and dies with the turn.

**Testing**: JUnit 5 + AssertJ + Mockito, as `agent` already uses. The guarded
path is tested with a stub `ChatModel` so the wiring, the reference swap and the
budgets are covered without a model. The eval harness needs a real reachable
model and is excluded from the default build, like the Testcontainers tests.

**Target Platform**: JVM. The model runs on a warm rented cloud GPU VM
(ADR-0010, option 3) reached over `celestia.llm.base-url`.

**Project Type**: Spring Boot application code in the existing `agent` module.

**Performance Goals**: a reply within 30 seconds for 9 of 10 typical questions,
end to end (SC-008). The budget is now meaningful because ADR-0010 settled that
the VM stays warm and `OLLAMA_KEEP_ALIVE` holds the model resident — a cold load
of tens of seconds would have consumed the whole budget on the first question of
a session. Chart and dasha are pre-fetched before the model is invoked, so the
model's own latency is the only variable.

**Constraints**:
- Constitution II: `guardrail` and `core` stay pure. Everything here is in
  `agent`, which is where Spring belongs.
- Constitution IV: the model never calculates. Every number comes from a tool
  result; tool arguments never carry `userId`, coordinates or a clock.
- Constitution V (NON-NEGOTIABLE): one route. `GuardedPipeline` calls the
  answerer; the ArchUnit rule names the only two classes that may touch a
  chat-model type.
- Constitution VII: personal data now crosses the public internet to the
  inference host (ADR-0010). Transport encryption to that host is required; its
  provider and region are an open sub-decision recorded in ADR-0010, due before
  real traffic rather than before this preview.
- Constitution X (NON-NEGOTIABLE): no reply names the toolkit, a sheet or a row.
  Two controls: the reference swap keeps the strings out of the model's context,
  and SPEC-011's leak scan still runs on the draft.
- ADR-0009: the fallback model is not optional, because this feature is almost
  entirely tool calling and Llama 4's tool-calling under Ollama is immature.

**Scale/Scope**: one user. One permit of concurrency (ADR-0010), taken after
entitlement and before the gate; one prompt; two tool adapters exposing the five
toolkit tools plus the engine tools; one new table reached through an
entity/repository/store trio, with a "forget me" delete and a scheduled purge on
a 90-day default; ten eval questions; one amended ArchUnit rule; one deleted
class (`EchoReplyHandler`). Eleven new test classes, of which one needs a real
model and the rest run on a stub.

## Constitution Check

*GATE: checked before Phase 0 and re-checked after Phase 1 (v2.1.0).*

| Principle | How this plan complies | Status |
|-----------|------------------------|--------|
| I Spec-driven | spec → clarified (2 questions, both answered) → this plan → tasks. ADR-0009 and ADR-0010 accepted before planning, because the plan cannot name where the model runs without them | ✅ |
| II Deterministic core | Nothing is added to `ephemeris`, `core`, `geo` or `guardrail`. Spring AI lives in `agent` alone, as the Technology Constraints require | ✅ |
| III Correctness gated | The guarded path is covered with a stub model; the eval harness gates the prompt against a real one. A model or prompt change that drops below SC-001's bar is a regression | ✅ |
| IV LLM never calculates | Every number comes from a tool result (FR-008). `userId`, coordinates and clock are injected server-side and never read from tool arguments (FR-006) | ✅ |
| V Guarded single path (NON-NEGOTIABLE) | Entitlement → gate → model → output stage, in that order. `GuardedPipeline` is the only caller of `Answerer`; the ArchUnit rule is amended to name `OwnerAnswerer` explicitly rather than widened to a package (research §2) | ✅ |
| VI Deterministic flows | Not touched — onboarding is SPEC-017. Statelessness here means no flow state exists to keep in the model (FR-018) | n/a |
| VII Data protection | Birth data is seeded, not collected; a supplied second person's data is used for the turn and not persisted (FR-011). The new turn record holds a message body, so it joins the "forget me" delete and carries a 90-day default retention (FR-020a, FR-020b) — the gap the first analysis pass caught. **Remaining exposure**: personal data now reaches a rented inference host, which ADR-0010 records with its open obligations | ⚠️ see note |
| VIII Payments | Not touched. Entitlement is an allow-list, and the interface is what SPEC-018 plugs into | n/a |
| IX Adapters at the edge | `ReplyHandler` is unchanged as an interface; the webhook, worker and sender are untouched. Spring AI is a provider type and stays inside `agent.llm` | ✅ |
| X Toolkit is the judgement (NON-NEGOTIABLE) | Interpretation comes only from tool results (FR-014); the answer obligations of `agent-tools.md` are prompt requirements (FR-015); and the toolkit's strings never enter the model's context (FR-013a) while the leak scan still runs | ✅ |

**Note on VII.** This is the one principle this feature makes materially harder to
satisfy, and the plan does not pretend otherwise. Accepting ADR-0010 option 3
moved inference off the premises, so birth data, names and question text now
travel to a machine in someone else's datacentre. For an owner preview of one
known person that is a defensible exposure; for public traffic it is not, until
the provider, the region, prompt-logging and the processor relationship are
settled. Those are recorded as open in ADR-0010 with a due date of "before real
traffic". **No violation to track here, but it is the reason this feature must
not quietly become the public launch.**

No violations, so no Complexity Tracking entries.

## Project Structure

### Documentation (this feature)

```text
specs/012-owner-preview-agent/
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── answerer-api.md  # what agent implements for guardrail, and the turn budget
│   ├── entitlement-api.md # the seam SPEC-018 replaces, and what it must preserve
│   ├── llm-tools.md     # the tool surface the model sees, including the reference swap
│   └── system-prompt.md # the prompt as a contract: what it must say, and why each line
├── checklists/requirements.md
└── spec.md
```

### Source Code (repository root)

```text
agent/src/main/java/com/celestia/agent/
├── entitlement/
│   ├── EntitlementService.java      # may this sender be answered? allow-list first
│   └── EntitlementProperties.java   # celestia.entitlement.allow-list
├── llm/
│   ├── LlmConfig.java               # ChatClient, fallback, concurrency permit
│   ├── LlmProperties.java           # base-url, model, fallback-model, budgets
│   ├── OwnerAnswerer.java           # implements guardrail Answerer — the ONLY chat-model caller
│   ├── SystemPrompt.java            # loads system-prompt.md once
│   ├── TurnContext.java             # chart + running dasha, pre-fetched
│   ├── SourceReferences.java        # per-turn {sheetId,sheetTitle,row} -> s1, s2 …
│   ├── TurnDetail.java              # the value handed to the store: decision, tool calls, sources
│   └── tools/
│       ├── EngineTools.java         # @Tool over core: chart, lords, dasha, horary
│       └── ToolkitTools.java        # @Tool over the five toolkit tools of SPEC-010
├── channel/
│   └── GuardedReplyHandler.java     # entitlement -> GuardedPipeline -> outbound
├── admin/
│   └── SeedOwnerBirthDataCommand.java  # FR-019
└── eval/
    └── OwnerEvalHarness.java        # ten questions -> reviewable documents

├── persistence/
│   ├── entity/OwnerTurnRecordEntity.java   # the JPA entity, where SPEC-008 says entities live
│   ├── repository/OwnerTurnRecordRepository.java
│   └── store/TurnRecordStore.java          # what llm/ and channel/ talk to; also the purge
agent/src/main/resources/
├── com/celestia/agent/llm/system-prompt.md
└── db/migration/V6__owner_turn_record.sql

agent/src/test/java/com/celestia/agent/
├── entitlement/EntitlementServiceTest.java      # FR-001..FR-003, fail closed
├── llm/OwnerAnswererTest.java                   # stub model: tool loop, budgets, fallback
├── llm/SourceReferencesTest.java                # FR-013a..d, no toolkit string escapes
├── llm/ModelContextLeakTest.java                # SC-010 + FR-018, asserted on what the model received
├── llm/SystemPromptTest.java                    # every obligation present
├── llm/NoBasisTest.java                         # FR-014, the out-of-scope path
├── llm/AsksRatherThanGuessesTest.java           # FR-018b, SC-012
├── llm/TurnContextTest.java                     # FR-009, FR-011
├── llm/tools/ToolSetClosedTest.java             # FR-007, the tool set is closed
├── persistence/store/TurnRecordRetentionTest.java # FR-020a/b, SC-013
├── channel/GuardedReplyHandlerTest.java         # order: entitlement, gate, model, output
├── channel/GuardrailRegressionTest.java        # SC-006: SPEC-011's corpus through the REAL path, zero invocations
└── architecture/DeterminismArchitectureTest.java # the amended single-path rule

agent/src/test/resources/com/celestia/agent/eval/owner-questions.tsv

DELETED: agent/src/main/java/com/celestia/agent/channel/EchoReplyHandler.java
```

**Structure Decision**: three new packages in `agent` — `entitlement`, `llm` and
`eval` — plus one new class in the existing `channel` package and one deleted.
Nothing outside `agent` changes except the ArchUnit rule's allow-list, which is
an `agent` test. `guardrail` and `core` are consumed, not modified: the whole
point of SPEC-011 was that this feature should be able to plug in without
touching the gate.

`agent.llm.tools` is a separate sub-package because the tool adapters are the
one place where Constitution IV is at risk — they are what the model can reach,
and keeping them together makes "what may the model call?" a question you answer
by listing a directory.

## Phase 0 — Research

All unknowns resolved in [research.md](research.md): where the code lives and
what may touch Spring (§1), how the single-path ArchUnit rule survives a real
chat model without being loosened to a package (§2), the per-turn source
reference and why it must not be reversible (§3), stateless turns and what the
prompt must do instead (§4), the prompt as a versioned classpath resource and
the obligations it carries (§5), tool exposure with a round cap as well as a
deadline (§6), what the eval harness can and cannot assert (§7), the entitlement
check placed before the gate and failing closed (§8), replacing rather than
flagging `EchoReplyHandler` (§9), and what is deliberately deferred (§10).

## Phase 1 — Design & Contracts

[data-model.md](data-model.md), [contracts/answerer-api.md](contracts/answerer-api.md),
[contracts/llm-tools.md](contracts/llm-tools.md),
[contracts/system-prompt.md](contracts/system-prompt.md),
[quickstart.md](quickstart.md).

### Post-design Constitution re-check

Re-checked against v2.1.0 after the design above. All gates still pass, with
four points worth stating explicitly:

- **V:** the amended ArchUnit rule names two classes — `GuardedPipeline` and
  `OwnerAnswerer` — and nothing else may depend on a chat-model type. That is
  tighter than a package allow-list and is the first time the rule does real
  work rather than passing vacuously.
- **X:** two independent controls now hold the same duty. The reference swap
  means the model never receives a sheet id, title or row; the leak scan means a
  draft that somehow contains one is still withheld. Neither is removed on
  account of the other.
- **IV:** the tool adapters are the enforcement point. No `@Tool` method accepts
  `userId`, a coordinate or a time as an argument; all three are injected, so a
  model that tries to pass someone else's identity has nowhere to put it.
- **VII:** unchanged from the pre-design check, and the reason this feature is a
  preview rather than a launch. The exposure is recorded in ADR-0010 with its
  obligations open and dated.

## Complexity Tracking

None.
