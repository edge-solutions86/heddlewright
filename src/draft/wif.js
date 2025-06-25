import { DraftError } from '../util/errors.js';
import { nonEmptyString } from '../util/check.js';
import { parseIni, boolValue, intValue, numberedList, section, value } from './ini.js';
import { createDraft } from './draft.js';

/**
 * Reading a weaving interchange file.
 *
 * The format keeps the draft in four numbered sections: the threading by end,
 * the tie-up by treadle, the treadling by pick and, on a dobby draft, a
 * liftplan by pick instead of the last two. What it does not keep is any
 * guarantee that those agree with the shaft and treadle counts in the header,
 * so everything is checked on the way in rather than trusted.
 */

export const WIF_VERSION = '1.1';

/** Read a file into the pieces it declares, before any draft is built. */
export function readWif(text) {
  const sections = parseIni(text);
  if (section(sections, 'WIF').size === 0) {
    throw new DraftError('this file has no WIF section, so it is not a weaving file', {});
  }
  const shafts = intValue(sections, 'WEAVING', 'Shafts');
  const treadles = intValue(sections, 'WEAVING', 'Treadles');
  if (shafts === null) {
    throw new DraftError('the file does not say how many shafts the draft needs', {});
  }
  const warpThreads = intValue(sections, 'WARP', 'Threads', 0);
  const weftThreads = intValue(sections, 'WEFT', 'Threads', 0);
  return {
    version: value(sections, 'WIF', 'Version', ''),
    program: value(sections, 'WIF', 'Source Program', ''),
    title: value(sections, 'TEXT', 'Title', ''),
    author: value(sections, 'TEXT', 'Author', ''),
    shafts,
    treadles,
    risingShed: boolValue(sections, 'WEAVING', 'Rising Shed', true),
    warpThreads,
    weftThreads,
    threading: numberedList(sections, 'THREADING', warpThreads, { fill: 0 }),
    tieup:
      treadles === null
        ? []
        : numberedList(sections, 'TIEUP', treadles, { asList: true, fill: [] }),
    treadling: numberedList(sections, 'TREADLING', weftThreads, { asList: true, fill: [] }),
    liftplan: numberedList(sections, 'LIFTPLAN', weftThreads, { asList: true, fill: [] }),
    hasTieup: section(sections, 'TIEUP').size > 0,
    hasLiftplan: section(sections, 'LIFTPLAN').size > 0,
  };
}

/**
 * A weaving file writes an absent title as an empty string as often as it
 * leaves the key out, so a plain nullish fallback picks the empty one up and
 * hands it on to a check that refuses it.
 */
function firstNonEmpty(...candidates) {
  for (const candidate of candidates) {
    if (typeof candidate === 'string' && candidate.trim() !== '') return candidate;
  }
  return 'untitled';
}

/** Turn a file into a draft, checking everything the header promised. */
export function draftFromWif(text, { name } = {}) {
  const read = readWif(text);
  if (read.warpThreads === 0) {
    throw new DraftError('the file does not say how many warp threads it has', {});
  }
  const missing = read.threading
    .map((shaft, index) => (shaft === 0 ? index + 1 : 0))
    .filter((end) => end !== 0);
  if (missing.length > 0) {
    throw new DraftError('the threading has ends with no shaft', {
      ends: missing.slice(0, 10),
      missing: missing.length,
    });
  }
  if (read.hasLiftplan) {
    return draftFromLiftplanFile(read, name);
  }
  if (read.treadles === null || !read.hasTieup) {
    throw new DraftError('the file has neither a tie-up nor a liftplan', {});
  }
  return createDraft({
    name: firstNonEmpty(name, read.title),
    shafts: read.shafts,
    treadles: read.treadles,
    shed: read.risingShed ? 'rising' : 'sinking',
    threading: read.threading,
    tieup: read.tieup,
    treadling: read.treadling,
    notes: read.author === '' ? '' : `after ${read.author}`,
  });
}

/**
 * A dobby file carries a liftplan instead of a tie-up. Each distinct lift
 * becomes a treadle, in the order the picks first use it.
 */
function draftFromLiftplanFile(read, name) {
  const seen = new Map();
  const tieup = [];
  const treadling = [];
  for (const lift of read.liftplan) {
    const key = [...lift].sort((a, b) => a - b).join(',');
    if (!seen.has(key)) {
      seen.set(key, tieup.length + 1);
      tieup.push([...lift].sort((a, b) => a - b));
    }
    treadling.push([seen.get(key)]);
  }
  return createDraft({
    name: firstNonEmpty(name, read.title),
    shafts: read.shafts,
    treadles: tieup.length,
    shed: read.risingShed ? 'rising' : 'sinking',
    threading: read.threading,
    tieup,
    treadling,
  });
}

/** What a file declares it carries, without reading any of it. */
export function wifContents(text) {
  const sections = parseIni(text);
  const declared = section(sections, 'CONTENTS');
  const carried = [];
  for (const [key, flag] of declared) {
    if (/^(yes|true|on|1)$/i.test(flag.trim())) carried.push(key);
  }
  return carried.sort();
}

/** True when the file says it carries the section, and actually does. */
export function declaresAndCarries(text, name) {
  nonEmptyString(name, 'name');
  const sections = parseIni(text);
  const declared = boolValue(sections, 'CONTENTS', name, false);
  return declared && section(sections, name).size > 0;
}
