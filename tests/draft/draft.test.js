import { describe, it, expect } from 'vitest';
import { DraftError, InvalidArgument } from '../../src/util/errors.js';
import { straightDraw } from '../../src/draft/threading.js';
import { twillTieup } from '../../src/draft/tieup.js';
import { straightTreadling } from '../../src/draft/treadling.js';
import {
  createDraft,
  draftId,
  draftSummary,
  inspectDraft,
  assertDraftSound,
  reviseDraft,
  sameDraft,
} from '../../src/draft/draft.js';

import { twillRecord } from '../fixtures.js';

const TWILL_INPUT = twillRecord();

describe('createDraft', () => {
  it('fills in the counts a caller does not have to repeat', () => {
    const draft = createDraft(TWILL_INPUT);
    expect(draft.ends).toBe(8);
    expect(draft.picks).toBe(8);
    expect(draft.shed).toBe('rising');
    expect(draft.notes).toBe('');
  });

  it('normalises the treadling into pick lists', () => {
    const draft = createDraft({ ...TWILL_INPUT, treadling: [1, 2, 3, 4] });
    expect(draft.treadling).toEqual([[1], [2], [3], [4]]);
  });

  it('defaults the name rather than refusing an unnamed draft', () => {
    const draft = createDraft({ ...TWILL_INPUT, name: undefined });
    expect(draft.name).toBe('untitled');
  });

  it('freezes the result', () => {
    const draft = createDraft(TWILL_INPUT);
    expect(Object.isFrozen(draft)).toBe(true);
  });

  it('copies the threading, so the caller cannot edit it afterwards', () => {
    const threading = straightDraw(4, 8);
    const draft = createDraft({ ...TWILL_INPUT, threading });
    threading[0] = 3;
    expect(draft.threading[0]).toBe(1);
  });

  it('refuses a tie-up that does not match the treadle count', () => {
    expect(() => createDraft({ ...TWILL_INPUT, treadles: 6 })).toThrow(DraftError);
  });

  it('refuses a missing shaft count and a shed it does not know', () => {
    expect(() => createDraft({ ...TWILL_INPUT, shafts: undefined })).toThrow(InvalidArgument);
    expect(() => createDraft({ ...TWILL_INPUT, shed: 'half' })).toThrow(/rising, sinking/);
  });
});

describe('draftId', () => {
  it('is stable for the same draft', () => {
    expect(draftId(createDraft(TWILL_INPUT))).toBe(draftId(createDraft(TWILL_INPUT)));
  });

  it('ignores the name and the notes, which do not change the cloth', () => {
    const a = createDraft(TWILL_INPUT);
    const b = createDraft({ ...TWILL_INPUT, name: 'something else', notes: 'for the fair' });
    expect(a.id).toBe(b.id);
  });

  it('changes when the shed changes', () => {
    const a = createDraft(TWILL_INPUT);
    const b = createDraft({ ...TWILL_INPUT, shed: 'sinking' });
    expect(a.id).not.toBe(b.id);
  });

  it('reads as a draft prefix and eight hex digits', () => {
    expect(createDraft(TWILL_INPUT).id).toMatch(/^draft-[0-9a-f]{8}$/);
  });
});

describe('draftSummary', () => {
  it('reports the shape and the face', () => {
    const summary = draftSummary(createDraft(TWILL_INPUT));
    expect(summary).toMatchObject({
      name: 'four shaft twill',
      shafts: 4,
      treadles: 4,
      ends: 8,
      picks: 8,
      shaftsUsed: 4,
      warpFaceRatio: 0.5,
    });
  });

  it('counts only the shafts the threading uses', () => {
    const draft = createDraft({
      ...TWILL_INPUT,
      threading: [1, 2, 1, 2, 1, 2, 1, 2],
    });
    expect(draftSummary(draft).shaftsUsed).toBe(2);
  });
});

describe('inspectDraft', () => {
  it('is quiet on a sound draft', () => {
    expect(inspectDraft(createDraft(TWILL_INPUT))).toEqual([]);
  });

  it('reports a shaft with nothing threaded on it', () => {
    const draft = createDraft({
      ...TWILL_INPUT,
      threading: [1, 2, 3, 1, 2, 3, 1, 2],
    });
    const codes = inspectDraft(draft).map((problem) => problem.code);
    expect(codes).toContain('idle_shaft');
  });

  it('reports an untied and an unpressed treadle separately', () => {
    const draft = createDraft({
      ...TWILL_INPUT,
      tieup: [[1, 2], [2, 3], [3, 4], []],
      treadling: straightTreadling(3, 6),
    });
    const codes = inspectDraft(draft).map((problem) => problem.code);
    expect(codes).toContain('untied_treadle');
    expect(codes).toContain('unused_treadle');
  });

  it('reports an end that never binds', () => {
    const draft = createDraft({
      name: 'floating end',
      shafts: 3,
      treadles: 3,
      threading: [1, 2, 3],
      tieup: [[1], [2], [1, 2]],
      treadling: [1, 2, 3],
    });
    const problem = inspectDraft(draft).find((entry) => entry.code === 'unwoven_end');
    expect(problem.detail).toEqual({ end: 3, state: 'always down' });
  });
});

describe('assertDraftSound', () => {
  it('returns the draft when nothing is wrong', () => {
    const draft = createDraft(TWILL_INPUT);
    expect(assertDraftSound(draft)).toBe(draft);
  });

  it('throws with the whole problem list attached', () => {
    const draft = createDraft({
      ...TWILL_INPUT,
      threading: [1, 2, 3, 1, 2, 3, 1, 2],
    });
    try {
      assertDraftSound(draft);
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(DraftError);
      expect(err.detail.problems.length).toBeGreaterThan(0);
    }
  });
});

describe('reviseDraft', () => {
  it('replaces a field and renormalises the rest', () => {
    const draft = createDraft(TWILL_INPUT);
    const revised = reviseDraft(draft, { name: 'renamed' });
    expect(revised.name).toBe('renamed');
    expect(revised.id).toBe(draft.id);
  });

  it('gives a new id when the cloth changes', () => {
    const draft = createDraft(TWILL_INPUT);
    const revised = reviseDraft(draft, { tieup: twillTieup(4, 3) });
    expect(revised.id).not.toBe(draft.id);
  });

  it('checks the change like any other draft input', () => {
    const draft = createDraft(TWILL_INPUT);
    expect(() => reviseDraft(draft, { shafts: 0 })).toThrow(InvalidArgument);
  });
});

describe('sameDraft', () => {
  it('is true for two drafts weaving the same cloth', () => {
    expect(sameDraft(createDraft(TWILL_INPUT), createDraft({ ...TWILL_INPUT, name: 'x' }))).toBe(
      true,
    );
  });

  it('is false once a treadling differs', () => {
    const other = createDraft({ ...TWILL_INPUT, treadling: straightTreadling(4, 4) });
    expect(sameDraft(createDraft(TWILL_INPUT), other)).toBe(false);
  });
});
