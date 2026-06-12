import { NotFound } from '../util/errors.js';
import { nonEmptyString, plainObject } from '../util/check.js';
import { renderTable } from '../util/table.js';
import { usageFor } from './parse.js';

/**
 * The command registry.
 *
 * The commands themselves live in files grouped by what they are about; this
 * holds the register they put themselves in and everything that reads it. It
 * knows nothing about weaving, which is why it can be read on its own.
 */

const COMMANDS = new Map();

/** Put a command in the register. */
export function define(name, summary, spec, run) {
  nonEmptyString(name, 'name');
  nonEmptyString(summary, 'summary');
  plainObject(spec, 'spec');
  if (typeof run !== 'function') {
    throw new NotFound(`the command "${name}" has nothing to run`, { name });
  }
  COMMANDS.set(name, { name, summary, spec, run });
  return name;
}

/** The spec a command declared, for a run function that wants to read it back. */
export function specOf(name) {
  return commandByName(name).spec;
}

/** Every command, in name order. */
export function listCommands() {
  return [...COMMANDS.values()].sort((a, b) => a.name.localeCompare(b.name));
}

/** Look a command up. */
export function commandByName(name) {
  nonEmptyString(name, 'name');
  const found = COMMANDS.get(name);
  if (found === undefined) {
    throw new NotFound(`no command called "${name}"`, { name, known: [...COMMANDS.keys()] });
  }
  return found;
}

/** The usage text for one command. */
export function commandUsage(name) {
  const command = commandByName(name);
  return usageFor(command.name, command.spec, { summary: command.summary });
}

/** The usage text for the whole tool. */
export function toolUsage() {
  const rows = listCommands().map((command) => ({
    command: command.name,
    summary: command.summary,
  }));
  return [
    'heddle <command> [options]',
    '',
    renderTable(rows, [{ key: 'command' }, { key: 'summary' }]),
  ].join('\n');
}
