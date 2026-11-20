# Changelog

## 0.2.0

Colour weaving and the layer check.

- Drop box motion: allot a box to each weft, lace the chain, count the changes,
  and refuse a weft order a loom boxed at one end cannot pick. `heddle boxes`
  prints the chain and `POST /looms/:reference/boxes` returns it.
- A style may be quoted as a weft pattern rather than a colour count. The
  pattern carries the order, which is what decides whether the boxing will pick
  it, and the register checks it along with width, shafts, hooks and boxes.
- Costing works the loom time out at the speed the box changing leaves rather
  than the speed on the loom's plate.
- `docs/architecture.md` is generated from the imports, and the build fails if
  any class uses one in a layer above its own.
- The coverage floor is declared in the build, and the `Coverage` tool ranks the
  classes dragging the figure down.
- Shared test fixtures live in one `Fixtures` class. The four shaft twill had
  been written out in nine files and three of them had drifted.
- `Numeric.sum` and `Numeric.sumBy` replace the totals that had been folded by
  hand across the source. Three of them gave a wrong figure rather than naming
  the record at fault.

## 0.1.0

The first working shed.

- Drafting: threading, tie-up, treadling, liftplan, drawdown, and the
  transforms between them. WIF in and out.
- Weave construction: plain, twill, satin, honeycomb, overshot, summer and
  winter, mock leno, double cloth and crepe, with a measured catalogue.
- Cloth analysis: floats, interlacement, sett, crimp, weight, colour order and
  a report that puts them together against a specification.
- Jacquard: machines, harness ties, cards, decks, lacing, cylinders, punching,
  quotes and verification.
- The loom floor: dobby chains, shed timing, take-up wheels, speeds,
  efficiency, let-off and temples.
- Yarn and warp: counts, packages, blends, twist, reeds, warping, creels, beams
  and heddles.
- Cloth room: four point grading, inspection, rolls, cut plans, lots and
  finishing shrinkage.
- Works: orders, scheduling, production booking, costing, despatch and stock.
- An append-only journal with a hash chain, a record projection over it, and
  snapshots.
- An HTTP service and a command line over the same code.
