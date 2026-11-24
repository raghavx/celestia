# Feature Specification: Owner-Preview Agent

**Feature Branch**: `012-owner-preview-agent`

**Created**: 2026-11-24

**Status**: Draft

**Input**: User description: "SPEC-012 Owner-preview agent — the first real answer. An Ollama-backed chat model reachable ONLY through the SPEC-011 guardrail, replacing EchoReplyHandler as the reply path."

_Every piece is now built and none of them is connected. The engine computes the
chart, the toolkit supplies the judgement, the gate stands in front of the model
and the output stage stands behind it — and the reply a user receives is still an
echo. This feature is the wiring, and the first real answer. It is also the first
time a model is allowed to run at all, which is why it comes after the gate
rather than before it._

### Where this sits

| | |
|---|---|
| **Today** | message → verified → queued → `EchoReplyHandler` → the same canned text |
| **After this** | message → **entitlement check** → gate (SPEC-011) → **hardened prompt + fixed tool set → the model** → output stage (SPEC-011) → reply |

The gate and the output stage already exist and are tested. What this feature
adds is the middle: the agent that answers, the tools it may call, the prompt
that constrains it, and the check that decides whether it runs at all.

**This serves one user: the owner.** Entitlement is an allow-list of one. Plans,
payments and public sign-up are Stage C (SPEC-017–020). That narrow audience is
what makes it acceptable to ship an English-only gate and an unreviewed prompt.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The owner gets a real answer (Priority: P1)

The owner sends "when will I get married?" from his own number. The gate allows
it, his chart and running dasha are already computed, the model calls the
toolkit tools, and what comes back is a KP answer in an astrologer's voice —
the 7th cusp sub lord, what it connects to, the period when it matures.

**Why this priority**: This is Stage A's exit criterion and the first time the
product does what it exists to do. Everything built so far is unobservable to a
user until this works.

**Independent Test**: Send each of the ten eval questions from the owner's
number and read the replies. Every one must be answerable from the toolkit and
must read as astrology rather than as a system report.

**Acceptance Scenarios**:

1. **Given** the owner's birth data is seeded and his number is allow-listed,
   **When** he asks a question the toolkit covers, **Then** he receives an answer
   that rests on named KP reasoning (house, sub lord, star lord, matter) and on
   at least one toolkit tool call.
2. **Given** a question about timing, **When** the agent answers, **Then** every
   number it states — positions, lords, cusps, dasha dates — came from a tool
   call and not from the model's own arithmetic.
3. **Given** a reply that drew on health content, **When** it reaches the owner,
   **Then** it carries exactly one advisory sentence, appended by the output
   stage rather than written by the model.
4. **Given** the owner supplies a second person's birth data for a matching
   question, **When** the agent answers, **Then** it uses that data for that
   reply only and does not store it.

---

### User Story 2 - A stranger gets nothing (Priority: P1)

Someone who is not the owner sends a message. They get one fixed, polite
message saying the service is not open yet. No chart is computed, no gate
decision is needed, and the model is never invoked.

**Why this priority**: Equal to US1 and the reason US1 is safe to ship. A model
that answers anyone is a model answering strangers at the owner's cost, with an
unreviewed prompt and no payment. The allow-list is what makes a preview a
preview.

**Independent Test**: Send messages from a number that is not on the allow-list
and confirm a fixed reply, zero model invocations, and no chart computation.

**Acceptance Scenarios**:

1. **Given** a sender who is not allow-listed, **When** they send any message,
   **Then** they receive the fixed not-open message and the model is not called.
2. **Given** a sender who is not allow-listed, **When** they send a hostile
   message, **Then** they still receive only the fixed not-open message — the
   entitlement check runs first, so the gate never sees it.
3. **Given** the allow-list is empty or unreadable, **When** any message
   arrives, **Then** nobody is answered, because failing closed is the only safe
   failure for an entitlement check.

---

### User Story 3 - The answer never names where it came from (Priority: P2)

