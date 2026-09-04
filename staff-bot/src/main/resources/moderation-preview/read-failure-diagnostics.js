'use strict';

/**
 * Keeps the live workspace fail-closed while making the failure class visible to the
 * authenticated staff user. No identifiers, provider responses, URLs, or credentials
 * are exposed; only the already-public API status class is rendered.
 */
async function loadSession() {
  try {
    const sessionResponse = await fetch('/api/session', {headers:{Accept:'application/json'}});
    if (!sessionResponse.ok) throw diagnosticError('session_unavailable');
    state.session = await sessionResponse.json();

    const bootstrapResponse = await requestDirectModerationRead('/api/bootstrap', {headers:{Accept:'application/json'}});
    const payload = await readJsonResponse(bootstrapResponse);
    if (!bootstrapResponse.ok) throw diagnosticResponseError(bootstrapResponse, payload);

    applyLiveBootstrap(payload);
    $('#actorMeta').textContent = payload.actor?.displayName || 'Verified staff session';
  } catch (error) {
    applyDiagnosticFailure(error);
  }
}

function diagnosticResponseError(response, payload) {
  const code = typeof payload?.code === 'string' ? payload.code : '';
  if (response.status === 403 || code === 'forbidden') return diagnosticError('forbidden');
  if (response.status === 503 || code === 'source_unavailable') return diagnosticError('source_unavailable');
  if (response.status === 401 || code === 'unauthorized') return diagnosticError('request_rejected');
  return diagnosticError('read_failed');
}

function diagnosticError(code) {
  const error = new Error('Moderation read failed.');
  error.readDiagnosticCode = code;
  return error;
}

function applyDiagnosticFailure(error) {
  const failure = diagnosticFailure(error?.readDiagnosticCode);
  clearLiveData();
  liveModeration.warning = failure.warning;
  Object.assign(identity, {
    status:failure.status,
    statusDetail:failure.detail,
    linkState:failure.badge
  });
  $('#actorMeta').textContent = failure.actorMeta;
  showToast(failure.toast, true);
  renderAll();
}

function diagnosticFailure(code) {
  if (code === 'forbidden') {
    return {
      status:'Access denied',
      badge:'Staff authority not verified',
      actorMeta:'Access denied · staff authority not verified',
      detail:'Current staff identity is not authorized for this read',
      warning:'Access denied. Verify the Discord-to-Minecraft staff link and current Enthusia staff rank.',
      toast:'Access denied for this staff session.'
    };
  }
  if (code === 'source_unavailable') {
    return {
      status:'Backend unavailable',
      badge:'Read dependency unavailable',
      actorMeta:'Backend unavailable · read source failed',
      detail:'Authoritative database, Paper authority, or Discord read dependency is unavailable',
      warning:'Backend unavailable. A protected moderation read dependency failed.',
      toast:'Moderation backend is temporarily unavailable.'
    };
  }
  if (code === 'session_unavailable' || code === 'request_rejected') {
    return {
      status:'Session unavailable',
      badge:'Reopen from Discord',
      actorMeta:'Session unavailable · reopen from Discord',
      detail:'The signed moderation session is unavailable',
      warning:'Session unavailable. Reopen the moderation preview from Discord.',
      toast:'Session unavailable. Reopen the panel from Discord.'
    };
  }
  return {
    status:'Transport unavailable',
    badge:'Protected request failed',
    actorMeta:'Read transport unavailable',
    detail:'The protected browser read did not complete',
    warning:'Read transport unavailable. The protected request did not complete.',
    toast:'Moderation read transport is temporarily unavailable.'
  };
}
