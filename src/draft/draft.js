import { DraftError } from '../util/errors.js';
import { nonEmptyString, oneOf, plainObject, positiveInteger } from '../util/check.js';
import { contentId } from '../util/ids.js';
import { canonicalStringify } from '../util/json.js';
import { assertThreading, shaftsUsed, idleShafts } from './threading.js';
import { SHED_TYPES, normaliseTieup, untiedTreadles } from './tieup.js';
import { normaliseTreadling, unusedTreadles } from './treadling.js';
import { drawdownFromDraft, warpFaceRatio, unwovenEnds, unwovenPicks } from './drawdown.js';

/**
 * A draft is the whole instruction for a piece of cloth on a treadle loom:
 * threading, tie-up, treadling, and which way the shed opens. Building one
 * normalises every part, so nothing downstream has to guess at a shorthand.
 */

/** Build a normalised, frozen draft, or throw naming what is wrong with it. */
export function createDraft(input) {
  plainObject(input, 'draft');
  const name = nonEmptyString(input.name ?? 'untitled', 'name');
  const shafts = positiveInteger(input.shafts, 'shafts');
  const treadles = positiveInteger(input.treadles, 'treadles');
  const shed = oneOf(input.shed ?? 'rising', 'shed', SHED_TYPES);
  const threading = [...assertThreading(input.threading, shafts)];
  const tieup = normaliseTieup(input.tieup, shafts);
  const treadling = normaliseTreadling(input.treadling, treadles);

  if (tieup.length !== treadles) {
    throw new DraftError('the tie-up does not have one entry per treadle', {
      treadles,
      tied: tieup.length,
    });
  }

  const draft = {
    name,
    shafts,
    treadles,
    shed,
    threading,
    tieup,
    treadling,
    ends: threading.length,
    picks: treadling.length,
    notes: input.notes ?? '',
  };
  draft.id = draftId(draft);
  return Object.freeze(draft);
}

/** A content-derived id, stable across machines and key order. */
export function draftId(draft) {
  const { shafts, treadles, shed, threading, tieup, treadling } = draft;
  return contentId(
    'draft',
    canonicalStringify({ shafts, treadles, shed, threading, tieup, treadling }),
  );
}

/** Everything a review wants to know without opening the drawdown. */
export function draftSummary(draft) {
  const cloth = drawdownFromDraft(draft);
  return {
    id: draft.id,
    name: draft.name,
    shafts: draft.shafts,
    treadles: draft.treadles,
    ends: draft.ends,
    picks: draft.picks,
    shed: draft.shed,
    shaftsUsed: shaftsUsed(draft.threading),
    warpFaceRatio: warpFaceRatio(cloth),
  };
}

/**
 * Every problem a draft can have that does not stop it being a draft: idle
 * shafts, spare treadles, threads that never bind. Returned as a list of
 * `{ code, message, detail }` so a report can group them.
 */
export function inspectDraft(draft) {
  const problems = [];
  for (const shaft of idleShafts(draft.threading, draft.shafts)) {
    problems.push({
      code: 'idle_shaft',
      message: `shaft ${shaft} carries no ends`,
      detail: { shaft },
    });
  }
  for (const treadle of untiedTreadles(draft.tieup, draft.shafts)) {
    problems.push({
      code: 'untied_treadle',
      message: `treadle ${treadle} lifts nothing`,
      detail: { treadle },
    });
  }
  for (const treadle of unusedTreadles(draft.treadling, draft.treadles)) {
    problems.push({
      code: 'unused_treadle',
      message: `treadle ${treadle} is never pressed`,
      detail: { treadle },
    });
  }
  const cloth = drawdownFromDraft(draft);
  for (const { end, state } of unwovenEnds(cloth)) {
    problems.push({
      code: 'unwoven_end',
      message: `end ${end} is ${state} through the repeat`,
      detail: { end, state },
    });
  }
  for (const { pick, state } of unwovenPicks(cloth)) {
    problems.push({
      code: 'unwoven_pick',
      message: `pick ${pick} is ${state}`,
      detail: { pick, state },
    });
  }
  return problems;
}

/** Throw on the first problem, for callers that want a draft or nothing. */
export function assertDraftSound(draft) {
  const problems = inspectDraft(draft);
  if (problems.length > 0) {
    throw new DraftError(problems[0].message, { problems });
  }
  return draft;
}

/** A copy with some fields replaced, renormalised and given a fresh id. */
export function reviseDraft(draft, changes) {
  plainObject(changes, 'changes');
  return createDraft({ ...draft, ...changes });
}

/** Two drafts weave the same cloth when their ids agree. */
export function sameDraft(a, b) {
  return draftId(a) === draftId(b);
}
