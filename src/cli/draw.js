import { formatMatrix } from '../util/matrix.js';
import { renderTable, renderPairs } from '../util/table.js';
import { straightDraw, pointDraw } from '../draft/threading.js';
import { twillTieup, directTieup } from '../draft/tieup.js';
import { straightTreadling, trompAsWrit } from '../draft/treadling.js';
import { createDraft, draftSummary, inspectDraft } from '../draft/draft.js';
import { drawdownFromDraft } from '../draft/drawdown.js';
import { longestFloat, floatHistogram } from '../analysis/floats.js';
import { clothReport, renderReport } from '../analysis/report.js';
import { checkOptions } from './parse.js';
import { define, specOf } from './registry.js';

/**
 * The commands that draw a draft and read the cloth it makes.
 *
 * They all take the same five options, declared once below, because a default
 * that differs between two commands drawing the same thing is a bug nobody
 * notices until the two answers disagree.
 */

/** What this file puts in the register. */
export const DRAW_COMMANDS = ['draft', 'floats', 'check', 'report'];

export const DRAW_OPTIONS = {
  style: { type: 'string', default: 'twill' },
  shafts: { type: 'integer', default: 4 },
  ends: { type: 'integer', default: 16 },
  picks: { type: 'integer', default: 16 },
  notation: { type: 'string', default: '2/2' },
};

/** Build the draft every command here draws. */
export function buildDraft({ style, shafts, ends, picks, notation }) {
  if (style === 'point') {
    return createDraft({
      name: `point on ${shafts}`,
      shafts,
      treadles: shafts,
      threading: pointDraw(shafts, ends),
      tieup: directTieup(shafts),
      treadling: trompAsWrit(pointDraw(shafts, picks)),
    });
  }
  const raise = Number.parseInt(notation.split('/')[0], 10);
  return createDraft({
    name: `${notation} twill on ${shafts}`,
    shafts,
    treadles: shafts,
    threading: straightDraw(shafts, ends),
    tieup: twillTieup(shafts, Math.min(Math.max(raise, 1), shafts - 1)),
    treadling: straightTreadling(shafts, picks),
  });
}

define('draft', 'draw a named draft and print its cloth', { ...DRAW_OPTIONS }, (parsed) => {
  const options = checkOptions(parsed, specOf('draft'));
  const draft = buildDraft(options);
  return [renderPairs(draftSummary(draft)), '', formatMatrix(drawdownFromDraft(draft))].join('\n');
});

define('floats', 'measure the floats in a drawn draft', { ...DRAW_OPTIONS }, (parsed) => {
  const options = checkOptions(parsed, specOf('floats'));
  const cloth = drawdownFromDraft(buildDraft(options));
  const { warp, weft } = floatHistogram(cloth);
  const rows = [...warp.entries()]
    .map(([length, count]) => ({ direction: 'warp', length, count }))
    .concat([...weft.entries()].map(([length, count]) => ({ direction: 'weft', length, count })))
    .sort((a, b) => a.direction.localeCompare(b.direction) || a.length - b.length);
  const columns = [{ key: 'direction' }, { key: 'length' }, { key: 'count' }];
  return [renderPairs(longestFloat(cloth)), '', renderTable(rows, columns)].join('\n');
});

define('check', 'report anything wrong with a drawn draft', { ...DRAW_OPTIONS }, (parsed) => {
  const problems = inspectDraft(buildDraft(checkOptions(parsed, specOf('check'))));
  if (problems.length === 0) return 'nothing wrong with it';
  return renderTable(problems, [{ key: 'code' }, { key: 'message' }]);
});

define(
  'report',
  'measure a drawn draft every way the package can',
  {
    ...DRAW_OPTIONS,
    count: { type: 'number', default: 30 },
    system: { type: 'string', default: 'ne' },
    floatLimit: { type: 'integer', default: 3 },
  },
  (parsed) => {
    const options = checkOptions(parsed, specOf('report'));
    return renderReport(clothReport(buildDraft(options), options));
  },
);
