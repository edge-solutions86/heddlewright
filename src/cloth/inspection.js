import { NotFound } from '../util/errors.js';
import { array, nonEmptyString, number, oneOf, plainObject } from '../util/check.js';
import { roundTo, sumBy } from '../util/numeric.js';
import { tally } from '../util/group.js';
import { defectPoints } from './fourpoint.js';

/**
 * The fault catalogue and the inspection sheet.
 *
 * A grader books a fault against a name, and the name carries where the fault
 * comes from. That is what turns a roll of points into an instruction: a sheet
 * full of weft bars is a weft supply problem, a sheet full of reed marks is a
 * loom problem, and the two go to different people.
 */

export const FAULT_ORIGINS = ['warp', 'weft', 'loom', 'yarn', 'handling'];

const FAULTS = new Map(
  [
    { code: 'broken_pick', name: 'broken pick', origin: 'weft', kind: 'length' },
    { code: 'double_pick', name: 'double pick', origin: 'loom', kind: 'length' },
    { code: 'missing_end', name: 'missing end', origin: 'warp', kind: 'length' },
    { code: 'float', name: 'float', origin: 'loom', kind: 'length' },
    { code: 'reed_mark', name: 'reed mark', origin: 'loom', kind: 'length' },
    { code: 'starting_mark', name: 'starting mark', origin: 'loom', kind: 'length' },
    { code: 'weft_bar', name: 'weft bar', origin: 'weft', kind: 'length' },
    { code: 'temple_mark', name: 'temple mark', origin: 'loom', kind: 'length' },
    { code: 'tight_end', name: 'tight end', origin: 'warp', kind: 'length' },
    { code: 'slack_end', name: 'slack end', origin: 'warp', kind: 'length' },
    { code: 'slub', name: 'slub', origin: 'yarn', kind: 'length' },
    { code: 'knot', name: 'knot', origin: 'yarn', kind: 'length' },
    { code: 'oil_stain', name: 'oil stain', origin: 'handling', kind: 'length' },
    { code: 'hole', name: 'hole', origin: 'loom', kind: 'hole' },
    { code: 'selvedge_fault', name: 'selvedge fault', origin: 'loom', kind: 'length' },
    { code: 'thin_place', name: 'thin place', origin: 'weft', kind: 'length' },
  ].map((fault) => [fault.code, Object.freeze(fault)]),
);

/** Every fault the catalogue knows, in code order. */
export function listFaults() {
  return [...FAULTS.values()].sort((a, b) => a.code.localeCompare(b.code));
}

/** Look a fault up by its code. */
export function faultByCode(code) {
  nonEmptyString(code, 'code');
  const fault = FAULTS.get(code);
  if (fault === undefined) {
    throw new NotFound(`no fault called "${code}"`, { code, known: [...FAULTS.keys()] });
  }
  return fault;
}

/** Every fault that comes from a given place. */
export function faultsByOrigin(origin) {
  oneOf(origin, 'origin', FAULT_ORIGINS);
  return listFaults().filter((fault) => fault.origin === origin);
}

/** Book a defect, filling in the kind and the origin from the catalogue. */
export function recordDefect(entry) {
  plainObject(entry, 'entry');
  const fault = faultByCode(entry.code);
  const inches = number(entry.inches, 'entry.inches', { min: 0.01 });
  const yard = number(entry.yard, 'entry.yard', { min: 0.0001 });
  const record = {
    code: fault.code,
    name: fault.name,
    origin: fault.origin,
    kind: fault.kind,
    inches,
    yard,
    note: entry.note ?? '',
  };
  record.points = defectPoints(record);
  return record;
}

/** Book a whole sheet at once. */
export function recordSheet(entries) {
  array(entries, 'entries');
  return entries.map((entry) => recordDefect(entry));
}

/** Points and counts grouped by fault, worst first. */
export function byFault(defects) {
  return tally(defects, {
    key: (defect) => defect.code,
    label: 'code',
    fields: { points: (defect) => defect.points },
    sortBy: 'points',
  });
}

/** Points and counts grouped by where the fault came from, worst first. */
export function byOrigin(defects) {
  return tally(defects, {
    key: (defect) => defect.origin,
    label: 'origin',
    fields: { points: (defect) => defect.points },
    sortBy: 'points',
  });
}

/** Where most of the trouble is coming from, or null when nothing is booked. */
export function dominantOrigin(defects) {
  const ranked = byOrigin(defects);
  return ranked.length === 0 ? null : ranked[0].origin;
}

/** Defects per hundred square yards, which is the count rather than the score. */
export function defectDensity(defects, { widthInches, yards }) {
  array(defects, 'defects');
  number(widthInches, 'widthInches', { min: 0.01 });
  number(yards, 'yards', { min: 0.01 });
  return roundTo((defects.length * 3600) / (widthInches * yards), 2);
}

/** A printed sheet: the totals, the ranking and the worst single yard. */
export function inspectionSheet(defects, { widthInches, yards }) {
  const records = array(defects, 'defects');
  const worst = records.reduce((best, defect) => (defect.points > best.points ? defect : best), {
    points: 0,
    yard: null,
  });
  return {
    defects: records.length,
    points: sumBy(records, (defect) => defect.points, 'defects'),
    density: defectDensity(records, { widthInches, yards }),
    byFault: byFault(records),
    byOrigin: byOrigin(records),
    dominantOrigin: dominantOrigin(records),
    worstDefect: worst.yard === null ? null : worst,
  };
}
