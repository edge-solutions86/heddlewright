package io.github.rkaushikdev.heddlewright.works;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.ConflictException;
import io.github.rkaushikdev.heddlewright.util.Group;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonObject;
import io.github.rkaushikdev.heddlewright.util.NotFoundException;
import io.github.rkaushikdev.heddlewright.util.Numeric;
import io.github.rkaushikdev.heddlewright.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Yarn stock.
 *
 * <p>A yarn store has three numbers for every line and they are never the same: what is on the
 * shelf, what is promised to jobs not yet started, and what is left to promise. A store that keeps
 * only the first will happily promise the same cone to two warps, and nobody finds out until the
 * second one is beamed.
 */
public final class Stock {

    /** Stock promised to an order. */
    public record Reservation(String order, double kilograms) {}

    /** A stock line: what it is, what is on the shelf, and what is promised out of it. */
    public record Line(String code, String description, double tex, double kilograms, List<Reservation> reservations) {
        /** Copies the promises. */
        public Line {
            reservations = List.copyOf(reservations);
        }

        /** The same line with another shelf weight. */
        public Line withKilograms(double value) {
            return new Line(code, description, tex, value, reservations);
        }

        private Line withReservations(List<Reservation> value) {
            return new Line(code, description, tex, kilograms, value);
        }
    }

    private record Claim(String order, double kilograms, String code) {}

    private Stock() {}

    /** Open a stock line with no description. */
    public static Line createLine(String code, double tex, double kilograms) {
        return createLine(code, "", tex, kilograms);
    }

    /** Open a stock line with nothing promised. */
    public static Line createLine(String code, String description, double tex, double kilograms) {
        Check.nonEmptyString(code, "code");
        Check.positiveNumber(tex, "tex");
        Check.number(kilograms, "kilograms", Bounds.atLeast(0));
        return new Line(code, description, tex, Numeric.roundTo(kilograms, 4), List.of());
    }

    /** What a line has promised out. */
    public static double reserved(Line line) {
        return Numeric.roundTo(Numeric.sumBy(line.reservations(), Reservation::kilograms, "reservations"), 4);
    }

    /** What is left to promise. */
    public static double available(Line line) {
        return Numeric.roundTo(line.kilograms() - reserved(line), 4);
    }

    private static Optional<Reservation> promiseFor(Line line, String order) {
        return line.reservations().stream()
                .filter(entry -> entry.order().equals(order))
                .findFirst();
    }

    /** Promise stock to a job, which cannot promise what is not there, nor promise one job twice. */
    public static Line reserve(Line line, String order, double kilograms) {
        Check.nonEmptyString(order, "order");
        Check.positiveNumber(kilograms, "kilograms");
        if (kilograms > available(line)) {
            throw new ConflictException(
                    line.code() + " has only " + Text.number(available(line)) + " kg left to promise",
                    Json.obj("code", line.code(), "wanted", kilograms, "available", available(line)));
        }
        if (promiseFor(line, order).isPresent()) {
            throw new ConflictException(
                    order + " already has stock promised on " + line.code(),
                    Json.obj("code", line.code(), "order", order));
        }
        List<Reservation> more = new ArrayList<>(line.reservations());
        more.add(new Reservation(order, Numeric.roundTo(kilograms, 4)));
        return line.withReservations(more);
    }

    /** Release a promise, which a cancelled order does. */
    public static Line release(Line line, String order) {
        Check.nonEmptyString(order, "order");
        if (promiseFor(line, order).isEmpty()) {
            throw new NotFoundException(
                    order + " has nothing promised on " + line.code(), Json.obj("code", line.code(), "order", order));
        }
        return line.withReservations(line.reservations().stream()
                .filter(entry -> !entry.order().equals(order))
                .toList());
    }

    /**
     * Take stock off the shelf against a promise. The promise goes with it, because yarn that has
     * been issued is no longer promised: it is gone.
     */
    public static Line issue(Line line, String order, double kilograms) {
        Check.positiveNumber(kilograms, "kilograms");
        Reservation promise = promiseFor(line, order)
                .orElseThrow(() -> new NotFoundException(
                        order + " has nothing promised on " + line.code(), Json.obj("order", order)));
        if (kilograms > promise.kilograms()) {
            throw new ConflictException(
                    "that is more than the order was promised",
                    Json.obj("order", order, "promised", promise.kilograms(), "wanted", kilograms));
        }
        double left = Numeric.roundTo(promise.kilograms() - kilograms, 4);
        List<Reservation> reservations = line.reservations().stream()
                .map(entry -> entry.order().equals(order) ? new Reservation(entry.order(), left) : entry)
                .filter(entry -> entry.kilograms() > 0)
                .toList();
        return new Line(
                line.code(),
                line.description(),
                line.tex(),
                Numeric.roundTo(line.kilograms() - kilograms, 4),
                reservations);
    }

    /** Put stock in, which a delivery does. */
    public static Line receive(Line line, double kilograms) {
        Check.positiveNumber(kilograms, "kilograms");
        return line.withKilograms(Numeric.roundTo(line.kilograms() + kilograms, 4));
    }

    /** Find a line by code. */
    public static Line lineByCode(List<Line> lines, String code) {
        Check.array(lines, "lines");
        return lines.stream()
                .filter(line -> line.code().equals(code))
                .findFirst()
                .orElseThrow(
                        () -> new NotFoundException("no stock line called \"" + code + "\"", Json.obj("code", code)));
    }

    /** Lines with nothing left to promise, which is what a buyer wants to see. */
    public static List<String> fullyPromised(List<Line> lines) {
        Check.array(lines, "lines");
        return lines.stream()
                .filter(line -> available(line) <= 0)
                .map(Line::code)
                .toList();
    }

    /** What each order has promised across the whole store, most first. */
    public static List<JsonObject> byOrder(List<Line> lines) {
        Check.array(lines, "lines");
        List<Claim> claims = new ArrayList<>();
        for (Line line : lines) {
            for (Reservation entry : line.reservations()) {
                claims.add(new Claim(entry.order(), entry.kilograms(), line.code()));
            }
        }
        return Group.tally(
                claims,
                Group.<Claim>by(Claim::order)
                        .label("order")
                        .field("kilograms", Claim::kilograms)
                        .sortBy("kilograms"));
    }

    /** Refuse a store whose promises add up to more than it holds. */
    public static List<Line> assertStoreSound(List<Line> lines) {
        Check.array(lines, "lines");
        List<String> over = lines.stream()
                .filter(line -> reserved(line) > line.kilograms())
                .map(Line::code)
                .toList();
        if (!over.isEmpty()) {
            throw new InvalidArgumentException("some lines have promised more than they hold", Json.obj("lines", over));
        }
        return lines;
    }
}
