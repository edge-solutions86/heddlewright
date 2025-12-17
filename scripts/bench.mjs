import { straightDraw } from '../src/draft/threading.js';
import { twillTieup } from '../src/draft/tieup.js';
import { straightTreadling } from '../src/draft/treadling.js';
import { createDraft } from '../src/draft/draft.js';
import { drawdownFromDraft } from '../src/draft/drawdown.js';
import { warpFloats, weftFloats } from '../src/analysis/floats.js';
import { interlacementFactor } from '../src/analysis/interlacement.js';
import { defineMachine } from '../src/jacquard/machine.js';
import { deckFromLiftplan, deckId } from '../src/jacquard/deck.js';
import { twillWeave } from '../src/weave/twill.js';

/**
 * The benchmarks.
 *
 * These are not a performance target and nothing here fails on a slow machine.
 * They exist so that a change which makes the drawdown quadratic shows up as a
 * number somebody can see, rather than as a suite that has quietly started
 * taking four minutes.
 */

/** Time one call, in milliseconds, over a number of rounds. */
export function time(rounds, work) {
  const started = process.hrtime.bigint();
  let last;
  for (let round = 0; round < rounds; round += 1) last = work(round);
  const elapsed = Number(process.hrtime.bigint() - started) / 1e6;
  return { rounds, milliseconds: Math.round(elapsed * 100) / 100, last };
}

/** A draft of a given size, for the drawdown benchmarks. */
export function benchDraft(ends, picks) {
  return createDraft({
    name: `bench ${ends} by ${picks}`,
    shafts: 8,
    treadles: 8,
    threading: straightDraw(8, ends),
    tieup: twillTieup(8, 3),
    treadling: straightTreadling(8, picks),
  });
}

/** Every benchmark, each returning a name and a timing. */
export function benchmarks() {
  return [
    {
      name: 'drawdown 200 by 200',
      run: () => time(20, () => drawdownFromDraft(benchDraft(200, 200)).length),
    },
    {
      name: 'floats over a 200 by 200 cloth',
      run: () => {
        const cloth = drawdownFromDraft(benchDraft(200, 200));
        return time(20, () => warpFloats(cloth).length + weftFloats(cloth).length);
      },
    },
    {
      name: 'interlacement over a 200 by 200 cloth',
      run: () => {
        const cloth = drawdownFromDraft(benchDraft(200, 200));
        return time(20, () => interlacementFactor(cloth));
      },
    },
    {
      name: 'cutting a 400 card deck',
      run: () => {
        const machine = defineMachine({ name: 'bench', rows: 8, columns: 50 });
        const plan = twillWeave('3/1', { picks: 400, ends: 400 });
        return time(5, () => deckFromLiftplan(machine, plan).length);
      },
    },
    {
      name: 'identifying a 400 card deck',
      run: () => {
        const machine = defineMachine({ name: 'bench', rows: 8, columns: 50 });
        const deck = deckFromLiftplan(machine, twillWeave('3/1', { picks: 400, ends: 400 }));
        return time(5, () => deckId(deck));
      },
    },
  ];
}

/** Run them all and report the timings. */
export function runBenchmarks() {
  return benchmarks().map((benchmark) => ({ name: benchmark.name, ...benchmark.run() }));
}

const invoked = process.argv[1] !== undefined && process.argv[1].endsWith('bench.mjs');
if (invoked) {
  for (const result of runBenchmarks()) {
    const each = (result.milliseconds / result.rounds).toFixed(2);
    console.log(`${result.name}: ${result.rounds} rounds, ${result.milliseconds} ms, ${each} each`);
  }
}
