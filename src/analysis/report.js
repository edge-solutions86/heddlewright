import { plainObject, positiveNumber } from '../util/check.js';
import { renderPairs, renderTable } from '../util/table.js';
import { dimensions } from '../util/matrix.js';
import { drawdownFromDraft, warpFaceRatio } from '../draft/drawdown.js';
import { inspectDraft } from '../draft/draft.js';
import { longestFloat, floatsOver, meanFloatLength } from './floats.js';
import { interlacementFactor, firmnessClass, ashenhurstDivisor } from './interlacement.js';
import { ashenhurstSett, settFactor, coverFactor, isJammed } from './sett.js';
import { clothWeightGsm, gsmToOuncesPerSquareYard } from './weight.js';
import { motif, compression } from './motif.js';

/**
 * The cloth report.
 *
 * One draft in, one reading out, gathered from every part of the package that
 * measures something. It is the document a designer signs off, so nothing in it
 * is stated that was not derived: every figure here comes from the drawdown or
 * from the construction, and the report says which.
 */

/** The structure of the cloth, read off the drawdown alone. */
export function structureSection(draft) {
  const cloth = drawdownFromDraft(draft);
  const size = dimensions(cloth);
  const found = motif(cloth);
  return {
    picks: size.rows,
    ends: size.cols,
    repeatPicks: found.picks,
    repeatEnds: found.ends,
    repeatShare: compression(cloth).ratio,
    warpFace: warpFaceRatio(cloth),
    interlacement: interlacementFactor(cloth),
    firmness: firmnessClass(cloth),
  };
}

/** The floats, which is what decides whether the cloth will wear. */
export function floatSection(draft, { floatLimit = 3 } = {}) {
  const cloth = drawdownFromDraft(draft);
  const longest = longestFloat(cloth);
  const mean = meanFloatLength(cloth);
  return {
    longestWarp: longest.warp,
    longestWeft: longest.weft,
    meanWarp: mean.warp,
    meanWeft: mean.weft,
    floatLimit,
    over: floatsOver(cloth, floatLimit).length,
  };
}

/** The sett and the cover, which need the yarn as well as the cloth. */
export function settSection(draft, { count, system = 'ne', endsPerInch, picksPerInch }) {
  positiveNumber(count, 'count');
  const cloth = drawdownFromDraft(draft);
  const sett = ashenhurstSett(cloth, count, { system });
  const cover =
    endsPerInch === undefined
      ? null
      : coverFactor({ endsPerInch, picksPerInch, warpCount: count, weftCount: count });
  return {
    diameters: sett.diameters,
    settFactor: settFactor(cloth).warp,
    maximumEndsPerInch: sett.warp,
    maximumPicksPerInch: sett.weft,
    ashenhurstDivisor: ashenhurstDivisor(cloth).warp,
    cover,
    jammed: cover === null ? null : isJammed(cover),
  };
}

/** The weight, which needs the construction rather than the drawdown. */
export function weightSection(construction) {
  const weight = clothWeightGsm(construction);
  return {
    warpGsm: weight.warp,
    weftGsm: weight.weft,
    gsm: weight.total,
    ouncesPerSquareYard: gsmToOuncesPerSquareYard(weight.total),
    warpShare: weight.warpShare,
  };
}

/** The whole report. */
export function clothReport(draft, options) {
  plainObject(options, 'options');
  return {
    name: draft.name,
    structure: structureSection(draft),
    floats: floatSection(draft, options),
    sett: settSection(draft, options),
    weight: options.construction === undefined ? null : weightSection(options.construction),
    problems: inspectDraft(draft),
  };
}

/** The report printed for a designer to sign. */
export function renderReport(report) {
  const blocks = [
    report.name,
    '',
    'Structure',
    renderPairs(report.structure),
    '',
    'Floats',
    renderPairs(report.floats),
    '',
    'Sett',
    renderPairs({ ...report.sett, cover: report.sett.cover?.cloth ?? 'not given' }),
  ];
  if (report.weight !== null) {
    blocks.push('', 'Weight', renderPairs(report.weight));
  }
  if (report.problems.length > 0) {
    blocks.push(
      '',
      'Problems',
      renderTable(report.problems, [{ key: 'code' }, { key: 'message' }]),
    );
  }
  return blocks.join('\n');
}

/** Whether a report clears a set of house limits. */
export function meetsSpecification(report, { maxFloat, minInterlacement, maxGsm } = {}) {
  const findings = [];
  if (maxFloat !== undefined && report.structure !== undefined) {
    const longest = Math.max(report.floats.longestWarp, report.floats.longestWeft);
    if (longest > maxFloat) {
      findings.push({ code: 'float_too_long', got: longest, limit: maxFloat });
    }
  }
  if (minInterlacement !== undefined && report.structure.interlacement < minInterlacement) {
    findings.push({
      code: 'too_open',
      got: report.structure.interlacement,
      limit: minInterlacement,
    });
  }
  if (maxGsm !== undefined && report.weight !== null && report.weight.gsm > maxGsm) {
    findings.push({ code: 'too_heavy', got: report.weight.gsm, limit: maxGsm });
  }
  return { meets: findings.length === 0, findings };
}
