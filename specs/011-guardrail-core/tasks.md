---

description: "Task list for SPEC-011: Guardrail Core"
---

# Tasks: Guardrail Core

**Input**: Design documents from `/specs/011-guardrail-core/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/guardrail-api.md](contracts/guardrail-api.md),
[contracts/corpus-format.md](contracts/corpus-format.md), [quickstart.md](quickstart.md)

**Tests**: included — not optional. FR-019/FR-020 make the two corpora a standing
CI gate, and plan.md's Project Structure names the test classes explicitly, so
they are tasks here (Constitution III).

**Organization**: tasks are grouped by user story (spec.md priorities
P1/P1/P2/P2/P2/P3) so each story is independently implementable and testable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no unmet dependency)
- **[Story]**: US1 … US6, matching spec.md's user stories
- Every task names an exact file path

## A note on what already exists

Two files were drafted during planning and are already in the tree:

- `guardrail/src/test/resources/com/celestia/guardrail/corpus-in-scope.tsv` —
  15 English cases, one per answerable topic group. Needs the owner's read
  (quickstart step 4), not re-authoring.
- `specs/011-guardrail-core/deferred-multilingual/` — the drafted Hindi and
  Hinglish work, parked and **excluded from the build**. No task here touches it.

`guardrail` itself is otherwise empty apart from `package-info.java`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: give the module its one new dependency and its resource layout
before any rule is written.

- [ ] T001 Add the `core` dependency to `guardrail/pom.xml` (for `TopicGroup`,
      `Sensitivity`, `KpToolkit`) and remove the stale comment about an injected
      intent-classifier client, which ADR-0023's layer 2 defers to SPEC-021
      (research.md §1)
- [ ] T002 [P] Create the resource directories
      `guardrail/src/main/resources/com/celestia/guardrail/` and
      `guardrail/src/test/resources/com/celestia/guardrail/` (the latter already
      holds `corpus-in-scope.tsv` from planning)
- [ ] T003 [P] Rewrite `guardrail/src/main/java/com/celestia/guardrail/package-info.java`:
      it currently promises an intent classifier and cites "SPEC-014", both stale.
      State what this module is — the deterministic gate and output stage, the
      only route to the model — and that the classifier is SPEC-021

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the value types, the TSV reader and the normaliser that every rule
and every test depends on.

**⚠️ CRITICAL**: no user story task may start until this phase is complete.

- [ ] T004 [P] `Tsv` package-private reader for this module's resources
      (`#` comment line, header line, records; cells unescape `\\`, `\t`, `\n`)
      in `guardrail/src/main/java/com/celestia/guardrail/Tsv.java`. A deliberate
      small duplication of `core.toolkit`'s reader rather than widening that
      module's API — note the reason in the Javadoc (plan.md Structure Decision)
- [ ] T005 [P] `GateRequest` record (`text`, `senderRef`) in
      `guardrail/src/main/java/com/celestia/guardrail/GateRequest.java`.
      Deliberately not a channel type (Constitution IX)