Whatever the model saw while composing, the owner reads an astrologer. No
toolkit name, no sheet id or title, no row number — only houses, lords, stars
and timing.

**Why this priority**: Constitution X is NON-NEGOTIABLE and ADR-0025 settled
it. SPEC-011's leak scan already catches a disclosure, but catching one means
withholding the whole reply — a lost answer. This story is about not producing
one in the first place.

**Independent Test**: Run the eval set and the adversarial corpus and confirm no
delivered reply names the toolkit, a sheet or a row — and that none was withheld
by the leak scan either, because a withheld reply is a prompt failure.

**Acceptance Scenarios**:

1. **Given** any eval question, **When** the agent answers, **Then** the reply
   names no sheet, id, row or workbook.
2. **Given** an answer that rests on several toolkit rules, **When** it is
   delivered, **Then** the owner sees the astrological basis, while the sources
   behind it are recorded internally for review.
2a. **Given** any turn, **When** the tool results are assembled for the model,
   **Then** they carry opaque references rather than sheet ids, titles or row
   numbers — so the strings the reply must never contain are strings the model
   never received.
3. **Given** the owner asks directly where the knowledge comes from, **When** the
   agent answers, **Then** it speaks as the astrologer rather than naming a
   document, and discloses nothing about its own construction.

---

### User Story 4 - The agent says when it has no basis (Priority: P2)

The owner asks something the toolkit does not cover. The agent says so plainly,
in its own voice, and does not invent KP judgement to fill the gap.

**Why this priority**: The failure mode that destroys trust in an astrology
product is confident invention. Constitution X forbids stating interpretation no
sheet supports, and this is the behaviour that makes the prohibition visible.

**Independent Test**: Ask questions chosen because the toolkit has no rule for
them and confirm the agent declines rather than improvising.

**Acceptance Scenarios**:

1. **Given** a question with no toolkit basis, **When** the agent answers,
   **Then** it says it cannot judge that from the chart, without naming the
   toolkit, and offers what it can speak to.
2. **Given** a question the toolkit does cover, **When** the agent answers,
   **Then** it does not decline — a false "no basis" is as much a defect as an
   invention, and is counted separately.
3. **Given** a rule that is conditional rather than promised, **When** the agent
   uses it, **Then** it is stated as a condition to check and never as a firm
   promise.

---

### User Story 5 - The chart is ready before the model runs (Priority: P2)

The owner's natal chart and current dasha period are computed before the model
is invoked, and handed to it as context rather than fetched by it.

**Why this priority**: It removes a whole class of failure — the model asking
for, guessing at, or mis-ordering the basics — and it is what keeps the first
answer fast enough to feel like a conversation.

**Independent Test**: Confirm that a turn begins with the chart and running
dasha already in hand, and that the model's tool calls are about judgement
rather than about recomputing them.

**Acceptance Scenarios**:

1. **Given** an allowed question, **When** the turn begins, **Then** the owner's
   chart and running dasha are already computed and supplied as context.
2. **Given** the owner's birth data has not been seeded, **When** he asks a
   question, **Then** he is told what is missing rather than given an answer
   built on a default or an assumed chart.

---

### User Story 6 - A practitioner can judge the answers (Priority: P3)

Ten of the owner's own questions are run, and each answer is recorded with the
toolkit sources behind it, so a KP practitioner can mark it right or wrong
without reading code.

**Why this priority**: Correctness of KP judgement cannot be asserted by tests —
only a practitioner can say whether an answer is good astrology. This is the
instrument for that, and the precedent for every Stage B topic group.

**Independent Test**: Produce the ten answers with their sources in a reviewable
form and have them marked.

**Acceptance Scenarios**:

1. **Given** the evaluation set, **When** it is run, **Then** each answer is
   recorded together with the toolkit sources behind it and the tool calls that
   produced them.
2. **Given** a recorded answer, **When** a practitioner reviews it, **Then**
   they can judge it as astrology without reading code or querying a database.
3. **Given** a verdict on each answer, **When** the set is scored, **Then** the
   result is a count a later release can be compared against.

