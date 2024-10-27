import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import {
  slug,
  stableHash,
  contentId,
  uniqueId,
  referenceCounter,
  parseReference,
} from '../../src/util/ids.js';

describe('slug', () => {
  it('lowercases and joins with hyphens', () => {
    expect(slug('Broken Twill 2/2')).toBe('broken-twill-2-2');
    expect(slug('  Warp  Beam  ')).toBe('warp-beam');
  });

  it('strips accents rather than dropping the letter', () => {
    expect(slug('Gobelin Fauteuil')).toBe('gobelin-fauteuil');
    expect(slug('Écru')).toBe('ecru');
  });

  it('never starts or ends with a hyphen', () => {
    expect(slug('--satin--')).toBe('satin');
    expect(slug('5/3 satin!')).toBe('5-3-satin');
  });

  it('truncates without leaving a trailing hyphen', () => {
    expect(slug('abcdefgh ijkl', { maxLength: 9 })).toBe('abcdefgh');
    expect(slug('abcdefghij', { maxLength: 4 })).toBe('abcd');
  });

  it('refuses text with nothing usable in it', () => {
    expect(() => slug('///')).toThrow(/no slug-able/);
    expect(() => slug('')).toThrow(InvalidArgument);
  });
});

describe('stableHash', () => {
  it('is eight hex digits', () => {
    expect(stableHash('plain weave')).toMatch(/^[0-9a-f]{8}$/);
  });

  it('is stable for the same input and different for a small change', () => {
    expect(stableHash('twill')).toBe(stableHash('twill'));
    expect(stableHash('twill')).not.toBe(stableHash('twil'));
    expect(stableHash('')).toMatch(/^[0-9a-f]{8}$/);
  });

  it('holds the published FNV-1a values for the standard vectors', () => {
    expect(stableHash('a')).toBe('e40c292c');
    expect(stableHash('foobar')).toBe('bf9cf968');
  });

  it('sees multi-byte characters as their UTF-8 bytes', () => {
    expect(stableHash('é')).not.toBe(stableHash('e'));
  });

  it('refuses a non-string rather than stringifying it', () => {
    expect(() => stableHash(42)).toThrow(InvalidArgument);
  });
});

describe('contentId', () => {
  it('prefixes a hash of the content', () => {
    const id = contentId('draft', { shafts: 4 });
    expect(id).toMatch(/^draft-[0-9a-f]{8}$/);
    expect(contentId('draft', { shafts: 4 })).toBe(id);
  });

  it('separates different content under the same prefix', () => {
    expect(contentId('draft', { shafts: 4 })).not.toBe(contentId('draft', { shafts: 8 }));
  });

  it('slugs the prefix', () => {
    expect(contentId('Card Deck', 'x').startsWith('card-deck-')).toBe(true);
  });
});

describe('uniqueId', () => {
  it('hands back the plain slug when it is free', () => {
    expect(uniqueId('Plain Weave', new Set())).toBe('plain-weave');
    expect(uniqueId('Plain Weave', [])).toBe('plain-weave');
  });

  it('suffixes from two upward, skipping the ones already taken', () => {
    const taken = new Set(['twill', 'twill-2', 'twill-3']);
    expect(uniqueId('twill', taken)).toBe('twill-4');
  });

  it('takes an array as well as a Set', () => {
    expect(uniqueId('satin', ['satin'])).toBe('satin-2');
  });

  it('treats a missing collection as empty', () => {
    expect(uniqueId('satin')).toBe('satin');
  });
});

describe('referenceCounter', () => {
  it('pads to the requested width and counts up', () => {
    const counter = referenceCounter('CD');
    expect(counter.next()).toBe('CD0001');
    expect(counter.next()).toBe('CD0002');
    expect(counter.count).toBe(2);
  });

  it('peek does not advance', () => {
    const counter = referenceCounter('B', { width: 2 });
    expect(counter.peek()).toBe('B01');
    expect(counter.peek()).toBe('B01');
    expect(counter.next()).toBe('B01');
    expect(counter.peek()).toBe('B02');
  });

  it('starts where it is told', () => {
    const counter = referenceCounter('W', { width: 3, start: 42 });
    expect(counter.next()).toBe('W042');
    expect(counter.count).toBe(1);
  });

  it('does not truncate a number wider than the pad', () => {
    const counter = referenceCounter('X', { width: 2, start: 1234 });
    expect(counter.next()).toBe('X1234');
  });

  it('checks its arguments', () => {
    expect(() => referenceCounter('', {})).toThrow(InvalidArgument);
    expect(() => referenceCounter('X', { width: 0 })).toThrow(InvalidArgument);
  });
});

describe('parseReference', () => {
  it('splits a reference into prefix, number and width', () => {
    expect(parseReference('CD0007')).toEqual({ prefix: 'CD', number: 7, width: 4 });
    expect(parseReference('warp-beam-12')).toEqual({ prefix: 'warp-beam-', number: 12, width: 2 });
  });

  it('is null when there is no trailing number', () => {
    expect(parseReference('CD')).toBeNull();
    expect(parseReference('0007')).toBeNull();
  });

  it('round-trips with referenceCounter', () => {
    const counter = referenceCounter('CD', { width: 5, start: 9 });
    const parsed = parseReference(counter.next());
    expect(parsed).toEqual({ prefix: 'CD', number: 9, width: 5 });
  });
});
