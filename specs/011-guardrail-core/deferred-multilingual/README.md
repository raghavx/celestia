# Deferred: Hindi and Hinglish support for the gate

**Status**: drafted 2026-11-23, deferred the same day. **Not in the build.**

These two files were drafted while planning SPEC-011, when the gate was scoped
to understand English, Hindi and transliterated Hinglish from the start. That
scope was revised to **English only for the first cut**, with multilingual
support moving to post-launch (spec.md Clarifications Q1, revised 2026-11-23).
The work is kept here rather than discarded, because it will be wanted then.

| File | What it is |
|------|------------|
| `corpus-in-scope-hi.tsv` | 30 in-scope cases — one per answerable topic group, in Devanagari Hindi and in transliterated Hinglish |
| `topic-terms-hi.tsv` | ~130 Hindi / Hinglish terms, by topic group, with the eight shortest flagged `SHORT` |

## Read this before trusting any of it

**No native speaker has reviewed a single row.** The questions were written to
read like real WhatsApp messages, but that judgement is exactly what has not
been checked. Specific items already known to be doubtful:

- `hor-hi` — is **होरारी** what a Hindi speaker actually writes, or is
  **प्रश्न कुंडली** the commoner term?
- `life-hil` — **bhagya** was used, but **kismat** is probably commoner in speech.
- `rem-hil` — **ratna** is the written form; **ratan** and **nag** are what
  people say.
- The eight `SHORT` terms (`ank`, `rog`, `ayu`, `nag`, `dhan`, `pati`, `ghar`,
  `bhav`) are only safe with word-boundary matching. `ank` sits inside "bank"
  and "thanks"; `rog` inside "progress"; `ayu` inside "ayurveda".

## What was learned, and still applies

Replaying these against the real toolkit vocabulary produced the findings in
`../research.md` §12. Three of the four are **not** about Hindi and remain live
for the English-only build:

1. topic-group attribution is broken by `1500+Rules` donating all-subject
   vocabulary to `LIFE_MATTERS`;
2. multi-group matches need a deterministic tie-break;
3. "gemstone" matches nothing, because the sheet gives "gems" — compounds and
   synonyms need a small English supplement.

The fourth — Latin-script terms need word-boundary matching — was found through
the Hinglish terms but applies to any Latin-script matching, so it stays
relevant too.

## When this is picked up

Whoever re-enables multilingual support should expect to:

- get a native-speaker pass over every row here before any of it is trusted;
- re-run the replay from `research.md` §12 against the then-current toolkit,
  since the derived English vocabulary will have moved;
- decide whether the deterministic rules are still the right mechanism at all,
  or whether the SPEC-021 classifier handles mixed language better — which was
  the original argument for deferring (research §4, and ADR-0023 option 1).
