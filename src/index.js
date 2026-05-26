/**
 * The public surface.
 *
 * Everything a caller outside the package should reach for is re-exported here
 * under a namespace per area, rather than as one flat pile of two hundred
 * names. The namespaces match the directories, so a name found in the docs can
 * be traced to the file it lives in without a search.
 */

export * as errors from './util/errors.js';
export * as check from './util/check.js';
export * as matrix from './util/matrix.js';
export * as numeric from './util/numeric.js';
export * as runs from './util/runs.js';
export * as cycle from './util/cycle.js';
export * as ids from './util/ids.js';
export * as json from './util/json.js';
export * as table from './util/table.js';
export * as range from './util/range.js';
export * as indexing from './util/indexing.js';
export * as group from './util/group.js';
export * as dates from './util/dates.js';
export * as units from './util/units.js';
export * as limits from './util/limits.js';

export * as threading from './draft/threading.js';
export * as tieup from './draft/tieup.js';
export * as treadling from './draft/treadling.js';
export * as liftplan from './draft/liftplan.js';
export * as drawdown from './draft/drawdown.js';
export * as draft from './draft/draft.js';
export * as ini from './draft/ini.js';
export * as wif from './draft/wif.js';
export * as wifWrite from './draft/wifwrite.js';
export * as transform from './draft/transform.js';
export * as equivalence from './draft/equivalence.js';

export * as plain from './weave/plain.js';
export * as twill from './weave/twill.js';
export * as satin from './weave/satin.js';
export * as honeycomb from './weave/honeycomb.js';
export * as combine from './weave/combine.js';
export * as blocks from './weave/blocks.js';
export * as overshot from './weave/overshot.js';
export * as summerWinter from './weave/summerwinter.js';
export * as leno from './weave/leno.js';
export * as doublecloth from './weave/doublecloth.js';
export * as crepe from './weave/crepe.js';
export * as catalogue from './weave/catalogue.js';

export * as floats from './analysis/floats.js';
export * as interlacement from './analysis/interlacement.js';
export * as sett from './analysis/sett.js';
export * as crimp from './analysis/crimp.js';
export * as weight from './analysis/weight.js';
export * as colourorder from './analysis/colourorder.js';
export * as diff from './analysis/diff.js';
export * as motif from './analysis/motif.js';
export * as report from './analysis/report.js';

export * as machine from './jacquard/machine.js';
export * as harness from './jacquard/harness.js';
export * as card from './jacquard/card.js';
export * as deck from './jacquard/deck.js';
export * as lacing from './jacquard/lacing.js';
export * as cylinder from './jacquard/cylinder.js';
export * as punch from './jacquard/punch.js';
export * as estimate from './jacquard/estimate.js';
export * as deckVerify from './jacquard/verify.js';
export * as design from './jacquard/design.js';

export * as dobby from './loom/dobby.js';
export * as shed from './loom/shed.js';
export * as takeup from './loom/takeup.js';
export * as speed from './loom/speed.js';
export * as efficiency from './loom/efficiency.js';
export * as registry from './loom/registry.js';
export * as letoff from './loom/letoff.js';
export * as temple from './loom/temple.js';

export * as count from './yarn/count.js';
export * as yarnPackage from './yarn/package.js';
export * as blend from './yarn/blend.js';
export * as twist from './yarn/twist.js';
export * as reed from './warp/reed.js';
export * as warping from './warp/warping.js';
export * as creel from './warp/creel.js';
export * as beam from './warp/beam.js';
export * as heddles from './warp/heddles.js';

export * as fourpoint from './cloth/fourpoint.js';
export * as inspection from './cloth/inspection.js';
export * as roll from './cloth/roll.js';
export * as cutplan from './cloth/cutplan.js';
export * as lot from './cloth/lot.js';
export * as finishing from './cloth/finishing.js';

export * as orders from './works/orders.js';
export * as schedule from './works/schedule.js';
export * as production from './works/production.js';
export * as costing from './works/costing.js';
export * as despatch from './works/despatch.js';

export * as journal from './store/journal.js';
export * as repository from './store/repository.js';
export * as snapshot from './store/snapshot.js';

export * as router from './service/router.js';
export * as app from './service/app.js';
export * as middleware from './service/middleware.js';

/** The package version, kept beside the surface it describes. */
export const VERSION = '0.1.0';
