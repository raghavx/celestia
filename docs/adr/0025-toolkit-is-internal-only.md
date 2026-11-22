# ADR 0025: The toolkit is internal-only; the agent is the astrologer (v2.1.0)

- **Status:** accepted
- **Date:** 2026-11-23
- **Decision ref:** Constitution v2.0.0 → v2.1.0; amends ADR-0024's Principle X; carries
  into PROJECT_PLAN.md principle 14 and Phase 10's grounding check; SPEC-010
  (`specs/010-kp-toolkit-knowledge-base`) `spec.md` FR-008 and
  `contracts/agent-tools.md`

## Context

ADR-0024 made the licensed *Prediction ToolKit for Advanced* the single source of KP
judgement (Constitution v2.0.0, Principle X): "every KP interpretation ... MUST trace
to a sheet of the licensed toolkit. Each such statement cites that sheet." While
working out SPEC-010's contracts (what `getChartPromises`, `getHoraryMindset` and the
other agent tools return, and what the eval harness checks), this wording turned out
to be ambiguous: it does not say *where* the citation has to land. One reading —
the one the implementation drafts initially took — has the chat reply itself naming
the source sheet (e.g. "per the ReadyReckoner sheet ..."). That reading is wrong for
this product: the toolkit is a licensed asset the owner uses to build Celestia: it is
not something the end user has any relationship to, and naming it, its sheet titles,
ids, or row numbers in a WhatsApp reply would expose implementation detail (and
indirectly, the identity of a third-party licensed workbook) for no product benefit.
The owner's intent is that Celestia *is* the KP astrologer to the user — it should
explain judgement the way an astrologer would (house, sub lord, star lord, timing),
never as a system quoting a document it consulted.

## Options considered

1. Keep the literal reading: the reply names the source sheet for every
   interpretation (closest to ADR-0024's original wording, but wrong for the
   product and a licensing/UX problem).
2. Drop traceability from the principle entirely — the model explains freely,
   checked only by the guardrail's topic gate (weakens Principle IV and X's actual
   purpose: preventing fabricated KP judgement).
3. **Keep traceability, make it explicitly internal.** Every interpretation still
   MUST be attributable in code/data to a toolkit sheet — nothing changes about
   *whether* the system can prove where a claim came from — but that proof is for
   the system (logging, the SPEC-012 eval harness, human review), never for the
   chat reply. The reply explains as an astrologer would, in ordinary KP language,
   and never names the toolkit, a sheet id/title, or a row number.

## Decision

Option 3, as recorded in Constitution v2.1.0:

- **Principle X** (still NON-NEGOTIABLE, unchanged in substance): every KP
  interpretation MUST still trace to a toolkit sheet, and the model MUST still NOT
  state interpretation no sheet supports. What is new is an explicit bullet: the
  toolkit is an internal asset (it powers the agent's calculations and is its
  knowledge base); no reply may name the toolkit, a sheet's id or title, or a row
  number; the agent **is** the KP astrologer to the user; traceability is for the
  system, not the user.
- **Preamble**: reworded so the agent is described as the astrologer the user talks
  to, with toolkit traceability named as internal.
- **PROJECT_PLAN.md** (principle 14, Phase 10's grounding check): the same "cites
  it" / "cites a toolkit sheet" wording updated to match.
- **SPEC-010** (`spec.md` FR-008, `contracts/agent-tools.md`): already carries this
  resolution — it is where the ambiguity was first found and fixed, ahead of this
  constitution amendment catching the wording up.
- **Unchanged:** every other part of Principle X (methods followed in written
  order with engine-filled numbers; toolkit content kept word for word; new
  capabilities specified as toolkit topics); Principle IV (matching is code, never
  the model); the security guardrails (ADR-0023, injection gate, identity
  isolation, PII, payment gate).

## Consequences

- MINOR version bump (2.0.0 → 2.1.0): new, previously-unstated guidance added to a
  NON-NEGOTIABLE principle, nothing existing removed or weakened.
- SPEC-012's eval harness (consuming `contracts/agent-tools.md`) gets a concrete,
  checkable obligation: the reply never mentions the toolkit, a sheet id/title, or a
  row number — only the astrological basis. This is now part of the contract, not
  left to the prompt author's judgement.
- No change to what the system must be able to prove (traceability), only to what
  it says out loud. Internal logging / `source` fields on every tool result are
  unaffected and remain mandatory.
- Any future spec that quotes Principle X verbatim should quote the v2.1.0 wording,
  not ADR-0024's, to avoid reintroducing this ambiguity.
