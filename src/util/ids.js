import { InvalidArgument } from './errors.js';
import { nonEmptyString, integer } from './check.js';

/**
 * Identifiers. Nothing here reaches for randomness: a draft, a card deck and a
 * warp beam all get an id derived from what they are, so replaying a journal on
 * a second machine produces the same references.
 */

const SLUG_STRIP = /[^a-z0-9]+/g;

/** Lowercase, hyphen-joined, safe in a filename and in a URL path segment. */
export function slug(text, { maxLength = 64 } = {}) {
  nonEmptyString(text, 'text');
  integer(maxLength, 'maxLength', { min: 1, max: 256 });
  const cleaned = text
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(SLUG_STRIP, '-')
    .replace(/^-+|-+$/g, '');
  if (cleaned === '') {
    throw new InvalidArgument('text has no slug-able characters', { text });
  }
  return cleaned.slice(0, maxLength).replace(/-+$/g, '');
}

/**
 * FNV-1a over the UTF-8 bytes, returned as eight lowercase hex digits. Short
 * enough to read off a card label and stable across Node versions.
 */
export function stableHash(text) {
  if (typeof text !== 'string') {
    throw new InvalidArgument('stableHash needs a string', { got: typeof text });
  }
  const bytes = new TextEncoder().encode(text);
  let hash = 0x811c9dc5;
  for (const byte of bytes) {
    hash ^= byte;
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return hash.toString(16).padStart(8, '0');
}

/** A prefixed, content-derived id such as `draft-8f21ac03`. */
export function contentId(prefix, content) {
  nonEmptyString(prefix, 'prefix');
  return `${slug(prefix)}-${stableHash(typeof content === 'string' ? content : JSON.stringify(content))}`;
}

/** Hand out `name`, `name-2`, `name-3` and so on against a set of taken ids. */
export function uniqueId(base, taken) {
  const wanted = slug(base);
  const seen = taken instanceof Set ? taken : new Set(taken ?? []);
  if (!seen.has(wanted)) return wanted;
  let n = 2;
  while (seen.has(`${wanted}-${n}`)) n += 1;
  return `${wanted}-${n}`;
}

/**
 * A counter that formats as a zero-padded reference, the way a shed numbers
 * card decks and warp beams.
 */
export function referenceCounter(prefix, { width = 4, start = 1 } = {}) {
  nonEmptyString(prefix, 'prefix');
  integer(width, 'width', { min: 1, max: 12 });
  integer(start, 'start', { min: 0 });
  let next = start;
  return {
    next() {
      const value = next;
      next += 1;
      return `${prefix}${String(value).padStart(width, '0')}`;
    },
    peek() {
      return `${prefix}${String(next).padStart(width, '0')}`;
    },
    get count() {
      return next - start;
    },
  };
}

/** Split a reference back into its prefix and number, or null when it does not fit. */
export function parseReference(reference) {
  nonEmptyString(reference, 'reference');
  const match = /^([A-Za-z][A-Za-z0-9-]*?)(\d+)$/.exec(reference);
  if (match === null) return null;
  return { prefix: match[1], number: Number.parseInt(match[2], 10), width: match[2].length };
}
