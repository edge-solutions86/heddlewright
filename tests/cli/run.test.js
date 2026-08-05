import { describe, it, expect } from 'vitest';
import { readFileSync, existsSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { run, EXIT_OK, EXIT_USAGE, EXIT_ERROR } from '../../src/cli/run.js';

describe('run', () => {
  it('runs a command and reports success', () => {
    const result = run(['weave', '--notation', '2/2']);
    expect(result.code).toBe(EXIT_OK);
    expect(result.out).toBe('XX..\n.XX.\n..XX\nX..X');
    expect(result.err).toBe('');
  });

  it('prints the tool usage and fails when nothing is asked for', () => {
    const result = run([]);
    expect(result.code).toBe(EXIT_USAGE);
    expect(result.out).toContain('heddle <command>');
  });

  it('prints the tool usage and succeeds on help', () => {
    const result = run(['help']);
    expect(result.code).toBe(EXIT_OK);
    expect(result.out).toContain('weave');
  });

  it('prints one command usage on help with a name', () => {
    const result = run(['help', 'count']);
    expect(result.code).toBe(EXIT_OK);
    expect(result.out).toContain('--value <number> (required)');
  });

  it('fails on help for a command it does not have', () => {
    const result = run(['help', 'nothing']);
    expect(result.code).toBe(EXIT_USAGE);
    expect(result.err).toContain('no command called');
  });

  it('names the commands it does have when given an unknown one', () => {
    const result = run(['nothing']);
    expect(result.code).toBe(EXIT_USAGE);
    expect(result.err).toContain('heddle <command>');
  });

  it('shows the command usage when an argument is wrong', () => {
    const result = run(['count', '--value', 'lots', '--from', 'ne']);
    expect(result.code).toBe(EXIT_ERROR);
    expect(result.err).toContain('--value must be a number');
    expect(result.err).toContain('heddle count');
  });

  it('reports a domain error without the usage, because the arguments were fine', () => {
    const result = run(['weave', '--kind', 'satin', '--threads', '5', '--move', '1']);
    expect(result.code).toBe(EXIT_ERROR);
    expect(result.err).toContain('twill line');
    expect(result.err).not.toContain('heddle weave');
  });

  it('puts the error detail beside the message', () => {
    const result = run(['weave', '--kind', 'satin', '--threads', '5', '--move', '1']);
    expect(result.err).toContain('"threads":5');
  });

  it('never throws, whatever it is given', () => {
    for (const argv of [[], ['--=1'], ['nothing'], ['sett'], ['draft', '--shafts', 'x']]) {
      expect(() => run(argv)).not.toThrow();
    }
  });
});

describe('the binary', () => {
  /**
   * `fileURLToPath`, not the path property of the URL. On Windows that reads
   * `/C:/somewhere` and the leading slash has to come off; on Linux taking it
   * off turns an absolute path into a relative one, which is what it did here
   * until a Linux runner said so. This function knows the difference and the
   * tests do not have to.
   */
  const bin = fileURLToPath(new URL('../../bin/heddle.js', import.meta.url));

  it('has no shebang, which a checkout with converted line endings would break', () => {
    const source = readFileSync(bin, 'utf8');
    expect(source.startsWith('#!')).toBe(false);
  });

  it('is a real file at the path the tests build', () => {
    // The positive control for the two below. A path that resolves to nothing
    // makes node exit non-zero for the wrong reason, and the exit code test
    // cannot tell that apart from the failure it means to check.
    expect(existsSync(bin)).toBe(true);
  });

  it('prints what run gives it', () => {
    const out = execFileSync(process.execPath, [bin, 'weave']);
    expect(out.toString().trim()).toBe('XX..\n.XX.\n..XX\nX..X');
  });

  it('sets the usage exit code when the command is not one', () => {
    let code = 0;
    let stderr = '';
    try {
      execFileSync(process.execPath, [bin, 'nothing'], { stdio: 'pipe' });
    } catch (err) {
      code = err.status;
      stderr = err.stderr.toString();
    }
    expect(code).toBe(EXIT_USAGE);
    // Without this the test passes on a binary node could not even load.
    expect(stderr).toContain('nothing');
  });

  it('sets the error exit code when the command is one but the options are wrong', () => {
    let code = 0;
    try {
      execFileSync(process.execPath, [bin, 'sett'], { stdio: 'pipe' });
    } catch (err) {
      code = err.status;
    }
    expect(code).toBe(EXIT_ERROR);
  });
});
