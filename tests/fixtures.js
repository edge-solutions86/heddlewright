import { straightDraw } from '../src/draft/threading.js';
import { twillTieup } from '../src/draft/tieup.js';
import { straightTreadling } from '../src/draft/treadling.js';
import { createDraft } from '../src/draft/draft.js';

/**
 * The cloths, looms and orders the tests share.
 *
 * The four shaft twill had been written out longhand in nine test files, and
 * they had drifted: some threaded eight ends and some sixteen, so two files
 * that read a repeat count off "the same" draft were measuring different
 * pieces. The looms had drifted the same way. They come from here now.
 *
 * Everything is a function and not a constant. The drafts are frozen and would
 * be safe to share, but the plain records are not, and one test amending a
 * shared record to make its own case would silently change every test that read
 * it afterwards.
 */

/** The plain record for a warp faced twill, of whatever size is wanted. */
export function twillRecord({ shafts = 4, ends = 8, picks = 8, raise = 2, name } = {}) {
  return {
    name: name ?? 'four shaft twill',
    shafts,
    treadles: shafts,
    threading: straightDraw(shafts, ends),
    tieup: twillTieup(shafts, raise),
    treadling: straightTreadling(shafts, picks),
  };
}

/** The same, built into a draft. */
export function twillDraft(options) {
  return createDraft(twillRecord(options));
}

/** A narrow eight shaft dobby, the loom most of the shop floor tests load. */
export function narrowLoom({ picksMinute = 200 } = {}) {
  return {
    reference: 'N1',
    shedding: 'dobby',
    reedWidthCm: 120,
    shafts: 8,
    picksMinute,
  };
}

/** A broad sixteen shaft dobby, for the styles the narrow one cannot take. */
export function wideLoom({ picksMinute = 200 } = {}) {
  return {
    reference: 'W1',
    shedding: 'dobby',
    reedWidthCm: 260,
    shafts: 16,
    picksMinute,
  };
}

/** A shirting order, priced and dated. */
export function poplinOrder(overrides = {}) {
  return {
    reference: 'SO-1',
    customer: 'Ravensward',
    style: 'poplin',
    metres: 400,
    widthCm: 100,
    pricePerMetre: 5,
    wantedBy: '2025-10-01',
    ...overrides,
  };
}

/** A construction for the weight, sett and costing calculations. */
export function shirtingConstruction(overrides = {}) {
  return {
    endsPerCm: 24,
    picksPerCm: 20,
    warpCount: 20,
    weftCount: 20,
    system: 'tex',
    ...overrides,
  };
}
