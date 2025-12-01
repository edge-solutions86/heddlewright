import { NotFound } from '../util/errors.js';
import { nonEmptyString, plainObject } from '../util/check.js';
import { dimensions } from '../util/matrix.js';
import { warpFaceRatio } from '../draft/drawdown.js';
import { longestFloat } from '../analysis/floats.js';
import { interlacementFactor, firmnessClass } from '../analysis/interlacement.js';
import { plainWeave, basketWeave, warpRib, weftRib } from './plain.js';
import { twillWeave, brokenTwill, pointedTwill } from './twill.js';
import { satinWeave } from './satin.js';
import { honeycombWeave } from './honeycomb.js';

/**
 * The catalogue.
 *
 * A weave is a name and a way of building one repeat. Keeping them in one
 * register means a report can walk every weave the package knows and measure
 * it the same way, which is how the numbers in the docs stay honest: nothing is
 * written down that was not read off the cloth.
 */

const WEAVES = new Map();

function define(name, family, build) {
  WEAVES.set(name, { name, family, build });
}

define('plain', 'plain', () => plainWeave());
define('basket 2/2', 'plain', () => basketWeave(2));
define('basket 3/3', 'plain', () => basketWeave(3));
define('warp rib', 'plain', () => warpRib(2));
define('weft rib', 'plain', () => weftRib(2));
define('twill 2/2', 'twill', () => twillWeave('2/2'));
define('twill 3/1', 'twill', () => twillWeave('3/1'));
define('twill 1/3', 'twill', () => twillWeave('1/3'));
define('twill 2/1/1/2', 'twill', () => twillWeave('2/1/1/2'));
define('broken twill 2/2', 'twill', () => brokenTwill('2/2', { breakAt: 2 }));
define('herringbone 2/2', 'twill', () => pointedTwill('2/2', { width: 4 }));
define('satin 5', 'satin', () => satinWeave(5, 2));
define('satin 8', 'satin', () => satinWeave(8, 3));
define('sateen 5', 'satin', () => satinWeave(5, 2, { face: 'weft' }));
define('honeycomb 5', 'figured', () => honeycombWeave(5));
define('honeycomb 7', 'figured', () => honeycombWeave(7));

/** Every weave in the catalogue, in name order. */
export function listWeaves() {
  return [...WEAVES.values()].sort((a, b) => a.name.localeCompare(b.name));
}

/** The families the catalogue is grouped into. */
export function families() {
  return [...new Set(listWeaves().map((weave) => weave.family))].sort();
}

/** Every weave in one family. */
export function weavesInFamily(family) {
  nonEmptyString(family, 'family');
  return listWeaves().filter((weave) => weave.family === family);
}

/** Look a weave up and build one repeat of it. */
export function weaveByName(name) {
  nonEmptyString(name, 'name');
  const found = WEAVES.get(name);
  if (found === undefined) {
    throw new NotFound(`no weave called "${name}"`, { name, known: [...WEAVES.keys()] });
  }
  return found.build();
}

/** Everything a report wants to say about one weave, read off the cloth. */
export function measure(name) {
  const repeat = weaveByName(name);
  const size = dimensions(repeat);
  const floats = longestFloat(repeat);
  return {
    name,
    family: WEAVES.get(name).family,
    picks: size.rows,
    ends: size.cols,
    warpFace: warpFaceRatio(repeat),
    longestFloat: floats.longest,
    interlacement: interlacementFactor(repeat),
    firmness: firmnessClass(repeat),
  };
}

/** The whole catalogue measured, for a table. */
export function measureAll() {
  return listWeaves().map((weave) => measure(weave.name));
}

/** The weaves that stay inside a float limit, firmest first. */
export function weavesWithin(limit) {
  return measureAll()
    .filter((row) => row.longestFloat <= limit)
    .sort((a, b) => b.interlacement - a.interlacement || a.name.localeCompare(b.name));
}

/** Add a weave to the catalogue at runtime, for a shed with its own book. */
export function addWeave(entry) {
  plainObject(entry, 'entry');
  nonEmptyString(entry.name, 'entry.name');
  nonEmptyString(entry.family, 'entry.family');
  if (typeof entry.build !== 'function') {
    throw new NotFound('a catalogue entry needs a build function', { name: entry.name });
  }
  define(entry.name, entry.family, entry.build);
  return entry.name;
}

/** Take a weave back out again, which a test that added one should do. */
export function removeWeave(name) {
  nonEmptyString(name, 'name');
  return WEAVES.delete(name);
}
