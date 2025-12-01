import { describe, it, expect, afterEach } from 'vitest';
import { NotFound, InvalidArgument } from '../../src/util/errors.js';
import { plainWeave } from '../../src/weave/plain.js';
import {
  listWeaves,
  families,
  weavesInFamily,
  weaveByName,
  measure,
  measureAll,
  weavesWithin,
  addWeave,
  removeWeave,
} from '../../src/weave/catalogue.js';

afterEach(() => {
  removeWeave('shed special');
});

describe('listWeaves', () => {
  it('lists them in name order', () => {
    const names = listWeaves().map((weave) => weave.name);
    expect(names).toEqual([...names].sort());
  });

  it('gives every weave a family and a builder', () => {
    for (const weave of listWeaves()) {
      expect(typeof weave.family).toBe('string');
      expect(typeof weave.build).toBe('function');
    }
  });

  it('groups into the families the report uses', () => {
    expect(families()).toEqual(['figured', 'plain', 'satin', 'twill']);
    expect(weavesInFamily('satin').length).toBe(3);
    expect(weavesInFamily('nothing')).toEqual([]);
  });
});

describe('weaveByName', () => {
  it('builds one repeat', () => {
    expect(weaveByName('plain')).toEqual(plainWeave());
  });

  it('names what it does have when it cannot find one', () => {
    try {
      weaveByName('gauze');
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(NotFound);
      expect(err.detail.known).toContain('plain');
    }
  });

  it('builds a fresh repeat each time, so a caller cannot edit the catalogue', () => {
    const first = weaveByName('plain');
    first[0][0] = 0;
    expect(weaveByName('plain')[0][0]).toBe(1);
  });
});

describe('measure', () => {
  it('reads plain weave as the firmest thing in the book', () => {
    expect(measure('plain')).toMatchObject({
      family: 'plain',
      picks: 2,
      ends: 2,
      warpFace: 0.5,
      longestFloat: 1,
      interlacement: 1,
      firmness: 'firm',
    });
  });

  it('reads a satin as long floating and open', () => {
    const satin = measure('satin 8');
    expect(satin.longestFloat).toBe(7);
    expect(satin.warpFace).toBe(0.875);
    expect(satin.firmness).toBe('open');
  });

  it('reads a warp faced twill as showing more warp', () => {
    expect(measure('twill 3/1').warpFace).toBe(0.75);
    expect(measure('twill 1/3').warpFace).toBe(0.25);
  });
});

describe('measureAll', () => {
  it('measures every weave in the catalogue', () => {
    expect(measureAll().length).toBe(listWeaves().length);
  });

  it('never reports a float longer than the repeat it came from', () => {
    for (const row of measureAll()) {
      expect(row.longestFloat).toBeLessThanOrEqual(Math.max(row.picks, row.ends));
    }
  });

  it('reports an interlacement between nothing and one for every weave', () => {
    for (const row of measureAll()) {
      expect(row.interlacement).toBeGreaterThan(0);
      expect(row.interlacement).toBeLessThanOrEqual(1);
    }
  });
});

describe('weavesWithin', () => {
  it('keeps only the weaves inside a float limit, firmest first', () => {
    const found = weavesWithin(2);
    expect(found.every((row) => row.longestFloat <= 2)).toBe(true);
    expect(found[0].name).toBe('plain');
  });

  it('lets everything through on a limit nothing exceeds', () => {
    expect(weavesWithin(99).length).toBe(listWeaves().length);
  });

  it('keeps nothing on a limit of nothing', () => {
    expect(weavesWithin(0)).toEqual([]);
  });
});

describe('addWeave and removeWeave', () => {
  it('takes a weave a shed adds and measures it like any other', () => {
    addWeave({ name: 'shed special', family: 'plain', build: () => plainWeave() });
    expect(measure('shed special').firmness).toBe('firm');
    expect(listWeaves().some((weave) => weave.name === 'shed special')).toBe(true);
  });

  it('takes it back out again', () => {
    addWeave({ name: 'shed special', family: 'plain', build: () => plainWeave() });
    expect(removeWeave('shed special')).toBe(true);
    expect(() => weaveByName('shed special')).toThrow(NotFound);
  });

  it('refuses an entry with no builder', () => {
    expect(() => addWeave({ name: 'shed special', family: 'plain' })).toThrow(NotFound);
    expect(() => addWeave({ name: '', family: 'plain', build: () => [] })).toThrow(InvalidArgument);
  });
});
