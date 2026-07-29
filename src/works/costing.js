import { InvalidArgument } from '../util/errors.js';
import { number, plainObject, positiveNumber } from '../util/check.js';
import { roundTo } from '../util/numeric.js';
import { clothWeightGsm } from '../analysis/weight.js';
import { allotBoxes, boxChain, speedWithChanging } from '../loom/boxmotion.js';

/**
 * Costing a cloth.
 *
 * A metre of cloth costs its yarn and its loom time, and which of the two
 * dominates is not a general fact: it turns on the pick density. At twenty
 * picks a centimetre a loom running two hundred picks a minute makes about five
 * metres an hour, so an hour of loom time is spread over very little cloth and
 * the weaving is most of the cost. Open the sett out and the yarn takes over.
 *
 * Nothing here decides anything. It lays the two terms out separately so a
 * designer can see which one their change actually moved.
 *
 * A colour woven cloth pays a third thing that neither term shows: the loom
 * stops for its box to lift on every change of weft, so the same sett on the
 * same loom weaves slower. That is loom time and it is costed as loom time.
 */

/** Yarn cost for a metre of cloth at its full width. */
export function yarnCostPerMetre(construction, prices, { widthMetres }) {
  plainObject(prices, 'prices');
  positiveNumber(widthMetres, 'widthMetres');
  positiveNumber(prices.warpPricePerKg, 'prices.warpPricePerKg');
  positiveNumber(prices.weftPricePerKg, 'prices.weftPricePerKg');
  const weight = clothWeightGsm(construction);
  const warp = (weight.warp / 1000) * widthMetres * prices.warpPricePerKg;
  const weft = (weight.weft / 1000) * widthMetres * prices.weftPricePerKg;
  return {
    warp: roundTo(warp, 4),
    weft: roundTo(weft, 4),
    total: roundTo(warp + weft, 4),
    gsm: weight.total,
  };
}

/**
 * The speed a weft order leaves, which is the quoted speed less the box
 * changing. A cloth with one weft never changes and runs at the quoted figure.
 */
export function runningSpeed({ picksMinute, weftPattern, boxes }) {
  positiveNumber(picksMinute, 'picksMinute');
  if (weftPattern === undefined) return picksMinute;
  const allotment = allotBoxes(weftPattern, { boxes: boxes ?? new Set(weftPattern).size });
  return speedWithChanging(boxChain(weftPattern, allotment), { picksMinute });
}

/**
 * Loom cost for a metre, from the speed, the sett and an hourly rate.
 *
 * A weft pattern may be given, in which case the speed used is the one the box
 * motion actually leaves rather than the one on the loom's plate.
 */
export function loomCostPerMetre({
  picksMinute,
  picksPerCm,
  efficiency = 0.85,
  ratePerHour,
  weftPattern,
  boxes,
}) {
  positiveNumber(picksPerCm, 'picksPerCm');
  positiveNumber(ratePerHour, 'ratePerHour');
  number(efficiency, 'efficiency', { min: 0.01, max: 1 });
  const running = runningSpeed({ picksMinute, weftPattern, boxes });
  const metresPerHour = ((running * 60) / picksPerCm / 100) * efficiency;
  return roundTo(ratePerHour / metresPerHour, 4);
}

/** Waste, as a share added to the yarn rather than a cost of its own. */
export function withWaste(cost, { wasteShare = 0.04 } = {}) {
  number(wasteShare, 'wasteShare', { min: 0, max: 1 });
  return roundTo(cost * (1 + wasteShare), 4);
}

/** The whole cost of a metre, split so the parts can be argued about. */
export function costPerMetre({
  construction,
  prices,
  loom,
  widthMetres,
  wasteShare,
  overheads = 0,
}) {
  number(overheads, 'overheads', { min: 0 });
  const yarn = yarnCostPerMetre(construction, prices, { widthMetres });
  const yarnWithWaste = withWaste(yarn.total, { wasteShare });
  const weaving = loomCostPerMetre(loom);
  const total = yarnWithWaste + weaving + overheads;
  return {
    yarn: yarn.total,
    yarnWithWaste,
    weaving,
    overheads: roundTo(overheads, 4),
    total: roundTo(total, 4),
    yarnShare: roundTo(yarnWithWaste / total, 4),
    gsm: yarn.gsm,
  };
}

/** The price a wanted margin needs, on top of the cost. */
export function priceForMargin(cost, { margin }) {
  number(margin, 'margin', { min: 0, max: 0.99 });
  positiveNumber(cost, 'cost');
  return roundTo(cost / (1 - margin), 4);
}

/** The margin a price actually gives, which is the check on the one above. */
export function marginAtPrice(cost, price) {
  positiveNumber(cost, 'cost');
  positiveNumber(price, 'price');
  if (price < cost) {
    throw new InvalidArgument('that price is below the cost', { cost, price });
  }
  return roundTo((price - cost) / price, 4);
}

/**
 * Two constructions costed side by side, and which term the difference came
 * from. A change that moved the yarn and a change that moved the loom time cost
 * the same and are not the same decision.
 */
export function compare(left, right) {
  const a = costPerMetre(left);
  const b = costPerMetre(right);
  const yarnMoved = Math.abs(b.yarnWithWaste - a.yarnWithWaste);
  const weavingMoved = Math.abs(b.weaving - a.weaving);
  return {
    left: a,
    right: b,
    difference: roundTo(b.total - a.total, 4),
    movedBy: yarnMoved >= weavingMoved ? 'yarn' : 'weaving',
    yarnMoved: roundTo(yarnMoved, 4),
    weavingMoved: roundTo(weavingMoved, 4),
  };
}
