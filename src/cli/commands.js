import { NotFound } from '../util/errors.js';
import { nonEmptyString } from '../util/check.js';
import { formatMatrix } from '../util/matrix.js';
import { renderTable, renderPairs } from '../util/table.js';
import { straightDraw, pointDraw } from '../draft/threading.js';
import { twillTieup, directTieup } from '../draft/tieup.js';
import { straightTreadling, trompAsWrit } from '../draft/treadling.js';
import { createDraft, draftSummary, inspectDraft } from '../draft/draft.js';
import { drawdownFromDraft } from '../draft/drawdown.js';
import { twillWeave, twillAngle } from '../weave/twill.js';
import { satinWeave, regularSatinMoves } from '../weave/satin.js';
import { longestFloat, floatHistogram } from '../analysis/floats.js';
import { ashenhurstSett } from '../analysis/sett.js';
import { convertCount, COUNT_SYSTEMS } from '../yarn/count.js';
import { clothReport, renderReport } from '../analysis/report.js';
import { measureAll, weaveByName, families } from '../weave/catalogue.js';
import { smallestMachineFor } from '../jacquard/machine.js';
import { deckFromLiftplan, deckLength, deckRepeat, totalHoles } from '../jacquard/deck.js';
import { quoteDeck } from '../jacquard/estimate.js';
import { checkOptions, usageFor } from './parse.js';

/**
 * The commands the tool offers.
 *
 * Each one is a spec and a run function that returns text. Nothing here writes
 * to a stream, so every command can be tested by reading what it says rather
 * than by capturing output.
 */

const COMMANDS = new Map();

function define(name, summary, spec, run) {
  COMMANDS.set(name, { name, summary, spec, run });
}

/**
 * The options every drawing command takes. They were written out four times
 * over, which meant a default could be changed in one place and not the others.
 */
const DRAW_OPTIONS = {
  style: { type: 'string', default: 'twill' },
  shafts: { type: 'integer', default: 4 },
  ends: { type: 'integer', default: 16 },
  picks: { type: 'integer', default: 16 },
  notation: { type: 'string', default: '2/2' },
};

define('draft', 'draw a named draft and print its cloth', { ...DRAW_OPTIONS }, (parsed) => {
  const options = checkOptions(parsed, COMMANDS.get('draft').spec);
  const draft = buildDraft(options);
  const cloth = drawdownFromDraft(draft);
  return [renderPairs(draftSummary(draft)), '', formatMatrix(cloth)].join('\n');
});

define('floats', 'measure the floats in a drawn draft', { ...DRAW_OPTIONS }, (parsed) => {
  const options = checkOptions(parsed, COMMANDS.get('floats').spec);
  const cloth = drawdownFromDraft(buildDraft(options));
  const found = longestFloat(cloth);
  const { warp, weft } = floatHistogram(cloth);
  const rows = [...warp.entries()]
    .map(([length, count]) => ({ direction: 'warp', length, count }))
    .concat([...weft.entries()].map(([length, count]) => ({ direction: 'weft', length, count })))
    .sort((a, b) => a.direction.localeCompare(b.direction) || a.length - b.length);
  const columns = [{ key: 'direction' }, { key: 'length' }, { key: 'count' }];
  return [renderPairs(found), '', renderTable(rows, columns)].join('\n');
});

define(
  'weave',
  'print one repeat of a named weave',
  {
    kind: { type: 'string', default: 'twill' },
    notation: { type: 'string', default: '2/2' },
    threads: { type: 'integer', default: 5 },
    move: { type: 'integer', default: 2 },
  },
  (parsed) => {
    const options = checkOptions(parsed, COMMANDS.get('weave').spec);
    if (options.kind === 'satin') {
      const moves = regularSatinMoves(options.threads);
      const header = `satin ${options.threads} move ${options.move}, legal moves ${moves.join(' ')}`;
      return [header, formatMatrix(satinWeave(options.threads, options.move))].join('\n');
    }
    return formatMatrix(twillWeave(options.notation));
  },
);