---

### Edge Cases

- The model is unreachable or times out. The owner gets a fixed message saying
  the reading could not be completed, not a stack trace and not silence. The
  turn is recorded as a fault rather than as an answer.
- The model calls no tool at all and answers from its own training. Any KP
  judgement so produced is ungrounded and must be caught: a reply asserting
  astrological content with zero tool calls behind it is a defect, countable.
- The model loops, calling tools repeatedly without converging. The turn is
  bounded and ends in the same fixed message as a timeout.
- The model's draft trips the leak scan. The owner receives the safe
  replacement, which is correct but is a lost answer — so every withheld reply is
  recorded as a prompt defect to fix, not as normal operation.
- The model emits the out-of-scope marker for a question the toolkit does cover.
  The owner gets the redirect, which looks to him like the product not knowing
  its own subject. Counted separately from a true "no basis".
- The answer is longer than one WhatsApp message. It is kept within the reply
  budget rather than truncated mid-sentence.
- The owner sends a second message while the first is still being answered.
  Only one turn runs at a time (FR-010a), so the second is told to try again
  shortly rather than queued behind the first. Nothing of an in-flight turn
  leaks into the next, because no turn carries state anyway (FR-018).
- The owner's question is in Hindi or Hinglish. The gate refuses it (English
  only until that is re-enabled), so the agent never sees it. Acceptable only
  because the audience is one known person.
- The owner asks a follow-up that depends on the previous turn — "what about
  her?", "and before 2027?". Nothing is remembered, so the agent asks what is
  meant. The owner will experience this as the product having a short memory,
  which is the accepted cost of answering every question on its own.
- A question that names its own matter — "and what about my career?" — works
  despite the previous point, because the owner's chart is already context and
  the matter is in the message. The limitation is anaphora, not follow-ups as
  such.
- The gate refuses a bare continuation ("and before 2027?") as unmatched,
  because it judges each message alone and that message carries no in-scope
  vocabulary. Counted under SPEC-011's unmatched refusals — a reason to widen
  the vocabulary, never to weaken the gate.

## Requirements *(mandatory)*

### Functional Requirements

#### Who is answered at all

- **FR-001**: The system MUST check entitlement before anything else in the
  reply path — before the gate, before any chart computation, and before the
  model. A sender without entitlement MUST receive one fixed message and MUST
  NOT cause a model invocation.
- **FR-002**: Entitlement MUST be served by a dedicated check whose first source
  is an owner allow-list, and whose interface is the one SPEC-018's plans and
  quotas will later plug into without changing the reply path.
- **FR-003**: An entitlement check that cannot be completed MUST deny rather
  than allow.

#### The single path to the model

- **FR-004**: The agent MUST reach the chat model only as the answering seam the
  guardrail calls, so the existing single-path rule continues to hold with no
  second route introduced.
- **FR-005**: The guardrail's inbound decision and outbound stage MUST both
  remain in force for every turn; this feature adds the middle of that path and
  MUST NOT bypass either end.
- **FR-006**: Every tool call's user identity, coordinates and clock MUST be
  injected from the authenticated sender, never read from the message text.

#### What the model may do

- **FR-007**: The model MUST be given a fixed tool set — the engine tools plus
  the five toolkit tools — and MUST NOT be able to call anything else.
- **FR-008**: Every number the answer states MUST come from a tool result. The
  model MUST NOT perform astronomical or calendar arithmetic.
- **FR-009**: The owner's chart and running dasha MUST be computed before the
  model is invoked and supplied as context for the turn.
- **FR-010**: A turn MUST be bounded in both tool calls and elapsed time. When a
  bound is exceeded, or the model is unreachable, the owner MUST receive a fixed
  message and the turn MUST be recorded as a fault.
- **FR-010a**: The system MUST serve a bounded number of turns at once —
  configurable, and **one** for the preview — and MUST tell a sender to try again
  shortly rather than queue them behind a turn in progress. An unbounded queue in
  front of a single model is how a slow reply becomes an outage.
