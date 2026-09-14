'use strict';

const LIVE_LADDER_FAMILIES = new Set(['spam', 'harassment', 'hate', 'advertising', 'cheating']);
const RULES_BASE_URL = 'https://enthusia.info/rules';
const OFFENSE_POLICY_LINKS = Object.freeze({
  spam: Object.freeze({label:'Conduct · Spam and disruption', href:`${RULES_BASE_URL}#conduct`}),
  harassment: Object.freeze({label:'Conduct · Harassment and discrimination', href:`${RULES_BASE_URL}#conduct`}),
  hate: Object.freeze({label:'Conduct · Slurs and hate speech', href:`${RULES_BASE_URL}#conduct`}),
  advertising: Object.freeze({label:'Conduct · Advertising and impersonation', href:`${RULES_BASE_URL}#conduct`}),
  cheating: Object.freeze({label:'Mods and clients · Unfair tools', href:`${RULES_BASE_URL}#mods-clients`}),
  other: Object.freeze({label:'Enforcement and tickets', href:`${RULES_BASE_URL}#enforcement`})
});

function mapHistoryRow(row) {
  const occurred = firstText(row.occurredAt, new Date(0).toISOString());
  const family = optionalText(row.sanctionFamily);
  return {
    id:firstText(row.caseId, row.stableKey), date:occurred.slice(0, 10), time:occurred,
    key:firstText(family, 'other'), offense:firstText(row.reason, row.exactReasonId, row.eventType, 'Moderation event'),
    action:firstText(row.punishmentType, row.eventType, 'Record'), duration:'—',
    staff:firstText(row.actorName, 'System'), status:firstText(row.status, 'Recorded'),
    ladderRelevant:LIVE_LADDER_FAMILIES.has(family), exactReasonId:nullableText(row.exactReasonId),
    sanctionFamily:nullableText(family)
  };
}

function realHistoryTotal() {
  const total = liveModeration.bootstrap?.totalHistoryCount;
  return Number.isSafeInteger(total) && total >= state.history.length ? total : state.history.length;
}

function realRelevantHistoryCount(key) {
  const counts = liveModeration.bootstrap?.relevantHistoryCounts;
  if (!Array.isArray(counts)) return 0;
  const match = counts.find((entry) => entry?.sanctionFamily === key);
  return Number.isSafeInteger(match?.count) && match.count > 0 ? match.count : 0;
}

function recommend(key) {
  const relevant = state.history.filter((row) => row.ladderRelevant && row.key === key);
  const relevantCount = realRelevantHistoryCount(key);
  const base = ['hate', 'cheating'].includes(key) ? 3 : key === 'advertising' ? 2 : 1;
  const step = Math.min(4, Math.max(base, relevantCount + 1));
  const consequence = consequenceFor(key, step);
  return {
    ...consequence, relevant, relevantCount, total:realHistoryTotal(), step,
    explanation:recommendationReason(key, relevantCount, step, base)
  };
}

function relevantHistoryCard(workflow, recommendation) {
  const card = element('section', {className:'card'},
    element('h3', {text:`Relevant history for ${workflow.offense.label}`}),
    summaryList([
      ['Total moderation records', recommendation.total],
      ['Relevant prior cases', recommendation.relevantCount],
      ['Recent matching rows loaded', recommendation.relevant.length],
      ['Ladder step', recommendation.step]
    ]));
  if (recommendation.relevant.length) {
    card.append(element('div', {className:'relevant-history'}, recommendation.relevant.map(historyCompactNode)));
  } else {
    card.append(element('p', {className:'muted small', text:'No recent matching rows are loaded.'}));
  }
  return card;
}

function renderCounts() {
  const counts = {
    messages:baseMessages.length, history:realHistoryTotal(), cases:liveModeration.cases.length,
    notes:liveModeration.notes.length, accounts:liveModeration.accounts.length + 1
  };
  for (const [key, value] of Object.entries(counts)) {
    const target = $(`[data-count="${key}"]`);
    if (target) target.textContent = value;
  }
}

function overviewNode() {
  const recentHistory = state.history.slice(0, 3).map(historyCompactNode);
  return element('div', {}, pageHeading(
    'Player overview', 'Moderation context', 'Live account state, recent history, and current investigation activity.'),
  element('div', {className:'metric-grid'},
    metricNode('Active sanctions', liveModeration.sanctions.length, liveModeration.sanctions.length ? 'Live active records' : 'None'),
    metricNode('Total history', realHistoryTotal(), 'Moderation records returned for this player'),
    metricNode('Evidence selected', state.evidence.size, 'Evidence attached to this simulation')),
  element('div', {className:'two-column'},
    element('section', {className:'card'},
      sectionHeading('Recent moderation history', buttonNode('View all', 'text-button', {viewLink:'history'})),
      recentHistory.length ? recentHistory : emptyState('No moderation history')),
    element('section', {className:'card'},
      sectionHeading('Investigation', buttonNode('Open messages', 'text-button', {viewLink:'messages'})),
      summaryList([
        ['Selected messages', state.selected.size],
        ['Evidence', state.evidence.size],
        ['Simulated deletions', state.deleting.size]
      ]))));
}

function scenarioOffense() {
  return null;
}

function offenseChoiceNode(key, label) {
  const choice = buttonNode('', 'choice-card', {offense:key});
  choice.append(element('strong', {text:label}), element('span', {text:offenseHint(key)}));
  return choice;
}

function offensePolicy(key) {
  return OFFENSE_POLICY_LINKS[key] || OFFENSE_POLICY_LINKS.other;
}

function actionNeedsEvidence(workflow) {
  return workflow?.actual?.action !== 'Warning';
}

function workflowReasonReady(workflow) {
  return String(workflow?.reason || '').trim().length >= 10;
}

function workflowExternalEvidenceReady(workflow) {
  return String(workflow?.externalEvidence || '').trim().length >= 5;
}

function workflowEvidenceReady(workflow) {
  if (!actionNeedsEvidence(workflow)) return true;
  return state.evidence.size > 0 || workflowExternalEvidenceReady(workflow);
}

function workflowApprovalRequired(workflow) {
  if (!workflow?.actual) return false;
  return approvalFor(workflow.actual.action, workflow.duration) !== 'None';
}

function workflowApprovalReady(workflow) {
  return !workflowApprovalRequired(workflow) || workflow?.approvalConfirmed === true;
}

function workflowRestrictionReady(workflow) {
  return workflow?.actual?.action !== 'Restrict' || workflow.restrictionTargets?.size > 0;
}

function workflowReviewStatus(workflow) {
  const reasonReady = workflowReasonReady(workflow);
  const evidenceReady = workflowEvidenceReady(workflow);
  const approvalReady = workflowApprovalReady(workflow);
  const restrictionReady = workflowRestrictionReady(workflow);
  const errors = [];
  if (!reasonReady) errors.push('Add a staff explanation of at least 10 characters.');
  if (!evidenceReady) errors.push('Attach Discord evidence or add an outside-evidence reference.');
  if (!restrictionReady) errors.push('Choose at least one Discord restriction target.');
  if (!approvalReady) errors.push('Verify the required Admin+ approval before confirmation.');
  return {reasonReady, evidenceReady, approvalReady, restrictionReady, errors, ready:errors.length === 0};
}

function workflowCanEnterReview(workflow) {
  const status = workflowReviewStatus(workflow);
  return status.reasonReady && status.evidenceReady && status.restrictionReady;
}
