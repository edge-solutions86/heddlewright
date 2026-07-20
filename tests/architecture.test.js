import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import {
  LAYERS,
  sourceFiles,
  layerOf,
  rankOf,
  importsOf,
  readTree,
  violations,
  layerGraph,
  layerSizes,
  unimported,
  render,
  check,
} from '../scripts/architecture.mjs';

/**
 * The layering is the one architectural rule this package has, so it is checked
 * rather than described. Each helper is also run against a planted tree that it
 * has to reject, because a rule that has quietly stopped matching anything
 * reports a clean sweep and reads exactly like a clean repository.
 */

const TREE = readTree();

describe('the layers', () => {
  it('are declared lowest first and cover every area in src', () => {
    expect(LAYERS.map((layer) => layer.name)).toEqual([
      'util',
      'craft',
      'plant',
      'works',
      'store',
      'edge',
    ]);
    const unplaced = sourceFiles().filter((file) => file !== 'index.js' && layerOf(file) === null);
    expect(unplaced).toEqual([]);
  });

  it('name no area twice', () => {
    const areas = LAYERS.flatMap((layer) => layer.areas);
    expect(new Set(areas).size).toBe(areas.length);
  });

  it('rank from the floor up', () => {
    expect(rankOf('util')).toBe(0);
    expect(rankOf('edge')).toBe(LAYERS.length - 1);
    expect(rankOf('nowhere')).toBe(-1);
  });

  it('leave the barrel out, because it imports everything on purpose', () => {
    expect(layerOf('index.js')).toBeNull();
  });
});

describe('importsOf', () => {
  it('resolves a relative import to a path under src', () => {
    const text = "import { sum } from '../util/numeric.js';\n";
    expect(importsOf('loom/boxmotion.js', text)).toEqual(['util/numeric.js']);
  });

  it('resolves one inside the same folder', () => {
    expect(importsOf('service/app.js', "import { route } from './router.js';")).toEqual([
      'service/router.js',
    ]);
  });

  it('ignores a package import, which cannot be a layer', () => {
    expect(importsOf('util/dates.js', "import { readFileSync } from 'node:fs';")).toEqual([]);
  });

  it('names an import once however many times it appears', () => {
    const text = "from './a.js'\nfrom './a.js'\n";
    expect(importsOf('util/x.js', text)).toEqual(['util/a.js']);
  });
});

describe('the tree as it stands', () => {
  it('breaks the layering nowhere', () => {
    expect(violations(TREE)).toEqual([]);
  });

  it('has read every file and given each one imports or none', () => {
    expect(TREE.length).toBeGreaterThan(100);
    expect(TREE.every((entry) => Array.isArray(entry.imports))).toBe(true);
  });

  it('keeps the weaving arithmetic clear of the service and the store', () => {
    const craft = TREE.filter((entry) => entry.layer === 'craft');
    const reached = new Set(craft.flatMap((entry) => entry.imports.map(layerOf)));
    expect(reached.has('edge')).toBe(false);
    expect(reached.has('store')).toBe(false);
    expect(reached.has('works')).toBe(false);
  });
});

describe('violations', () => {
  it('catches an import that goes up a layer', () => {
    const planted = [{ file: 'util/numeric.js', layer: 'util', imports: ['service/app.js'] }];
    expect(violations(planted)).toEqual([
      'util/numeric.js imports service/app.js, which is a layer up',
    ]);
  });

  it('lets an import go down or sideways', () => {
    const planted = [
      { file: 'service/app.js', layer: 'edge', imports: ['util/numeric.js', 'service/router.js'] },
    ];
    expect(violations(planted)).toEqual([]);
  });

  it('says nothing about a file in no layer', () => {
    expect(violations([{ file: 'index.js', layer: null, imports: ['service/app.js'] }])).toEqual(
      [],
    );
  });
});

describe('layerGraph', () => {
  it('counts the imports between layers and leaves the ones inside a layer out', () => {
    const planted = [
      { file: 'works/orders.js', layer: 'works', imports: ['util/numeric.js', 'works/stock.js'] },
      { file: 'works/stock.js', layer: 'works', imports: ['util/numeric.js'] },
    ];
    expect(layerGraph(planted)).toEqual([{ edge: 'works -> util', count: 2 }]);
  });

  it('puts the busiest edge first, and the real graph rests on util', () => {
    expect(layerGraph(TREE)[0].edge).toBe('craft -> util');
  });
});

describe('layerSizes', () => {
  it('counts the files and the imports in each layer', () => {
    const rows = layerSizes(TREE);
    expect(rows.map((row) => row.layer)).toEqual(LAYERS.map((layer) => layer.name));
    expect(rows.every((row) => row.files > 0)).toBe(true);
    expect(rows.find((row) => row.layer === 'util').imports).toBeGreaterThan(0);
  });

  it('reports a layer with nothing in it rather than dropping it', () => {
    const rows = layerSizes([]);
    expect(rows.length).toBe(LAYERS.length);
    expect(rows.every((row) => row.files === 0 && row.imports === 0)).toBe(true);
  });
});

describe('unimported', () => {
  it('finds the files that are ways in rather than parts', () => {
    expect(unimported(TREE)).toContain('service/server.js');
    expect(unimported(TREE)).toContain('cli/run.js');
  });

  it('leaves the barrel out, which nothing imports either', () => {
    expect(unimported(TREE)).not.toContain('index.js');
  });

  it('is everything when nothing imports anything', () => {
    const planted = [
      { file: 'util/a.js', layer: 'util', imports: [] },
      { file: 'util/b.js', layer: 'util', imports: ['util/a.js'] },
    ];
    expect(unimported(planted)).toEqual(['util/b.js']);
  });
});

describe('the document', () => {
  it('says how it is made and names every layer', () => {
    const text = render(TREE);
    expect(text).toContain('npm run architecture');
    for (const layer of LAYERS) expect(text).toContain(`**${layer.name}**`);
  });

  it('matches the copy on disk', () => {
    expect(check()).toEqual({ matches: true, reason: '' });
  });

  it('is the copy on disk byte for byte', () => {
    const onDisk = readFileSync(new URL('../docs/architecture.md', import.meta.url), 'utf8');
    expect(onDisk).toBe(render(TREE));
  });

  it('does not run itself on import', () => {
    const text = readFileSync(new URL('../scripts/architecture.mjs', import.meta.url), 'utf8');
    expect(text).toContain("process.argv[1].endsWith('architecture.mjs')");
  });
});
