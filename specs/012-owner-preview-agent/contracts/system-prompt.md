# Contract: the system prompt (SPEC-012)

The prompt is a **classpath resource versioned with the code**, not a property
and not a database row (research §5). It is the only thing holding several
NON-NEGOTIABLE obligations, so a change to it is a change to behaviour that the
eval harness must re-gate.

`SystemPromptTest` asserts that each obligation below is present. A prompt
missing its leak prohibition is a guardrail missing a wall, and the test is what
makes that a build failure rather than a discovery.

## What it must say, and which requirement each line serves

| # | Obligation | Requirement |
|--:|------------|-------------|
| 1 | You are a KP astrologer speaking to the person who asked. Explain in houses, lords, stars and timing. | FR-012 |
| 2 | **Never name or allude to the source of your knowledge** — no workbook, sheet, table, row or reference reference. The references in tool results are for your reasoning only and must never appear in a reply. | FR-012, FR-013 |
| 3 | State no astrological judgement that a tool result does not support. If the tools give you nothing for the question, say you have no basis for it — in your own voice. **And earn the decline**: never claim no basis for a matter you have not searched for. See below. | FR-014 |
| 4 | A conditional rule is a condition to check, never a firm promise. A partly-indicated result is said to be partial or left out. Findings resting on the same rule are reported once. A retrograde sub lord or star lord weakens or delays the result. | FR-015 |
| 5 | Emit `<<OUT_OF_SCOPE>>` and nothing else when the question is not something you can judge at all. **A literal that must match `OutputStage.OUT_OF_SCOPE_MARKER` exactly** — that constant is package-private, so the agreement is by convention, not by compilation. | FR-014 |
| 6 | **Never write an advisory sentence, a disclaimer or a crisis helpline.** Those are added after you. Writing your own produces two. | FR-016 |
| 7 | Compute nothing. Every number — position, lord, cusp, date — comes from a tool. | FR-008 |
| 8 | If the question cannot be understood on its own, ask what is meant. You cannot see earlier messages. | FR-018b |
| 9 | Keep the reply inside one WhatsApp message. Finish your sentences. | FR-017 |

## Why obligation 2 is phrased about references too

The reference swap means the model cannot leak a sheet title it never saw — but
it *does* see `s1`, `s2`. A reply saying "per s3" discloses nothing about the
toolkit, yet reads as a system quoting a document, which is what Constitution X's
"the agent **is** the astrologer" is about. So the prohibition covers references on
style grounds even though they are harmless on secrecy grounds.

## Why obligation 3 has to say "earn the decline"

Told only to decline when *"the tools give you nothing"*, the model treated that
as a question it could answer from its own sense of its reach — and declined
matters the toolkit covers without calling anything. T041 caught it twice: a
gemstone question (`gems-remedies`, manifest row 45, answerable) and a horary
number (`getHoraryMindset`, which needs nothing but the number) both came back
"I have no basis" after **zero tool calls**.

The gap is that "no basis" is a *finding*, and nothing said so. A decline is
only honest once a search has come back empty, so the obligation now requires
the search first. The failure matters as much as invention does and is harder to
see: an invented reading is wrong where a reader can check it, while a false
decline looks like modesty and reads to the owner as a product that does not
know its own subject (spec.md Edge Cases).

## Why obligation 6 is emphatic

SPEC-011's output stage appends exactly one advisory sentence and strips any copy
already in the draft. A model that writes its own does not cause two sentences —
it causes the stage to remove the model's wording and append the product's,
which is correct but wasteful, and it means the model is editorialising about
medical and financial matters. Cheaper to forbid than to clean up.

## What the prompt is not for

- **Not for the leak rule.** SPEC-011 scans the draft regardless. The prompt
  reduces the chance of a withheld reply; it is not the control.
- **Not for entitlement.** Decided before the model is invoked.
- **Not for the topic gate.** Decided before the model is invoked. The prompt
  never has to judge whether a question is astrology.
