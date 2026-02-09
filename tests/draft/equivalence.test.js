import { describe, it, expect } from 'vitest';
import { createDraft } from '../../src/draft/draft.js';
import { straightDraw } from '../../src/draft/threading.js';
import { twillTieup } from '../../src/draft/tieup.js';
import { straightTreadling } from '../../src/draft/treadling.js';
import {
  clothKey,
  sameCloth,
  sameWriting,
  shaftPermutation,
  sameThreadingUpToNumbering,
  compareDrafts,
  shaftsNeeded,
  simpler,
  groupByCloth,
  distinctCloths,
} from '../../src/draft/equivalence.js';

const TWILL = createDraft({
  name: 'twill',
  shafts: 4,
  treadles: 4,
  threading: straightDraw(4, 8),
  tieup: twillTieup(4, 2),
  treadling: straightTreadling(4, 8),
});

const RENUMBERED = createDraft({
  name: 'twill renumbered',
  shafts: 4,
  treadles: 4,
  threading: straightDraw(4, 8).map((shaft) => ((shaft + 1) % 4) + 1),
  tieup: twillTieup(4, 2).map((entry) => entry.map((shaft) => ((shaft + 1) % 4) + 1).sort()),
  treadling: straightTreadling(4, 8),
});

const OTHER = createDraft({ ...TWILL, name: 'other', tieup: twillTieup(4, 3) });

describe('clothKey', () => {
  it('is stable for the same draft', () => {
    expect(clothKey(TWILL)).toBe(clothKey(TWILL));
  });

  it('separates two drafts weaving different cloth', () => {
    expect(clothKey(TWILL)).not.toBe(clothKey(OTHER));
  });

  it('reads as eight hex digits', () => {
    expect(clothKey(TWILL)).toMatch(/^[0-9a-f]{8}$/);
  });
});

describe('sameWriting', () => {
  it('is true only for two drafts written the same way', () => {
    expect(sameWriting(TWILL, createDraft({ ...TWILL, name: 'copy' }))).toBe(true);
    expect(sameWriting(TWILL, RENUMBERED)).toBe(false);
  });
});

describe('shaftPermutation', () => {
  it('finds the renumbering between two threadings', () => {
    const mapping = shaftPermutation([1, 2, 3], [3, 4, 1]);
    expect(mapping.get(1)).toBe(3);
    expect(mapping.get(3)).toBe(1);
  });

  it('is null when one shaft would have to become two', () => {
    expect(shaftPermutation([1, 1], [2, 3])).toBeNull();
  });

  it('is null when two shafts would have to become one', () => {
    expect(shaftPermutation([1, 2], [3, 3])).toBeNull();
  });

  it('is null for threadings of different lengths', () => {
    expect(shaftPermutation([1, 2], [1, 2, 3])).toBeNull();
  });

  it('reads a renumbered threading as the same one', () => {
    expect(sameThreadingUpToNumbering(TWILL.threading, RENUMBERED.threading)).toBe(true);
    expect(sameThreadingUpToNumbering([1, 2, 1], [1, 2, 2])).toBe(false);
  });
});

describe('compareDrafts', () => {
  it('calls two identical drafts identical', () => {
    expect(compareDrafts(TWILL, createDraft({ ...TWILL, name: 'copy' })).verdict).toBe('identical');
  });

  it('sees through a renumbering', () => {
    const verdict = compareDrafts(TWILL, RENUMBERED);
    expect(verdict.cloth).toBe(true);
    expect(verdict.verdict).toBe('same cloth, shafts renumbered');
  });

  it('says plainly when the cloth differs', () => {
    expect(compareDrafts(TWILL, OTHER)).toEqual({ verdict: 'different cloth', cloth: false });
  });
});

describe('shaftsNeeded and simpler', () => {
  it('counts the shafts a threading actually uses', () => {
    expect(shaftsNeeded(TWILL)).toBe(4);
    const sparse = createDraft({ ...TWILL, threading: [1, 2, 1, 2, 1, 2, 1, 2] });
    expect(shaftsNeeded(sparse)).toBe(2);
  });

  it('prefers the draft on fewer shafts', () => {
    expect(simpler(TWILL, RENUMBERED)).toBe('neither');
  });

  it('will not rank two drafts weaving different cloth', () => {
    expect(simpler(TWILL, OTHER)).toBeNull();
  });
});

describe('groupByCloth', () => {
  it('gathers the drafts that weave the same thing', () => {
    const groups = groupByCloth([TWILL, RENUMBERED, OTHER]);
    expect(groups.length).toBe(2);
    expect(groups[0].length).toBe(2);
  });

  it('counts the distinct cloths in a pile', () => {
    expect(distinctCloths([TWILL, RENUMBERED, OTHER])).toBe(2);
    expect(distinctCloths([TWILL])).toBe(1);
  });
});

describe('sameCloth', () => {
  it('is the question the whole module is for', () => {
    expect(sameCloth(TWILL, RENUMBERED)).toBe(true);
    expect(sameCloth(TWILL, OTHER)).toBe(false);
  });
});
