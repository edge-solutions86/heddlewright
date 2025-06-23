import { InvalidArgument } from '../util/errors.js';
import { nonEmptyString, plainObject } from '../util/check.js';

/**
 * The keyed-section text format that weaving files are written in.
 *
 * A file is a run of `[SECTION]` headers, each holding `key=value` lines.
 * Comments start with a semicolon. Section names and keys are compared without
 * regard to case, because thirty years of files have written them every way,
 * but the values are kept exactly as they were typed.
 */

const SECTION = /^\[(.+)\]$/;

/** Parse the whole file into a Map of section name to a Map of keys. */
export function parseIni(text) {
  if (typeof text !== 'string') {
    throw new InvalidArgument('parseIni needs a string', { got: typeof text });
  }
  const sections = new Map();
  let current = null;
  for (const [index, raw] of text.split(/\r?\n/).entries()) {
    const line = stripComment(raw).trim();
    if (line === '') continue;
    const header = SECTION.exec(line);
    if (header !== null) {
      current = header[1].trim().toUpperCase();
      if (!sections.has(current)) sections.set(current, new Map());
      continue;
    }
    const split = line.indexOf('=');
    if (split === -1) {
      throw new InvalidArgument(`line ${index + 1} is neither a section nor a key`, {
        line: index + 1,
        text: line,
      });
    }
    if (current === null) {
      throw new InvalidArgument(`line ${index + 1} sits outside any section`, {
        line: index + 1,
      });
    }
    const key = line.slice(0, split).trim().toUpperCase();
    const value = line.slice(split + 1).trim();
    sections.get(current).set(key, value);
  }
  return sections;
}

function stripComment(line) {
  const at = line.indexOf(';');
  return at === -1 ? line : line.slice(0, at);
}

/** True when the file carries that section. */
export function hasSection(sections, name) {
  nonEmptyString(name, 'name');
  return sections.has(name.toUpperCase());
}

/** A section, or an empty Map when the file does not carry it. */
export function section(sections, name) {
  nonEmptyString(name, 'name');
  return sections.get(name.toUpperCase()) ?? new Map();
}

/** One value, with a fallback when the key is missing. */
export function value(sections, name, key, fallback = null) {
  nonEmptyString(key, 'key');
  const found = section(sections, name).get(key.toUpperCase());
  return found === undefined ? fallback : found;
}

/** A value read as an integer, or the fallback when it is missing or unreadable. */
export function intValue(sections, name, key, fallback = null) {
  const raw = value(sections, name, key);
  if (raw === null || !/^-?\d+$/.test(raw)) return fallback;
  return Number.parseInt(raw, 10);
}

/** A value read as a yes or no flag. */
export function boolValue(sections, name, key, fallback = false) {
  const raw = value(sections, name, key);
  if (raw === null) return fallback;
  return /^(yes|true|on|1)$/i.test(raw.trim());
}

/** A comma-separated value read as a list of integers. */
export function intListValue(sections, name, key) {
  const raw = value(sections, name, key);
  if (raw === null || raw.trim() === '') return [];
  return raw
    .split(',')
    .map((part) => part.trim())
    .filter((part) => part !== '')
    .map((part) => {
      if (!/^-?\d+$/.test(part)) {
        throw new InvalidArgument(`"${part}" in ${name}.${key} is not a whole number`, {
          section: name,
          key,
          part,
        });
      }
      return Number.parseInt(part, 10);
    });
}

/**
 * A numbered section read as a dense array. Weaving files key threadings and
 * treadlings by 1-based thread number, and they are allowed to be sparse, so
 * anything missing comes back as the fill value.
 */
export function numberedList(sections, name, count, { fill = null, asList = false } = {}) {
  const entries = section(sections, name);
  const out = new Array(count).fill(fill);
  for (const [key, raw] of entries) {
    if (!/^\d+$/.test(key)) continue;
    const at = Number.parseInt(key, 10);
    if (at < 1 || at > count) continue;
    out[at - 1] = asList ? intListValue(sections, name, key) : Number.parseInt(raw, 10);
  }
  return out;
}

/** Write a Map-of-Maps back out as text, sections in insertion order. */
export function formatIni(sections) {
  const blocks = [];
  for (const [name, entries] of sections) {
    const lines = [`[${name}]`];
    for (const [key, entryValue] of entries) lines.push(`${key}=${entryValue}`);
    blocks.push(lines.join('\n'));
  }
  return `${blocks.join('\n\n')}\n`;
}

/** Build a Map-of-Maps from a plain object, for callers writing a file. */
export function iniFromObject(source) {
  plainObject(source, 'source');
  const sections = new Map();
  for (const [name, entries] of Object.entries(source)) {
    plainObject(entries, `source.${name}`);
    sections.set(
      name,
      new Map(Object.entries(entries).map(([key, entry]) => [key, String(entry)])),
    );
  }
  return sections;
}
