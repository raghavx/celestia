# agent

The Spring Boot application: the WhatsApp channel, persistence, the guarded reply
path, and the chat model. This is the only module where Spring, a database or a
network call is allowed (Constitution II).

## The reply path, as it now stands

One route from a message to an answer, and the order is the requirement rather
than an implementation detail (SPEC-012 `data-model.md` "Order of operations"):

```
webhook → inbound_event → worker → GuardedReplyHandler
                                     │
  1. entitlement      EntitlementService — denied? one fixed message. No permit,
                      no gate, no chart, no model. Work done for someone not
                      entitled to it is work that should not have been done.
  2. non-text         fixed message, and no permit taken — a "text only" reply
                      must not consume the one the owner is waiting for.
  3. turn permit      one at a time; unavailable? "busy, try again shortly", and
                      NO turn record, because no turn began.
     ── a turn starts here ──
  4. pre-fetch        chart + running dasha, before the model is invoked.
  5. gate             SPEC-011's deterministic injection/topic gate.
  6. the model        OwnerAnswerer — hardened prompt + a fixed tool set.
  7. output stage     SPEC-011: out-of-scope marker, leak scan, support line,
                      exactly one advisory sentence.
  8. clamp            the channel budget, trimmed at a sentence boundary.
  9. turn record      written whatever happened from step 3 on, faults included.
 10. release permit   in a finally, or one fault locks the product permanently.
```

`EchoReplyHandler` is gone. There is exactly one `ReplyHandler`, and that is the
point: a second reply path in the code is what SPEC-012 exists to prevent.

## Configuration

Everything below comes from the environment; the committed defaults are for
development. **Production values are supplied per environment, never committed.**

### `celestia.llm.*` — the chat model (ADR-0009, ADR-0010)

| Key | Default | Notes |
|---|---|---|
| `base-url` | `http://localhost:11434` | a local `ollama serve` in development; the warm GPU VM in production |
| `model` | `qwen2.5:0.5b` | **provisional** — verify it reports the `tools` capability (`ollama show <model>`) before relying on it |
| `fallback-model` | `qwen2.5:1.5b` | not optional: this feature is almost entirely tool calling and tool-call JSON from a small model is often unreadable |
| `keep-alive` | `5m` | short on a laptop; long on the VM, which never pays the cold load |
| `max-tool-rounds` | `6` | the round cap. Exceeding it is a **prompt** defect |
| `turn-deadline` | `PT60S` | the wall clock for a turn. Exceeding it is a **hosting** defect. Not the service level: SC-008's 30 seconds is measured end to end on the warm VM |
| `max-concurrent-turns` | `1` | one for the preview. An unbounded queue in front of a single model is how a slow reply becomes an outage |

### `celestia.entitlement.*` — who is answered

| Key | Default | Notes |
|---|---|---|
| `allow-list` | *empty* | **empty denies everyone.** An empty list meaning "allow all" is how a configuration slip becomes an open service. One malformed entry makes the whole list unusable and nobody is answered — a typo in the owner's own number is a broken config, not a stranger at the door |

### `celestia.turn-record.*` — the record, and deleting it

| Key | Default | Notes |
|---|---|---|
| `retention-days` | `90` | records older than this are purged. **`0` means never purge on age** — it is not a way to switch off the "forget me" hard delete, which no setting may disable |
| `purge-cron` | `0 30 3 * * *` | on the scheduler `ChannelConfig` already enables |

### `celestia.seed-owner.*` — the administrative seed (FR-019)

Off unless `enabled: true`, so an ordinary boot never writes to `birth_data`.
Takes `phone-e164`, `name`, `birth-date`, `birth-time`, `zone-id`, `latitude`,
`longitude`, `place-name`, `country`. Coordinates are given explicitly because no
geocoder is wired into this application yet — see "Known limits".

## Running a model for development

```bash
ollama serve                       # the default base-url is localhost:11434
ollama pull qwen2.5:0.5b
ollama show qwen2.5:0.5b | grep -i tools   # it MUST report the tools capability
```

Then point `base-url` at it and allow-list your own number. **What this proves is
the round trip** — the client connects, a tool call is parsed, the output stage
decorates, a reply comes back. It proves **nothing about answer quality**: a 0.5B
model on a CPU will emit malformed tool JSON often, which exercises the fallback
path as the normal path. Do not put its output in front of a practitioner, and do
not tune anything to SC-008's latency budget on a laptop.

## Operating it

Three counters say what was turned away before a turn ever began — the busiest
failure mode is otherwise the least visible one:

```
celestia.agent.turned_away{reason="no_entitlement"}
celestia.agent.turned_away{reason="not_text"}
celestia.agent.turned_away{reason="no_permit"}
```

Everything from the permit onward is in `owner_turn_record`, including which model
answered and whether it was the fallback — a practitioner's verdict on an answer
is meaningless without knowing which model produced it.

## Tests, and which tier proves what

A result from the wrong tier is worse than no result.

| Tier | How | Proves | Does **not** prove |
|---|---|---|---|
| **Stub model** | `./mvnw -pl agent -am test` | wiring, the reference swap, statelessness, the budgets, the permit, the leak scan — **correctness lives here** | that any real model behaves |
| **Testcontainers** | `./mvnw -pl agent -am verify` (needs Docker, ADR-0008) | the schema, the "forget me" delete, the retention purge | anything about the model |
| **A real model** | `./mvnw -pl agent -am test -Dtest=OwnerEvalHarness` | the round trip; against the production VM and Llama 4 Scout, SC-001 and SC-008 | answer quality, when run against a small local model |

## Known limits

- **No geocoder.** `geo` defines the port (SPEC-007) but no implementation is
  wired, so a place *name* cannot be resolved to coordinates. The admin seed takes
  coordinates directly, and the model's `castChartFor` tool returns "unavailable"
  so it declines rather than inventing another person's chart (FR-011's plumbing
  is in place and tested; the tool that would feed it is not).
- **English only.** The gate refuses Hindi and Hinglish, so the agent never sees
  one. Acceptable only because the audience is one known person.
- **No conversation memory.** Each question is answered on its own (FR-018).
  "What about her?" is answered with a question, by design.
- **Two literals agree with `guardrail` by convention, not by compilation.**
  `OutputStage.OUT_OF_SCOPE_MARKER` and `Gate.MAX_MESSAGE_LENGTH` are
  package-private, so `system-prompt.md` writes `<<OUT_OF_SCOPE>>` and the reply
  clamp writes 4,096 as literals. `SystemPromptTest` pins the marker. If you are
  editing `guardrail` anyway, making both `public` removes the duplication
  (SPEC-012 research.md §12).
- **`maven.compiler.parameters=true` is load-bearing.** Spring AI binds `@Tool`
  arguments by parameter name; without the flag every tool argument arrives null
  and the tools fail silently.
