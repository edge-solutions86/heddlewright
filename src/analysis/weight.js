import { number, positiveNumber } from '../util/check.js';
import { roundTo } from '../util/numeric.js';
import { toTex } from '../yarn/count.js';

/**
 * Cloth weight from construction.
 *
 * A square metre of cloth holds one hundred times the ends per centimetre in
 * metres of warp, each longer than the cloth by its crimp, and the same again
 * in weft. Multiply that length by the yarn's tex and the grams fall out. The
 * whole calculation is two lines, and getting the crimp on the right system is
 * the only place it goes wrong.
 *
 *   grams per square metre = 0.1 x threads per cm x (1 + crimp) x tex
 */

const OUNCES_PER_GRAM = 1 / 28.349523125;
const SQUARE_YARDS_PER_SQUARE_METRE = 1 / 0.83612736;

/** The grams a square metre of warp weighs. */
export function warpWeightGsm({ endsPerCm, warpCount, system = 'tex', warpCrimp = 0 }) {
  positiveNumber(endsPerCm, 'endsPerCm');
  number(warpCrimp, 'warpCrimp', { min: 0 });
  const tex = toTex(warpCount, system);
  return roundTo(0.1 * endsPerCm * (1 + warpCrimp / 100) * tex, 3);
}

/** The grams a square metre of weft weighs. */
export function weftWeightGsm({ picksPerCm, weftCount, system = 'tex', weftCrimp = 0 }) {
  positiveNumber(picksPerCm, 'picksPerCm');
  number(weftCrimp, 'weftCrimp', { min: 0 });
  const tex = toTex(weftCount, system);
  return roundTo(0.1 * picksPerCm * (1 + weftCrimp / 100) * tex, 3);
}

/** The whole cloth, split so a designer can see which system is carrying it. */
export function clothWeightGsm(construction) {
  const warp = warpWeightGsm(construction);
  const weft = weftWeightGsm(construction);
  return {
    warp,
    weft,
    total: roundTo(warp + weft, 3),
    warpShare: roundTo(warp / (warp + weft), 4),
  };
}

/** Grams a square metre in ounces a square yard, the other trade unit. */
export function gsmToOuncesPerSquareYard(gsm) {
  positiveNumber(gsm, 'gsm');
  return roundTo((gsm * OUNCES_PER_GRAM) / SQUARE_YARDS_PER_SQUARE_METRE, 3);
}

/** And back the other way. */
export function ouncesPerSquareYardToGsm(ounces) {
  positiveNumber(ounces, 'ounces');
  return roundTo((ounces * SQUARE_YARDS_PER_SQUARE_METRE) / OUNCES_PER_GRAM, 3);
}

/** What a length of cloth weighs, in kilograms. */
export function pieceWeightKg({ gsm, metres, widthMetres }) {
  positiveNumber(gsm, 'gsm');
  positiveNumber(metres, 'metres');
  positiveNumber(widthMetres, 'widthMetres');
  return roundTo((gsm * metres * widthMetres) / 1000, 3);
}

/** The running weight of a cloth, grams per linear metre at its own width. */
export function gramsPerLinearMetre({ gsm, widthMetres }) {
  positiveNumber(gsm, 'gsm');
  positiveNumber(widthMetres, 'widthMetres');
  return roundTo(gsm * widthMetres, 3);
}

/**
 * The yarn cost of a square metre, from the two counts and the two prices. It
 * follows the same split as the weight, which is what makes a warp-heavy cloth
 * expensive when the warp yarn is the dear one.
 */
export function yarnCostPerSquareMetre(construction, { warpPricePerKg, weftPricePerKg }) {
  positiveNumber(warpPricePerKg, 'warpPricePerKg');
  positiveNumber(weftPricePerKg, 'weftPricePerKg');
  const weight = clothWeightGsm(construction);
  const warp = (weight.warp / 1000) * warpPricePerKg;
  const weft = (weight.weft / 1000) * weftPricePerKg;
  return {
    warp: roundTo(warp, 4),
    weft: roundTo(weft, 4),
    total: roundTo(warp + weft, 4),
  };
}

/** The picks per centimetre a target weight needs, holding everything else. */
export function picksForWeight({
  targetGsm,
  endsPerCm,
  warpCount,
  weftCount,
  system = 'tex',
  warpCrimp = 0,
  weftCrimp = 0,
}) {
  positiveNumber(targetGsm, 'targetGsm');
  const warp = warpWeightGsm({ endsPerCm, warpCount, system, warpCrimp });
  const remaining = targetGsm - warp;
  if (remaining <= 0) return 0;
  const tex = toTex(weftCount, system);
  return roundTo(remaining / (0.1 * (1 + weftCrimp / 100) * tex), 3);
}
