import { describe, it, expect } from 'vitest';
import { NotFound, InvalidArgument } from '../../src/util/errors.js';
import {
  FAULT_ORIGINS,
  listFaults,
  faultByCode,
  faultsByOrigin,
  recordDefect,
  recordSheet,
  byFault,
  byOrigin,
  dominantOrigin,
  defectDensity,
  inspectionSheet,
} from '../../src/cloth/inspection.js';

const SHEET = recordSheet([
  { code: 'weft_bar', yard: 3, inches: 12 },
  { code: 'weft_bar', yard: 9, inches: 8 },
  { code: 'reed_mark', yard: 12, inches: 4 },
  { code: 'slub', yard: 20, inches: 1 },
  { code: 'hole', yard: 40, inches: 0.5 },
]);

describe('listFaults', () => {
  it('carries a catalogue in code order', () => {
    const codes = listFaults().map((fault) => fault.code);
    expect(codes).toEqual([...codes].sort());
    expect(codes).toContain('reed_mark');
  });

  it('gives every fault an origin the catalogue recognises', () => {
    for (const fault of listFaults()) {
      expect(FAULT_ORIGINS).toContain(fault.origin);
    }
  });

  it('hands back frozen records', () => {
    expect(Object.isFrozen(listFaults()[0])).toBe(true);
  });
});

describe('faultByCode', () => {
  it('finds a fault and reports where it comes from', () => {
    expect(faultByCode('reed_mark')).toMatchObject({ origin: 'loom', kind: 'length' });
  });

  it('says plainly when there is no such fault', () => {
    expect(() => faultByCode('gremlins')).toThrow(NotFound);
  });
});

describe('faultsByOrigin', () => {
  it('gathers the faults a warp is to blame for', () => {
    const codes = faultsByOrigin('warp').map((fault) => fault.code);
    expect(codes).toContain('missing_end');
    expect(codes).toContain('tight_end');
    expect(codes).not.toContain('weft_bar');
  });

  it('refuses an origin it does not know', () => {
    expect(() => faultsByOrigin('gremlins')).toThrow(InvalidArgument);
  });
});

describe('recordDefect', () => {
  it('fills the origin and kind in from the catalogue', () => {
    const record = recordDefect({ code: 'weft_bar', yard: 4, inches: 12 });
    expect(record).toMatchObject({ origin: 'weft', kind: 'length', points: 4 });
  });

  it('scores a hole on the hole rule, not the length rule', () => {
    expect(recordDefect({ code: 'hole', yard: 1, inches: 0.5 }).points).toBe(2);
  });

  it('keeps a note when the grader writes one', () => {
    expect(recordDefect({ code: 'slub', yard: 1, inches: 1, note: 'left edge' }).note).toBe(
      'left edge',
    );
  });

  it('refuses a defect with no length or an unknown code', () => {
    expect(() => recordDefect({ code: 'slub', yard: 1, inches: 0 })).toThrow(InvalidArgument);
    expect(() => recordDefect({ code: 'nope', yard: 1, inches: 1 })).toThrow(NotFound);
  });
});

describe('byFault', () => {
  it('ranks faults by the points they cost', () => {
    const ranked = byFault(SHEET);
    expect(ranked[0]).toEqual({ code: 'weft_bar', count: 2, points: 7 });
  });

  it('counts every defect exactly once', () => {
    expect(byFault(SHEET).reduce((total, entry) => total + entry.count, 0)).toBe(SHEET.length);
  });
});

describe('byOrigin', () => {
  it('gathers the loom faults together', () => {
    const loom = byOrigin(SHEET).find((entry) => entry.origin === 'loom');
    expect(loom.count).toBe(2);
    expect(loom.points).toBe(2 + 2);
  });

  it('is empty for an empty sheet', () => {
    expect(byOrigin([])).toEqual([]);
  });
});

describe('dominantOrigin', () => {
  it('names where most of the trouble comes from', () => {
    expect(dominantOrigin(SHEET)).toBe('weft');
  });

  it('is null when nothing is booked', () => {
    expect(dominantOrigin([])).toBeNull();
  });
});

describe('defectDensity', () => {
  it('counts defects per hundred square yards', () => {
    expect(defectDensity(SHEET, { widthInches: 60, yards: 100 })).toBe(3);
  });

  it('is nothing for a clean roll', () => {
    expect(defectDensity([], { widthInches: 60, yards: 100 })).toBe(0);
  });
});

describe('inspectionSheet', () => {
  it('gathers the whole reading into one record', () => {
    const sheet = inspectionSheet(SHEET, { widthInches: 60, yards: 100 });
    expect(sheet.defects).toBe(5);
    expect(sheet.points).toBe(SHEET.reduce((total, defect) => total + defect.points, 0));
    expect(sheet.dominantOrigin).toBe('weft');
    expect(sheet.worstDefect.yard).toBe(3);
  });

  it('reports no worst defect on a clean roll', () => {
    const sheet = inspectionSheet([], { widthInches: 60, yards: 100 });
    expect(sheet.worstDefect).toBeNull();
    expect(sheet.points).toBe(0);
  });
});
