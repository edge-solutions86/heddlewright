import { formatMatrix } from '../util/matrix.js';
import { renderTable, renderPairs } from '../util/table.js';
import { twillWeave, twillAngle } from '../weave/twill.js';
import { satinWeave, regularSatinMoves } from '../weave/satin.js';
import { ashenhurstSett } from '../analysis/sett.js';
import { convertCount, COUNT_SYSTEMS } from '../yarn/count.js';
import { measureAll, weaveByName, families } from '../weave/catalogue.js';
import { smallestMachineFor } from '../jacquard/machine.js';
import { deckFromLiftplan, deckLength, deckRepeat, totalHoles } from '../jacquard/deck.js';
import { quoteDeck } from '../jacquard/estimate.js';
import { checkOptions } from './parse.js';
import { define, specOf } from './registry.js';

/**
 * The commands that answer about weaves, yarns and card decks rather than about
 * one drawn draft.
 */

/**
 * What this file puts in the register. It is exported so that a reader can see
 * what a file contributes without running it, and so that nothing here is a
 * pure side effect on import with no trace of itself.
 */
export const SHOP_COMMANDS = ['weave', 'sett', 'count', 'systems', 'catalogue', 'deck'];

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
    const options = checkOptions(parsed, specOf('weave'));
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
    const options = checkOptions(parsed, specOf('sett'));
    const sett = ashenhurstSett(twillWeave(options.notation), options.count, {
      system: options.system,
    });
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
    const options = checkOptions(parsed, specOf('count'));
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

define(
  'catalogue',
  'list the weaves the package knows, measured',
  { family: { type: 'string' } },
  (parsed) => {
    const options = checkOptions(parsed, specOf('catalogue'));
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
    const options = checkOptions(parsed, specOf('deck'));
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
