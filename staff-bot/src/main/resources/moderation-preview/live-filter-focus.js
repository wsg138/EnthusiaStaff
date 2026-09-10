'use strict';

const FILTER_FOCUS_IDS = new Set(['messageSearch', 'authorFilter']);

function preserveFilterFocus(event) {
  const target = event.target;
  if (!(target instanceof HTMLInputElement) || !FILTER_FOCUS_IDS.has(target.id)) return;
  const selection = {
    start:target.selectionStart,
    end:target.selectionEnd,
    direction:target.selectionDirection
  };
  queueMicrotask(() => restoreFilterFocus(target.id, selection));
}

function restoreFilterFocus(id, selection) {
  const replacement = document.getElementById(id);
  if (!(replacement instanceof HTMLInputElement)) return;
  replacement.focus({preventScroll:true});
  if (selection.start === null || selection.end === null) return;
  replacement.setSelectionRange(selection.start, selection.end, selection.direction || 'none');
}

document.addEventListener('input', preserveFilterFocus, true);
