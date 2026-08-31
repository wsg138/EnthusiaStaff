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

function scenarioOffense() {
  return null;
}

function offenseChoiceNode(key, label) {
  const choice = buttonNode('', 'choice-card', {offense:key});
  choice.append(element('strong', {text:label}), element('span', {text:offenseHint(key)}));
  return choice;
}
