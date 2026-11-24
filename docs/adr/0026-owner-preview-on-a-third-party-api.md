# ADR 0026: Running the owner preview on a third-party inference API

- **Status:** **accepted** 2026-11-24 — constitution amended to v3.0.0 the same
  day, and condition 1 enforced in code (`OwnerOnlyAllowListGuard`)
- **Date:** 2026-11-24 (proposed) · 2026-11-24 (accepted)
- **Decision ref:** Constitution v2.3.0 → **v3.0.0** (the amendment below, now
  applied); follows ADR-0009's 2026-11-24 amendment, which
  covers development only; interacts with ADR-0010 (hosting)

## Context

Constitution v2.3.0 defines a **deployment** as any run an inbound message can
reach from outside a developer's machine, and names the owner preview as one:
entitlement limits who it answers, not what it is. Deployments must use a
self-hosted model — we run the weights — and **no prompt or data of any user may
reach a third-party inference API**.

SPEC-012 is built: 47 of 51 tasks, 111 unit and 52 integration tests green, the
echo deleted. Four tasks remain and all four need a model capable of driving
eleven tool definitions:

- **T044** exercise the prompt and the tool surface against a real model
- **T040** measure SC-008's 30 seconds warm
- **T041** the practitioner's nine-of-ten verdict — **Stage A's exit criterion**
- **T045** make the turn deadline a real bound (buildable without a model)

Two facts make this ADR necessary rather than academic:

1. **The laptop is out.** ADR-0009's development story was measured on
   2026-11-24 and withdrawn: a 0.5B model took ~24 minutes a question and faulted
   on three of four. Development moved to the Claude API under v2.3.0's
   development permission.
2. **Development alone does not ship the preview.** The preview is a live
   WhatsApp webhook, so v2.3.0 requires self-hosted weights for it. Shipping
   therefore means renting the GPU VM of ADR-0010 — a four-figure standing
   monthly cost — **to serve one person asking ten questions.**

So the question this ADR puts: may the owner preview, and only the owner preview,
run on the Claude API?

## Options considered

1. **Rent the GPU VM, as ADR-0010 already decided.** No amendment. Stage A's exit
   criterion waits on procurement; the monthly cost begins before a single answer
   has been judged acceptable.
2. **Run the preview on the Claude API under conditions** — this ADR's proposal.
   Needs a MAJOR constitution amendment. Stage A becomes reachable immediately and
   the practitioner's verdict is earned on the model that serves it.
3. **Stay on the laptop.** Measured and withdrawn. Not an option.
4. **Ship nothing until public launch is ready.** Defers the only feedback that
   can tell us whether the product is any good — which is the whole purpose of a
   preview.

## Decision

**Permit the owner preview to run on a third-party inference API**, under six
conditions, one of which is enforced in code rather than promised.

### The argument for

- **One user, and he is the person deciding.** The only personal data that would
  reach the provider is the owner's own birth details and his own questions. He
  consents for himself. There is no third party whose data is at stake — which is
  the entire substance the prohibition protects.
- **The verdict would then transfer.** Principle III (v2.3.0) ties a correctness
  verdict to the model that produced it. If development and the preview run on the
  same model, **T041's verdict is the verdict that counts**. Developing on the API
  and deploying on Scout means tuning twice and judging twice, on hardware that
  does not exist yet.
- **The cost ratio is not close.** About $0.05–0.08 a question against a standing
  monthly GPU bill, for an audience of one.
- **Reversible by construction.** v2.3.0's condition 5 — the provider stays
  configuration, never code — is already structural: `celestia.llm.provider`
  selects, and both Spring AI autoconfigurations build the model around
  `TurnToolCallingManager`. Moving to self-hosted is a property edit.

### The argument against, stated fairly

- **This is a weakening, not a clarification.** v2.2.0 defined a term; v2.3.0
  bounded development. This removes a prohibition from a class of deployment that
  previously had it. That is why the version below is **3.0.0** and not 2.4.0, and
  why this ADR does not amend the constitution itself.
- **It is the exact transition the prohibition exists to stop.** A working preview
  on an API creates pressure to add a second number. Condition 1 answers that and
  is the reason this proposal is defensible at all.
- **It may contradict the product's own story.** If "your birth details never
  leave our infrastructure" is part of what Celestia sells, this breaks it for one
  user today and sets a precedent for tomorrow. Only the owner can weigh that.

### The six conditions

