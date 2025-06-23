import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import {
  parseIni,
  hasSection,
  section,
  value,
  intValue,
  boolValue,
  intListValue,
  numberedList,
  formatIni,
  iniFromObject,
} from '../../src/draft/ini.js';

const FILE = [
  '; a small weaving file',
  '[WIF]',
  'Version=1.1',
  'Source Program=heddlewright',
  '',
  '[WEAVING]',
  'Shafts=4',
  'Treadles=6',
  'Rising Shed=yes',
  '',
  '[THREADING]',
  '1=1',
  '2=2',
  '4=4',
  '',
  '[TIEUP]',
  '1=1,2',
  '2=2,3',
].join('\n');

describe('parseIni', () => {
  it('reads sections and keys', () => {
    const sections = parseIni(FILE);
    expect([...sections.keys()]).toEqual(['WIF', 'WEAVING', 'THREADING', 'TIEUP']);
    expect(sections.get('WEAVING').get('SHAFTS')).toBe('4');
  });

  it('upper-cases section names and keys but keeps values as typed', () => {
    const sections = parseIni('[text]\nTitle=Broken Twill\n');
    expect(sections.get('TEXT').get('TITLE')).toBe('Broken Twill');
  });

  it('drops comments and blank lines', () => {
    const sections = parseIni('; nothing\n\n[A]\nx=1 ; trailing\n');
    expect(sections.get('A').get('X')).toBe('1');
  });

  it('reads a file written with carriage returns', () => {
    const sections = parseIni('[A]\r\nx=1\r\n');
    expect(sections.get('A').get('X')).toBe('1');
  });

  it('keeps an equals sign inside a value', () => {
    expect(parseIni('[A]\nx=a=b\n').get('A').get('X')).toBe('a=b');
  });

  it('merges two headers with the same name', () => {
    const sections = parseIni('[A]\nx=1\n[A]\ny=2\n');
    expect(sections.size).toBe(1);
    expect(sections.get('A').size).toBe(2);
  });

  it('refuses a key outside any section and a line that is neither', () => {
    expect(() => parseIni('x=1\n')).toThrow(/outside any section/);
    expect(() => parseIni('[A]\nnonsense\n')).toThrow(/neither a section nor a key/);
  });

  it('refuses a non-string', () => {
    expect(() => parseIni(null)).toThrow(InvalidArgument);
  });
});

describe('section lookups', () => {
  const sections = parseIni(FILE);

  it('finds sections whatever case is asked for', () => {
    expect(hasSection(sections, 'weaving')).toBe(true);
    expect(hasSection(sections, 'COLOR PALETTE')).toBe(false);
  });

  it('gives an empty section rather than undefined for one that is missing', () => {
    expect(section(sections, 'nothing').size).toBe(0);
  });

  it('reads a value with a fallback', () => {
    expect(value(sections, 'wif', 'Version')).toBe('1.1');
    expect(value(sections, 'wif', 'Date', 'unknown')).toBe('unknown');
  });
});

describe('typed lookups', () => {
  const sections = parseIni(FILE);

  it('reads an integer and falls back when it is missing or unreadable', () => {
    expect(intValue(sections, 'weaving', 'Shafts')).toBe(4);
    expect(intValue(sections, 'weaving', 'Colours', 0)).toBe(0);
    expect(intValue(parseIni('[A]\nx=four\n'), 'A', 'x', -1)).toBe(-1);
  });

  it('reads a yes as true and anything else as false', () => {
    expect(boolValue(sections, 'weaving', 'Rising Shed')).toBe(true);
    expect(boolValue(parseIni('[A]\nx=no\n'), 'A', 'x')).toBe(false);
    expect(boolValue(sections, 'weaving', 'Sinking Shed', true)).toBe(true);
  });

  it('reads a comma separated list', () => {
    expect(intListValue(sections, 'tieup', '1')).toEqual([1, 2]);
    expect(intListValue(sections, 'tieup', '9')).toEqual([]);
  });

  it('refuses a list entry that is not a number', () => {
    expect(() => intListValue(parseIni('[A]\nx=1,two\n'), 'A', 'x')).toThrow(/not a whole number/);
  });
});

describe('numberedList', () => {
  const sections = parseIni(FILE);

  it('reads a numbered section into a dense array', () => {
    expect(numberedList(sections, 'threading', 4)).toEqual([1, 2, null, 4]);
  });

  it('fills the gaps with whatever it is told to', () => {
    expect(numberedList(sections, 'threading', 4, { fill: 0 })).toEqual([1, 2, 0, 4]);
  });

  it('reads list values when asked', () => {
    expect(numberedList(sections, 'tieup', 2, { asList: true, fill: [] })).toEqual([
      [1, 2],
      [2, 3],
    ]);
  });

  it('ignores entries past the count and non-numeric keys', () => {
    const odd = parseIni('[A]\n1=5\n9=9\nname=x\n');
    expect(numberedList(odd, 'A', 2, { fill: 0 })).toEqual([5, 0]);
  });
});

describe('formatIni and iniFromObject', () => {
  it('writes sections back out in order', () => {
    const sections = iniFromObject({ WIF: { Version: '1.1' }, WEAVING: { Shafts: 4 } });
    expect(formatIni(sections)).toBe('[WIF]\nVersion=1.1\n\n[WEAVING]\nShafts=4\n');
  });

  it('round-trips through the parser', () => {
    const written = formatIni(iniFromObject({ A: { x: 1, y: 2 } }));
    const read = parseIni(written);
    expect(read.get('A').get('X')).toBe('1');
    expect(read.get('A').get('Y')).toBe('2');
  });

  it('refuses a section that is not an object', () => {
    expect(() => iniFromObject({ A: 'x' })).toThrow(InvalidArgument);
  });
});
