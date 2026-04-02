import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import { createDraft } from '../../src/draft/draft.js';
import { straightDraw } from '../../src/draft/threading.js';
import { twillTieup } from '../../src/draft/tieup.js';
import { straightTreadling } from '../../src/draft/treadling.js';
import {
  structureSection,
  floatSection,
  settSection,
  weightSection,
  clothReport,
  renderReport,
  meetsSpecification,
} from '../../src/analysis/report.js';

const DRAFT = createDraft({
  name: 'four shaft twill',
  shafts: 4,
  treadles: 4,
  threading: straightDraw(4, 16),
  tieup: twillTieup(4, 2),
  treadling: straightTreadling(4, 16),
});

const CONSTRUCTION = {
  endsPerCm: 24,
  picksPerCm: 20,
  warpCount: 20,
  weftCount: 20,
  system: 'tex',
};

const OPTIONS = {
  count: 30,
  system: 'ne',
  endsPerInch: 60,
  picksPerInch: 50,
  construction: CONSTRUCTION,
};

describe('structureSection', () => {
  it('reads the repeat out of the cloth rather than the draft', () => {
    const structure = structureSection(DRAFT);
    expect(structure.picks).toBe(16);
    expect(structure.repeatPicks).toBe(4);
    expect(structure.repeatEnds).toBe(4);
    expect(structure.repeatShare).toBeCloseTo(16 / 256, 6);
  });

  it('reads a 2/2 twill as balanced and medium firm', () => {
    expect(structureSection(DRAFT).warpFace).toBe(0.5);
    expect(structureSection(DRAFT).firmness).toBe('medium');
  });
});

describe('floatSection', () => {
  it('measures the floats and counts what is over the limit', () => {
    const floats = floatSection(DRAFT);
    expect(floats.longestWarp).toBe(2);
    expect(floats.over).toBe(0);
  });

  it('counts more over a tighter limit', () => {
    expect(floatSection(DRAFT, { floatLimit: 1 }).over).toBeGreaterThan(0);
  });
});

describe('settSection', () => {
  it('works the sett out from the cloth and the yarn', () => {
    const sett = settSection(DRAFT, OPTIONS);
    expect(sett.diameters).toBeGreaterThan(0);
    expect(sett.maximumEndsPerInch).toBeLessThan(sett.diameters);
    expect(sett.settFactor).toBeCloseTo(4 / 6, 3);
  });

  it('leaves the cover out when no sett is given', () => {
    const sett = settSection(DRAFT, { count: 30 });
    expect(sett.cover).toBeNull();
    expect(sett.jammed).toBeNull();
  });

  it('refuses a yarn count of nothing', () => {
    expect(() => settSection(DRAFT, { count: 0 })).toThrow(InvalidArgument);
  });
});

describe('weightSection', () => {
  it('reports the weight both ways round', () => {
    const weight = weightSection(CONSTRUCTION);
    expect(weight.gsm).toBe(88);
    expect(weight.ouncesPerSquareYard).toBeGreaterThan(0);
    expect(weight.warpShare).toBeCloseTo(48 / 88, 4);
  });
});

describe('clothReport', () => {
  it('gathers every section', () => {
    const report = clothReport(DRAFT, OPTIONS);
    expect(report.name).toBe('four shaft twill');
    expect(report.structure.picks).toBe(16);
    expect(report.floats.longestWarp).toBe(2);
    expect(report.weight.gsm).toBe(88);
    expect(report.problems).toEqual([]);
  });

  it('leaves the weight out when no construction is given', () => {
    expect(clothReport(DRAFT, { count: 30 }).weight).toBeNull();
  });

  it('carries the draft problems through', () => {
    const sparse = createDraft({ ...DRAFT, shafts: 8, tieup: twillTieup(8, 2), treadles: 8 });
    expect(clothReport(sparse, { count: 30 }).problems.length).toBeGreaterThan(0);
  });
});

describe('renderReport', () => {
  it('prints every section it has', () => {
    const text = renderReport(clothReport(DRAFT, OPTIONS));
    expect(text).toContain('four shaft twill');
    expect(text).toContain('Structure');
    expect(text).toContain('Floats');
    expect(text).toContain('Weight');
  });

  it('leaves the weight block out when there is none', () => {
    const text = renderReport(clothReport(DRAFT, { count: 30 }));
    expect(text).not.toContain('Weight');
    expect(text).toContain('not given');
  });

  it('prints the problems when there are any', () => {
    const sparse = createDraft({ ...DRAFT, shafts: 8, tieup: twillTieup(8, 2), treadles: 8 });
    expect(renderReport(clothReport(sparse, { count: 30 }))).toContain('Problems');
  });
});

describe('meetsSpecification', () => {
  const report = clothReport(DRAFT, OPTIONS);

  it('passes a cloth inside every limit', () => {
    const reading = meetsSpecification(report, {
      maxFloat: 4,
      minInterlacement: 0.4,
      maxGsm: 200,
    });
    expect(reading.meets).toBe(true);
  });

  it('names the limit a cloth breaks', () => {
    expect(meetsSpecification(report, { maxFloat: 1 }).findings[0].code).toBe('float_too_long');
    expect(meetsSpecification(report, { maxGsm: 50 }).findings[0].code).toBe('too_heavy');
    expect(meetsSpecification(report, { minInterlacement: 0.9 }).findings[0].code).toBe('too_open');
  });

  it('checks nothing when no limits are given', () => {
    expect(meetsSpecification(report)).toEqual({ meets: true, findings: [] });
  });
});
