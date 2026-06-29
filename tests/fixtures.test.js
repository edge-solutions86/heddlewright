import { describe, it, expect } from 'vitest';
import { createDraft } from '../src/draft/draft.js';
import { createLoom } from '../src/loom/registry.js';
import {
  twillRecord,
  twillDraft,
  narrowLoom,
  wideLoom,
  poplinOrder,
  shirtingConstruction,
} from './fixtures.js';

/**
 * The shared fixtures get their own tests because a wrong fixture does not
 * announce itself. It makes some other test fail somewhere else, and the reader
 * of that failure starts by suspecting the module under test.
 */

describe('twillRecord', () => {
  it('threads and treads the counts it was asked for', () => {
    const record = twillRecord({ ends: 16, picks: 12 });
    expect(record.threading.length).toBe(16);
    expect(record.treadling.length).toBe(12);
    expect(record.shafts).toBe(4);
    expect(record.treadles).toBe(4);
  });

  it('builds on more shafts when asked', () => {
    const record = twillRecord({ shafts: 8, raise: 3, ends: 24 });
    expect(record.shafts).toBe(8);
    expect(record.tieup.length).toBe(8);
    expect(record.tieup[0].length).toBe(3);
  });

  it('hands out a fresh record every time', () => {
    const first = twillRecord();
    const second = twillRecord();
    expect(first).not.toBe(second);
    expect(first.threading).not.toBe(second.threading);
    first.threading[0] = 99;
    expect(second.threading[0]).toBe(1);
  });

  it('takes a name for the tests that read one back', () => {
    expect(twillRecord({ name: 'twill' }).name).toBe('twill');
    expect(twillRecord().name).toBe('four shaft twill');
  });
});

describe('twillDraft', () => {
  it('is the record built, so the two agree', () => {
    const draft = twillDraft({ ends: 16, picks: 16 });
    expect(draft.id).toBe(createDraft(twillRecord({ ends: 16, picks: 16 })).id);
    expect(draft.ends).toBe(16);
    expect(draft.picks).toBe(16);
  });
});

describe('the looms', () => {
  it('are both registrable as they stand', () => {
    expect(() => createLoom(narrowLoom())).not.toThrow();
    expect(() => createLoom(wideLoom())).not.toThrow();
  });

  it('differ in the way the tests rely on, which is width', () => {
    expect(narrowLoom().reedWidthCm).toBeLessThan(wideLoom().reedWidthCm);
    expect(narrowLoom().shafts).toBeLessThan(wideLoom().shafts);
  });

  it('takes a running speed for the files that need a slower one', () => {
    expect(wideLoom({ picksMinute: 180 }).picksMinute).toBe(180);
    expect(wideLoom().picksMinute).toBe(200);
  });
});

describe('poplinOrder', () => {
  it('is narrow enough for either loom', () => {
    expect(poplinOrder().widthCm).toBeLessThan(narrowLoom().reedWidthCm);
  });

  it('takes overrides without changing the next caller', () => {
    expect(poplinOrder({ metres: 10 }).metres).toBe(10);
    expect(poplinOrder().metres).toBe(400);
  });
});

describe('shirtingConstruction', () => {
  it('is a real close sett rather than round numbers', () => {
    const construction = shirtingConstruction();
    expect(construction.endsPerCm).toBeGreaterThan(construction.picksPerCm);
    expect(construction.system).toBe('tex');
  });

  it('takes overrides too', () => {
    expect(shirtingConstruction({ picksPerCm: 8 }).picksPerCm).toBe(8);
    expect(shirtingConstruction().picksPerCm).toBe(20);
  });
});
