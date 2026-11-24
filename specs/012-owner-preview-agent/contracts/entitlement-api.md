# Contract: the entitlement seam (SPEC-012)

Small on purpose. This is the interface **SPEC-018 replaces** when plans, quotas
and subscriptions arrive, and the reply path must not change when it does — which
is the whole reason it is a seam rather than an `if` statement in the handler.

## `EntitlementService`

```java
EntitlementDecision check(String senderE164)
```

| Guarantee | Why |
|-----------|-----|
| Consulted **before** the gate, before any chart work, before the model | FR-001. Work done for someone not entitled to it is work that should not have been done |
| Never throws | A configuration fault is a denial, not an exception out of the reply path |
| **Denies when it cannot decide** | FR-003. An empty or unreadable allow-list denies everyone; the alternative turns a configuration slip into an open service |
| Takes a verified E.164 number, nothing else | The authenticated identity is the only input; Constitution V forbids reading identity from message text |

## `EntitlementDecision`

| Value | Meaning |
|-------|---------|
| `OWNER_ALLOW_LIST` | allowed, because the number is on the list |
| `DENIED_NOT_LISTED` | a stranger |
| `DENIED_UNAVAILABLE` | the check could not be completed — the fail-closed case |

The two denials produce the **same** message to the sender and **different**
lines in the log, for the same reason SPEC-011 separated `OFF_TOPIC` from
`UNMATCHED`: the sender must learn nothing, and the operator must be able to tell
a stranger from a broken config.

## What SPEC-018 must be able to do without touching the reply path

- Replace the allow-list with a subscription lookup, keeping `check` as the only
  entry point. **This ends constitution v3.0.0's bounded exception**, whose first
  condition is an allow-list of exactly one number and whose permission lapses
  when sign-up opens. `OwnerOnlyAllowListGuard` will fail at startup at that
  point — deliberately — so SPEC-018 must either move the deployment to a
  self-hosted model or carry its own constitution amendment. It is not a bug to
  route around (ADR-0026 conditions 1 and 4).
- Add quota semantics by returning a denial the handler already knows how to
  render.
- Keep the owner allow-list as a complementary source, which PROJECT_PLAN
  anticipates — so `check` must be able to consult more than one source and
  report which one decided.

That last point is why `EntitlementDecision` carries a basis rather than a
boolean: a future denial for "quota exhausted" needs to be distinguishable from
"not a subscriber", and a boolean would force that distinction into a second
call.

## What this contract does not cover

- **Payment, plans and quota arithmetic** — SPEC-018.
- **Rate limiting and abuse** — SPEC-022. A denial here is about entitlement, not
  about behaviour.
