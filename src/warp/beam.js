import { CapacityError, InvalidArgument } from '../util/errors.js';
import { number, positiveInteger, positiveNumber } from '../util/check.js';
import { roundTo } from '../util/numeric.js';

/**
 * The warp beam.
 *
 * A beam is a barrel with two flanges, and the warp winds on it as an annulus.
 * How much fits is geometry: the area of the ring between the barrel and the
 * flange, times the width, times how densely the yarn packs. Getting it wrong
 * means either a beam that runs out mid-piece or one that jams on the flanges
 * halfway through beaming, and the second is much the more expensive.
 */

/** The volume, in cubic centimetres, of the ring a full beam winds into. */
export function windingVolumeCm3({ barrelDiameterCm, flangeDiameterCm, widthCm }) {
  positiveNumber(barrelDiameterCm, 'barrelDiameterCm');
  positiveNumber(flangeDiameterCm, 'flangeDiameterCm');
  positiveNumber(widthCm, 'widthCm');
  if (flangeDiameterCm <= barrelDiameterCm) {
    throw new InvalidArgument('the flange has to be wider than the barrel', {
      barrelDiameterCm,
      flangeDiameterCm,
    });
  }
  const outer = (flangeDiameterCm / 2) ** 2;
  const inner = (barrelDiameterCm / 2) ** 2;
  return roundTo(Math.PI * (outer - inner) * widthCm, 3);
}

/**
 * How much yarn a beam holds, in kilograms, at a packing density. Cotton on a
 * loom beam packs at around half a gram a cubic centimetre; a shed measures its
 * own and passes it in.
 */
export function beamCapacityKg(beam, { packingDensity = 0.5 } = {}) {
  positiveNumber(packingDensity, 'packingDensity');
  return roundTo((windingVolumeCm3(beam) * packingDensity) / 1000, 3);
}

/** How long a warp of a given end count and yarn fits on the beam, in metres. */
export function beamCapacityMetres(beam, { ends, tex, packingDensity }) {
  positiveInteger(ends, 'ends');
  positiveNumber(tex, 'tex');
  const kilograms = beamCapacityKg(beam, { packingDensity });
  const gramsPerMetre = (ends * tex) / 1000;
  return roundTo((kilograms * 1000) / gramsPerMetre, 1);
}

/** How thick the wound warp is at a given length, in centimetres of build-up. */
export function buildUpCm(beam, { ends, tex, metres, packingDensity = 0.5 }) {
  positiveNumber(metres, 'metres');
  positiveInteger(ends, 'ends');
  positiveNumber(tex, 'tex');
  positiveNumber(packingDensity, 'packingDensity');
  const grams = (ends * tex * metres) / 1000;
  const volume = grams / packingDensity;
  const inner = (beam.barrelDiameterCm / 2) ** 2;
  const outer = volume / (Math.PI * beam.widthCm) + inner;
  return roundTo(Math.sqrt(outer) * 2 - beam.barrelDiameterCm, 4);
}

/** The diameter the wound warp reaches at a given length. */
export function woundDiameterCm(beam, options) {
  return roundTo(beam.barrelDiameterCm + buildUpCm(beam, options), 4);
}

/** Refuse a warp that would wind past the flanges. */
export function assertFitsBeam(beam, options) {
  const diameter = woundDiameterCm(beam, options);
  if (diameter > beam.flangeDiameterCm) {
    throw new CapacityError('that warp would wind past the flanges', {
      diameter,
      flangeDiameterCm: beam.flangeDiameterCm,
      over: roundTo(diameter - beam.flangeDiameterCm, 4),
    });
  }
  return diameter;
}

/** How full the beam is, as a share of the ring the flanges allow. */
export function fillShare(beam, options) {
  const used = woundDiameterCm(beam, options) - beam.barrelDiameterCm;
  const available = beam.flangeDiameterCm - beam.barrelDiameterCm;
  return roundTo(used / available, 4);
}

/**
 * A beam wound below a working minimum is not worth the change-over, so the
 * plan is either to lengthen the warp or to put it on a smaller beam.
 */
export function isWorthBeaming(beam, options, { minimumShare = 0.4 } = {}) {
  number(minimumShare, 'minimumShare', { min: 0, max: 1 });
  return fillShare(beam, options) >= minimumShare;
}
