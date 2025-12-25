import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import {
  routesSection,
  commandsSection,
  weavesSection,
  surface,
  check,
} from '../scripts/surface.mjs';

describe('routesSection', () => {
  it('lists every route the service answers on', () => {
    const text = routesSection();
    expect(text).toContain('GET /health');
    expect(text).toContain('POST /plan');
  });

  it('writes each route as a code span in a list', () => {
    for (const line of routesSection().split('\n').slice(2)) {
      expect(line.startsWith('- `')).toBe(true);
    }
  });
});

describe('commandsSection', () => {
  it('lists every command with its summary', () => {
    const text = commandsSection();
    expect(text).toContain('draft');
    expect(text).toContain('convert a yarn count between systems');
  });
});

describe('weavesSection', () => {
  it('reports the measurements rather than a written list', () => {
    const text = weavesSection();
    expect(text).toContain('plain');
    expect(text).toContain('satin 8');
    expect(text).toContain('longest float');
  });

  it('carries the numbers the catalogue actually measures', () => {
    expect(weavesSection()).toMatch(/satin 8\s+satin\s+8x8\s+0.875\s+7/);
  });
});

describe('surface', () => {
  it('says it is generated and how to regenerate it', () => {
    expect(surface()).toContain('npm run surface');
  });

  it('has all three sections in order', () => {
    const text = surface();
    expect(text.indexOf('## Routes')).toBeLessThan(text.indexOf('## Commands'));
    expect(text.indexOf('## Commands')).toBeLessThan(text.indexOf('## Weaves'));
  });

  it('ends with a newline', () => {
    expect(surface().endsWith('\n')).toBe(true);
  });
});

describe('check', () => {
  it('says the document on disk matches the code', () => {
    expect(check()).toEqual({ matches: true, reason: '' });
  });

  it('reads the file it checks against', () => {
    const onDisk = readFileSync(new URL('../docs/surface.md', import.meta.url), 'utf8');
    expect(onDisk).toBe(surface());
  });

  it('does not run itself on import', () => {
    const text = readFileSync(new URL('../scripts/surface.mjs', import.meta.url), 'utf8');
    expect(text).toContain("process.argv[1].endsWith('surface.mjs')");
  });
});
