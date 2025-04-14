import { describe, it, expect } from 'vitest';
import { DraftError, InvalidArgument } from '../../src/util/errors.js';
import {
  BEAT_UP,
  normaliseAngle,
  angleWithin,
  shedCycle,
  isShedOpenAt,
  shuttleWindow,
  shuttleClearance,
  assertShuttleClears,
  sheddingLead,
  timingChart,
} from '../../src/loom/shed.js';

describe('normaliseAngle', () => {
  it('wraps into a single turn', () => {
    expect(normaliseAngle(370)).toBe(10);
    expect(normaliseAngle(-10)).toBe(350);
    expect(normaliseAngle(360)).toBe(0);
    expect(normaliseAngle(0)).toBe(BEAT_UP);
  });

  it('refuses something that is not an angle', () => {
    expect(() => normaliseAngle('early')).toThrow(InvalidArgument);
  });
});

describe('angleWithin', () => {
  it('reads a plain arc', () => {
    expect(angleWithin(90, 0, 180)).toBe(true);
    expect(angleWithin(270, 0, 180)).toBe(false);
  });

  it('reads an arc that crosses beat-up', () => {
    expect(angleWithin(350, 300, 60)).toBe(true);
    expect(angleWithin(10, 300, 60)).toBe(true);
    expect(angleWithin(180, 300, 60)).toBe(false);
  });

  it('includes both ends', () => {
    expect(angleWithin(0, 0, 180)).toBe(true);
    expect(angleWithin(180, 0, 180)).toBe(true);
  });
});

describe('shedCycle', () => {
  it('opens a quarter turn after the cross and holds for the dwell', () => {
    expect(shedCycle()).toEqual({
      crossAngle: 300,
      fullyOpen: 0,
      startClosing: 140,
      dwellDegrees: 140,
      openDegrees: 60,
    });
  });

  it('follows a different cross angle', () => {
    expect(shedCycle({ crossAngle: 330 }).fullyOpen).toBe(30);
  });

  it('refuses a cycle that will not fit inside one turn', () => {
    expect(() => shedCycle({ dwellDegrees: 300, openDegrees: 60 })).toThrow(DraftError);
  });
});

describe('isShedOpenAt', () => {
  const cycle = shedCycle();

  it('is open through the dwell', () => {
    expect(isShedOpenAt(cycle, 0)).toBe(true);
    expect(isShedOpenAt(cycle, 70)).toBe(true);
    expect(isShedOpenAt(cycle, 140)).toBe(true);
  });

  it('is shut on the way round to the next cross', () => {
    expect(isShedOpenAt(cycle, 200)).toBe(false);
    expect(isShedOpenAt(cycle, 300)).toBe(false);
  });
});

describe('shuttleWindow', () => {
  it('lands the shuttle a flight after the launch', () => {
    expect(shuttleWindow()).toEqual({ launch: 10, arrival: 130, flightDegrees: 120 });
  });

  it('wraps an arrival past beat-up', () => {
    expect(shuttleWindow({ pickingAngle: 300, flightDegrees: 120 }).arrival).toBe(60);
  });

  it('refuses a flight of a whole turn or more', () => {
    expect(() => shuttleWindow({ flightDegrees: 360 })).toThrow(DraftError);
  });
});

describe('shuttleClearance', () => {
  it('clears on the default timing, with room at both ends', () => {
    const clearance = shuttleClearance(shedCycle(), shuttleWindow());
    expect(clearance).toEqual({
      clears: true,
      entryMargin: 10,
      exitMargin: 10,
      dwellDegrees: 140,
    });
  });

  it('reports a negative exit margin when the flight is too long', () => {
    const clearance = shuttleClearance(shedCycle(), shuttleWindow({ flightDegrees: 160 }));
    expect(clearance.clears).toBe(false);
    expect(clearance.exitMargin).toBe(-30);
  });

  it('a later launch eats into the exit margin one degree at a time', () => {
    const early = shuttleClearance(shedCycle(), shuttleWindow({ pickingAngle: 10 }));
    const late = shuttleClearance(shedCycle(), shuttleWindow({ pickingAngle: 15 }));
    expect(late.entryMargin - early.entryMargin).toBe(5);
    expect(early.exitMargin - late.exitMargin).toBe(5);
  });

  it('a longer dwell buys clearance back', () => {
    const wide = shedCycle({ dwellDegrees: 180 });
    expect(shuttleClearance(wide, shuttleWindow({ flightDegrees: 160 })).clears).toBe(true);
  });
});

describe('assertShuttleClears', () => {
  it('returns the clearance when the timing is sound', () => {
    expect(assertShuttleClears(shedCycle(), shuttleWindow()).clears).toBe(true);
  });

  it('refuses a timing that flies into a closing shed', () => {
    try {
      assertShuttleClears(shedCycle(), shuttleWindow({ flightDegrees: 200 }));
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(DraftError);
      expect(err.detail.flightDegrees).toBe(200);
      expect(err.detail.dwellDegrees).toBe(140);
    }
  });
});

describe('sheddingLead', () => {
  it('calls a cross before beat-up early', () => {
    expect(sheddingLead(shedCycle({ crossAngle: 300 }))).toEqual({
      leadDegrees: 60,
      timing: 'early',
    });
  });

  it('calls a cross after beat-up late', () => {
    expect(sheddingLead(shedCycle({ crossAngle: 30 })).timing).toBe('late');
  });

  it('calls a cross at beat-up level', () => {
    expect(sheddingLead(shedCycle({ crossAngle: 0 }))).toEqual({ leadDegrees: 0, timing: 'level' });
  });
});

describe('timingChart', () => {
  it('lists the whole pick in crank order', () => {
    const chart = timingChart(shedCycle(), shuttleWindow());
    expect(chart.map((entry) => entry.event)).toEqual([
      'beat-up',
      'shed fully open',
      'shuttle launched',
      'shuttle boxed',
      'shed starts closing',
      'shed crosses',
    ]);
  });

  it('never goes backwards', () => {
    const chart = timingChart(shedCycle({ crossAngle: 330 }), shuttleWindow({ pickingAngle: 40 }));
    for (let i = 1; i < chart.length; i += 1) {
      expect(chart[i].at).toBeGreaterThanOrEqual(chart[i - 1].at);
    }
  });
});
