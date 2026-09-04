'use strict';

/** Replaces the legacy sample scenario with a truthful neutral state while live reads load. */
function applyScenario() {
  state.scenario = 'live';
  state.selected.clear();
  state.evidence.clear();
  state.violating.clear();
  state.deleting.clear();
  state.anchor = null;
  state.contextId = null;
  state.history = [];
  state.search = '';
  state.channel = 'all';
  state.date = 'all';
  state.author = '';
  state.selectedOnly = false;
  state.evidenceRevision++;
  state.workflow = null;
  liveModeration.bootstrap = null;
  liveModeration.cases = [];
  liveModeration.notes = [];
  liveModeration.sanctions = [];
  liveModeration.accounts = [];
  liveModeration.channels = [];
  liveModeration.warning = 'Loading moderation data…';
  baseMessages.splice(0);
  RESTRICTION_TARGETS.splice(0);
  Object.assign(identity, {
    displayName:'Loading moderation data…', username:'loading', discordId:'Loading…', minecraft:'Loading…',
    minecraftUuid:'Loading…', alts:[], status:'Loading', statusDetail:'Waiting for authoritative read data',
    avatarUrl:'', linkState:'Loading'
  });
  renderAll();
}
