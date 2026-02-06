import { describe, it, expect } from 'vitest';
import { DraftError, InvalidArgument } from '../../src/util/errors.js';
import { formatMatrix, parseMatrix, dimensions } from '../../src/util/matrix.js';
import { plainWeave } from '../../src/weave/plain.js';
import { twillWeave } from '../../src/weave/twill.js';
import {
  LAYERS,
  endLayers,
  pickLayers,
  doubleCloth,
  stitchingPoints,
  assertLayersSeparate,
  extractLayer,
  layerCounts,
} from '../../src/weave/doublecloth.js';

const BUILT = doubleCloth({ face: plainWeave(), back: plainWeave() });

describe('endLayers and pickLayers', () => {
  it('run the order out across the threads', () => {
    expect(endLayers(['face', 'back'], 4)).toEqual(['face', 'back', 'face', 'back']);
    expect(pickLayers(['face', 'face', 'back'], 6)).toEqual([
      'face',
      'face',
      'back',
      'face',
      'face',
      'back',
    ]);
  });

  it('names its two layers and refuses anything else', () => {
    expect(LAYERS).toEqual(['face', 'back']);
    expect(() => endLayers(['face', 'middle'], 4)).toThrow(InvalidArgument);
  });
});

describe('doubleCloth', () => {
  it('builds a repeat holding both cloths', () => {
    expect(dimensions(BUILT.cloth)).toEqual({ rows: 4, cols: 4 });
    expect(formatMatrix(BUILT.cloth)).toBe('X...\nXXX.\n..X.\nX.XX');
  });

  it('keeps the back ends down on a face pick', () => {
    const backEnds = BUILT.endLayers
      .map((layer, index) => (layer === 'back' ? index : -1))
      .filter((index) => index !== -1);
    const facePick = BUILT.pickLayers.indexOf('face');
    for (const end of backEnds) {
      expect(BUILT.cloth[facePick][end]).toBe(0);
    }
  });

  it('keeps the face ends up on a back pick', () => {
    const faceEnds = BUILT.endLayers
      .map((layer, index) => (layer === 'face' ? index : -1))
      .filter((index) => index !== -1);
    const backPick = BUILT.pickLayers.indexOf('back');
    for (const end of faceEnds) {
      expect(BUILT.cloth[backPick][end]).toBe(1);
    }
  });

  it('takes a different weave in each layer', () => {
    const mixed = doubleCloth({ face: twillWeave('2/2'), back: plainWeave() });
    expect(formatMatrix(extractLayer(mixed, 'face'))).toBe(formatMatrix(twillWeave('2/2')));
  });

  it('takes an uneven order, two face ends to one back', () => {
    const built = doubleCloth({
      face: plainWeave(),
      back: plainWeave(),
      endOrder: ['face', 'face', 'back'],
    });
    expect(layerCounts(built).faceEnds).toBe(4);
    expect(layerCounts(built).backEnds).toBe(2);
  });
});

describe('stitchingPoints', () => {
  it('finds nothing in a cloth built by the rules', () => {
    expect(stitchingPoints(BUILT)).toEqual([]);
    expect(assertLayersSeparate(BUILT)).toBe(BUILT);
  });

  it('finds a back end raised on a face pick', () => {
    const broken = { ...BUILT, cloth: parseMatrix('XX..\nXXX.\n..X.\nX.XX') };
    const points = stitchingPoints(broken);
    expect(points).toEqual([{ pick: 1, end: 2, kind: 'back end raised on a face pick' }]);
  });

  it('finds a face end lowered on a back pick', () => {
    const broken = { ...BUILT, cloth: parseMatrix('X...\n.XX.\n..X.\nX.XX') };
    expect(stitchingPoints(broken)[0].kind).toBe('face end lowered on a back pick');
  });

  it('refuses a cloth that is stitched, naming the first point', () => {
    const broken = { ...BUILT, cloth: parseMatrix('XX..\nXXX.\n..X.\nX.XX') };
    try {
      assertLayersSeparate(broken);
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(DraftError);
      expect(err.detail.first.pick).toBe(1);
    }
  });
});

describe('extractLayer', () => {
  it('pulls each cloth back out on its own numbering', () => {
    expect(formatMatrix(extractLayer(BUILT, 'face'))).toBe('X.\n.X');
    expect(formatMatrix(extractLayer(BUILT, 'back'))).toBe('X.\n.X');
  });

  it('refuses a layer that is not there', () => {
    expect(() => extractLayer(BUILT, 'middle')).toThrow(InvalidArgument);
  });
});

describe('layerCounts', () => {
  it('counts what each layer takes of the repeat', () => {
    expect(layerCounts(BUILT)).toEqual({
      faceEnds: 2,
      backEnds: 2,
      facePicks: 2,
      backPicks: 2,
    });
  });
});
