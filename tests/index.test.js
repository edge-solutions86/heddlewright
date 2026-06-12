import { describe, it, expect } from 'vitest';
import { readdirSync, statSync, readFileSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import * as heddlewright from '../src/index.js';

const SRC = fileURLToPath(new URL('../src', import.meta.url));

function sourceFiles(directory = SRC) {
  const out = [];
  for (const entry of readdirSync(directory)) {
    const full = join(directory, entry);
    if (statSync(full).isDirectory()) {
      out.push(...sourceFiles(full));
      continue;
    }
    if (entry.endsWith('.js')) out.push(full);
  }
  return out.sort();
}

describe('the public surface', () => {
  it('exports a namespace per area rather than a flat pile', () => {
    for (const [name, value] of Object.entries(heddlewright)) {
      if (name === 'VERSION') continue;
      expect(typeof value).toBe('object');
      expect(Object.keys(value).length).toBeGreaterThan(0);
    }
  });

  it('carries a version', () => {
    expect(heddlewright.VERSION).toMatch(/^\d+\.\d+\.\d+$/);
  });

  it('reaches the draft layer', () => {
    expect(typeof heddlewright.draft.createDraft).toBe('function');
    expect(typeof heddlewright.drawdown.drawdownFromDraft).toBe('function');
  });

  it('reaches the jacquard layer', () => {
    expect(typeof heddlewright.deck.deckFromLiftplan).toBe('function');
    expect(typeof heddlewright.machine.listMachines).toBe('function');
  });

  it('reaches the shop floor', () => {
    expect(typeof heddlewright.schedule.schedule).toBe('function');
    expect(typeof heddlewright.orders.createOrder).toBe('function');
  });

  it('names no namespace twice', () => {
    const names = Object.keys(heddlewright);
    expect(new Set(names).size).toBe(names.length);
  });
});

describe('every source file', () => {
  const files = sourceFiles();

  it('finds a good many of them', () => {
    expect(files.length).toBeGreaterThan(40);
  });

  it('exports something', async () => {
    for (const file of files) {
      const loaded = await import(`file://${file.split(sep).join('/')}`);
      const names = Object.keys(loaded);
      expect(names.length, `${relative(SRC, file)} exports nothing`).toBeGreaterThan(0);
    }
  });

  it('is named in the barrel, except the ones that are wiring', () => {
    // The barrel's own source is read rather than its exported names. Two
    // modules can export the same name, so a name-based check passes for a
    // module nobody exported as long as some other module happens to share one
    // of its names, which is exactly the case this has to catch.
    const barrel = readFileSync(join(SRC, 'index.js'), 'utf8');
    const wiring = new Set([
      'index.js',
      'cli/parse.js',
      'cli/commands.js',
      'cli/registry.js',
      'cli/draw.js',
      'cli/shop.js',
      'cli/run.js',
      'service/draftroutes.js',
      'service/clothroutes.js',
      'service/floorroutes.js',
      'service/weaveroutes.js',
      'service/server.js',
    ]);
    for (const file of files) {
      const key = relative(SRC, file).split(sep).join('/');
      if (wiring.has(key)) continue;
      expect(barrel, `${key} is not named in the barrel`).toContain(`'./${key}'`);
    }
  });

  it('names nothing in the barrel that is not a source file', () => {
    const barrel = readFileSync(join(SRC, 'index.js'), 'utf8');
    const named = [...barrel.matchAll(/from '\.\/([^']+)'/g)].map((match) => match[1]);
    const present = new Set(files.map((file) => relative(SRC, file).split(sep).join('/')));
    for (const key of named) {
      expect(present.has(key), `the barrel names ${key}, which is not there`).toBe(true);
    }
  });
});
