# heddlewright

[![CI](https://github.com/rkaushik-dev/heddlewright/actions/workflows/ci.yml/badge.svg)](https://github.com/rkaushik-dev/heddlewright/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-007396)
![Maven](https://img.shields.io/badge/build-Maven-C71A36)
![JUnit 5](https://img.shields.io/badge/tests-JUnit%205-25A162)

Draft, card deck and cloth analysis tooling for a weaving shed that runs both
dobby and Jacquard looms.

It covers the work between a design and a delivery note: drawing a draft and
reading the cloth it makes, cutting the cards a Jacquard reads, setting the
loom, planning the warp, running the floor and grading what comes off it.

## Tech stack

- Java 21, built with Maven through the wrapper (`./mvnw`).
- picocli 4.7.7 for the `heddle` command line. The library itself has no
  runtime dependencies.
- The HTTP service runs on the JDK's own `com.sun.net.httpserver`, so there is
  no web framework to deploy.
- JUnit 5 and AssertJ for the tests, JaCoCo for coverage (the build fails below
  80% of lines), and Spotless with palantir-java-format for formatting.

## What is in it

Every class lives under `io.github.rkaushikdev.heddlewright`.

| package             | what it does                                                                                       |
| ------------------- | -------------------------------------------------------------------------------------------------- |
| `draft`             | threading, tie-up, treadling, liftplan, drawdown, and the weaving file format (WIF)                |
| `weave`             | plain, twill, satin, honeycomb, overshot, summer and winter, and the arithmetic that combines them |
| `analysis`          | floats, interlacement, sett, crimp, cloth weight, colour and weave effects                         |
| `jacquard`          | machines, harness ties, punched cards, the card deck, lacing and the cylinder                      |
| `loom`              | the dobby chain, shed timing, take-up gearing, speed and efficiency, the drop box motion           |
| `yarn`, `warp`      | count systems, reeds and denting, warping and creel plans                                          |
| `cloth`             | four point grading, the fault catalogue, the piece register, cut planning                          |
| `works`             | orders, scheduling onto looms, and what the floor actually made                                    |
| `store`             | an append-only journal with a hash chain, a repository over it, and snapshots                      |
| `service`           | a small router, the routes, and a thin adapter over the JDK HTTP server                            |
| `cli`               | the `heddle` command line tool                                                                     |
| `util`              | numbers, matrices, runs, errors, dates, tables, and the JSON model everything is written in        |
| `tools`             | the repository checks and the generators for `docs/`                                               |

`Heddlewright` at the package root lists every area as a namespace, so a name
found in the docs can be traced to the class it lives in without a search.

## Build, test and run

```
./mvnw -B verify        # compile, format check, the whole suite, the coverage floor
./mvnw test             # the suite alone
./mvnw spotless:apply   # format the sources
```

`verify` leaves the command line as one runnable jar:

```
java -jar target/heddlewright-0.2.0-cli.jar help
```

## Using it

```java
import io.github.rkaushikdev.heddlewright.analysis.Floats;
import io.github.rkaushikdev.heddlewright.draft.Draft;
import io.github.rkaushikdev.heddlewright.draft.Drawdown;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import java.util.List;

Matrix cloth = Drawdown.drawdownFromDraft(Draft.create(Json.obj(
        "name", "four shaft twill",
        "shafts", 4,
        "treadles", 4,
        "threading", List.of(1, 2, 3, 4, 1, 2, 3, 4),
        "tieup", List.of(List.of(1, 2), List.of(2, 3), List.of(3, 4), List.of(1, 4)),
        "treadling", List.of(List.of(1), List.of(2), List.of(3), List.of(4)))));

Floats.longestFloat(cloth);
```

A draft, a loom, an order or a piece is built from a plain JSON record, checked
field by field, and handed back as an immutable Java record. Every error is one
of five exception types (`InvalidArgumentException`, `DraftException`,
`CapacityException`, `NotFoundException`, `ConflictException`), each carrying a
code and a JSON detail that says what was wrong.

## The command line

```
heddle draft --notation 3/1 --shafts 4 --ends 16
heddle floats --notation 2/2
heddle weave --kind satin --threads 5 --move 2
heddle sett --count 30 --notation 2/2
heddle count --value 30 --from ne --to tex
heddle check --shafts 8 --ends 4
heddle boxes --pattern "ground ground blue blue" --boxes 2
heddle deck --weave "twill 2/2" --repeats 3
```

`heddle help` lists the commands; `heddle help <command>` describes one. An
option a command does not declare is refused by name, and `--` ends the
options. The exit code is 0 for success, 1 for a line that names no command,
and 2 for a command that was refused. A line that cannot be read at all, such as `--=1`, also exits 1.

## The service

`App.createApp()` builds the whole service over one journal and answers request
values with response values, so it can be driven without a socket.
`Server.createServer(app)` puts the JDK HTTP server round it, and
`Server.listen(server, port)` starts it. Every body is JSON, and an error comes
back as `{ "error", "code", "detail" }` with 400, 404, 409 or 422 by its code.

| method | path                           | what it answers                                              |
| ------ | ------------------------------ | ------------------------------------------------------------ |
| GET    | `/health`                      | the version, the journal length and head, records per kind   |
| GET    | `/routes`                      | every route, as `METHOD /path`                               |
| GET    | `/journal`                     | the journal's entries, `?since=` a sequence number           |
| GET    | `/journal/verify`              | whether the hash chain holds                                 |
| GET    | `/drafts`                      | a summary of every stored draft                              |
| POST   | `/drafts`                      | store a draft from its record, or from a `wif` file          |
| GET    | `/drafts/:id`                  | one draft's summary                                          |
| DELETE | `/drafts/:id`                  | remove a draft                                               |
| GET    | `/drafts/:id/cloth`            | the drawdown, `?rows=text` for printed rows                  |
| GET    | `/drafts/:id/analysis`         | floats, interlacement, firmness and problems, `?floatLimit=` |
| GET    | `/drafts/:id/wif`              | the draft as a WIF file                                      |
| GET    | `/weaves`                      | the measured catalogue, `?family=` to filter                 |
| GET    | `/weaves/families`             | the catalogue's families                                     |
| GET    | `/weaves/:name`                | one weave measured, with its repeat                          |
| GET    | `/weaves/:name/crepe`          | whether a weave reads as a crepe                             |
| POST   | `/twill`                       | a twill from its notation, its balance and angle             |
| GET    | `/satin/:threads`              | a satin and its legal moves, `?move=`                        |
| POST   | `/report`                      | the full cloth report for a draft, `render: text` to print   |
| GET    | `/pieces`                      | the piece register                                           |
| POST   | `/pieces`                      | open a piece                                                 |
| GET    | `/pieces/totals`               | the register added up by grade                               |
| GET    | `/pieces/:reference`           | one piece                                                    |
| POST   | `/pieces/:reference/defects`   | book a defect sheet and read it back                         |
| GET    | `/pieces/:reference/grade`     | the four point score and grade, `?acceptance=`               |
| POST   | `/pieces/:reference/cut`       | plan cuts round the booked faults                            |
| GET    | `/cloth/summary`               | totals, first quality share, `?woven=` to reconcile          |
| GET    | `/looms`                       | the loom register                                            |
| POST   | `/looms`                       | register a loom                                              |
| GET    | `/looms/summary`               | the looms grouped by shedding motion                         |
| GET    | `/looms/:reference`            | one loom                                                     |
| POST   | `/looms/capable`               | the looms that can take a style                              |
| POST   | `/looms/:reference/boxes`      | gait a weft order up on a loom's drop boxes                  |
| GET    | `/orders`                      | the order book, `?open=yes` for open orders                  |
| POST   | `/orders`                      | open an order                                                |
| GET    | `/orders/book`                 | the book totalled by state                                   |
| POST   | `/orders/:reference/advance`   | move an order on, or to a named state                        |
| POST   | `/plan`                        | put runs on looms, with the load and the late orders         |
| POST   | `/outturn`                     | book what the floor made, with the rates by loom             |
| POST   | `/outturn/against-plan`        | set the outturn beside the plan                              |

`docs/surface.md` lists every route, command and catalogued weave, and is
generated from the code rather than written by hand.

## The journal

Nothing is edited in place: every write appends an entry to the journal, and
the state is the projection of the whole journal. Each entry carries the
FNV-1a hash of the canonical JSON (keys sorted) of its sequence, type, payload,
time and the hash before it, so `Journal.toJson` writes a file whose chain
`Journal.fromJson` checks as it reads, and a file written at any time reads back
to the same head. Snapshots carry the head they were taken at and refuse a
journal with a different history.

## How it is put together

The packages form six layers and a class may only use classes downwards or
sideways: `util` at the floor, then the drafting and cloth arithmetic, then the
machines, then the works, the store, and the service and command line at the
edge. `docs/architecture.md` is generated from the imports themselves, and a
test fails the build if anything reaches upwards.

## Running the checks

The suite runs every check below. After a build, each one also runs on its own:

```
java -cp target/classes io.github.rkaushikdev.heddlewright.tools.Verify        # bytes, loading, dependencies, pairing, guards
java -cp target/classes io.github.rkaushikdev.heddlewright.tools.Surface       # rewrite docs/surface.md (--check to compare)
java -cp target/classes io.github.rkaushikdev.heddlewright.tools.Architecture  # rewrite docs/architecture.md (--check to compare)
java -cp target/classes io.github.rkaushikdev.heddlewright.tools.Smoke         # the service over a real socket
java -cp target/classes io.github.rkaushikdev.heddlewright.tools.Bench         # timings, which never fail
java -cp target/classes io.github.rkaushikdev.heddlewright.tools.Coverage      # the classes dragging coverage down
```

## A note on the numbers

Every constant in here is worked out rather than copied. The cotton count
constant falls out of the pound and the yard; the satin move numbers fall out of
the coprimality rule; the honeycomb cell falls out of its own geometry, and the
draft that weaves it is checked against that geometry in the tests. Where a
figure could not be derived it is named as a shed convention and made an option
rather than a constant.
