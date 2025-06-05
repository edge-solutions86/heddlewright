import { InvalidArgument } from '../util/errors.js';
import { number, positiveInteger, positiveNumber } from '../util/check.js';
import { roundTo } from '../util/numeric.js';

/**
 * Warping plans.
 *
 * A creel holds a few hundred packages; a warp needs a few thousand ends. The
 * two ways round that are to warp the full width several times onto back beams
 * and beam them together, or to build the warp in sections across a drum and
 * wind the whole thing off at once.
 *
 * Sectional warping is where the arithmetic bites: the sections have to be the
 * same width or the beam builds up unevenly, so the ends are spread rather than
 * simply divided, and the last section is never allowed to be the thin one.
 */

/** How many back beams a direct warping needs, and how the ends divide. */
export function beamWarpingPlan({ totalEnds, creelCapacity, warpLengthMetres }) {
  positiveInteger(totalEnds, 'totalEnds');
  positiveInteger(creelCapacity, 'creelCapacity');
  positiveNumber(warpLengthMetres, 'warpLengthMetres');
  const beams = Math.ceil(totalEnds / creelCapacity);
  const endsPerBeam = balanceEnds(totalEnds, beams);
  return {
    beams,
    endsPerBeam,
    creelCapacity,
    warpLengthMetres,
    creelFillsPerBeam: 1,
    totalEnds,
  };
}

/**
 * Spread `totalEnds` over `groups` as evenly as whole ends allow, giving the
 * extra ends to the earliest groups so no group ends up thin.
 */
export function balanceEnds(totalEnds, groups) {
  positiveInteger(totalEnds, 'totalEnds');
  positiveInteger(groups, 'groups');
  if (groups > totalEnds) {
    throw new InvalidArgument('there are fewer ends than groups to spread them over', {
      totalEnds,
      groups,
    });
  }
  const base = Math.floor(totalEnds / groups);
  const extra = totalEnds % groups;
  return Array.from({ length: groups }, (_, index) => base + (index < extra ? 1 : 0));
}

/** A sectional warping plan: how many sections, how wide, and how many ends. */
export function sectionalPlan({ totalEnds, creelCapacity, endsPerInch, warpLengthMetres }) {
  positiveInteger(totalEnds, 'totalEnds');
  positiveInteger(creelCapacity, 'creelCapacity');
  positiveNumber(endsPerInch, 'endsPerInch');
  positiveNumber(warpLengthMetres, 'warpLengthMetres');
  const sections = Math.ceil(totalEnds / creelCapacity);
  const endsPerSection = balanceEnds(totalEnds, sections);
  return {
    sections,
    endsPerSection,
    widthPerSectionInches: endsPerSection.map((ends) => roundTo(ends / endsPerInch, 3)),
    totalWidthInches: roundTo(totalEnds / endsPerInch, 3),
    warpLengthMetres,
    evenSections: new Set(endsPerSection).size === 1,
  };
}

/**
 * How much the section widths vary. A beam wound from sections that differ by
 * more than a few ends builds up unevenly and the warp tension follows it.
 */
export function sectionSpread(plan) {
  const widths = plan.endsPerSection;
  const smallest = Math.min(...widths);
  const largest = Math.max(...widths);
  return { smallest, largest, spread: largest - smallest };
}

/** Refuse a plan whose sections differ by more than the shed will accept. */
export function assertSectionsEven(plan, { maxSpread = 1 } = {}) {
  number(maxSpread, 'maxSpread', { min: 0 });
  const spread = sectionSpread(plan);
  if (spread.spread > maxSpread) {
    throw new InvalidArgument('the sections are too uneven to wind a level beam', {
      ...spread,
      maxSpread,
    });
  }
  return plan;
}

/**
 * The yarn a warping run uses, in metres, including the tail left in the creel
 * and the length lost at the change-over between sections.
 */
export function warpingYarnMetres({
  totalEnds,
  warpLengthMetres,
  creelWasteMetres = 2,
  sections = 1,
}) {
  positiveInteger(totalEnds, 'totalEnds');
  positiveNumber(warpLengthMetres, 'warpLengthMetres');
  number(creelWasteMetres, 'creelWasteMetres', { min: 0 });
  positiveInteger(sections, 'sections');
  const useful = totalEnds * warpLengthMetres;
  const waste = totalEnds * creelWasteMetres * sections;
  return {
    useful: roundTo(useful, 3),
    waste: roundTo(waste, 3),
    total: roundTo(useful + waste, 3),
    wasteShare: roundTo(waste / (useful + waste), 4),
  };
}

/** How long a warping run takes at a given warping speed. */
export function warpingHours({ warpLengthMetres, sections, metresPerMinute, changeMinutes = 8 }) {
  positiveNumber(warpLengthMetres, 'warpLengthMetres');
  positiveInteger(sections, 'sections');
  positiveNumber(metresPerMinute, 'metresPerMinute');
  number(changeMinutes, 'changeMinutes', { min: 0 });
  const running = (warpLengthMetres * sections) / metresPerMinute;
  const changes = changeMinutes * Math.max(0, sections - 1);
  return {
    runningMinutes: roundTo(running, 2),
    changeMinutes: roundTo(changes, 2),
    hours: roundTo((running + changes) / 60, 3),
  };
}
