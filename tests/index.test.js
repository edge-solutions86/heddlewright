import { describe, it, expect } from 'vitest';
import { readdirSync, statSync } from 'node:fs';
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

  it('is reachable from the barrel, except the ones that are wiring', async () => {
    const wiring = new Set([
      'index.js',
      'cli/parse.js',
      'cli/commands.js',
      'cli/run.js',
      'service/draftroutes.js',
      'service/clothroutes.js',
      'service/floorroutes.js',
      'service/server.js',
    ]);
    const exported = new Set(
      Object.entries(heddlewright)
        .filter(([name]) => name !== 'VERSION')
        .flatMap(([, namespace]) => Object.keys(namespace)),
    );
    for (const file of files) {
      const key = relative(SRC, file).split(sep).join('/');
      if (wiring.has(key)) continue;
      const loaded = await import(`file://${file.split(sep).join('/')}`);
      const reachable = Object.keys(loaded).some((name) => exported.has(name));
      expect(reachable, `${key} is not reachable from the barrel`).toBe(true);
    }
  });
});
