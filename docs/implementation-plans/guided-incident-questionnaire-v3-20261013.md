# Enthusia Guided Incident Questionnaire (true hybrid) — GIW-1
**2026-10-13 — Proposed, non-live policy and engineering design**

## Correction to PR #581
The merged hybrid GUI is actually a better browser of the old 85 predefined reasons and their ladders. It is not the guided investigation the owner wants.
Existing Policy v2 still makes staff choose a specific offense first, and 77 of its 85 active offense configurations have no incident-attribute questions. Do not mistake v2 for the full solution.

## New primary flow
**Category → location/channel → behavior → branching factual questions → evidence → system-selected violation candidates → proportionate recommendation → optional justified staff adjustment → review/approval → case.**

Staff must not know the exact reason ID. The backend identifies it from their answers. If the facts are insufficient to classify safely, show **Needs Review** instead of inventing an offense.

**Example:**
1. Punish Steve → Chat & Communication.
2. Where? Public chat / Private message / Sign / Book / Voice / Profile-item / Other.
3. What? Spam / Targeted abuse / Hate or slur / Threat or privacy / Advertising / Sexual content / Other.
4. If spam: How extensive? Few repeats / Sustained flooding / Automated or large-scale. Did it continue after a clear warning? Yes / No / Unsure.
5. Evidence? Verified log, screenshots or recording / Not verified / Conflicting. Attach separately; a typed claim is not independent evidence.
6. Assess relevant case history automatically, with relationship weighting, expiry and decay.
7. Show: matched conduct, medium, impact, aggravating and mitigating facts, history contribution, reasoning, confidence and allowable outcomes. Staff reviews and confirms.

A few repetitions without history could produce a warning. A sustained public flood after a warning with recent related history should suggest a more severe mute, even when the underlying conduct family is the same. Sign spam might instead need sign removal and gameplay remedy; a chat mute isn't automatically appropriate. Targeted slurs ask about targeting, context and independent evidence; uncertain/high-risk interpretation goes to review.

## Bounded, variable punishments
No inflexible six-step ladder as the final interface. The proposed engine uses owner-approved **ranges** for type and duration, adjusted by observed factors and related history. A recommendation and available alternatives can be shown, and an authorized staff member can pick a proportionate value **inside policy bounds**, recording why when deviating. Outside bounds requires separate approval; no arbitrary permanent bans. High-sensitivity classes have special evidence/approval thresholds, not a one-number severity formula.

Proposed scoring structure, not deployed numbers:
- baseline seriousness per conduct, not channel alone;
- positive modifiers for demonstrated duration, scale, targeted impact, ignoring warning;
- mitigating factors only when supported (e.g., harmless quotation, immediately corrected);
- related-history score automatically calculated with family weights and decay;
- clamp to owner-approved limits, map to allowed remedy bands and duration ranges;
- lack of evidence never increases severity; ambiguous cases require review.

**Nothing auto-punishes.** Store human-readable reasons for the proposed outcome, not just scores.

## Questionnaire UX
- One clear question per GUI, colored icons with unique material, short non-italic lore, bold important words.
- Only 2–7 answers per question, including Not sure. Ordinary cases should need around 3–6 questions; complex cases can use more.
- Back preserves valid prior answers; changing a parent removes answers to irrelevant follow-ups.
- Accessible vanilla inventory UI for Java and Bedrock. No chat requirement for multiple choice.
- Provide an expert direct-reason fallback as a secondary route, not the default.
- Record location, conduct, answers, evidence references, policy version and actor/target. Permit a safe resume.
- Do not ask staff to decide punishments or guess psychological intent as a substitute for evidence.

## Architecture and authority
- Old v1 85 reason IDs remain stored for historical continuity and as migration/mapping references.
- Reuse Policy v2 HistoryEvaluator, PolicyResolver or rule concepts where useful; never silently enable currently disabled Policy v2.
- Introduce versioned questionnaire, medium/conduct compatibility map, conditional branches and separately versioned proportionate remedy bounds.
- Staff answers produce one or several candidate incident classes, confidence and evidence requirements. Policy-gap cases are review-only, not best-guess enforcement.
- Recheck current target hierarchy, staff rank, case history, verified evidence and policy version before any action.
- Store complete audit: answers, verified sources, rule IDs, calculated modifiers and history, suggested outcome, selected outcome, override explanation and approvals.
- Relevant behavior families can influence each other; one new incident is not blindly counted multiple times for multiple categories.

## Acceptance scenarios
1. Staff reaches precise conduct without first selecting a reason.
2. Chat spam, PM spam, sign spam, harassment, slur, threat, uncertain behavior branch differently.
3. Replacing an early answer removes now-irrelevant answers and their severity modifiers.
4. Short harmless incident vs deliberate persistent incident produces different recommendations.
5. Unknown evidence/uncertain targeting routes to review; cannot justify an automatically harsher sanction.
6. Appropriate remedy for medium: sign incident does not blindly generate a chat mute.
7. Every override remains within policy limits and records an explanation; beyond limits requires approval.
8. Old and new policy engines can be compared in a non-destructive shadow test.
9. All GUI roles and Bedrock navigation tested; no unauthorized sanction path.
10. New sanctions are NOT activated without independently approved policy file, case migration and in-game acceptance.

## Implementation slices
- **GIW-1A** (start now): pure Java branching questionnaire and candidate classifier with deterministic tests. No new punishments.
- **GIW-1B**: polished GUI question-by-question flow, saved draft, back and review. Shadow only.
- **GIW-1C**: calibrated bounded variable remedy engine, history and evidence integration, approval paths.
- **GIW-1D**: synthetic staff-case testing, quality/security reviews, shadow comparison, owner-approved policy cutover.

## Release boundary
Do not modify the merged live hybrid plugin, v1 punishment YAML, v2 active policy, sanction API, case storage or installed Paper JAR in GIW-1A. This is a new separate design/code branch, not a production deployment.