- **FR-010b**: The bound MUST be taken **after the entitlement check and before
  the gate**. After entitlement, so a sender who may not be answered cannot
  consume the one permit and lock the owner out — a denial-of-service costing
  nothing. Before the gate, so no chart work or gate decision is done for a
  message that will be turned away anyway.
- **FR-010c**: A **turn begins when the bound is acquired.** A message turned
  away for want of it is therefore not a turn, and FR-020 does not require a
  turn record for it — but it MUST still be countable, or the product's busiest
  failure mode is its least visible one.
- **FR-011**: Birth data the owner supplies for a second person MUST be used for
  that reply only and MUST NOT be persisted.

#### What the answer must and must not say

- **FR-012**: The reply MUST NEVER name the toolkit, a sheet's id or title, or a
  row number. It MUST explain the astrological basis in ordinary KP language.
- **FR-013**: The toolkit source behind every interpretation MUST be recorded
  internally for logging, evaluation and human review, and MUST NOT reach the
  user.
- **FR-013a**: Tool results presented to the model MUST carry an **opaque
  reference** in place of the real source. The sheet id, sheet title and row
  number MUST NOT enter the model's context at all.
  *(Clarified 2026-11-24, Q1: B.)*
- **FR-013b**: The real source MUST be recorded against its reference in the
  turn's tool-call record, and MUST be resolvable there by the evaluation
  harness, the logs and human review.
- **FR-013c**: A reference MUST be stable within a turn, so that two findings
  resting on the same source can be recognised as agreeing, and MUST NOT encode
  the source it stands for — a reversible reference would put the string back in
  the model's context by another route.
- **FR-013d**: The output stage's leak scan MUST remain in force unchanged.
  Removing the string from the model's context makes the disclosure improbable,
  not impossible; the scan is what makes it caught.
- **FR-014**: The agent MUST NOT state KP interpretation that no toolkit rule or
  look-up supports. Where there is no basis it MUST say so in its own voice,
  without naming the toolkit.
- **FR-015**: The agent MUST observe the toolkit's answer obligations: a
  conditional rule is never stated as a firm promise; partly-indicated results
  are said to be partial or omitted; results sharing a matter are reported once;
  a retrograde sub lord or star lord is reported as weakening or delaying.
- **FR-016**: The advisory sentence and the crisis support line MUST continue to
  come from the output stage. The model MUST NOT write either.
- **FR-017**: A reply MUST fit within **4,096 characters** — WhatsApp's own
  limit for a text body, and the same bound SPEC-011 applies to inbound messages
  — without being truncated mid-sentence.

#### Memory and continuity

- **FR-018**: Each question MUST be answered on its own. No conversation state
  MUST be carried between turns, and the model MUST NOT be shown the text of any
  previous message. *(Clarified 2026-11-24, Q2: A.)*
- **FR-018a**: A question that names its own subject MUST still work without
  restatement, because the owner's chart is already context (FR-009): "and what
  about my career?" is answerable because it names the matter, not because
  anything was remembered.
- **FR-018b**: A question that cannot be understood on its own — "what about
  her?", "and before 2027?" — MUST be answered by asking what is meant rather
  than by guessing. Answering the wrong question confidently is worse than one
  more exchange.

#### Operating it

- **FR-019**: The owner's birth data MUST be seedable by an administrative
  action, without a conversational onboarding flow.
- **FR-020**: Every turn — every message that acquired the bound of FR-010c —
  MUST be recorded with its decision, its tool calls and their sources,
  sufficient for a practitioner to review the answer and for an operator to tell
  a fault from a refusal. Messages stopped before that point (no entitlement, not
  text, no permit) produce no turn record, because no turn happened; they are
  counted rather than recorded.
- **FR-020a**: A turn record holds the owner's question, which is a message
  body and therefore personal data. It MUST be covered by the "forget me" hard
  delete, alongside the rows SPEC-008 FR-010 already enumerates. A deletion
  request that leaves every question a user ever asked is not a deletion.
