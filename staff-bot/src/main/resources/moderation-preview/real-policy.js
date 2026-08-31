'use strict';

const LIVE_LADDER_FAMILIES = new Set(['spam', 'harassment', 'hate', 'advertising', 'cheating']);

function mapHistoryRow(row) {
  const occurred = row.occurredAt || new Date(0).toISOString();
  const family = typeof row.sanctionFamily === 'string' ? row.sanctionFamily : '';
  return {
    id: row.caseId || row.stableKey,
    date: occurred.slice(0, 10),
    key: family || 'other',
    offense: row.reason || row.exactReasonId || row.eventType || 'Moderation event',
    action: row.punishmentType || row.eventType || 'Record',
    duration: '—',
    staff: row.actorName || 'System',
    status: row.status || 'Recorded',
    ladderRelevant: LIVE_LADDER_FAMILIES.has(family),
    exactReasonId: row.exactReasonId || null,
    sanctionFamily: family || null
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
    ...consequence,
    relevant,
    relevantCount,
    total: realHistoryTotal(),
    step,
    explanation: recommendationReason(key, relevantCount, step, base)
  };
}

function relevantHistoryCard(workflow, recommendation) {
  const card = element('section', {className: 'card'},
    element('h3', {text: `Relevant history for ${workflow.offense.label}`}),
    summaryList([
      ['Total moderation records', recommendation.total],
      ['Relevant prior cases', recommendation.relevantCount],
      ['Recent matching rows loaded', recommendation.relevant.length],
      ['Ladder step', recommendation.step]
    ]));
  if (recommendation.relevant.length) {
    card.append(element('div', {className: 'relevant-history'}, recommendation.relevant.map(historyCompactNode)));
  } else {
    card.append(element('p', {className: 'muted small', text: 'No recent matching rows are loaded.'}));
  }
  return card;
}

function renderCounts() {
  const counts = {
    messages: baseMessages.length,
    history: realHistoryTotal(),
    cases: liveModeration.cases.length,
    notes: liveModeration.notes.length,
    accounts: liveModeration.accounts.length + 1
  };
  for (const [key, value] of Object.entries(counts)) {
    const target = $(`[data-count="${key}"]`);
    if (target) target.textContent = value;
  }
}

function overviewNode() {
  const recentHistory = state.history.slice(0, 3).map(historyCompactNode);
  return element('div', {}, pageHeading(
    'Player overview', 'Moderation context',
    'Authoritative account state, recent history and current investigation activity.'),
  element('div', {className:'metric-grid'},
    metricNode('Active sanctions', liveModeration.sanctions.length,
      liveModeration.sanctions.length ? 'Authoritative active records' : 'None'),
    metricNode('Total history', realHistoryTotal(), 'Authoritative moderation records'),
    metricNode('Evidence selected', state.evidence.size, 'Messages attached to this simulated review')),
  element('div', {className:'two-column'},
    element('section', {className:'card'},
      sectionHeading('Recent moderation history', buttonNode('View all', 'text-button', {viewLink:'history'})),
      recentHistory.length ? recentHistory : emptyState('No moderation history')),
    element('section', {className:'card'},
      sectionHeading('Investigation', buttonNode('Open messages', 'text-button', {viewLink:'messages'})),
      summaryList([
        ['Selected messages', state.selected.size],
        ['Evidence', state.evidence.size],
        ['Delete on confirm', state.deleting.size]
      ]),
      buttonNode('Issue Punishment','button primary full',{punish:''}))));
}

function scenarioOffense() {
  return null;
}

function offenseChoiceNode(key, label) {
  const choice = buttonNode('', 'choice-card', {offense:key});
  choice.append(element('strong', {text:label}), element('span', {text:offenseHint(key)}));
  return choice;
}
