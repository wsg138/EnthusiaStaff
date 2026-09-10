'use strict';

/** Extends the existing single-page context reader without duplicating UI/context orchestration. */
document.addEventListener('DOMContentLoaded', () => queueMicrotask(installContextReadPagination));

function installContextReadPagination() {
  const singlePageRead = window.fetchContextPage;
  window.fetchContextPage = (channelId, direction, triggerId) =>
    paginatedContextRead(singlePageRead, channelId, direction, triggerId);
}

function paginatedContextRead(singlePageRead, channelId, direction, triggerId) {
  return readContextPage({
    singlePageRead,
    trigger: baseMessages.find((message) => message.id === triggerId),
    channelId,
    direction,
    cursor: triggerId,
    pageNumber: 0,
    messages: []
  });
}

async function readContextPage(request) {
  const page = await request.singlePageRead(request.channelId, request.direction, request.cursor);
  const progress = contextPageProgress(request, page);
  if (progress.complete) return progress.messages;
  return readContextPage(nextContextRequest(request, progress));
}

function contextPageCursor(page, direction) {
  const ordered = page.slice().sort((left, right) => new Date(left.time) - new Date(right.time));
  return ordered.at(CONTEXT_DIRECTIONS[direction].cursorIndex).id;
}
