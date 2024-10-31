import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import { renderTable, renderPairs, wrapText } from '../../src/util/table.js';

const ROWS = [
  { shaft: 1, ends: 12, note: 'ground' },
  { shaft: 2, ends: 4, note: 'selvedge' },
];

describe('renderTable', () => {
  it('aligns numbers right and text left by default', () => {
    const text = renderTable(ROWS, [{ key: 'shaft' }, { key: 'ends' }, { key: 'note' }]);
    expect(text.split('\n')).toEqual([
      'shaft  ends  note',
      '-----  ----  --------',
      '    1    12  ground',
      '    2     4  selvedge',
    ]);
  });

  it('takes an explicit label and alignment', () => {
    const text = renderTable(ROWS, [
      { key: 'note', label: 'Purpose' },
      { key: 'ends', label: 'N', align: 'left' },
    ]);
    expect(text.split('\n')[0]).toBe('Purpose   N');
    expect(text.split('\n')[2]).toBe('ground    12');
  });

  it('widens a column to fit its longest value', () => {
    const text = renderTable([{ a: 'x' }, { a: 'a much longer value' }], [{ key: 'a' }]);
    expect(text.split('\n')[1]).toBe('-'.repeat('a much longer value'.length));
  });

  it('drops the rule when asked and honours the gap', () => {
    const text = renderTable(ROWS, [{ key: 'shaft' }, { key: 'ends' }], { rule: false, pad: 1 });
    expect(text.split('\n')).toEqual(['shaft ends', '    1   12', '    2    4']);
  });

  it('renders a header on its own when there are no rows', () => {
    expect(renderTable([], [{ key: 'shaft' }])).toBe('shaft\n-----');
  });

  it('writes an empty cell for a missing or null value', () => {
    const text = renderTable([{ a: 'x', b: null }, { a: 'y' }], [{ key: 'a' }, { key: 'b' }]);
    expect(text.split('\n')[2]).toBe('x');
  });

  it('joins an array cell with spaces and trims a fraction', () => {
    const text = renderTable(
      [{ shafts: [1, 2, 3], ratio: 1.23456789 }],
      [{ key: 'shafts' }, { key: 'ratio' }],
    );
    expect(text.split('\n')[2]).toBe('1 2 3   1.2346');
  });

  it('never leaves trailing spaces on a line', () => {
    const text = renderTable(ROWS, [{ key: 'shaft' }, { key: 'note' }]);
    for (const line of text.split('\n')) {
      expect(line).toBe(line.replace(/\s+$/, ''));
    }
  });

  it('checks its arguments', () => {
    expect(() => renderTable(ROWS, [])).toThrow(InvalidArgument);
    expect(() => renderTable(ROWS, [{ key: 'shaft', align: 'centre' }])).toThrow(/left, right/);
    expect(() => renderTable(ROWS, ['shaft'])).toThrow(/must be an object/);
  });
});

describe('renderPairs', () => {
  it('pads labels to a common width', () => {
    expect(renderPairs({ shafts: 8, treadles: 10 })).toBe('shafts  : 8\ntreadles: 10');
  });

  it('takes a Map as well as an object', () => {
    expect(renderPairs(new Map([['a', 1]]))).toBe('a: 1');
  });

  it('is empty for nothing to show', () => {
    expect(renderPairs({})).toBe('');
    expect(renderPairs()).toBe('');
  });

  it('takes a different separator', () => {
    expect(renderPairs({ a: 1 }, { separator: ' = ' })).toBe('a = 1');
  });
});

describe('wrapText', () => {
  it('breaks between words at the given width', () => {
    expect(wrapText('one two three four', 9)).toBe('one two\nthree\nfour');
  });

  it('leaves a word longer than the width on its own line', () => {
    expect(wrapText('a supercalifragilistic b', 5)).toBe('a\nsupercalifragilistic\nb');
  });

  it('collapses runs of whitespace', () => {
    expect(wrapText('  one   two  ', 40)).toBe('one two');
  });

  it('is empty for empty input', () => {
    expect(wrapText('   ')).toBe('');
  });
});
