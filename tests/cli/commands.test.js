import { describe, it, expect } from 'vitest';
import { NotFound, InvalidArgument } from '../../src/util/errors.js';
import { parseArgs } from '../../src/cli/parse.js';
import {
  buildDraft,
  listCommands,
  commandByName,
  commandUsage,
  toolUsage,
} from '../../src/cli/commands.js';

function run(argv) {
  const parsed = parseArgs(argv);
  return commandByName(parsed.command).run(parsed);
}

describe('listCommands', () => {
  it('lists them in name order with a summary each', () => {
    const names = listCommands().map((command) => command.name);
    expect(names).toEqual([...names].sort());
    expect(listCommands().every((command) => command.summary.length > 0)).toBe(true);
  });

  it('gives every command a spec object', () => {
    expect(listCommands().every((command) => typeof command.spec === 'object')).toBe(true);
  });
});

describe('commandByName', () => {
  it('finds a command and names the rest when it cannot', () => {
    expect(commandByName('draft').name).toBe('draft');
    try {
      commandByName('nothing');
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(NotFound);
      expect(err.detail.known).toContain('draft');
    }
  });
});

describe('the draft command', () => {
  it('prints the summary and the cloth', () => {
    const text = run(['draft', '--shafts', '4', '--ends', '8', '--picks', '4']);
    expect(text).toContain('shafts');
    expect(text).toContain('XX..XX..');
  });

  it('draws a point threading when asked', () => {
    const text = run(['draft', '--style', 'point', '--shafts', '4', '--ends', '6', '--picks', '6']);
    expect(text.split('\n').at(-1).length).toBe(6);
  });

  it('follows the twill notation', () => {
    const text = run([
      'draft',
      '--notation',
      '3/1',
      '--shafts',
      '4',
      '--ends',
      '4',
      '--picks',
      '4',
    ]);
    expect(text).toContain('XXX.');
  });

  it('refuses an option it does not declare', () => {
    expect(() => run(['draft', '--shaftz', '4'])).toThrow(InvalidArgument);
  });
});

describe('the floats command', () => {
  it('reports the longest float and a histogram', () => {
    const text = run(['floats', '--notation', '3/1', '--shafts', '4']);
    expect(text).toContain('warp');
    expect(text).toContain('direction');
  });

  it('reads a plain twill as floating two', () => {
    const text = run(['floats', '--notation', '2/2', '--shafts', '4']);
    expect(text).toMatch(/warp\s*:\s*2/);
  });
});

describe('the weave command', () => {
  it('prints a twill repeat', () => {
    expect(run(['weave', '--notation', '2/2'])).toBe('XX..\n.XX.\n..XX\nX..X');
  });

  it('prints a satin and names its legal moves', () => {
    const text = run(['weave', '--kind', 'satin', '--threads', '5', '--move', '2']);
    expect(text.split('\n')[0]).toBe('satin 5 move 2, legal moves 2 3');
    expect(text).toContain('.XXXX');
  });

  it('refuses a satin move that is not legal', () => {
    expect(() => run(['weave', '--kind', 'satin', '--threads', '5', '--move', '1'])).toThrow();
  });
});

describe('the sett command', () => {
  it('works the sett and the twill angle out', () => {
    const text = run(['sett', '--count', '30', '--notation', '2/2']);
    expect(text).toContain('warp');
    expect(text).toContain('twillAngle');
    expect(text).toMatch(/twillAngle\s*:\s*45/);
  });

  it('needs a count', () => {
    expect(() => run(['sett'])).toThrow(/--count is required/);
  });
});

describe('the count command', () => {
  it('converts between systems', () => {
    expect(run(['count', '--value', '30', '--from', 'ne', '--to', 'tex'])).toBe(
      '30 ne = 19.6847 tex',
    );
  });

  it('refuses a system it does not know', () => {
    expect(() => run(['count', '--value', '30', '--from', 'grist'])).toThrow(InvalidArgument);
  });
});

describe('the systems command', () => {
  it('lists every count system with its kind', () => {
    const text = run(['systems']);
    expect(text).toContain('tex');
    expect(text).toContain('indirect');
    expect(text.split('\n').length).toBeGreaterThan(8);
  });
});

describe('the check command', () => {
  it('says so when there is nothing wrong', () => {
    expect(run(['check', '--shafts', '4', '--ends', '8'])).toBe('nothing wrong with it');
  });

  it('reports an idle shaft', () => {
    const text = run(['check', '--shafts', '8', '--ends', '4', '--picks', '8']);
    expect(text).toContain('idle_shaft');
  });
});

describe('the report command', () => {
  it('prints every section of the cloth report', () => {
    const text = run(['report', '--shafts', '4', '--ends', '8', '--picks', '8']);
    expect(text).toContain('Structure');
    expect(text).toContain('Floats');
    expect(text).toContain('Sett');
  });

  it('reads the repeat out of the drawn cloth', () => {
    const text = run(['report', '--shafts', '4', '--ends', '16', '--picks', '16']);
    expect(text).toMatch(/repeatEnds\s*:\s*4/);
  });

  it('takes a float limit and a yarn count', () => {
    const text = run(['report', '--floatLimit', '1', '--count', '40']);
    expect(text).toMatch(/floatLimit\s*:\s*1/);
  });
});

describe('the catalogue command', () => {
  it('lists every weave measured', () => {
    const text = run(['catalogue']);
    expect(text).toContain('plain');
    expect(text).toContain('satin 8');
    expect(text).toContain('warp face');
  });

  it('filters to one family', () => {
    const text = run(['catalogue', '--family', 'satin']);
    expect(text).toContain('satin 5');
    expect(text).not.toContain('plain ');
  });

  it('says which families there are when it finds none', () => {
    const text = run(['catalogue', '--family', 'gauze']);
    expect(text).toContain('families:');
  });
});

describe('the deck command', () => {
  it('cuts a deck for a named weave and prices it', () => {
    const text = run(['deck', '--weave', 'twill 2/2']);
    expect(text).toContain('machine');
    expect(text).toMatch(/cards\s*:\s*4/);
    expect(text).toMatch(/poundsAndPence\s*:\s*0\./);
  });

  it('repeats the weave when asked', () => {
    const text = run(['deck', '--weave', 'twill 2/2', '--repeats', '3']);
    expect(text).toMatch(/cards\s*:\s*12/);
    expect(text).toMatch(/repeat\s*:\s*4/);
  });

  it('refuses a weave the catalogue does not have', () => {
    expect(() => run(['deck', '--weave', 'gauze'])).toThrow();
  });
});

describe('usage', () => {
  it('describes one command from the same spec the parser checks', () => {
    const text = commandUsage('count');
    expect(text).toContain('--value <number> (required)');
    expect(text).toContain('--from');
  });

  it('describes the whole tool', () => {
    const text = toolUsage();
    expect(text.split('\n')[0]).toBe('heddle <command> [options]');
    for (const command of listCommands()) {
      expect(text).toContain(command.name);
    }
  });
});

describe('buildDraft', () => {
  it('clamps a twill raise that would lift every shaft', () => {
    const draft = buildDraft({ style: 'twill', shafts: 4, ends: 8, picks: 8, notation: '9/1' });
    expect(draft.tieup[0].length).toBe(3);
  });
});
