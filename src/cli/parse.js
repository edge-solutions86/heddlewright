import { InvalidArgument } from '../util/errors.js';
import { array, nonEmptyString, plainObject } from '../util/check.js';

/**
 * Command line parsing.
 *
 * Arguments are a command, then positional values, then options. An option is
 * `--name value` or `--name=value`, and a flag is `--name` on its own. A single
 * `--` stops option parsing, so a value that looks like an option can still be
 * passed. Nothing is guessed: an option the command does not declare is an
 * error rather than a silent extra.
 */

/** Split argv into a command, its positional values and its options. */
export function parseArgs(argv) {
  array(argv, 'argv');
  const positional = [];
  const options = {};
  let literal = false;
  for (let i = 0; i < argv.length; i += 1) {
    const arg = String(argv[i]);
    if (literal || !arg.startsWith('--')) {
      positional.push(arg);
      continue;
    }
    if (arg === '--') {
      literal = true;
      continue;
    }
    const body = arg.slice(2);
    const equals = body.indexOf('=');
    // A bare `--` is the separator and is handled above, so anything reaching
    // here with no name is `--=value`, which is a typo rather than an option.
    const name = equals === -1 ? body : body.slice(0, equals);
    if (name === '') {
      throw new InvalidArgument('an option needs a name', { arg });
    }
    if (equals !== -1) {
      options[name] = body.slice(equals + 1);
      continue;
    }
    const next = argv[i + 1];
    if (next === undefined || String(next).startsWith('--')) {
      options[name] = true;
      continue;
    }
    options[name] = String(next);
    i += 1;
  }
  return { command: positional[0] ?? null, positional: positional.slice(1), options };
}

/**
 * Check the parsed options against what a command declares. An unknown option
 * is refused with the list of the ones it does take, because a typo in an
 * option name is otherwise silently ignored.
 */
export function checkOptions(parsed, spec) {
  plainObject(spec, 'spec');
  const known = Object.keys(spec);
  const unknown = Object.keys(parsed.options).filter((name) => !known.includes(name));
  if (unknown.length > 0) {
    throw new InvalidArgument(`unknown option --${unknown[0]}`, { unknown, known });
  }
  const out = {};
  for (const [name, declared] of Object.entries(spec)) {
    const raw = parsed.options[name];
    if (raw === undefined) {
      if (declared.required === true) {
        throw new InvalidArgument(`--${name} is required`, { option: name });
      }
      out[name] = declared.default ?? null;
      continue;
    }
    out[name] = coerce(name, raw, declared.type ?? 'string');
  }
  return out;
}

function coerce(name, raw, type) {
  if (type === 'flag') return raw === true || raw === 'yes' || raw === 'true';
  if (raw === true) {
    throw new InvalidArgument(`--${name} needs a value`, { option: name, type });
  }
  if (type === 'number') {
    const value = Number(raw);
    if (!Number.isFinite(value)) {
      throw new InvalidArgument(`--${name} must be a number`, { option: name, got: raw });
    }
    return value;
  }
  if (type === 'integer') {
    if (!/^-?\d+$/.test(raw)) {
      throw new InvalidArgument(`--${name} must be a whole number`, { option: name, got: raw });
    }
    return Number.parseInt(raw, 10);
  }
  if (type === 'list') {
    return raw
      .split(',')
      .map((part) => part.trim())
      .filter((part) => part !== '');
  }
  return raw;
}

/** Usage text for one command, built from the same spec the parser checks. */
export function usageFor(name, spec, { summary = '' } = {}) {
  nonEmptyString(name, 'name');
  plainObject(spec, 'spec');
  const lines = [`heddle ${name}${summary === '' ? '' : ` - ${summary}`}`];
  for (const [option, declared] of Object.entries(spec)) {
    const type = declared.type === 'flag' ? '' : ` <${declared.type ?? 'string'}>`;
    const mark = declared.required === true ? ' (required)' : '';
    lines.push(`  --${option}${type}${mark}  ${declared.describe ?? ''}`.trimEnd());
  }
  return lines.join('\n');
}
