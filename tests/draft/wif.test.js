import { describe, it, expect } from 'vitest';
import { DraftError } from '../../src/util/errors.js';
import { formatMatrix } from '../../src/util/matrix.js';
import { drawdownFromDraft } from '../../src/draft/drawdown.js';
import { twillWeave } from '../../src/weave/twill.js';
import {
  WIF_VERSION,
  readWif,
  draftFromWif,
  wifContents,
  declaresAndCarries,
} from '../../src/draft/wif.js';

const TWILL_FILE = [
  '[WIF]',
  'Version=1.1',
  'Source Program=heddlewright',
  '',
  '[CONTENTS]',
  'WEAVING=yes',
  'WARP=yes',
  'WEFT=yes',
  'THREADING=yes',
  'TIEUP=yes',
  'TREADLING=yes',
  'COLOR PALETTE=no',
  '',
  '[TEXT]',
  'Title=Four shaft twill',
  'Author=A Weaver',
  '',
  '[WEAVING]',
  'Shafts=4',
  'Treadles=4',
  'Rising Shed=yes',
  '',
  '[WARP]',
  'Threads=4',
  '',
  '[WEFT]',
  'Threads=4',
  '',
  '[THREADING]',
  '1=1',
  '2=2',
  '3=3',
  '4=4',
  '',
  '[TIEUP]',
  '1=1,2',
  '2=2,3',
  '3=3,4',
  '4=1,4',
  '',
  '[TREADLING]',
  '1=1',
  '2=2',
  '3=3',
  '4=4',
].join('\n');

describe('readWif', () => {
  it('reads the header and the four numbered sections', () => {
    const read = readWif(TWILL_FILE);
    expect(read.version).toBe(WIF_VERSION);
    expect(read.shafts).toBe(4);
    expect(read.treadles).toBe(4);
    expect(read.title).toBe('Four shaft twill');
    expect(read.threading).toEqual([1, 2, 3, 4]);
    expect(read.tieup).toEqual([
      [1, 2],
      [2, 3],
      [3, 4],
      [1, 4],
    ]);
  });

  it('reads the shed direction', () => {
    expect(readWif(TWILL_FILE).risingShed).toBe(true);
    expect(readWif(TWILL_FILE.replace('Rising Shed=yes', 'Rising Shed=no')).risingShed).toBe(false);
  });

  it('refuses a file with no WIF section', () => {
    expect(() => readWif('[WEAVING]\nShafts=4\n')).toThrow(/not a weaving file/);
  });

  it('refuses a file that never says how many shafts', () => {
    expect(() => readWif('[WIF]\nVersion=1.1\n')).toThrow(/how many shafts/);
  });
});

describe('draftFromWif', () => {
  it('builds a draft that weaves the twill the file describes', () => {
    const draft = draftFromWif(TWILL_FILE);
    expect(draft.shafts).toBe(4);
    expect(formatMatrix(drawdownFromDraft(draft))).toBe(formatMatrix(twillWeave('2/2')));
  });

  it('takes the title as the name and the author as a note', () => {
    const draft = draftFromWif(TWILL_FILE);
    expect(draft.name).toBe('Four shaft twill');
    expect(draft.notes).toBe('after A Weaver');
  });

  it('lets the caller override the name', () => {
    expect(draftFromWif(TWILL_FILE, { name: 'mine' }).name).toBe('mine');
  });

  it('refuses a threading with a gap in it, naming the ends', () => {
    const gappy = TWILL_FILE.replace('3=3\n', '');
    try {
      draftFromWif(gappy);
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(DraftError);
      expect(err.detail.ends).toContain(3);
    }
  });

  it('refuses a file with neither a tie-up nor a liftplan', () => {
    const bare = TWILL_FILE.split('[TIEUP]')[0];
    expect(() => draftFromWif(bare)).toThrow(/neither a tie-up nor a liftplan/);
  });
});

describe('a dobby file with a liftplan', () => {
  const DOBBY_FILE = [
    '[WIF]',
    'Version=1.1',
    '[WEAVING]',
    'Shafts=4',
    'Rising Shed=yes',
    '[WARP]',
    'Threads=4',
    '[WEFT]',
    'Threads=4',
    '[THREADING]',
    '1=1',
    '2=2',
    '3=3',
    '4=4',
    '[LIFTPLAN]',
    '1=1,2',
    '2=2,3',
    '3=3,4',
    '4=1,4',
  ].join('\n');

  it('gives each distinct lift its own treadle', () => {
    const draft = draftFromWif(DOBBY_FILE);
    expect(draft.treadles).toBe(4);
    expect(draft.tieup[0]).toEqual([1, 2]);
  });

  it('weaves the same cloth as the treadle file', () => {
    expect(formatMatrix(drawdownFromDraft(draftFromWif(DOBBY_FILE)))).toBe(
      formatMatrix(drawdownFromDraft(draftFromWif(TWILL_FILE))),
    );
  });

  it('collapses a repeated lift onto one treadle', () => {
    const repeated = DOBBY_FILE.replace('3=3,4', '3=1,2').replace('4=1,4', '4=2,3');
    expect(draftFromWif(repeated).treadles).toBe(2);
  });
});

describe('wifContents', () => {
  it('lists only the sections the file says it carries', () => {
    const carried = wifContents(TWILL_FILE);
    expect(carried).toContain('THREADING');
    expect(carried).not.toContain('COLOR PALETTE');
  });

  it('is empty when there is no contents section', () => {
    expect(wifContents('[WIF]\nVersion=1.1\n')).toEqual([]);
  });
});

describe('declaresAndCarries', () => {
  it('is true when the file both promises and holds a section', () => {
    expect(declaresAndCarries(TWILL_FILE, 'THREADING')).toBe(true);
  });

  it('is false when a promised section is missing', () => {
    const lying = TWILL_FILE.replace('COLOR PALETTE=no', 'COLOR PALETTE=yes');
    expect(declaresAndCarries(lying, 'COLOR PALETTE')).toBe(false);
  });

  it('is false when a section is there but not declared', () => {
    const quiet = TWILL_FILE.replace('TIEUP=yes', 'TIEUP=no');
    expect(declaresAndCarries(quiet, 'TIEUP')).toBe(false);
  });
});
