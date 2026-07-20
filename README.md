# heddlewright

Draft, card deck and cloth analysis tooling for a weaving shed that runs both
dobby and Jacquard looms.

It covers the work between a design and a delivery note: drawing a draft and
reading the cloth it makes, cutting the cards a Jacquard reads, setting the
loom, planning the warp, running the floor and grading what comes off it.

Zero runtime dependencies. Node 20 or later.

## What is in it

| area                   | what it does                                                                                       |
| ---------------------- | -------------------------------------------------------------------------------------------------- |
| `src/draft`            | threading, tie-up, treadling, liftplan, drawdown, and the weaving file format                      |
| `src/weave`            | plain, twill, satin, honeycomb, overshot, summer and winter, and the arithmetic that combines them |
| `src/analysis`         | floats, interlacement, sett, crimp, cloth weight, colour and weave effects                         |
| `src/jacquard`         | machines, harness ties, punched cards, the card deck, lacing and the cylinder                      |
| `src/loom`             | the dobby chain, shed timing, take-up gearing, speed and efficiency                                |
| `src/yarn`, `src/warp` | count systems, reeds and denting, warping and creel plans                                          |
| `src/cloth`            | four point grading, the fault catalogue, the piece register, cut planning                          |
| `src/works`            | orders, scheduling onto looms, and what the floor actually made                                    |
| `src/store`            | an append-only journal with a hash chain, and a repository over it                                 |
| `src/service`          | a small router, the routes, and a thin node adapter                                                |
| `src/cli`              | the `heddle` command line tool                                                                     |

## Using it

```js
import { draft, drawdown, floats } from 'heddlewright';

const cloth = drawdown.drawdownFromDraft(
  draft.createDraft({
    name: 'four shaft twill',
    shafts: 4,
    treadles: 4,
    threading: [1, 2, 3, 4, 1, 2, 3, 4],
    tieup: [
      [1, 2],
      [2, 3],
      [3, 4],
      [1, 4],
    ],
    treadling: [[1], [2], [3], [4]],
  }),
);

floats.longestFloat(cloth);
```

## The command line

```
heddle draft --notation 3/1 --shafts 4 --ends 16
heddle floats --notation 2/2
heddle weave --kind satin --threads 5 --move 2
heddle sett --count 30 --notation 2/2
heddle count --value 30 --from ne --to tex
heddle check --shafts 8 --ends 4
```

`heddle help` lists the commands; `heddle help <command>` describes one.

## The service

`createApp()` builds the whole service over one journal and answers plain
objects, so it can be driven without a socket. `createServer()` puts node's
http server round it. `docs/surface.md` lists every route and is generated from
the code rather than written by hand.

## How it is put together

`src` is six layers deep and a file may only import downwards or sideways:
`util` at the floor, then the drafting and cloth arithmetic, then the machines,
then the works, the store, and the service and command line at the edge.
`docs/architecture.md` is generated from the imports themselves, and the check
fails the build if anything imports upwards.

## Running the checks

```
npm test            # the suite
npm run lint        # eslint
npm run format:check
npm run verify      # bytes, imports, dependencies, pairing, script guards
npm run surface:check
npm run architecture:check
npm run smoke       # the service over a real socket
npm run bench       # timings, which never fail
npm run coverage
```

## A note on the numbers

Every constant in here is worked out rather than copied. The cotton count
constant falls out of the pound and the yard; the satin move numbers fall out of
the coprimality rule; the honeycomb cell falls out of its own geometry, and the
draft that weaves it is checked against that geometry in the tests. Where a
figure could not be derived it is named as a shed convention and made an option
rather than a constant.
