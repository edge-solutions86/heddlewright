import { array, oneOf, plainObject } from './check.js';

/**
 * Column-aligned plain text tables for the CLI and for printed shed dockets.
 * No dependency, no colour, and every column measured before anything is
 * written so a long value widens the column instead of breaking the row.
 */

const ALIGNMENTS = ['left', 'right'];

/**
 * Render rows of objects under the given columns.
 * Each column is `{ key, label, align }`; `align` defaults to left, or right
 * when every value under it is a number.
 */
export function renderTable(rows, columns, { pad = 2, rule = true } = {}) {
  array(rows, 'rows');
  array(columns, 'columns', { min: 1 });
  const specs = columns.map((column, i) => {
    plainObject(column, `columns[${i}]`);
    const key = column.key;
    const numeric = rows.length > 0 && rows.every((row) => typeof row[key] === 'number');
    const align = column.align ?? (numeric ? 'right' : 'left');
    oneOf(align, `columns[${i}].align`, ALIGNMENTS);
    return { key, label: column.label ?? String(key), align };
  });

  const cells = rows.map((row) => specs.map((spec) => formatCell(row[spec.key])));
  const widths = specs.map((spec, c) =>
    cells.reduce((width, line) => Math.max(width, line[c].length), spec.label.length),
  );
  const gap = ' '.repeat(Math.max(0, pad));

  const out = [];
  out.push(specs.map((spec, c) => alignCell(spec.label, widths[c], spec.align)).join(gap));
  if (rule) {
    out.push(widths.map((width) => '-'.repeat(width)).join(gap));
  }
  for (const line of cells) {
    out.push(line.map((value, c) => alignCell(value, widths[c], specs[c].align)).join(gap));
  }
  return out.map((line) => line.replace(/\s+$/, '')).join('\n');
}

function formatCell(value) {
  if (value === null || value === undefined) return '';
  if (typeof value === 'number') {
    return Number.isInteger(value) ? String(value) : String(Number(value.toFixed(4)));
  }
  if (Array.isArray(value)) return value.join(' ');
  return String(value);
}

function alignCell(text, width, align) {
  return align === 'right' ? text.padStart(width) : text.padEnd(width);
}

/** A two-column label and value block, used for single-record summaries. */
export function renderPairs(pairs, { separator = ': ' } = {}) {
  const entries = pairs instanceof Map ? [...pairs.entries()] : Object.entries(pairs ?? {});
  if (entries.length === 0) return '';
  const width = entries.reduce((w, [label]) => Math.max(w, String(label).length), 0);
  return entries
    .map(([label, value]) => `${String(label).padEnd(width)}${separator}${formatCell(value)}`)
    .join('\n');
}

/** Wrap prose to a column width without breaking inside a word. */
export function wrapText(text, width = 78) {
  const words = String(text).split(/\s+/).filter(Boolean);
  const lines = [];
  let line = '';
  for (const word of words) {
    if (line === '') {
      line = word;
    } else if (line.length + 1 + word.length <= width) {
      line = `${line} ${word}`;
    } else {
      lines.push(line);
      line = word;
    }
  }
  if (line !== '') lines.push(line);
  return lines.join('\n');
}
