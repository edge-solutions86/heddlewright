import { DraftError } from '../util/errors.js';
import { array, positiveInteger } from '../util/check.js';
import { createMatrix } from '../util/matrix.js';

/**
 * Plain weave and the weaves that come straight out of it by grouping threads:
 * basket, warp rib and weft rib. Everything here returns one repeat as a
 * picks-by-ends matrix with a set cell meaning the warp end is on the face.
 */

/** The one over one interlacement, the tightest cloth two shafts can make. */
export function plainWeave() {
  return [
    [1, 0],
    [0, 1],
  ];
}

/**
 * Basket, or hopsack: plain weave with `endsPerGroup` warp ends and
 * `picksPerGroup` weft picks working as one thread. A 2 by 2 basket is the
 * common one; 2 by 1 is a half basket.
 */
export function basketWeave(endsPerGroup = 2, picksPerGroup = endsPerGroup) {
  positiveInteger(endsPerGroup, 'endsPerGroup');
  positiveInteger(picksPerGroup, 'picksPerGroup');
  const ends = endsPerGroup * 2;
  const picks = picksPerGroup * 2;
  const repeat = createMatrix(picks, ends);
  for (let p = 0; p < picks; p += 1) {
    for (let e = 0; e < ends; e += 1) {
      const endBlock = Math.floor(e / endsPerGroup) % 2;
      const pickBlock = Math.floor(p / picksPerGroup) % 2;
      repeat[p][e] = endBlock === pickBlock ? 1 : 0;
    }
  }
  return repeat;
}

/**
 * Warp rib: plain weave stretched down the cloth, so each shed is held for
 * several picks and the warp covers the weft. The cord runs weft-way.
 */
export function warpRib(picksPerShed = 2) {
  positiveInteger(picksPerShed, 'picksPerShed');
  const picks = picksPerShed * 2;
  const repeat = createMatrix(picks, 2);
  for (let p = 0; p < picks; p += 1) {
    const shed = Math.floor(p / picksPerShed) % 2;
    repeat[p][0] = shed === 0 ? 1 : 0;
    repeat[p][1] = shed === 0 ? 0 : 1;
  }
  return repeat;
}

/**
 * Weft rib, the ottoman: plain weave stretched across the cloth, so groups of
 * ends work together and the weft covers the warp. The cord runs warp-way.
 */
export function weftRib(endsPerShed = 2) {
  positiveInteger(endsPerShed, 'endsPerShed');
  const ends = endsPerShed * 2;
  const repeat = createMatrix(2, ends);
  for (let e = 0; e < ends; e += 1) {
    const group = Math.floor(e / endsPerShed) % 2;
    repeat[0][e] = group === 0 ? 1 : 0;
    repeat[1][e] = group === 0 ? 0 : 1;
  }
  return repeat;
}

/**
 * An irregular rib, where the groups are not all the same size. The list gives
 * the run of picks for each shed in turn, alternating which shed is up.
 */
export function irregularWarpRib(runs) {
  array(runs, 'runs', { min: 2 });
  for (const [i, run] of runs.entries()) positiveInteger(run, `runs[${i}]`);
  if (runs.length % 2 !== 0) {
    throw new DraftError('an irregular rib needs an even number of runs to close its repeat', {
      runs: runs.length,
    });
  }
  const rows = [];
  for (const [i, run] of runs.entries()) {
    const up = i % 2 === 0;
    for (let n = 0; n < run; n += 1) rows.push(up ? [1, 0] : [0, 1]);
  }
  return rows;
}

/** Plain weave threaded over more shafts than two, for a wider heddle spread. */
export function plainOnShafts(shafts) {
  positiveInteger(shafts, 'shafts');
  if (shafts % 2 !== 0) {
    throw new DraftError('plain weave needs an even shaft count to divide evenly', { shafts });
  }
  const odd = [];
  const even = [];
  for (let shaft = 1; shaft <= shafts; shaft += 1) {
    (shaft % 2 === 1 ? odd : even).push(shaft);
  }
  return { tieup: [odd, even], treadling: [[1], [2]], shafts };
}

/** The float length of every one of these weaves is one thread. */
export function isPlainDerived(repeat) {
  return (
    repeat.length % 2 === 0 &&
    repeat[0].length % 2 === 0 &&
    repeat.every((row) => row.some((cell) => cell === 1) && row.some((cell) => cell === 0))
  );
}