1. **The allow-list holds exactly one number, and it is the owner's own —
   enforced at startup, not promised.** While the provider is a third-party API,
   the application **must refuse to start** if `celestia.entitlement.allow-list`
   holds more or fewer than one entry. This turns the condition from a sentence in
   a document into an invariant: admitting a second person requires either moving
   to a self-hosted model or coming back for another amendment. *(Implementation:
   a startup check beside `EntitlementService`'s existing fail-closed validation,
   asserted in `EntitlementServiceTest`.)*
2. **No other living person's data, ever.** While this permission is in force,
   second-person charts stay unavailable — the owner cannot consent on behalf of
   a friend whose birth details he types in. This happens to be the state today:
   `castChartFor` reports itself unavailable because no geocoder is wired, so
   FR-011's user-facing half is already dormant. **That dormancy becomes a
   condition rather than an accident**, and wiring a geocoder would end this
   permission unless the tool is gated on the provider.
3. **The provider account must not train on the prompts**, retention must be set
   to the minimum the provider offers, and a data-processing agreement must be in
   place — the API equivalent of v2.2.0's three obligations for a rented host, in
   which a provider is a processor and not part of the trust boundary.
4. **It lapses automatically** at the earlier of: a second number being
   allow-listed, or public sign-up opening. Lapsing needs no further decision —
   the deployment rule simply applies again, and a lapsed permission with a
   running service is a defect to fix, not a grace period.
5. **Named here**: the provider, the model and the date, so "what answered the
   ten questions a practitioner judged?" is answerable a year later.
6. **The provider stays configuration, never code** — v2.3.0's condition 5,
   unchanged and already satisfied.

## The amendment this required — applied

Acceptance meant editing the **LLM — in deployment** bullet of Technology
Constraints to carry a bounded exception, and bumping to **v3.0.0**:

> **One bounded exception, and it is the only one.** A deployment whose
> entitlement allow-list contains exactly one number — the developer's own — may
> use a third-party inference API, subject to the six conditions of ADR-0026. The
> exception is enforced at startup rather than trusted: the application refuses to
> run on a third-party API with any other allow-list. It lapses when a second
> number is added or public sign-up opens, whichever is first, and lapsing
> restores this rule with no further decision.

Principle III needs no change: it already says a verdict is tied to its model,
which is what makes running the preview and development on the same model
valuable rather than merely convenient.

`PROJECT_PLAN.md` D9, D10 and the overview would need the same propagation
v2.3.0 received.

## Condition 3 — status

Recorded as it is settled, because "under what terms were those ten questions
answered?" must be answerable later, and because a half-confirmed condition is
easy to mistake for a confirmed one.

| Requirement | Status | Date | Source |
|---|---|---|---|
| Provider does not train on the prompts | **confirmed off** | 2026-11-24 | owner, from the account settings |
| Retention at the provider's minimum | **outstanding** | — | zero data retention is available to API customers **on request** — it is not a toggle, so it has to be asked for and takes longer than the other two |
| Data-processing agreement in place | **outstanding** | — | accepting the published DPA is what makes the provider a processor rather than part of the trust boundary — the same logic v2.2.0 applied to a rented host |

**Condition 3 is therefore not yet satisfied, and SPEC-012 T039a stays open.** One
of three is not a pass: the condition exists to bound what happens to the owner's
birth details at rest and in the provider's hands, and training is only the first
of those. T044, T040 and T041 remain gated — not procedurally, but because once
the data has been sent it has been sent.

**If retention cannot be brought below the standard period** and that is not
acceptable for the owner's own birth details, the honest move is to rent the GPU
VM after all. This ADR was written so that a failing condition 3 falls back to
self-hosting rather than to relaxing the condition.

## Consequences

- **Stage A becomes reachable without procurement.** T044, T040 and T041 can all
  run this week, and T041's verdict is earned on the model that serves the
  preview.
- **ADR-0010 is deferred, not superseded.** The rented GPU VM remains the decided
  answer for public traffic. Nothing about it is withdrawn; its timing moves from
  "before the preview" to "before the allow-list grows".
- **Public launch inherits a migration, not a surprise.** The move to self-hosted
  is a property edit, but SC-001's verdict will have to be re-earned on Llama 4
  Scout before launch — Principle III requires it, and this ADR is the reason to
  schedule it rather than discover it.
- **T045 matters more, not less.** A fast provider hides an unbounded turn. The
  deadline must be a real bound before any latency number is trusted.

## What would make me advise against this

If "self-hosted, your data stays with us" is load-bearing in how Celestia is sold,
take option 1 and rent the machine. The technical argument here is strong and the
privacy argument is sound for an audience of one — but neither survives a
commercial promise to the contrary, and that is not a judgement this document can
make.
