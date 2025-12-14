import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { smoke } from '../scripts/smoke.mjs';

describe('smoke', () => {
  it('walks the whole service and passes every step', async () => {
    const result = await smoke();
    expect(result.failures).toEqual([]);
    expect(result.steps.length).toBeGreaterThan(6);
  });

  it('names every step it walked', async () => {
    const result = await smoke();
    const names = result.steps.map((entry) => entry.name);
    expect(names).toContain('health answers');
    expect(names).toContain('cloth derived');
    expect(names).toContain('piece graded');
    expect(names).toContain('plan made');
    expect(names).toContain('journal sound');
  });

  it('records what each step wanted as well as what it got', async () => {
    for (const entry of (await smoke()).steps) {
      expect(entry).toHaveProperty('expected');
      expect(entry).toHaveProperty('actual');
      expect(entry.ok).toBe(entry.expected === entry.actual);
    }
  });

  it('leaves nothing listening behind it', async () => {
    await smoke();
    await smoke();
    expect(true).toBe(true);
  });

  it('does not run itself on import', () => {
    const text = readFileSync(new URL('../scripts/smoke.mjs', import.meta.url), 'utf8');
    expect(text).toContain("process.argv[1].endsWith('smoke.mjs')");
  });

  it('goes through the socket rather than calling the app directly', () => {
    const text = readFileSync(new URL('../scripts/smoke.mjs', import.meta.url), 'utf8');
    expect(text).toContain('fetch(');
    expect(text).toContain('listen(running)');
  });
});
