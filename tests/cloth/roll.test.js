import { describe, it, expect } from 'vitest';
import { InvalidArgument, NotFound } from '../../src/util/errors.js';
import {
  GRADES,
  createPiece,
  cutPiece,
  cutPieces,
  regrade,
  registerTotals,
  reconcile,
  pieceByReference,
  firstQualityShare,
} from '../../src/cloth/roll.js';

const PIECE = createPiece({ reference: 'P1001', yards: 100, widthInches: 54 });

describe('createPiece', () => {
  it('works the area out from the length and the width', () => {
    expect(PIECE.squareYards).toBe(roundThree((100 * 54) / 36));
  });

  it('defaults to first quality', () => {
    expect(PIECE.grade).toBe('first');
    expect(GRADES).toEqual(['first', 'second', 'reject']);
  });

  it('gives the piece a content id and freezes it', () => {
    expect(PIECE.id).toMatch(/^piece-[0-9a-f]{8}$/);
    expect(Object.isFrozen(PIECE)).toBe(true);
  });

  it('refuses a piece of no length and an unknown grade', () => {
    expect(() => createPiece({ reference: 'x', yards: 0, widthInches: 54 })).toThrow(
      InvalidArgument,
    );
    expect(() =>
      createPiece({ reference: 'x', yards: 1, widthInches: 54, grade: 'thirds' }),
    ).toThrow(InvalidArgument);
  });
});

describe('cutPiece', () => {
  it('splits into two lengths that add back to the original', () => {
    const [head, tail] = cutPiece(PIECE, 40);
    expect(head.yards).toBe(40);
    expect(tail.yards).toBe(60);
    expect(head.yards + tail.yards).toBe(PIECE.yards);
  });

  it('suffixes the references', () => {
    const [head, tail] = cutPiece(PIECE, 40);
    expect(head.reference).toBe('P1001A');
    expect(tail.reference).toBe('P1001B');
  });

  it('takes different suffixes', () => {
    expect(cutPiece(PIECE, 40, { suffixes: ['/1', '/2'] })[0].reference).toBe('P1001/1');
  });

  it('refuses a cut at or past the end', () => {
    expect(() => cutPiece(PIECE, 100)).toThrow(/at or past the end/);
    expect(() => cutPiece(PIECE, 120)).toThrow(InvalidArgument);
  });

  it('keeps the width and the grade', () => {
    const second = regrade(PIECE, 'second');
    for (const part of cutPiece(second, 30)) {
      expect(part.widthInches).toBe(54);
      expect(part.grade).toBe('second');
    }
  });
});

describe('cutPieces', () => {
  it('cuts at several points and numbers the parts', () => {
    const parts = cutPieces(PIECE, [30, 70]);
    expect(parts.map((part) => part.yards)).toEqual([30, 40, 30]);
    expect(parts.map((part) => part.reference)).toEqual(['P1001-1', 'P1001-2', 'P1001-3']);
  });

  it('sorts the cut points before working', () => {
    expect(cutPieces(PIECE, [70, 30]).map((part) => part.yards)).toEqual([30, 40, 30]);
  });

  it('adds back to the original length', () => {
    const parts = cutPieces(PIECE, [10, 25, 60]);
    expect(parts.reduce((total, part) => total + part.yards, 0)).toBe(PIECE.yards);
  });

  it('refuses a repeated cut point, which would make a piece of nothing', () => {
    expect(() => cutPieces(PIECE, [30, 30])).toThrow(/run forwards/);
  });

  it('refuses a cut past the end', () => {
    expect(() => cutPieces(PIECE, [110])).toThrow(InvalidArgument);
  });
});

describe('regrade', () => {
  it('changes the grade and nothing else about the length', () => {
    const second = regrade(PIECE, 'second');
    expect(second.grade).toBe('second');
    expect(second.yards).toBe(PIECE.yards);
  });

  it('changes the id, because the grade is part of the record', () => {
    expect(regrade(PIECE, 'second').id).not.toBe(PIECE.id);
  });
});

describe('registerTotals', () => {
  const REGISTER = [
    createPiece({ reference: 'A', yards: 60, widthInches: 54 }),
    createPiece({ reference: 'B', yards: 30, widthInches: 54, grade: 'second' }),
    createPiece({ reference: 'C', yards: 10, widthInches: 54, grade: 'reject' }),
  ];

  it('adds the lengths and the areas', () => {
    const totals = registerTotals(REGISTER);
    expect(totals.pieces).toBe(3);
    expect(totals.yards).toBe(100);
    expect(totals.squareYards).toBe(roundThree((100 * 54) / 36));
  });

  it('splits by grade, listing every grade even at zero', () => {
    const totals = registerTotals(REGISTER);
    expect(totals.byGrade.map((entry) => entry.grade)).toEqual(GRADES);
    expect(totals.byGrade[0]).toEqual({ grade: 'first', pieces: 1, yards: 60 });
  });

  it('is empty for an empty register', () => {
    expect(registerTotals([]).yards).toBe(0);
  });
});

describe('reconcile', () => {
  it('balances when the register matches the loom', () => {
    const register = cutPieces(PIECE, [40]);
    expect(reconcile(register, 100)).toMatchObject({ difference: 0, balanced: true });
  });

  it('reports missing cloth as a negative difference', () => {
    const register = [createPiece({ reference: 'A', yards: 95, widthInches: 54 })];
    const result = reconcile(register, 100);
    expect(result.difference).toBe(-5);
    expect(result.balanced).toBe(false);
  });

  it('lets a small difference through on the tolerance', () => {
    const register = [createPiece({ reference: 'A', yards: 99.7, widthInches: 54 })];
    expect(reconcile(register, 100).balanced).toBe(true);
    expect(reconcile(register, 100, { toleranceYards: 0.1 }).balanced).toBe(false);
  });
});

describe('pieceByReference', () => {
  it('finds a piece', () => {
    const register = cutPiece(PIECE, 40);
    expect(pieceByReference(register, 'P1001B').yards).toBe(60);
  });

  it('says plainly when there is no such piece', () => {
    expect(() => pieceByReference([], 'P9999')).toThrow(NotFound);
  });
});

describe('firstQualityShare', () => {
  it('is the first quality length over the whole length', () => {
    const register = [
      createPiece({ reference: 'A', yards: 80, widthInches: 54 }),
      createPiece({ reference: 'B', yards: 20, widthInches: 54, grade: 'second' }),
    ];
    expect(firstQualityShare(register)).toBe(0.8);
  });

  it('is nothing for an empty register', () => {
    expect(firstQualityShare([])).toBe(0);
  });
});

function roundThree(value) {
  return Math.round(value * 1000) / 1000;
}
