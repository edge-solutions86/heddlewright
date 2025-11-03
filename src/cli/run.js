import { isHeddleError } from '../util/errors.js';
import { parseArgs } from './parse.js';
import { commandByName, commandUsage, toolUsage } from './commands.js';

/**
 * Running a command.
 *
 * `run` never throws and never writes anywhere. It returns the exit code and
 * the two streams as text, so the binary is four lines and everything about
 * what the tool says can be tested without spawning a process.
 */

export const EXIT_OK = 0;
export const EXIT_USAGE = 1;
export const EXIT_ERROR = 2;

/** Run one invocation and report what it would print. */
export function run(argv) {
  let parsed;
  try {
    parsed = parseArgs(argv);
  } catch (err) {
    return { code: EXIT_USAGE, out: '', err: message(err) };
  }

  if (parsed.command === null || parsed.command === 'help') {
    const wanted = parsed.positional[0];
    if (wanted !== undefined) {
      try {
        return { code: EXIT_OK, out: commandUsage(wanted), err: '' };
      } catch (err) {
        return { code: EXIT_USAGE, out: '', err: message(err) };
      }
    }
    return { code: parsed.command === null ? EXIT_USAGE : EXIT_OK, out: toolUsage(), err: '' };
  }

  let command;
  try {
    command = commandByName(parsed.command);
  } catch (err) {
    return { code: EXIT_USAGE, out: '', err: `${message(err)}\n\n${toolUsage()}` };
  }

  try {
    return { code: EXIT_OK, out: command.run(parsed), err: '' };
  } catch (err) {
    const usage = isHeddleError(err, 'invalid_argument') ? `\n\n${commandUsage(command.name)}` : '';
    return { code: EXIT_ERROR, out: '', err: `${message(err)}${usage}` };
  }
}

function message(err) {
  if (isHeddleError(err)) {
    const detail = Object.keys(err.detail).length === 0 ? '' : ` ${JSON.stringify(err.detail)}`;
    return `${err.message}${detail}`;
  }
  return String(err?.message ?? err);
}