- [ ] T006 [P] `GateDecision` record + `Outcome` (`ALLOWED`,
      `REFUSED_OUT_OF_SCOPE`, `REFUSED_HOSTILE` — FR-001's three outcomes) and
      `Reason` (`IN_SCOPE`, `HOSTILE`, `OVERSIZE`, `OFF_TOPIC`, `UNMATCHED`) in
      `guardrail/src/main/java/com/celestia/guardrail/GateDecision.java`.
      Javadoc `topicGroup` as **best-effort diagnostic only** and `matchedRule`
      as never carrying the message text (research.md §9, §13)
- [ ] T007 [P] `homoglyph-folds.tsv` — confusable Cyrillic/Greek/full-width
      glyphs to their Latin equivalents — in
      `guardrail/src/main/resources/com/celestia/guardrail/homoglyph-folds.tsv`
- [ ] T008 `Normaliser`: the ordered pipeline of research.md §2 — NFKC, strip
      zero-width/bidi/variation selectors, fold confusables from T007,
      lower-case with `Locale.ROOT`, collapse whitespace — producing a
      `NormalisedMessage` with the normalised view plus decoded base64/hex
      candidates (FR-005, FR-011), in
      `guardrail/src/main/java/com/celestia/guardrail/Normaliser.java`
      (depends on T004, T007)
- [ ] T009 `NormaliserTest`: each pipeline step; a jailbreak phrase hidden by
      zero-width characters, homoglyphs, base64 and hex all normalise to the same
      match; **and the invariant that decoded candidates are returned separately
      from the normalised view**, since conflating them is what would let an
      attacker encode a subject word past the topic gate in
      `guardrail/src/test/java/com/celestia/guardrail/NormaliserTest.java`
      (depends on T008)
- [ ] T009a `SelfHarmSignal.of` (FR-015): `INTENT` requires a **first-person
      volitional construction with a harm term**; a question about what the chart
      indicates is `CHART_INQUIRY`; **`INTENT` wins when both appear**, because a
      support line shown unnecessarily costs little and a missed one is not
      recoverable (research.md §7). Foundational rather than US4, because
      `GuardedPipeline` needs it on the refusal path (T017), in
      `guardrail/src/main/java/com/celestia/guardrail/SelfHarmSignal.java`
      (depends on T008)

**Checkpoint**: text can be normalised deterministically. Rule work can begin.

---

## Phase 3: User Story 1 - A hostile or off-topic message never reaches the model (Priority: P1) 🎯 MVP

**Goal**: a jailbreak, a prompt probe, a cross-user probe, an encoded payload or
a plainly off-topic message is refused with one fixed redirect, and the model is
never called.

**Independent Test**: replay the adversarial corpus with a counting fake
`Answerer` attached — every case refused with its expected reason, and zero
invocations across the whole file (spec.md US1, SC-001).

### Implementation for User Story 1

- [ ] T010 [P] [US1] `FixedMessages`: the refusal redirect, the safe replacement
      and the self-harm support line (Tele-MANAS 14416), as constants with a
      Javadoc note that this is the **only** source of this copy and that the
      four advisory sentences belong to SPEC-010's `Sensitivity.note()` instead,
      in `guardrail/src/main/java/com/celestia/guardrail/FixedMessages.java`
- [ ] T011 [P] [US1] `HostileRule` record + `Family` enum
      (`INSTRUCTION_OVERRIDE`, `PROMPT_EXTRACTION`, `REASONING_EXTRACTION`,
      `ROLE_IMPERSONATION`, `CROSS_USER`) in
      `guardrail/src/main/java/com/celestia/guardrail/HostileRule.java`
- [ ] T012 [US1] `hostile-phrases.tsv` — `family`, `name`, `phrase` — covering
      all five families of research.md §6 and so all three limbs of FR-004, in
      `guardrail/src/main/resources/com/celestia/guardrail/hostile-phrases.tsv`.
      **`CROSS_USER` must key on the identifier (an E.164-shaped number, a user
      id, "the user who…"), never on "another person"**: SPEC-010 FR-012
      deliberately allows a user to supply a second person's *birth data*, and
      keying on the wrong thing either leaks another user's data or breaks
      marriage matching (depends on T011)
- [ ] T013 [US1] `offtopic-phrases.tsv` — recognisably off-topic requests
      (recipes, code, general chat) — in
      `guardrail/src/main/resources/com/celestia/guardrail/offtopic-phrases.tsv`.
      This list buys **diagnosis, not a decision**: under fail-closed these
      messages would be refused anyway, and it exists only so `OFF_TOPIC` can be
      counted apart from `UNMATCHED` (research.md §3)
- [ ] T014 [US1] `HostileRules`: load T012, match every rule against the
      normalised view **and every decoded candidate**, return the first matching
      rule's name, in
      `guardrail/src/main/java/com/celestia/guardrail/HostileRules.java`
      (depends on T008, T012)
- [ ] T015 [US1] `Answerer` functional interface + `Answer` and `AnswerContext`
      records in `guardrail/src/main/java/com/celestia/guardrail/Answerer.java`
      — the seam SPEC-012 implements, and what makes "no model was called"
      countable before a model exists (research.md §11,
      contracts/guardrail-api.md)
- [ ] T016 [US1] `Gate.decide`: the three outcomes of FR-001 in the decision
      order of research.md §3 — oversize
      (4,096, WhatsApp's own text cap) → hostile → in-scope → off-topic →
      unmatched, the last being fail-closed (FR-009) — with `Gate.bundled()`
      throwing `IllegalStateException` when the
      toolkit cannot be loaded, since a gate with no taxonomy must refuse rather
      than fall open, in
      `guardrail/src/main/java/com/celestia/guardrail/Gate.java` (depends on
      T006, T014; the in-scope branch is T022)
- [ ] T017 [US1] `GuardedPipeline.handle` — **every** reply passes the output
      stage (FR-013): normalise once and derive `SelfHarmSignal.of` from the
      normalised view truncated to the 4,096-character bound, gate, call the
      `Answerer` **only** on `ALLOWED`, then run the result — or, on a refusal,
      `OutputRequest.refusal(selfHarm, recipientRef)` — through the output stage.
      A refusal never touches the answerer, and **a refused message carrying
      self-harm intent still receives the support line** (spec.md Edge Cases,
      SC-006), in
      `guardrail/src/main/java/com/celestia/guardrail/GuardedPipeline.java`
      (depends on T009a, T015, T016; the output stage is T028)
- [ ] T018 [US1] `corpus-adversarial.tsv` — `id`, `message`, `expectedReason`,
      `expectedRule`, `note` — with at least one case per hostile family, one per
      obfuscation (base64, hex, zero-width, homoglyph), an oversize case, a benign
      off-topic case, **a mixed message (a real astrology question plus an
      injection) that must be refused as hostile**, and **two self-harm cases: a
      bare expression of intent carrying no astrology vocabulary (refused as
      `UNMATCHED`, support line owed) and the same intent padded past the
      4,096-character bound (refused as `OVERSIZE`, support line still owed) —
      the corpus asserts their reason, and T036 asserts the support line, since
      this file carries no expected reply text** — in
      `guardrail/src/test/resources/com/celestia/guardrail/corpus-adversarial.tsv`
      per [contracts/corpus-format.md](contracts/corpus-format.md)
- [ ] T019 [US1] `AdversarialCorpusTest`: parameterised over T018 — each case
      refused with its `expectedReason` and, where given, its `expectedRule`
      (asserting the rule stops one rule being credited for another's refusal) —
      plus **one assertion over the whole file that a counting fake `Answerer`
      was invoked zero times** (SC-001); a **family-completeness assertion**
      reading `HostileRule.Family` values and failing when any family has no case
      in the file — the mirror of T025's group completeness, so a family added
      later cannot ship untested; and the **SC-009 structural assertion** that
      the executed cases come from the resource file, at least as many running as
      the file has data rows, so a reviewer adding a line changes no code, in
      `guardrail/src/test/java/com/celestia/guardrail/AdversarialCorpusTest.java`
      (depends on T017, T018)
- [ ] T020 [US1] `GateTest`: the decision order explicitly — hostile beats
      in-scope for a mixed message; oversize is refused before any matching work;
      a blank or emoji-only message is a decision and not an error; the refusal
      text is **identical** for hostile and out-of-scope so a probe learns
      nothing (FR-008); and **determinism** — the same message decided twice
      yields an identical decision, and the whole adversarial corpus replayed
      twice gives identical results, with no dependence on a clock, a model or
      stored state (FR-002, SC-003) — in
      `guardrail/src/test/java/com/celestia/guardrail/GateTest.java`
      (depends on T016)

**Checkpoint**: hostile and off-topic messages are refused and the model is
provably unreachable. US1 is independently functional.

---

## Phase 4: User Story 2 - A genuine KP question gets through (Priority: P1)

**Goal**: an English question about anything the toolkit answers is allowed
through untouched.

**Independent Test**: replay the in-scope corpus — one English question per
answerable topic group — and every one must be allowed (spec.md US2, SC-002).

### Implementation for User Story 2

- [ ] T021 [P] [US2] `stopwords.tsv` — English function words and generic verbs
      excluded from derivation — in
      `guardrail/src/main/resources/com/celestia/guardrail/stopwords.tsv`.
      Must exclude at least the generic matter words research.md §4 found
      (`gains`, `good`, `through`, `will`, `always`, `very`, `person`, `long`),
      which would otherwise make "I will be good" an astrology question
- [ ] T022 [US2] `InScopeVocabulary`: build the term set once at load from three
      sources (research.md §4) — the engine's `Nakshatra`/`Graha`/`Sign`
      constants plus KP terms; every answerable sheet's title and promise-rule
      matters minus T021's stopwords; and T023's supplement — attributing each
      term to a `TopicGroup`, **except terms from the master rule book
      `1500-rules`, which span every subject and so carry no subject claim**
      (research.md §12 finding 1). Latin-script terms match on **word
      boundaries, never bare substrings** (§12 finding 4). **One matched term is
      enough** (research.md §5): the terms that survive T021's stopwords are
      subject matter, and requiring two would refuse the short questions that are
      the common case on WhatsApp. Wire the in-scope branch of `Gate.decide` to
      it, in
      `guardrail/src/main/java/com/celestia/guardrail/InScopeVocabulary.java`
      (depends on T001, T016, T021)
- [ ] T023 [US2] `topic-terms-en.tsv` — the small supplement of compounds and
      synonyms the toolkit's own wording misses, by topic group — in
      `guardrail/src/main/resources/com/celestia/guardrail/topic-terms-en.tsv`.
      Must at minimum resolve the known false refusal: "gemstone" where the sheet
      gives "gems" (research.md §12 finding 3). Keep it to compounds and
      synonyms — a per-topic keyword list is what FR-006 exists to forbid
- [ ] T024 [US2] `InScopeVocabularyTest`: terms derive from the real bundled
      toolkit; a housekeeping or author-"ignore" sheet contributes nothing
      (FR-007); stopwords are absent; master-rule-book terms allow a message
      without claiming a subject; `ank` inside "bank" and `rog` inside
      "progress" do **not** match, proving word-boundary matching, in
      `guardrail/src/test/java/com/celestia/guardrail/InScopeVocabularyTest.java`
      (depends on T022, T023)
- [ ] T025 [US2] `InScopeCorpusTest`: parameterised over the existing
      `corpus-in-scope.tsv` — every case `ALLOWED`, **and nothing asserted about
      its topic group**, which two replays showed is not satisfiable and is a
      diagnostic label rather than a result (research.md §13,
      contracts/corpus-format.md). Plus one completeness assertion reading the
      answerable groups **from the toolkit** so a new answerable group fails the
      build until the corpus covers it, and the **SC-009 structural assertion**
      that at least as many cases run as the file has data rows, so adding a line
      needs no code change, in
      `guardrail/src/test/java/com/celestia/guardrail/InScopeCorpusTest.java`
      (depends on T022, T023)
- [ ] T026 [US2] Extend `GateTest` with the US2 acceptance scenarios: a bare
      horary number with a question is allowed; a sensitive but in-scope question
      (disease, lifespan, speculation, a gemstone) is **allowed rather than
      refused**, because the advisory sentence is how those are handled; and a
      second person's birth data supplied in the message is allowed, as the
      counterpart to T012's `CROSS_USER` cases; and **a one-word question
      ("marriage?") is allowed**, pinning the single-term threshold of
      research.md §5 so it cannot be tightened to two without a failing test, in
      `guardrail/src/test/java/com/celestia/guardrail/GateTest.java`
      (depends on T022, T012)

**Checkpoint**: both P1 stories hold — the gate refuses the hostile and admits
the genuine. **MVP complete.**

---

## Phase 5: User Story 3 - A sensitive answer always carries its advisory sentence (Priority: P2)

**Goal**: a reply that drew on health, longevity, finance or remedy content ends
with exactly one fixed advisory sentence — the most severe class used.

**Independent Test**: for every combination of sensitivity classes a reply might
draw on, the decorated reply ends with the single correct sentence (spec.md US3).

### Implementation for User Story 3

- [ ] T027 [P] [US3] `OutputRequest` and `OutputResult` records + `LeakFamily`
      enum in `guardrail/src/main/java/com/celestia/guardrail/OutputRequest.java`
      and `.../OutputResult.java`. `sensitivityUsed` is **supplied by the
      caller** from the toolkit tool results, never inferred from the draft text
      (data-model.md)
- [ ] T028 [US3] `OutputStage.decorate`: the fixed composition order of
      data-model.md — out-of-scope marker (FR-016) → leak scan → support line →
      advisory sentence — with both of the first two **terminal**, so a withheld
      reply is never decorated. The advisory is `sensitivityUsed.note()`
      verbatim, appended exactly once (FR-014), in
      `guardrail/src/main/java/com/celestia/guardrail/OutputStage.java`
      (depends on T010, T027; the leak scan is T034)
- [ ] T029 [US3] `OutputStageTest` for the advisory: health content ends with the
      health sentence verbatim; finance **and** longevity together end with the
      longevity sentence only; no sensitive content adds nothing; a draft that
      already contains advisory-like wording still ends with exactly one
      (SC-004), in
      `guardrail/src/test/java/com/celestia/guardrail/OutputStageTest.java`
      (depends on T028)

**Checkpoint**: sensitive answers carry their advisory. US1–US3 hold.

---

## Phase 6: User Story 4 - Someone in crisis gets help before astrology (Priority: P2)

**Goal**: a user's own message expressing intent to harm themselves produces a
reply opening with the support line, before any astrological content.

**Independent Test**: messages expressing intent produce a reply whose first line
is the support line; messages merely asking what the chart indicates do not
(spec.md US4).

### Implementation for User Story 4

- [ ] T031 [US4] Wire the support line into `OutputStage`: prepended when and
      only when the signal is `INTENT`, and before any astrological content —
      which by the composition order also covers the refusal path, since
      `OutputRequest.refusal` carries the signal and nothing else decorates it,
      in `guardrail/src/main/java/com/celestia/guardrail/OutputStage.java`
      (depends on T009a, T028)
- [ ] T032 [US4] `SelfHarmSignalTest`: "I want to die" and "I'm going to end it"
      are `INTENT`; "does my chart show suicide" and "is there danger to my life"
      are `CHART_INQUIRY` and get the health advisory with **no** support line
      (SPEC-010 FR-011 vs FR-011a); "my chart says I should die" — both present —
      is `INTENT` (SC-006), in
      `guardrail/src/test/java/com/celestia/guardrail/SelfHarmSignalTest.java`
      (depends on T009a, T031)

**Checkpoint**: the crisis path works and is distinct from the chart question.

---

## Phase 7: User Story 5 - The reply never leaks what it must not (Priority: P2)

**Goal**: no reply reaches a user carrying the system prompt, the tool
definitions, internal reasoning, another user's data, or any trace of the
toolkit's identity.

**Independent Test**: feed the output stage replies deliberately containing each
forbidden class and confirm none is delivered (spec.md US5), and confirm the
title classification resolves every sheet the toolkit carries — the scan cannot
be trusted on a title it never classified.

### Implementation for User Story 5

- [ ] T033 [P] [US5] `LeakRule` record (`family`, `name`) + the `LeakFamily` enum
      (`SHEET_ID`, `SHEET_TITLE`, `ROW_REFERENCE`, `CITATION_FRAME`, `SECRET`,
      `OTHER_PARTY_IDENTIFIER`, `REASONING_MARKER`) in
      `guardrail/src/main/java/com/celestia/guardrail/LeakRule.java`.
      What each family keys on is **not** a field: the six signals are
      heterogeneous and live in T034's code with research.md §8 as their
      reference (data-model.md `LeakRule`)
- [ ] T033a [P] [US5] `title-prose-classes.tsv` — `title`, `class`
      (`NON_PROSE` | `PROSE_COLLIDING`), `reason` — the **reviewed exceptions**
      to the mechanical classification of T034, not a copy of the 75 titles. Seed
      it with `ABCD Significance` → `PROSE_COLLIDING` (`ABCD` is KP technique
      vocabulary: sign, star, sub and sub-sub lords), and note `BTR` in a comment
      as the one classified `NON_PROSE` most likely to need revisiting, in
      `guardrail/src/main/resources/com/celestia/guardrail/title-prose-classes.tsv`
      (research.md §8)
- [ ] T034 [US5] The leak scan in `OutputStage`, keyed on the six signals of
      research.md §8: toolkit **sheet ids** (`1500-rules`, `ready-reckoner`),
      **row references** (`row 1349`, `<slug>:r<n>`), **sheet titles by prose
      class**, the **secrets supplied by the caller**, **another party's
      identifier**, and **structural reasoning markers**. The three that need
      stating precisely:
      **Titles by prose class** — ids and titles for all 75 sheets read from
      `KpToolkit.sheets()`, housekeeping included, since leaking
      `Version Control` is still a leak. A **non-prose** title is keyed **bare**
      (`ReadyReckoner`, `MarriageMatching`, `1500+Rules` — 34 of the 75 at the
      current release, derived not listed: `LeakFamily.SHEET_TITLE`); a
      **prose-colliding** one only **inside a citation frame**
      ("per the …", "source: …" — `Relationship`, `Aspects`,
      `Death`, `Vimshottari`: `LeakFamily.CITATION_FRAME`). The class is decided
      mechanically — any word with an internal capital, a digit or a `+` is
      non-prose — and overridden in both directions by T033a. Title matching is
      case-insensitive on the whole title at word boundaries, so `ReadyReckoner`
      fires and "ready to reckon with it" does not.
      **Another party's identifier** — an E.164-shaped number or user-id token
      that is not the caller's `recipientRef`
      (`LeakFamily.OTHER_PARTY_IDENTIFIER`, the control FR-017's "another user's
      data" limb requires).
      **Structural reasoning markers** — a `<thinking>` tag or a
      `system:`/`assistant:` role prefix (`LeakFamily.REASONING_MARKER`,
      FR-017's internal-reasoning limb).
      **Never on a bare common word or a bare number**: `Relationship`,
      `Aspects`, `Death` and `Numerology` are real sheet titles and ordinary
      English an answer must be able to use, and a horary number or a birth year
      is not an identifier (FR-017), in
      `guardrail/src/main/java/com/celestia/guardrail/OutputStage.java`
      (depends on T028, T033, T033a)
- [ ] T035 [US5] Extend `OutputStageTest` for the leak scan: a draft quoting the
      system prompt, naming a sheet ("per the ReadyReckoner sheet"), citing a row,
      or exposing a tool call is **withheld in full and replaced** by the safe
      message, never partly redacted (FR-018); a draft carrying
      `<<OUT_OF_SCOPE>>` is replaced by the fixed redirect; the regression that
      matters — "your relationship with your partner" **is** delivered,
      proving a prose-colliding sheet title does not trip the scan (SC-005); the
      **bare-title pair that this feature nearly shipped without** — "ReadyReckoner
      says your 7th house sub lord connects to 11" **is withheld** as
      `SHEET_TITLE` with no citation frame present, while "your Vimshottari dasha
      runs to 2031" and "the aspects between them are close" **are delivered**,
      proving the prose class is what separates them; the identifier
      pair, where **a draft containing a phone number that is not the
      recipient's is withheld as `OTHER_PARTY_IDENTIFIER`** while "take horary
      number 108", "you were born in 1987" and the recipient's own number are
      delivered; and a draft carrying a `<thinking>` block or an `assistant:`
      role prefix is withheld as `REASONING_MARKER`, in
      `guardrail/src/test/java/com/celestia/guardrail/OutputStageTest.java`
      (depends on T034)
- [ ] T035a [US5] `LeakRuleTest` — the classification itself, which T035 only
      exercises through three example drafts: **every** title from
      `KpToolkit.sheets()` resolves to exactly one class, so no sheet is
      unclassified; every override in T033a names a title that exists in the
      loaded manifest (data-model "Validation at load"), so a renamed sheet
      cannot leave a stale line behind; and the mechanical rule is pinned at both
      ends — `ReadyReckoner` and `1500+Rules` are `NON_PROSE`, `Relationship` and
      `Medical Astrology` are `PROSE_COLLIDING`. Assert the split is **derived**,
      never a hard count: a toolkit release that adds a sheet must change the
      numbers without failing this test, in
      `guardrail/src/test/java/com/celestia/guardrail/LeakRuleTest.java`
      (depends on T033a, T034)

**Checkpoint**: US1–US5 hold. The reply path is safe in both directions.

---

## Phase 8: User Story 6 - The owner can see why, and extend the gate (Priority: P3)

**Goal**: every refusal says which rule matched, `UNMATCHED` is countable on its
own, and a reviewer can extend either corpus — or any resource table — without
touching code and without silently breaking the gate.

**Independent Test**: every refusal carries a non-empty reason; adding a line to
either corpus file is exercised on the next run with no code change (spec.md
US6); and a malformed resource edit fails the build loudly rather than yielding a
gate that misbehaves quietly.

### Implementation for User Story 6

- [ ] T036 [US6] `GuardedPipelineTest`: a refused request never reaches the
      answerer (the property FR-003 asserts, tested directly rather than only
      through the corpus); an allowed one reaches it exactly once; the result
      always passes through the output stage, refusals included; **a refused
      message expressing self-harm intent comes back with the support line as its
      first line** (SC-006 — the case the pipeline would otherwise drop); and
      both `FixedMessages.refusalRedirect()` and `safeReplacement()` survive
      their own leak scan unchanged, so a copy edit cannot turn every refusal
      into the safe message, in
      `guardrail/src/test/java/com/celestia/guardrail/GuardedPipelineTest.java`
      (depends on T017, T028, T031)
- [ ] T037 [US6] Extend `GateTest`: every refusal carries a non-empty
      `matchedRule`; **no decision carries the message text anywhere** (the check
      that keeps the refusal log from quietly becoming a store of rejected
      message bodies, research.md §9); and `UNMATCHED` is distinguishable from
      `OFF_TOPIC` so the cost of failing closed is measurable (FR-010, SC-007), in
      `guardrail/src/test/java/com/celestia/guardrail/GateTest.java`
      (depends on T016, T020)
- [ ] T037a [US6] **Fail fast on bad data** — implement and test all six
      load-time validations of data-model.md "Validation at load", which no task
      covered: every `InScopeTerm.group` has answerable content; `topic-terms-en.tsv`
      names only known `TopicGroup` values; no hostile phrase is empty and every
      one belongs to a known family; no stopword appears as a `SUPPLEMENT` term
      (it would silence that term); every `title-prose-classes.tsv` entry names a
      real sheet title; and **the toolkit is loadable at all — for both
      `Gate.bundled()` and `OutputStage.bundled()`**. If it is not, each fails
      loudly rather than yielding a gate with no taxonomy or a leak scan with no
      vocabulary, either of which would fall open (spec.md Edge Cases; the gate
      half was implemented in T016 but untested, and the output half is new —
      the leak scan gained its dependency on `KpToolkit.sheets()` with T034).
      This is the owner's story because a bad data edit must break the
      build, not the gate: each check gets a negative test proving the bad input
      is rejected, in `HostileRules.java`, `InScopeVocabulary.java`, `Gate.java`
      and `OutputStage.java` under
      `guardrail/src/main/java/com/celestia/guardrail/`, with the tests in
      `guardrail/src/test/java/com/celestia/guardrail/LoadValidationTest.java`
      (depends on T014, T016, T022, T034)
- [ ] T038 [US6] Add the single-path ArchUnit rule to
      `agent/src/test/java/com/celestia/agent/architecture/DeterminismArchitectureTest.java`:
      no class outside `GuardedPipeline` may depend on a chat-model type. It
      **passes vacuously today** — there is no chat model in the build until
      SPEC-012 — which is the point: it is in place before a second path can be
      written. Use `allowEmptyShould(true)`, as the neighbouring rules do
      (research.md §11)

**Checkpoint**: all six stories are independently functional.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [ ] T039 [P] Add a `guardrail/README.md`: what the module is, the decision
      order, the two corpora and how to extend them, and the three things
      deliberately **not** here (the classifier and embedding fallback →
      SPEC-021; the LLM and reply wiring → SPEC-012; Hindi/Hinglish →
      post-launch, parked in `specs/011-guardrail-core/deferred-multilingual/`)
- [ ] T040 [P] Javadoc the API-stability note on the package: adding a
      `TopicGroup` to the toolkit widens the gate with no API change; adding a
      phrase or a term is a resource change; renaming a hostile rule requires
      updating `corpus-adversarial.tsv` in the same change
      (contracts/guardrail-api.md "Stability") in
      `guardrail/src/main/java/com/celestia/guardrail/package-info.java`
- [ ] T041 [P] `GatePerformanceTest`: a warmed-JIT timing assertion that a
      decision on a message at the 4,096-character bound averages **well under
      1 ms** over 1,000 calls — plan.md's Performance Goal rather than SC-008's
      much looser "tenth of a second", so a hundredfold regression fails CI
      instead of passing it; the mean, not a single call, so one GC pause does
      not fail CI (SC-008), in
      `guardrail/src/test/java/com/celestia/guardrail/GatePerformanceTest.java`
      (depends on T016, T022). SC-009's structural assertion now lives in the two
      corpus tests themselves (T019, T025), where the resource files are read
- [ ] T042 Run quickstart.md steps 1, 2, 3 and 5 — `./mvnw -pl guardrail -am
      test`, the two corpus tests, `DeterminismArchitectureTest`, and
      `./mvnw verify` — and confirm green, including Spotless and the ArchUnit
      layering rules that already list `com.celestia.guardrail..` among the pure
      packages. The build is expected **green**. The only tests that do not run
      are `agent`'s Testcontainers integration tests when Docker is not running
      (environmental, ADR-0008, not a defect). An earlier draft of this task also
      listed `ephemeris`'s `HoraryHouseProviderContractTest.isDeterministic` as a
      standing failure; **that was wrong** — verified 2026-11-24, it passes, as
      does the whole `ephemeris` module (51 tests, 0 failures), and the provider
      has not changed since SPEC-005 landed it. Treat any failure of it as a real
      Constitution II defect, not as expected noise
- [ ] T043 **MANUAL — owner only**: read
      `guardrail/src/test/resources/com/celestia/guardrail/corpus-in-scope.tsv`
      as a user would type it and correct anything stilted, then read
      `hostile-phrases.tsv` sceptically against the assumption that attacks
      arrive predominantly in English (spec.md Assumptions, quickstart step 4).
      Fail-closed means every gap in the in-scope corpus is a real question
      refused, so this is the highest-value review in the feature — and the
      only task here that code cannot do

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies — start immediately
- **Foundational (Phase 2)**: depends on Setup — BLOCKS every user story
- **User Stories (Phases 3–8)**:
  - **US1 and US2 are not independent of each other in code**, though they are in
    testing. Both write the in-scope/hostile branches of the same `Gate.decide`
    (T016, T022) and both extend the same `GateTest`. US1 lands the decision
    skeleton; US2 fills its in-scope branch. Build US1 first.
  - **US3 owns `OutputStage`** (T028). US4 (T031) and US5 (T034) both extend that
    same file, so they follow US3 and should not be worked in parallel with each
    other.
  - **The self-harm signal is Foundational, not US4** (T009a). `GuardedPipeline`
    computes it for every message, because a refused message can be the one that
    most needs the support line. US4 keeps only the wiring (T031) and the tests
    (T032).
  - **US6 depends on US1 and US3**: T036 needs both the pipeline and the output
    stage; T037 extends `GateTest`. T038 (the ArchUnit rule) depends on nothing
    and could land at any time. **T037a is last of the six**, because it
    validates data that every other loader reads — T014, T016, T022 and T034
    must all exist before their bad-input paths can be tested.
- **Polish (Phase 9)**: T039–T042 after the stories; T043 is the owner's and can
  begin as soon as T025 passes

### Within Each User Story

- Resource tables before the loaders that read them
- Loaders before the tests that exercise them
- `Gate`/`OutputStage` before the tests that drive them end to end

### Parallel Opportunities

- Setup: T002 and T003 run in parallel with T001
- Foundational: T004, T005, T006, T007 are all independent files
- US1: T010 and T011 are independent of each other; T018 (the corpus TSV) can be
  authored in parallel with T014–T017
- US2: T021 and T023 are both data files, independent of T022's code
- US5: T033 and T033a are both independent of T034's code; T035a is a separate
  test class from T035 and can be written alongside it
- Across stories: T038's ArchUnit rule is independent of everything here
- **Not parallel**: anything touching `Gate.java` (T016, T022) or
  `OutputStage.java` (T028, T031, T034), and the three tasks that extend
  `GateTest` (T020, T026, T037)

---

## Parallel Example: Foundational

```bash
Task: "Tsv reader in guardrail/.../Tsv.java"                      # T004
Task: "GateRequest record in guardrail/.../GateRequest.java"      # T005
Task: "GateDecision + Outcome/Reason in .../GateDecision.java"    # T006
Task: "homoglyph-folds.tsv in guardrail/src/main/resources/..."   # T007
```

## Parallel Example: User Story 1

```bash
Task: "FixedMessages in guardrail/.../FixedMessages.java"         # T010
Task: "HostileRule + Family in guardrail/.../HostileRule.java"    # T011
Task: "corpus-adversarial.tsv in src/test/resources/..."          # T018
```

---

## Implementation Strategy

### MVP First (User Stories 1 and 2 — both P1)

1. Phase 1 (Setup) and Phase 2 (Foundational) — CRITICAL, blocks everything.
2. Phase 3 (US1) — the gate refuses, and the model is provably unreachable.
3. Phase 4 (US2) — the gate admits genuine questions.
4. **STOP and VALIDATE**: both corpora green, zero answerer invocations. At this
   point Constitution V's inbound half holds and SPEC-012 has something safe to
   plug into.

### Incremental Delivery

1. Setup + Foundational → text normalises deterministically.
2. US1 → hostile and off-topic refused, model unreachable.
3. US2 → genuine questions allowed. **MVP.**
4. US3 → sensitive answers carry their advisory.
5. US4 → the crisis path, distinct from the chart question.
6. US5 → nothing leaks, in either the product's or the toolkit's direction.
7. US6 → the operator can see why and extend the gate.
8. Polish → docs, full build, then the owner's corpus review.

### Parallel Team Strategy

The file overlaps above make this a poor feature to split finely. With two
developers after Foundational:

- Developer A: US1 then US2 (both own `Gate.java`)
- Developer B: US3 then US4 then US5 (all own `OutputStage.java`)
- Either picks up US6 once US1 and US3 have landed; T038 can be done by whoever
  is free

---

## Notes

- [P] tasks touch different files with no unmet dependency
- [Story] labels trace every task back to its spec.md user story
- Tests are included per Constitution III and FR-019/FR-020 — the two corpora
  are a standing CI gate, not fixtures
- Commit after each task or logical group; stop at any checkpoint to validate a
  story independently
- T043 is the only task blocked on a human. Everything else is buildable today:
  this feature needs no LLM, no ephemeris data, no Docker, and no network — it
  depends only on the already-imported toolkit
- Nothing here touches `specs/011-guardrail-core/deferred-multilingual/`. That
  work is parked, unreviewed and excluded from the build until multilingual
  support is re-enabled post-launch
