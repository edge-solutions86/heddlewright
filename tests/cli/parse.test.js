import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import { parseArgs, checkOptions, usageFor } from '../../src/cli/parse.js';

describe('parseArgs', () => {
  it('takes the command first and the rest as positional', () => {
    expect(parseArgs(['draft', 'twill', 'plain'])).toEqual({
      command: 'draft',
      positional: ['twill', 'plain'],
      options: {},
    });
  });

  it('reads an option written with a space or an equals', () => {
    expect(parseArgs(['x', '--shafts', '4']).options).toEqual({ shafts: '4' });
    expect(parseArgs(['x', '--shafts=4']).options).toEqual({ shafts: '4' });
  });

  it('reads an option with no value as a flag', () => {
    expect(parseArgs(['x', '--verbose']).options).toEqual({ verbose: true });
    expect(parseArgs(['x', '--verbose', '--shafts', '4']).options.verbose).toBe(true);
  });

  it('keeps an empty value from the equals form', () => {
    expect(parseArgs(['x', '--name=']).options).toEqual({ name: '' });
  });

  it('stops reading options after a bare double dash', () => {
    const parsed = parseArgs(['x', '--', '--not-an-option']);
    expect(parsed.positional).toEqual(['--not-an-option']);
    expect(parsed.options).toEqual({});
  });

  it('has a null command when there is nothing to do', () => {
    expect(parseArgs([]).command).toBeNull();
  });

  it('refuses an option with no name', () => {
    expect(() => parseArgs(['x', '--=1'])).toThrow(/an option needs a name/);
  });

  it('still treats a bare double dash as the separator, not as a bad name', () => {
    expect(parseArgs(['x', '--', '--=1']).positional).toEqual(['--=1']);
  });
});

describe('checkOptions', () => {
  const spec = {
    shafts: { type: 'integer', required: true },
    name: { type: 'string', default: 'untitled' },
    ratio: { type: 'number' },
    colours: { type: 'list' },
    quiet: { type: 'flag' },
  };

  it('coerces each option to its declared type', () => {
    const parsed = parseArgs(['x', '--shafts', '4', '--ratio', '0.5', '--colours', 'a,b']);
    expect(checkOptions(parsed, spec)).toEqual({
      shafts: 4,
      name: 'untitled',
      ratio: 0.5,
      colours: ['a', 'b'],
      quiet: null,
    });
  });

  it('reads a flag as true when it is present', () => {
    const parsed = parseArgs(['x', '--shafts', '4', '--quiet']);
    expect(checkOptions(parsed, spec).quiet).toBe(true);
  });

  it('refuses a missing required option', () => {
    expect(() => checkOptions(parseArgs(['x']), spec)).toThrow(/--shafts is required/);
  });

  it('refuses an option the command does not declare, and lists the ones it does', () => {
    try {
      checkOptions(parseArgs(['x', '--shafts', '4', '--shaftz', '4']), spec);
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(InvalidArgument);
      expect(err.detail.known).toContain('shafts');
    }
  });

  it('refuses a value that is not the declared type', () => {
    const parsed = parseArgs(['x', '--shafts', 'four']);
    expect(() => checkOptions(parsed, spec)).toThrow(/must be a whole number/);
  });

  it('refuses a value option written as a bare flag', () => {
    const parsed = parseArgs(['x', '--shafts']);
    expect(() => checkOptions(parsed, spec)).toThrow(/needs a value/);
  });

  it('drops empty entries out of a list', () => {
    const parsed = parseArgs(['x', '--shafts', '4', '--colours', 'a,,b, ']);
    expect(checkOptions(parsed, spec).colours).toEqual(['a', 'b']);
  });
});

describe('usageFor', () => {
  it('describes the options a command takes', () => {
    const spec = {
      shafts: { type: 'integer', required: true, describe: 'how many shafts' },
      quiet: { type: 'flag' },
    };
    const text = usageFor('draft', spec, { summary: 'build a draft' });
    expect(text.split('\n')[0]).toBe('heddle draft - build a draft');
    expect(text).toContain('--shafts <integer> (required)  how many shafts');
    expect(text).toContain('--quiet');
  });

  it('leaves the summary off when there is none', () => {
    expect(usageFor('draft', {}).split('\n')[0]).toBe('heddle draft');
  });
});
