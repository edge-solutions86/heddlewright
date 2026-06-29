import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import { formatMatrix } from '../../src/util/matrix.js';
import { createDraft } from '../../src/draft/draft.js';
import { drawdownFromDraft } from '../../src/draft/drawdown.js';
import { liftplanFromTreadling } from '../../src/draft/liftplan.js';
import { parseIni } from '../../src/draft/ini.js';
import { draftFromWif, readWif, declaresAndCarries } from '../../src/draft/wif.js';
import { writeWif, writeDobbyWif, contentsFor } from '../../src/draft/wifwrite.js';

import { twillDraft } from '../fixtures.js';

const DRAFT = twillDraft();

describe('writeWif', () => {
  it('writes a file the reader takes back to the same cloth', () => {
    const text = writeWif(DRAFT);
    const back = draftFromWif(text);
    expect(formatMatrix(drawdownFromDraft(back))).toBe(formatMatrix(drawdownFromDraft(DRAFT)));
    expect(back.id).toBe(DRAFT.id);
  });

  it('keeps the name and the counts', () => {
    const read = readWif(writeWif(DRAFT));
    expect(read.title).toBe('four shaft twill');
    expect(read.shafts).toBe(4);
    expect(read.warpThreads).toBe(8);
    expect(read.weftThreads).toBe(8);
  });

  it('writes an author when one is given', () => {
    expect(readWif(writeWif(DRAFT, { author: 'A Weaver' })).author).toBe('A Weaver');
  });

  it('records a sinking shed as such', () => {
    const sinking = createDraft({ ...DRAFT, shed: 'sinking' });
    expect(readWif(writeWif(sinking)).risingShed).toBe(false);
  });

  it('declares every section it actually writes', () => {
    const text = writeWif(DRAFT);
    for (const name of ['TEXT', 'WEAVING', 'WARP', 'WEFT', 'THREADING', 'TIEUP', 'TREADLING']) {
      expect(declaresAndCarries(text, name)).toBe(true);
    }
  });

  it('ends with a newline and has no blank line inside a section', () => {
    const text = writeWif(DRAFT);
    expect(text.endsWith('\n')).toBe(true);
    expect(/\n\n\n/.test(text)).toBe(false);
  });

  it('refuses something that is not a draft', () => {
    expect(() => writeWif('twill')).toThrow(InvalidArgument);
  });
});

describe('writeDobbyWif', () => {
  const LIFTPLAN = liftplanFromTreadling(DRAFT.tieup, DRAFT.treadling, {
    shafts: 4,
    treadles: 4,
  });

  it('writes a liftplan instead of a tie-up and treadling', () => {
    const text = writeDobbyWif(DRAFT, LIFTPLAN);
    const sections = parseIni(text);
    expect(sections.has('LIFTPLAN')).toBe(true);
    expect(sections.has('TIEUP')).toBe(false);
    expect(sections.has('TREADLING')).toBe(false);
  });

  it('declares the liftplan in the contents', () => {
    expect(declaresAndCarries(writeDobbyWif(DRAFT, LIFTPLAN), 'LIFTPLAN')).toBe(true);
  });

  it('reads back to the same cloth', () => {
    const back = draftFromWif(writeDobbyWif(DRAFT, LIFTPLAN));
    expect(formatMatrix(drawdownFromDraft(back))).toBe(formatMatrix(drawdownFromDraft(DRAFT)));
  });

  it('writes one line per pick', () => {
    const sections = parseIni(writeDobbyWif(DRAFT, LIFTPLAN));
    expect(sections.get('LIFTPLAN').size).toBe(8);
    expect(sections.get('LIFTPLAN').get('1')).toBe('1,2');
  });
});

describe('contentsFor', () => {
  it('names the sections a draft will fill', () => {
    expect(contentsFor(DRAFT)).toEqual([
      'TEXT',
      'WEAVING',
      'WARP',
      'WEFT',
      'THREADING',
      'TIEUP',
      'TREADLING',
    ]);
  });

  it('refuses something that is not a draft', () => {
    expect(() => contentsFor(null)).toThrow(InvalidArgument);
  });
});