- **FR-020c**: The question column MUST be stored so that the deferred
  column-encryption pass can reach it without a schema change — the same
  position SPEC-008 took for its own PII columns, and what Constitution VII's
  "encrypted at rest where feasible" amounts to while that pass is still
  outstanding. Encryption itself is not in this feature's scope; being
  encryptable later is.
- **FR-020b**: Turn records MUST be purged once they pass a configurable
  retention age, **defaulting to 90 days**. A retention of `0` MUST mean *never
  purge on age* — and MUST NOT be read as disabling FR-020a, which is a standing
  obligation no setting may switch off.
- **FR-021**: The system MUST carry an evaluation set of at least ten of the
  owner's own questions, run on demand, producing answers with their sources in
  a form a practitioner can mark without reading code.

### Key Entities

- **Entitlement decision**: whether this sender may be answered, and on what
  basis — the allow-list today, a subscription later.
- **Turn**: one inbound question and everything that happened because of it —
  the gate decision, the tool calls, the draft, the delivered reply.
- **Tool call record**: which tool ran, what it was asked, and the sources it
  returned. Internal, and the raw material for review.
- **Eval question**: one of the owner's questions with the practitioner's
  verdict on the answer it produced.
- **Owner profile**: the seeded birth data from which the chart is computed.
- **Source reference**: the opaque token a tool result carries in place of a
  sheet id, title and row, resolvable to the real source only in the turn's
  record.
- **Turn record**: what a single question produced — decision, tool calls,
  sources, delivered reply. Written for review, never read back to answer the
  next question.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The owner asks each of the ten evaluation questions and a KP
  practitioner judges at least nine of the ten answers acceptable as astrology.
- **SC-002**: 100 % of replies that assert KP judgement have at least one
  toolkit tool call behind them; no reply states interpretation with no
  grounding.
- **SC-003**: Zero delivered replies name the toolkit, a sheet id or title, or a
  row number — across the evaluation set and the adversarial corpus.
- **SC-004**: Zero replies are withheld by the output stage's leak scan during
  the evaluation run. A withheld reply is a lost answer and a prompt defect.
- **SC-005**: 100 % of messages from senders without entitlement produce the
  fixed message and zero model invocations.
- **SC-006**: The adversarial corpus still records zero model invocations after
  the agent is wired in — the property SPEC-011 established does not regress
  when a real model exists.
- **SC-007**: Every question in the evaluation set that the toolkit covers is
  answered rather than declined, and every question it does not cover is
  declined rather than invented — both counted, neither inferred.
- **SC-008**: The owner receives a reply within **30 seconds** for 9 of 10
  typical questions, measured end to end from message received to reply sent.
  (Thirty seconds is the outside edge of what reads as a reply rather than a
  silence on WhatsApp. Whether it is achievable depends on where the model runs
  — ADR-0010 — which is why that decision blocks planning rather than this
  spec.)
- **SC-009**: No reply exceeds 4,096 characters or is truncated mid-sentence.
- **SC-013**: A "forget me" request for a user leaves **zero** turn records for
  that user, and a record past the retention age is gone on the next purge —
  both checked rather than assumed.
- **SC-010**: Zero tool results presented to the model contain a sheet id, a
  sheet title or a row number, across the whole evaluation run — checked on what
  the model received, not on what it replied.
- **SC-011**: Every recorded answer's references resolve to a real toolkit
  source, so a practitioner reviewing the evaluation set can see the basis for
  all of it and none is unaccounted for.
- **SC-012**: A question that cannot be understood on its own is answered with a
  question rather than a guess in 100 % of cases, and no reply is composed
  against context from an earlier turn, since none is kept.

## Clarifications

### Session 2026-11-24

