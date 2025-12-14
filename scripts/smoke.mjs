import { createApp } from '../src/service/app.js';
import { createServer, listen, close } from '../src/service/server.js';
import { straightDraw } from '../src/draft/threading.js';
import { twillTieup } from '../src/draft/tieup.js';
import { straightTreadling } from '../src/draft/treadling.js';

/**
 * The smoke test.
 *
 * It starts the service on a real socket and walks one order of work through
 * it end to end: store a draft, read its cloth, open a piece, book a fault,
 * grade it, register a loom and get a plan back. Anything the unit tests can
 * fake, this one does not.
 */

const DRAFT = {
  name: 'smoke twill',
  shafts: 4,
  treadles: 4,
  threading: straightDraw(4, 8),
  tieup: twillTieup(4, 2),
  treadling: straightTreadling(4, 8),
};

/** One step of the walk: what was asked, what came back, and whether it passed. */
function step(name, expected, actual) {
  return { name, expected, actual, ok: expected === actual };
}

/** Walk the whole service and report every step. */
export async function smoke() {
  const running = createServer(createApp());
  const port = await listen(running);
  const base = `http://127.0.0.1:${port}`;
  const steps = [];
  try {
    const health = await (await fetch(`${base}/health`)).json();
    steps.push(step('health answers', true, health.ok));

    const created = await fetch(`${base}/drafts`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(DRAFT),
    });
    const draft = await created.json();
    steps.push(step('draft stored', 201, created.status));

    const cloth = await (await fetch(`${base}/drafts/${draft.id}/cloth?rows=text`)).json();
    steps.push(step('cloth derived', 'XX..XX..', cloth.rows[0]));

    const analysis = await (await fetch(`${base}/drafts/${draft.id}/analysis`)).json();
    steps.push(step('floats measured', 2, analysis.longestFloat.warp));

    const piece = await fetch(`${base}/pieces`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ reference: 'SMOKE-1', yards: 100, widthInches: 60 }),
    });
    steps.push(step('piece opened', 201, piece.status));

    await fetch(`${base}/pieces/SMOKE-1/defects`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ defects: [{ code: 'weft_bar', yard: 3, inches: 12 }] }),
    });
    const grade = await (await fetch(`${base}/pieces/SMOKE-1/grade`)).json();
    steps.push(step('piece graded', 4, grade.score.totalPoints));

    const loom = await fetch(`${base}/looms`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({
        reference: 'SMOKE-L1',
        shedding: 'dobby',
        reedWidthCm: 200,
        shafts: 16,
        picksMinute: 220,
      }),
    });
    steps.push(step('loom registered', 201, loom.status));

    const plan = await (
      await fetch(`${base}/plan`, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          runs: [
            {
              order: 'SMOKE-SO-1',
              style: 'smoke twill',
              metres: 100,
              picksPerCm: 20,
              requirements: { widthCm: 150, shafts: 4, hooks: 0, weftColours: 1 },
            },
          ],
        }),
      })
    ).json();
    steps.push(step('plan made', 'SMOKE-L1', plan.placed[0].loom));

    const sound = await (await fetch(`${base}/journal/verify`)).json();
    steps.push(step('journal sound', true, sound.sound));
  } finally {
    await close(running);
  }
  return { steps, failures: steps.filter((entry) => !entry.ok) };
}

const invoked = process.argv[1] !== undefined && process.argv[1].endsWith('smoke.mjs');
if (invoked) {
  const result = await smoke();
  for (const entry of result.steps) {
    console.log(`${entry.ok ? 'ok  ' : 'FAIL'} ${entry.name}`);
  }
  for (const entry of result.failures) {
    console.error(`  ${entry.name}: wanted ${entry.expected}, got ${entry.actual}`);
  }
  process.exitCode = result.failures.length === 0 ? 0 : 1;
}
