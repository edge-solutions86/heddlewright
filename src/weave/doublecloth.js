import { DraftError } from '../util/errors.js';
import { array, oneOf, positiveInteger } from '../util/check.js';
import { assertMatrix, createMatrix } from '../util/matrix.js';

/**
 * Double cloth.
 *
 * Two cloths woven at the same time, one above the other, sharing the loom but
 * not the threads. The warp is split between them end by end and the weft pick
 * by pick, and two rules keep them apart:
 *
 *   on a face pick every back end must be down, so the face weft stays above
 *   the back warp;
 *   on a back pick every face end must be up, so the back weft passes beneath
 *   the face cloth.
 *
 * Break either one and the two cloths are stitched where they were not meant
 * to be, which is the fault this module exists to make visible.
 */

export const LAYERS = ['face', 'back'];

/** Which layer each end belongs to, from a repeating order of layer names. */
export function endLayers(order, ends) {
  array(order, 'order', { min: 1 });
  positiveInteger(ends, 'ends');
  for (const [index, layer] of order.entries()) oneOf(layer, `order[${index}]`, LAYERS);
  return Array.from({ length: ends }, (_, e) => order[e % order.length]);
}

/** The same for the picks. */
export function pickLayers(order, picks) {
  return endLayers(order, picks);
}

/**
 * Build a double cloth from a weave for each layer. The layer weaves are read
 * on their own thread numbering, so a face plain weave means plain weave in the
 * face ends and face picks, not in the combined cloth.
 */
export function doubleCloth({ face, back, endOrder = ['face', 'back'], pickOrder = endOrder }) {
  assertMatrix(face, 'face');
  assertMatrix(back, 'back');
  const ends = endOrder.length * Math.max(face[0].length, back[0].length);
  const picks = pickOrder.length * Math.max(face.length, back.length);
  const ofEnd = endLayers(endOrder, ends);
  const ofPick = pickLayers(pickOrder, picks);
  const cloth = createMatrix(picks, ends);

  const counters = { face: 0, back: 0 };
  const endIndex = ofEnd.map((layer) => {
    const at = counters[layer];
    counters[layer] += 1;
    return at;
  });
  const pickCounters = { face: 0, back: 0 };
  const pickIndex = ofPick.map((layer) => {
    const at = pickCounters[layer];
    pickCounters[layer] += 1;
    return at;
  });

  for (let p = 0; p < picks; p += 1) {
    for (let e = 0; e < ends; e += 1) {
      if (ofPick[p] === 'face') {
        cloth[p][e] =
          ofEnd[e] === 'face' ? face[pickIndex[p] % face.length][endIndex[e] % face[0].length] : 0;
        continue;
      }
      cloth[p][e] =
        ofEnd[e] === 'back' ? back[pickIndex[p] % back.length][endIndex[e] % back[0].length] : 1;
    }
  }
  return { cloth, endLayers: ofEnd, pickLayers: ofPick };
}

/**
 * Every cell where the two rules are broken, which is every place the cloths
 * are stitched together whether the designer meant it or not.
 */
export function stitchingPoints({ cloth, endLayers: ofEnd, pickLayers: ofPick }) {
  assertMatrix(cloth, 'cloth');
  const found = [];
  for (const [p, row] of cloth.entries()) {
    for (const [e, cell] of row.entries()) {
      if (ofPick[p] === 'face' && ofEnd[e] === 'back' && cell === 1) {
        found.push({ pick: p + 1, end: e + 1, kind: 'back end raised on a face pick' });
      }
      if (ofPick[p] === 'back' && ofEnd[e] === 'face' && cell === 0) {
        found.push({ pick: p + 1, end: e + 1, kind: 'face end lowered on a back pick' });
      }
    }
  }
  return found;
}

/** Refuse a double cloth that is stitched where it should be two cloths. */
export function assertLayersSeparate(built) {
  const points = stitchingPoints(built);
  if (points.length > 0) {
    throw new DraftError('the two cloths are stitched together', {
      points: points.length,
      first: points[0],
    });
  }
  return built;
}

/** Pull one layer back out of a double cloth, on its own thread numbering. */
export function extractLayer(built, layer) {
  oneOf(layer, 'layer', LAYERS);
  const ends = built.endLayers
    .map((value, index) => (value === layer ? index : -1))
    .filter((index) => index !== -1);
  const picks = built.pickLayers
    .map((value, index) => (value === layer ? index : -1))
    .filter((index) => index !== -1);
  return picks.map((p) => ends.map((e) => built.cloth[p][e]));
}

/** How many ends and picks each layer takes in the combined repeat. */
export function layerCounts(built) {
  return {
    faceEnds: built.endLayers.filter((layer) => layer === 'face').length,
    backEnds: built.endLayers.filter((layer) => layer === 'back').length,
    facePicks: built.pickLayers.filter((layer) => layer === 'face').length,
    backPicks: built.pickLayers.filter((layer) => layer === 'back').length,
  };
}