- Q: Does the model see the real toolkit source (`sheetId`, `sheetTitle`, `row`)
  on every tool result, or an opaque reference? → A: **An opaque reference.** The real
  source stays in the turn's tool-call record, resolvable by the eval harness and
  human review but never present in the model's context.

  **Why it matters.** Constitution X forbids the reply naming the toolkit, and
  until now that rested on the prompt asking the model not to repeat strings it
  was shown on every call, with SPEC-011's leak scan as the only mechanical
  control — and a catch there costs the entire reply, not just the citation. A
  reference makes the disclosure impossible rather than improbable, for the cost of
  one lookup. It preserves what the source was actually needed for in the model's
  context: distinct handles still let two findings be recognised as resting on
  the same rule (SPEC-010 `agent-tools.md` rule 3). The scan stays anyway
  (FR-013d) — two controls on a NON-NEGOTIABLE duty is not redundancy.

- Q: Does a turn see the conversation so far? → A (first answer): server-side
  continuity — a typed subject carried between turns. **→ Revised the same day to
  stateless: each question is answered on its own, nothing is carried, and the
  model is shown no prior message text.**

  **What the preview actually needs.** The audience is one person who can repeat
  himself, and the owner's chart is already context — so a follow-up that names
  its own matter ("and what about my career?") works regardless. What statelessness
  gives up is anaphora: "what about her?", "and before 2027?". FR-018b answers
  those by asking rather than guessing, which costs one exchange and never
  produces a confidently wrong answer. Shipping the first real answer is worth
  more than the follow-ups, and continuity retrofits onto a stateless turn more
  easily than a stale subject unwinds.

  **What the discarded option established, kept for when continuity returns.**
  Writing server-side continuity down surfaced a conflict that does not go away:
  **a chart cast from a second person's supplied birth data cannot be carried
  forward**, because FR-011 forbids persisting that data at all. Any future
  continuity feature must either ask for that data again or change FR-011, which
  is a Constitution VII conversation rather than a design tweak. Recorded here so
  the next person to pick this up does not have to rediscover it.

## Assumptions

- The audience is the owner alone, behind an allow-list. That is what makes an
  English-only gate, an unreviewed prompt and a single-tenant entitlement check
  acceptable; none of them is acceptable for public traffic.
- The gate and the output stage are built, tested and unchanged by this feature.
  This feature supplies the answering seam between them and must not alter
  either.
- The advisory sentences, the crisis support line and the fixed refusal text are
  SPEC-011's and SPEC-010's constants, consumed rather than restated.
- The toolkit tools' behaviour is SPEC-010's; this feature exposes it to the
  model and does not re-derive it.
- Where the model runs and which model it is are ADR-0009 and ADR-0010, both
  still *proposed*. The spec is written to be independent of that choice; the
  plan is not, and cannot be completed until those are accepted.
- A reply is produced synchronously within one turn. Long-running or streamed
  answers are not in scope.
- **Two requirements can only be checked by a person, and that is not an
  oversight.** Nothing can prove from the outside that the model did no
  arithmetic (FR-008) or that it honoured the toolkit's answer obligations
  (FR-015) — a stub model proves the wiring, not the behaviour. Both are gated by
  the practitioner's read of the evaluation set (SC-001), which is why that read
  is a task rather than a courtesy.
- Each question is answered on its own, with no state carried between turns.
  This is assumed acceptable because the audience is one person who can repeat
  himself, and because the owner's chart is already context — so a follow-up
  that names its matter works anyway. Continuity is a later feature, and the
  note under Clarifications records what it will have to resolve when it comes.
- The opaque source reference is assumed to be per-turn rather than global. A
  reference that persisted across turns would be a small, long-lived mapping of
  the toolkit's structure, which is more to protect than the problem needs.
- Nothing here weakens SPEC-011's leak scan. Keeping the toolkit's strings out of
  the model's context and scanning the draft anyway are two controls on the same
  duty, and Constitution X is NON-NEGOTIABLE enough to want both.
- Repeated-abuse handling, rate limiting and model failover are SPEC-021 and
  SPEC-022, not here.
- Onboarding — collecting name, birth date, time and place conversationally — is
  SPEC-017. Here the owner's data is seeded administratively.
