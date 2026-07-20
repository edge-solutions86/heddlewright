import { readFileSync, writeFileSync, mkdirSync, readdirSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join, sep, dirname, relative, resolve } from 'node:path';

/**
 * The layer map, read off the imports rather than written down.
 *
 * Every file in `src` belongs to a layer, and the layers are ordered. A file
 * may import from its own layer or from any layer below it and from nowhere
 * else. That one rule is what keeps the weaving arithmetic free of the service
 * and the store, so a reader can pick up `weave/twill.js` without meeting a
 * router on the way.
 *
 * The document this writes is generated for the same reason the surface
 * document is: a hand-kept diagram is out of date the week after it is drawn,
 * and nobody finds out until they trust it.
 */

const ROOT = fileURLToPath(new URL('..', import.meta.url));
const SRC = join(ROOT, 'src');
const OUTPUT = join(ROOT, 'docs', 'architecture.md');

/**
 * The layers, lowest first. A file may import downwards and sideways, never up.
 * `util` is the floor: it knows about numbers, matrices and errors and nothing
 * about cloth.
 */
export const LAYERS = [
  { name: 'util', areas: ['util'], summary: 'numbers, matrices, runs, errors, dates, tables' },
  {
    name: 'craft',
    areas: ['draft', 'weave', 'analysis', 'yarn', 'warp'],
    summary: 'drafting, weave construction, cloth analysis and yarn arithmetic',
  },
  {
    name: 'plant',
    areas: ['loom', 'jacquard', 'cloth'],
    summary: 'the machines, their card decks and the cloth that comes off them',
  },
  {
    name: 'works',
    areas: ['works'],
    summary: 'orders, scheduling, production, costing and despatch',
  },
  { name: 'store', areas: ['store'], summary: 'the journal and the record projection' },
  { name: 'edge', areas: ['service', 'cli'], summary: 'the http service and the command line' },
];

/** Every source file, as a path relative to `src` with forward slashes. */
export function sourceFiles(dir = SRC, out = []) {
  for (const entry of readdirSync(dir).sort()) {
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) sourceFiles(path, out);
    else if (path.endsWith('.js')) out.push(relative(SRC, path).split(sep).join('/'));
  }
  return out;
}

/** The layer a file belongs to, or null for `src/index.js`, which is the barrel. */
export function layerOf(file) {
  const area = file.includes('/') ? file.slice(0, file.indexOf('/')) : null;
  if (area === null) return null;
  const found = LAYERS.find((layer) => layer.areas.includes(area));
  return found === undefined ? null : found.name;
}

/** The rank of a layer, lowest first, or -1 for a file in no layer. */
export function rankOf(name) {
  return LAYERS.findIndex((layer) => layer.name === name);
}

/** The files one file imports, resolved to paths relative to `src`. */
export function importsOf(file, text) {
  const from = dirname(join(SRC, file));
  const found = [];
  for (const match of text.matchAll(/from '(\.[^']*)'/g)) {
    const target = relative(SRC, resolve(from, match[1])).split(sep).join('/');
    if (!found.includes(target)) found.push(target);
  }
  return found;
}

/** Read the whole tree into one record per file. */
export function readTree() {
  return sourceFiles().map((file) => {
    const text = readFileSync(join(SRC, file), 'utf8');
    return { file, layer: layerOf(file), imports: importsOf(file, text) };
  });
}

/**
 * Every import that goes the wrong way.
 *
 * The barrel is left out because it exists to import everything, and a file in
 * no layer cannot break an order it is not in.
 */
export function violations(tree) {
  const problems = [];
  for (const entry of tree) {
    if (entry.layer === null) continue;
    for (const target of entry.imports) {
      const targetLayer = layerOf(target);
      if (targetLayer === null) continue;
      if (rankOf(targetLayer) > rankOf(entry.layer)) {
        problems.push(`${entry.file} imports ${target}, which is a layer up`);
      }
    }
  }
  return problems;
}

/** How many times each layer reaches into each other layer. */
export function layerGraph(tree) {
  const counts = new Map();
  for (const entry of tree) {
    if (entry.layer === null) continue;
    for (const target of entry.imports) {
      const targetLayer = layerOf(target);
      if (targetLayer === null || targetLayer === entry.layer) continue;
      const key = `${entry.layer} -> ${targetLayer}`;
      counts.set(key, (counts.get(key) ?? 0) + 1);
    }
  }
  return [...counts.entries()]
    .map(([edge, count]) => ({ edge, count }))
    .sort((a, b) => b.count - a.count || a.edge.localeCompare(b.edge));
}

/** How many files sit in each layer, and how many other files they pull in. */
export function layerSizes(tree) {
  return LAYERS.map((layer) => {
    const files = tree.filter((entry) => entry.layer === layer.name);
    let imports = 0;
    for (const entry of files) imports += entry.imports.length;
    return { layer: layer.name, files: files.length, imports, summary: layer.summary };
  });
}

/** The files nothing else in `src` imports, which is where a reader starts. */
export function unimported(tree) {
  const wanted = new Set();
  for (const entry of tree) {
    for (const target of entry.imports) wanted.add(target);
  }
  return tree.map((entry) => entry.file).filter((file) => !wanted.has(file) && file !== 'index.js');
}

/** The whole document. */
export function render(tree = readTree()) {
  const lines = [
    '# How heddlewright is put together',
    '',
    'This file is generated by `npm run architecture`. Do not edit it by hand.',
    '',
    '## The layers',
    '',
    'Lowest first. A file may import from its own layer or from any layer below',
    'it, and never from one above.',
    '',
  ];
  for (const row of layerSizes(tree)) {
    lines.push(`- **${row.layer}** (${row.files} files) - ${row.summary}`);
  }
  lines.push('', '## What reaches into what', '');
  for (const edge of layerGraph(tree)) {
    lines.push(`- \`${edge.edge}\` ${edge.count} imports`);
  }
  const ends = unimported(tree);
  lines.push('', '## Nothing in src imports these', '');
  lines.push('They are the ways in, so they are where a reader starts.', '');
  for (const file of ends) lines.push(`- \`src/${file}\``);
  lines.push('');
  return lines.join('\n');
}

/** Write the document out. */
export function write() {
  mkdirSync(join(ROOT, 'docs'), { recursive: true });
  writeFileSync(OUTPUT, render(), 'utf8');
  return OUTPUT;
}

/** Whether the layering holds and the document on disk still matches the code. */
export function check() {
  const tree = readTree();
  const broken = violations(tree);
  if (broken.length > 0) return { matches: false, reason: broken.join('; ') };
  let onDisk;
  try {
    onDisk = readFileSync(OUTPUT, 'utf8');
  } catch {
    return { matches: false, reason: 'the architecture document has not been written yet' };
  }
  if (onDisk === render(tree)) return { matches: true, reason: '' };
  return { matches: false, reason: 'the architecture document is out of date' };
}

const invoked = process.argv[1] !== undefined && process.argv[1].endsWith('architecture.mjs');
if (invoked) {
  if (process.argv.includes('--check')) {
    const result = check();
    if (!result.matches) console.error(result.reason);
    process.exitCode = result.matches ? 0 : 1;
  } else {
    console.log(`wrote ${write()}`);
  }
}
