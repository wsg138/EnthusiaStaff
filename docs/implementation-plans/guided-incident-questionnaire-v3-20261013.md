# Enthusia Guided Incident Questionnaire (true hybrid) — GIW-1
**2026-10-13 — Proposed, non-live policy and engineering design**

## Correction to PR #581
The merged hybrid GUI is actually a better browser of the old 85 predefined reasons and their ladders. It is not the guided investigation the owner wants.
Existing Policy v2 still makes staff choose a specific offense first, and 77 of its 85 active offense configurations have no incident-attribute questions. Do not mistake v2 for the full solution.

## New primary flow
**Category → location/channel → behavior → 1–3 relevant follow-up questions → calculate punishment TYPE + DURATION using incident facts and prior history → show recommendation and editable approved range → confirm/request approval.**

Staff must not know the exact reason ID. The backend identifies it from their answers. If the facts are insufficient to classify safely, show **Needs Review** instead of inventing an offense.

**Example:**
1. Punish Steve → Chat & Communication.
2. Where? Public chat / Private message / Sign / Book / Voice / Profile-item / Other.
3. What? Spam / Targeted abuse / Hate or slur / Threat or privacy / Advertising / Sexual content / Other.
4. If spam: How extensive? Few repeats / Sustained flooding / Automated or large-scale.
5. Did it continue after a warning? **Yes — ignored warning / No — stopped after warning / Not applicable — no warning given / Unknown.** Never treat No as equivalent to Not applicable.
6. System checks relevant recorded history automatically, including relationship weighting, expiry and decay, then **calculates the recommended sanction type and its length**. An exact reason ID is only an internal classification; it is not the final result.
7. Final review shows e.g. "Warning" or "Mute: 2 hours" with explanation, the adjustable owner-approved interval and a submit/request-approval action. Evidence links may be attached or viewed **on the review screen** where available; staff should not be interrupted with a mandatory evidence quiz or asked to search for evidence to calculate an outcome.

A few repetitions with no prior warning and no history could produce a warning. A sustained public flood after a warning with recent related history should suggest a more severe mute, even when the underlying conduct family is the same. Sign spam might instead need sign removal and gameplay remedy; a chat mute isn't automatically appropriate. Targeted slurs ask about targeting, context and independent evidence; uncertain/high-risk interpretation goes to review.

## Bounded, variable punishments
No inflexible six-step ladder as the final interface. The proposed engine uses owner-approved **ranges** for type and duration, adjusted by observed factors and related history. A recommendation and available alternatives can be shown, and an authorized staff member can pick a proportionate value **inside policy bounds**, recording why when deviating. Outside bounds requires separate approval; no arbitrary permanent bans. High-sensitivity classes have special evidence/approval thresholds, not a one-number severity formula.

Proposed scoring structure, not deployed numbers:
- baseline seriousness per conduct, not channel alone;
- positive modifiers for demonstrated duration, scale, targeted impact, ignoring warning;
- mitigating factors only when supported (e.g., harmless quotation, immediately corrected);
- related-history score automatically calculated with family weights and decay;
- clamp to owner-approved limits, map to allowed remedy bands and duration ranges;
- evidence is a separate optional review/verification feature, not a punishment severity question. If the underlying facts are unreliable or a high-risk category requires verification, route to review without automatically increasing severity.

**Nothing auto-punishes.** Store human-readable reasons for the proposed outcome, not just scores.

## Questionnaire UX
- One clear question per GUI, colored icons with unique material, short non-italic lore, bold important words.
- Only 2–7 answers per question. Aim for **3–5 taps** in routine cases; complicated or unclear cases can expand. Include Not applicable whenever the premise of the question may not have occurred, and distinguish it from No and Unknown.
- Back preserves valid prior answers; changing a parent removes answers to irrelevant follow-ups.
- Accessible vanilla inventory UI for Java and Bedrock. No chat requirement for multiple choice.
- Provide an expert direct-reason fallback as a secondary route, not the default.
- Record location, conduct, answers, optional evidence references, policy version and actor/target. Permit a safe resume.
- Do not ask staff to decide punishments or guess psychological intent as a substitute for evidence.

## Architecture and authority
- Old v1 85 reason IDs remain stored for historical continuity and as migration/mapping references.
- Reuse Policy v2 HistoryEvaluator, PolicyResolver or rule concepts where useful; never silently enable currently disabled Policy v2.
- Introduce versioned questionnaire, medium/conduct compatibility map, conditional branches and separately versioned proportionate remedy bounds.
- Staff answers produce one or several internal incident classifications; the outcome screen **prioritizes recommended sanction type and duration**, not the offense taxonomy. Policy-gap cases are review-only, not best-guess enforcement.
- Recheck current target hierarchy, staff rank, case history, verified evidence and policy version before any action.
- Store complete audit: answers, verified sources, rule IDs, calculated modifiers and history, suggested outcome, selected outcome, override explanation and approvals.
- Relevant behavior families can influence each other; one new incident is not blindly counted multiple times for multiple categories.

