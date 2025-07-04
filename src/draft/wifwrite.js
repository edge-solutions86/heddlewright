import { plainObject } from '../util/check.js';
import { formatIni } from './ini.js';
import { WIF_VERSION } from './wif.js';

/**
 * Writing a weaving interchange file.
 *
 * The contents section is the part that matters and the part most writers get
 * wrong: it promises what the file holds, and a promise the file does not keep
 * makes every reader guess. Here the contents are worked out from the draft
 * rather than declared by the caller, so the two cannot drift apart.
 */

const PROGRAM = 'heddlewright';

/** Write a draft out as a weaving file. */
export function writeWif(draft, { author = '', notes = '', date } = {}) {
  plainObject(draft, 'draft');
  const sections = new Map();

  sections.set(
    'WIF',
    new Map([
      ['Version', WIF_VERSION],
      ['Date', date ?? 'unspecified'],
      ['Developers', PROGRAM],
      ['Source Program', PROGRAM],
    ]),
  );

  const carried = ['TEXT', 'WEAVING', 'WARP', 'WEFT', 'THREADING', 'TIEUP', 'TREADLING'];
  sections.set('CONTENTS', new Map(carried.map((name) => [name, 'yes'])));

  sections.set(
    'TEXT',
    new Map([
      ['Title', draft.name],
      ['Author', author],
      ['Notes', notes === '' ? (draft.notes ?? '') : notes],
    ]),
  );

  sections.set(
    'WEAVING',
    new Map([
      ['Shafts', String(draft.shafts)],
      ['Treadles', String(draft.treadles)],
      ['Rising Shed', draft.shed === 'rising' ? 'yes' : 'no'],
    ]),
  );

  sections.set('WARP', new Map([['Threads', String(draft.threading.length)]]));
  sections.set('WEFT', new Map([['Threads', String(draft.treadling.length)]]));

  sections.set(
    'THREADING',
    new Map(draft.threading.map((shaft, index) => [String(index + 1), String(shaft)])),
  );
  sections.set(
    'TIEUP',
    new Map(draft.tieup.map((shafts, index) => [String(index + 1), shafts.join(',')])),
  );
  sections.set(
    'TREADLING',
    new Map(draft.treadling.map((pick, index) => [String(index + 1), pick.join(',')])),
  );

  return formatIni(sections);
}

/**
 * Write a dobby draft with a liftplan instead of a tie-up and treadling, which
 * is what a loom with no treadles wants to read.
 */
export function writeDobbyWif(draft, liftplan, options = {}) {
  const text = writeWif(draft, options);
  const withoutTreadles = text
    .replace(/\[TIEUP\][\s\S]*?(?=\n\[|$)/, '')
    .replace(/\[TREADLING\][\s\S]*?(?=\n\[|$)/, '')
    .replace(/TIEUP=yes\n/, '')
    .replace(/TREADLING=yes\n/, 'LIFTPLAN=yes\n')
    .replace(/\n{3,}/g, '\n\n');
  const lines = liftplan.map(
    (row, index) =>
      `${index + 1}=${row
        .map((cell, shaft) => (cell === 1 ? shaft + 1 : 0))
        .filter((shaft) => shaft !== 0)
        .join(',')}`,
  );
  return `${withoutTreadles.trimEnd()}\n\n[LIFTPLAN]\n${lines.join('\n')}\n`;
}

/** The sections a written file will carry, for a caller checking before writing. */
export function contentsFor(draft) {
  plainObject(draft, 'draft');
  const carried = ['TEXT', 'WEAVING', 'WARP', 'WEFT', 'THREADING'];
  if (draft.tieup.length > 0) carried.push('TIEUP');
  if (draft.treadling.length > 0) carried.push('TREADLING');
  return carried;
}