define(
  'sett',
  'work out the sett a cloth and a yarn will take',
  {
    notation: { type: 'string', default: '2/2' },
    count: { type: 'number', required: true },
    system: { type: 'string', default: 'ne' },
    picksPerInch: { type: 'number', default: 24 },
    endsPerInch: { type: 'number', default: 24 },
  },
  (parsed) => {
    const options = checkOptions(parsed, COMMANDS.get('sett').spec);
    const cloth = twillWeave(options.notation);
    const sett = ashenhurstSett(cloth, options.count, { system: options.system });
    const angle = twillAngle({
      endsPerInch: options.endsPerInch,
      picksPerInch: options.picksPerInch,
    });
    return renderPairs({ ...sett, twillAngle: angle });
  },
);

define(
  'count',
  'convert a yarn count between systems',
  {
    value: { type: 'number', required: true },
    from: { type: 'string', required: true },
    to: { type: 'string', default: 'tex' },
  },
  (parsed) => {
    const options = checkOptions(parsed, COMMANDS.get('count').spec);
    const converted = convertCount(options.value, options.from, options.to);
    return `${options.value} ${options.from} = ${converted} ${options.to}`;
  },
);

define('systems', 'list the yarn count systems the tool knows', {}, () => {
  const rows = [...COUNT_SYSTEMS.entries()].map(([name, spec]) => ({
    system: name,
    kind: spec.kind,
    meaning: spec.label,
  }));
  return renderTable(rows, [{ key: 'system' }, { key: 'kind' }, { key: 'meaning' }]);
});

define('check', 'report anything wrong with a drawn draft', { ...DRAW_OPTIONS }, (parsed) => {
  const options = checkOptions(parsed, COMMANDS.get('check').spec);
  const problems = inspectDraft(buildDraft(options));
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
    const options = checkOptions(parsed, COMMANDS.get('report').spec);
    return renderReport(clothReport(buildDraft(options), options));
  },
);

define(
  'catalogue',
  'list the weaves the package knows, measured',
  { family: { type: 'string' } },
  (parsed) => {
    const options = checkOptions(parsed, COMMANDS.get('catalogue').spec);
    const rows = measureAll().filter(
      (row) => options.family === null || row.family === options.family,
    );
    if (rows.length === 0) {
      return `no weaves in that family. families: ${families().join(', ')}`;
    }
    const columns = [
      { key: 'name' },
      { key: 'family' },
      { key: 'warpFace', label: 'warp face' },
      { key: 'longestFloat', label: 'float' },
      { key: 'firmness' },
    ];
    return renderTable(rows, columns);
  },
);

define(
  'deck',
  'cut a card deck for a named weave and price it',
  {
    weave: { type: 'string', default: 'twill 2/2' },
    repeats: { type: 'integer', default: 1 },
  },
  (parsed) => {
    const options = checkOptions(parsed, COMMANDS.get('deck').spec);
    const repeat = weaveByName(options.weave);
    const machine = smallestMachineFor(repeat[0].length);
    const plan = [];
    for (let round = 0; round < options.repeats; round += 1) plan.push(...repeat);
    const deck = deckFromLiftplan(machine, plan);
    const quote = quoteDeck(deck);
    return renderPairs({
      weave: options.weave,
      machine: machine.name,
      cards: deckLength(deck),
      repeat: deckRepeat(deck),
      holes: totalHoles(deck),
      poundsAndPence: (quote.totalPence / 100).toFixed(2),
    });
  },
);

/** Build the draft the drawing commands share. */
export function buildDraft({ style, shafts, ends, picks, notation }) {
  if (style === 'point') {
    const threading = pointDraw(shafts, ends);
    return createDraft({
      name: `point on ${shafts}`,
      shafts,
      treadles: shafts,
      threading,
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

/** Every command, in name order. */
export function listCommands() {
  return [...COMMANDS.values()].sort((a, b) => a.name.localeCompare(b.name));
}

/** Look a command up. */
export function commandByName(name) {
  nonEmptyString(name, 'name');
  const found = COMMANDS.get(name);
  if (found === undefined) {
    throw new NotFound(`no command called "${name}"`, { name, known: [...COMMANDS.keys()] });
  }
  return found;
}

/** The usage text for one command. */
export function commandUsage(name) {
  const command = commandByName(name);
  return usageFor(command.name, command.spec, { summary: command.summary });
}

/** The usage text for the whole tool. */
export function toolUsage() {
  const rows = listCommands().map((command) => ({
    command: command.name,
    summary: command.summary,
  }));
  return [
    'heddle <command> [options]',
    '',
    renderTable(rows, [{ key: 'command' }, { key: 'summary' }]),
  ].join('\n');
}
