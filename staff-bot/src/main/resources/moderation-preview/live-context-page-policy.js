'use strict';

const CONTEXT_WINDOW_MS = 120_000;
const CONTEXT_PAGE_SIZE = 50;
const MAX_CONTEXT_PAGES_PER_DIRECTION = 4;
const CONTEXT_DIRECTIONS = Object.freeze({
  before:Object.freeze({sign:-1, cursorIndex:0}),
  after:Object.freeze({sign:1, cursorIndex:-1})
});

function contextPageProgress(request, page) {
  const messages = request.messages.concat(page.filter((message) => message.channelId === request.channelId));
  if (contextReadComplete(request, page)) return {complete:true, messages};
  if (request.pageNumber + 1 >= MAX_CONTEXT_PAGES_PER_DIRECTION) {
    throw new Error('Discord context is too dense to display safely within the two-minute window.');
  }
  const cursor = contextPageCursor(page, request.direction);
  if (cursor === request.cursor) throw new Error('Discord context pagination did not advance.');
  return {complete:false, messages, cursor};
}

function contextReadComplete(request, page) {
  if (page.length < CONTEXT_PAGE_SIZE) return true;
  const policy = CONTEXT_DIRECTIONS[request.direction];
  const triggerTime = new Date(request.trigger.time).getTime();
  const offsets = page.map((message) => policy.sign * (new Date(message.time).getTime() - triggerTime));
  return Math.max(...offsets) >= CONTEXT_WINDOW_MS;
}

function nextContextRequest(request, progress) {
  return {
    ...request,
    cursor:progress.cursor,
    pageNumber:request.pageNumber + 1,
    messages:progress.messages
  };
}
