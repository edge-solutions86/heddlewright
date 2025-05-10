import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import {
  DEFECT_KINDS,
  MAX_POINTS_PER_YARD,
  DEFAULT_ACCEPTANCE,
  defectPoints,
  scoreRoll,
  gradeRoll,
  pointsAllowed,
  scoreLot,
} from '../../src/cloth/fourpoint.js';

describe('defectPoints', () => {
  it('scores a length defect by the band it falls in', () => {
    expect(defectPoints({ inches: 1 })).toBe(1);
    expect(defectPoints({ inches: 3 })).toBe(1);
    expect(defectPoints({ inches: 3.5 })).toBe(2);
    expect(defectPoints({ inches: 6 })).toBe(2);
    expect(defectPoints({ inches: 7 })).toBe(3);
    expect(defectPoints({ inches: 9 })).toBe(3);
    expect(defectPoints({ inches: 12 })).toBe(4);
  });

  it('puts each band boundary in the lower band', () => {
    for (const [inches, points] of [
      [3, 1],
      [6, 2],
      [9, 3],
    ]) {
      expect(defectPoints({ inches })).toBe(points);
      expect(defectPoints({ inches: inches + 0.01 })).toBe(points + 1);
    }
  });

  it('starts a hole at two points however small it is', () => {
    expect(defectPoints({ kind: 'hole', inches: 0.25 })).toBe(2);
    expect(defectPoints({ kind: 'hole', inches: 1 })).toBe(2);
    expect(defectPoints({ kind: 'hole', inches: 1.5 })).toBe(4);
  });

  it('names its two kinds and refuses anything else', () => {
    expect(DEFECT_KINDS).toEqual(['length', 'hole']);
    expect(() => defectPoints({ kind: 'stain', inches: 1 })).toThrow(InvalidArgument);
  });

  it('refuses a defect with no length', () => {
    expect(() => defectPoints({ inches: 0 })).toThrow(InvalidArgument);
  });
});

describe('scoreRoll', () => {
  const ROLL = {
    widthInches: 60,
    yards: 100,
    defects: [
      { yard: 1, inches: 2 },
      { yard: 4, inches: 8 },
      { yard: 4, inches: 4 },
      { yard: 50, kind: 'hole', inches: 2 },
    ],
  };

  it('adds the points up per yard', () => {
    const score = scoreRoll(ROLL);
    expect(score.defects).toBe(4);
    expect(score.rawPoints).toBe(1 + 3 + 2 + 4);
    expect(score.totalPoints).toBe(1 + 4 + 4);
  });

  it('caps a yard at four points however many defects fall in it', () => {
    const score = scoreRoll(ROLL);
    expect(score.cappedYards).toEqual([4]);
    const yard4 = score.perYard.find((entry) => entry.yard === 4);
    expect(yard4.points).toBe(5);
    expect(yard4.counted).toBe(MAX_POINTS_PER_YARD);
  });

  it('quotes the score per hundred square yards', () => {
    const score = scoreRoll(ROLL);
    expect(score.pointsPer100SquareYards).toBe(roundTwo((9 * 3600) / (60 * 100)));
  });

  it('is nothing for a clean roll', () => {
    const score = scoreRoll({ widthInches: 60, yards: 100, defects: [] });
    expect(score.totalPoints).toBe(0);
    expect(score.pointsPer100SquareYards).toBe(0);
  });

  it('puts a defect part way through a yard into that yard', () => {
    const score = scoreRoll({
      widthInches: 60,
      yards: 10,
      defects: [{ yard: 2.4, inches: 1 }],
    });
    expect(score.perYard).toEqual([{ yard: 3, points: 1, counted: 1 }]);
  });

  it('refuses a defect past the end of the roll', () => {
    expect(() =>
      scoreRoll({ widthInches: 60, yards: 10, defects: [{ yard: 11, inches: 1 }] }),
    ).toThrow(/past the end/);
  });

  it('takes a different per-yard cap', () => {
    const score = scoreRoll({ ...ROLL, cap: 10 });
    expect(score.totalPoints).toBe(score.rawPoints);
    expect(score.cappedYards).toEqual([]);
  });
});

describe('gradeRoll', () => {
  it('passes a roll inside the trade limit and says by how much', () => {
    const score = scoreRoll({ widthInches: 60, yards: 100, defects: [{ yard: 1, inches: 2 }] });
    const grade = gradeRoll(score);
    expect(grade.accepted).toBe(true);
    expect(grade.acceptance).toBe(DEFAULT_ACCEPTANCE);
    expect(grade.margin).toBeGreaterThan(0);
  });

  it('fails a roll past it', () => {
    const defects = Array.from({ length: 40 }, (_, i) => ({ yard: i + 1, inches: 12 }));
    const score = scoreRoll({ widthInches: 36, yards: 40, defects });
    expect(gradeRoll(score).accepted).toBe(false);
  });

  it('takes a tighter acceptance from a fussier customer', () => {
    const score = scoreRoll({ widthInches: 60, yards: 10, defects: [{ yard: 1, inches: 12 }] });
    expect(gradeRoll(score, { acceptance: 30 }).accepted).toBe(true);
    expect(gradeRoll(score, { acceptance: 20 }).accepted).toBe(false);
  });
});

describe('pointsAllowed', () => {
  it('says how many points a roll may carry', () => {
    expect(pointsAllowed({ widthInches: 60, yards: 100 })).toBe(66);
  });

  it('allows more on a bigger roll', () => {
    expect(pointsAllowed({ widthInches: 60, yards: 200 })).toBeGreaterThan(
      pointsAllowed({ widthInches: 60, yards: 100 }),
    );
  });

  it('agrees with the grading, which is the point of it', () => {
    const allowed = pointsAllowed({ widthInches: 60, yards: 100 });
    const defects = Array.from({ length: allowed }, (_, i) => ({ yard: i + 1, inches: 1 }));
    expect(gradeRoll(scoreRoll({ widthInches: 60, yards: 100, defects })).accepted).toBe(true);
  });
});

describe('scoreLot', () => {
  it('weights each roll by its own area', () => {
    const lot = scoreLot([
      { widthInches: 60, yards: 100, defects: [{ yard: 1, inches: 12 }] },
      { widthInches: 60, yards: 100, defects: [] },
    ]);
    expect(lot.rolls).toBe(2);
    expect(lot.totalPoints).toBe(4);
    expect(lot.squareYards).toBeCloseTo((60 * 200) / 36, 3);
  });

  it('reads lower than the bad roll alone, because the good one dilutes it', () => {
    const bad = { widthInches: 60, yards: 100, defects: [{ yard: 1, inches: 12 }] };
    const alone = scoreRoll(bad).pointsPer100SquareYards;
    const lot = scoreLot([bad, { widthInches: 60, yards: 100, defects: [] }]);
    expect(lot.pointsPer100SquareYards).toBeLessThan(alone);
  });

  it('refuses an empty lot', () => {
    expect(() => scoreLot([])).toThrow(InvalidArgument);
  });
});

function roundTwo(value) {
  return Math.round(value * 100) / 100;
}
