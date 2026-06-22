import { readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

/**
 * Reading the coverage report back.
 *
 * The runner enforces the floors on its own. What it will not tell you is which
 * files are dragging the figure down, and that is the only part anybody acts
 * on, so this reads the report it wrote and ranks them.
 */

const ROOT = fileURLToPath(new URL('..', import.meta.url));
const REPORT = join(ROOT, 'coverage', 'lcov.info');

/** Whether a coverage report has been written. */
export function hasReport(path = REPORT) {
  return existsSync(path);
}

/**
 * Parse an lcov report into one record per file. Only the four counters are
 * read; everything else in the format is branch and function detail the ranking
 * does not use.
 */
export function parseLcov(text) {
  const files = [];
  let current = null;
  for (const line of text.split(/\r?\n/)) {
    if (line.startsWith('SF:')) {
      current = {
        file: line.slice(3).replace(/\\/g, '/'),
        lines: 0,
        hit: 0,
        branches: 0,
        branchHit: 0,
      };
      continue;
    }
    if (current === null) continue;
    if (line.startsWith('LF:')) current.lines = Number(line.slice(3));
    if (line.startsWith('LH:')) current.hit = Number(line.slice(3));
    if (line.startsWith('BRF:')) current.branches = Number(line.slice(4));
    if (line.startsWith('BRH:')) current.branchHit = Number(line.slice(4));
    if (line === 'end_of_record') {
      files.push({
        ...current,
        lineShare: current.lines === 0 ? 1 : current.hit / current.lines,
        branchShare: current.branches === 0 ? 1 : current.branchHit / current.branches,
        missed: current.lines - current.hit,
      });
      current = null;
    }
  }
  return files;
}

/** The files with the most uncovered lines, worst first. */
export function worstCovered(files, { limit = 10 } = {}) {
  return [...files]
    .filter((entry) => entry.missed > 0)
    .sort((a, b) => b.missed - a.missed || a.file.localeCompare(b.file))
    .slice(0, limit);
}

/** The whole-project figures, added up from the per-file ones. */
export function totals(files) {
  const lines = files.reduce((total, entry) => total + entry.lines, 0);
  const hit = files.reduce((total, entry) => total + entry.hit, 0);
  const branches = files.reduce((total, entry) => total + entry.branches, 0);
  const branchHit = files.reduce((total, entry) => total + entry.branchHit, 0);
  return {
    files: files.length,
    lines,
    hit,
    lineShare: lines === 0 ? 1 : hit / lines,
    branchShare: branches === 0 ? 1 : branchHit / branches,
  };
}

/** Read the report from disk, or say plainly that there is none. */
export function readReport(path = REPORT) {
  if (!hasReport(path)) return null;
  return parseLcov(readFileSync(path, 'utf8'));
}

const invoked = process.argv[1] !== undefined && process.argv[1].endsWith('coverage.mjs');
if (invoked) {
  const files = readReport();
  if (files === null) {
    console.error('no coverage report yet, run npm run coverage first');
    process.exitCode = 1;
  } else {
    const summary = totals(files);
    const percent = (share) => `${(share * 100).toFixed(2)}%`;
    console.log(`${summary.files} files, ${percent(summary.lineShare)} of lines`);
    for (const entry of worstCovered(files)) {
      console.log(`  ${entry.missed} lines uncovered in ${entry.file}`);
    }
  }
}