## Acceptance scenarios
1. Staff reaches precise conduct without first selecting a reason.
2. Chat spam, PM spam, sign spam, harassment, slur, threat, uncertain behavior branch differently.
3. Replacing an early answer removes now-irrelevant answers and their severity modifiers.
4. Short harmless incident vs deliberate persistent incident produces different recommendations.
5. An unanswered/not-applicable warning does not count as defiance. Unknown context and serious evidence-sensitive allegations route to review instead of a harsher automatic result.
6. Appropriate remedy for medium: sign incident does not blindly generate a chat mute.
7. Every override remains within policy limits and records an explanation; beyond limits requires approval.
8. Old and new policy engines can be compared in a non-destructive shadow test.
9. All GUI roles and Bedrock navigation tested; no unauthorized sanction path.
10. New sanctions are NOT activated without independently approved policy file, case migration and in-game acceptance.

## Implementation slices
- **GIW-1A** (current): pure Java branching questionnaire, candidate classifier, and test-only configurable warning/mute duration recommendation preview. No real punishments.
- **GIW-1B**: polished GUI question-by-question flow, saved draft, back and review. Shadow only.
- **GIW-1C**: approved configurable sanction rules, real case-history integration, optional evidence lookup, adjustable duration bounds and approval paths.
- **GIW-1D**: synthetic staff-case testing, quality/security reviews, shadow comparison, owner-approved policy cutover.

## Release boundary
Do not modify the merged live hybrid plugin, v1 punishment YAML, v2 active policy, sanction API, case storage or installed Paper JAR in GIW-1A. This is a new separate design/code branch, not a production deployment.

## Revised interaction standard (owner feedback, 2026-10-13)

The first wizard draft was still over-structured. The target is **incident-based
decision support**, not another offense tree disguised as a survey.

- **Short default route:** category, where, what happened, how serious/how much,
  any relevant contextual qualifier (e.g. ignored a warning). Most normal
  cases should finish in about 3–5 taps.
- **Adaptive depth:** after selecting spam, do not ask harassment or slur
  questions; after selecting a threat, do not ask flood duration. Ask for
  extra context only if the answer can materially affect the outcome.
- **Warning question semantics:** Yes = a warning occurred and was ignored;
  No = a warning occurred and conduct stopped; Not applicable = nobody
  warned the player; Unknown = the moderator cannot verify whether a
  warning was given. Not applicable has no aggravating modifier.
- **Automatic context:** system fetches relevant recent punishment history,
  including related categories and decay, without asking the moderator to
  re-enter facts the server already records.
- **Primary output:** **Warning / Mute / Ban / Needs Review**, plus **specific
  duration**, a concise reasoning breakdown and optional owner-approved
  adjustment range. The same conduct may yield a warning or a 1h/3h/etc.
  mute depending on fact pattern and weighted history. The duration can
  vary continuously within limits; don't just choose step 1 to step 6.
- **Evidence:** logs/cases can be shown as optional reviewer context or
  required separately by specialized high-risk policy. Evidence is not
  step 6 and does not affect punishment length by itself.
- **Staff judgment:** final authorized staff member chooses/adjusts within
  approved policy bounds and sees exactly what will happen; a rationale is
  required for significant adjustments, and escalation beyond bounds
  requires further approval. Never punish from questionnaire responses alone.
- **Review-only fallback:** no supported classification, high-risk or
  disputed facts cannot silently turn into a chosen punishment. Do not
  invent a ban simply because a high score was reached.

GIW-1A contains a **read-only** variable duration preview for low-risk spam
with test-injected bounds and a hypothetical history input. Those numbers
are demonstrative, not live server punishment thresholds or approved ranges.
The real enforcement mapper, evidence verification, history source, override
auth and GUI still need separate implementation and acceptance.


## Engineering review status — 2026-10-14

This document originally outlined the design. The implementation in PR #597
now contains the Paper inventory questionnaire, dynamic questions across six
categories, live v1 reason mapping, bounded type/duration computation using
authoritative related-history escalation, permission/approval routing through
the existing draft workflow, staff adjustment choices, and audit explanation.

**Deployment guard:** \`guided-punishment.enabled\` in the Paper \`config.yml\`
defaults to **false**. Existing \`/punish\` category browsing remains
authoritative if the setting is absent or disabled. The new route is activated
only for \`/punish\` when explicitly enabled. Existing sanction commands and
policies remain unchanged.

**Safety boundaries:**
- The questionnaire does not itself apply punishment; server-side policy
  evaluation, permission checks, draft confirmation and approval remain.
- Configured permanent sanctions, mixed remedies and type transitions other
  than WARNING to MUTE cannot be reached solely from the generic severity
  adjustment. Cases needing them use human review.
- Unknown critical facts and high-sensitivity allegations fail closed to
  the existing expert review flow.
- An uncertain or absent prior warning does not count as defiance.
- Custom duration is bounded between two configured temporary sanctions of
  the same type and requires normal authorization/approval.
- Updating final-review notes or visibility preserves the selected sanction
  override. Revalidation at confirmation guards changed history or policy.

**Remaining release gates:** current GitHub CI + Codacy review, Java/Bedrock
in-game GUI acceptance, sample-policy calibration with staff cases, and a
staged restart/rollback plan. No automatic merge or live deployment until
those gates are satisfied.

This changes the original GIW-1A-only prototype status. The two obsolete
\`GuidedChatInterview\` and \`GuidedRemedyPreview\` prototype classes/tests
have been retired in favor of the integrated questionnaire/resolver.
