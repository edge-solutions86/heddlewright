package io.github.rkaushikdev.heddlewright.loom;

import io.github.rkaushikdev.heddlewright.util.Bounds;
import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.Cycle;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.Indexing;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Limits;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The dobby chain.
 *
 * <p>A dobby does the same job as a Jacquard on a much smaller scale: instead of a card per pick it
 * has a wooden lag with a peg hole for every shaft, and the lags are hinged into a chain that runs
 * over a barrel. A peg in the hole lifts the shaft on a positive dobby and holds it down on a
 * negative one, so the same chain weaves the reverse cloth on the other kind of machine.
 */
public final class Dobby {

    /** What a peg does to its shaft. */
    public enum Action {
        /** A peg lifts the shaft. */
        POSITIVE,
        /** A peg holds the shaft down. */
        NEGATIVE;

        /** The two actions, positive first. */
        public static final List<Action> DOBBY_ACTIONS = List.of(values());

        /** Read an action by its name, refusing anything else. */
        public static Action parse(Object text) {
            return Check.member(text, "action", values());
        }

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** A lag on which no shed opens, and why. */
    public record ShedlessLag(int lag, String reason) {}

    private Dobby() {}

    /** A single lag: one peg position per shaft. */
    public static List<Integer> lagFromShafts(int shafts, List<?> lifted) {
        Check.positiveInteger(shafts, "shafts");
        List<Object> pegs = Check.array(lifted, "lifted");
        int[] lag = new int[shafts];
        for (int i = 0; i < pegs.size(); i += 1) {
            int shaft = Check.integer(pegs.get(i), "lifted[" + i + "]", Bounds.atLeast(1));
            if (shaft > shafts) {
                throw new CapacityException(
                        "the dobby has no peg hole for that shaft", Json.obj("shaft", shaft, "shafts", shafts));
            }
            lag[shaft - 1] = 1;
        }
        return java.util.Arrays.stream(lag).boxed().toList();
    }

    /** The shafts a lag lifts on a positive dobby. */
    public static List<Integer> shaftsOnLag(List<Integer> lag) {
        return shaftsOnLag(lag, Action.POSITIVE);
    }

    /** The shafts a lag lifts, read back. */
    public static List<Integer> shaftsOnLag(List<Integer> lag, Action action) {
        Check.array(lag, "lag", 1);
        Check.required(action, "action");
        List<Integer> pegged = Indexing.setPositions(lag);
        if (action == Action.POSITIVE) {
            return pegged;
        }
        List<Integer> out = new ArrayList<>();
        for (int shaft = 1; shaft <= lag.size(); shaft += 1) {
            if (!pegged.contains(shaft)) {
                out.add(shaft);
            }
        }
        return List.copyOf(out);
    }

    /** A whole chain from a liftplan, one lag per pick, as wide as the liftplan. */
    public static Matrix chainFromLiftplan(Matrix liftplan) {
        return chainFromLiftplan(liftplan, liftplan.cols());
    }

    /** A whole chain from a liftplan, on a dobby with this many shafts. */
    public static Matrix chainFromLiftplan(Matrix liftplan, int shafts) {
        Check.positiveInteger(shafts, "shafts");
        if (liftplan.cols() > shafts) {
            throw new CapacityException(
                    "the liftplan needs more shafts than the dobby has",
                    Json.obj("shafts", shafts, "needed", liftplan.cols()));
        }
        int[][] chain = new int[liftplan.rows()][shafts];
        for (int p = 0; p < liftplan.rows(); p += 1) {
            for (int s = 0; s < liftplan.cols(); s += 1) {
                chain[p][s] = liftplan.get(p, s);
            }
        }
        return Matrix.of(chain);
    }

    /** The liftplan a chain weaves on a positive dobby. */
    public static Matrix liftplanFromChain(Matrix chain) {
        return liftplanFromChain(chain, Action.POSITIVE);
    }

    /** The liftplan a chain weaves, which is the reverse on a negative dobby. */
    public static Matrix liftplanFromChain(Matrix chain, Action action) {
        Check.required(action, "action");
        return action == Action.POSITIVE ? chain : chain.invert();
    }

    /** How many pegs the chain needs, which is what the peg box has to hold. */
    public static int pegCount(Matrix chain) {
        return chain.countOnes();
    }

    /** How many lags the chain runs to. */
    public static int chainLength(Matrix chain) {
        return chain.rows();
    }

    /** The shortest chain that weaves the same cloth. */
    public static int chainRepeat(Matrix chain) {
        return Cycle.period(lagKeys(chain));
    }

    private static List<String> lagKeys(Matrix chain) {
        return chain.allRows().stream()
                .map(lag -> lag.stream().map(String::valueOf).collect(Collectors.joining()))
                .toList();
    }

    /** Two chains weave the same cloth when they match round the barrel. */
    public static boolean sameChain(Matrix a, Matrix b) {
        return String.join("|", Cycle.canonicalRotation(lagKeys(a)))
                .equals(String.join("|", Cycle.canonicalRotation(lagKeys(b))));
    }

    /** Lags pegged identically, which a shed could have cut once and reused. */
    public static List<List<Integer>> repeatedLags(Matrix chain) {
        return Indexing.duplicateGroups(lagKeys(chain), (String key) -> key);
    }

    /** Refuse a chain the barrel cannot carry. */
    public static Matrix assertChainFitsBarrel(Matrix chain, int lagCapacity) {
        Check.positiveInteger(lagCapacity, "lagCapacity");
        Limits.assertFits(
                chain.rows(),
                lagCapacity,
                "chain",
                "the chain is longer than the barrel holds",
                Json.obj("lags", chain.rows(), "lagCapacity", lagCapacity));
        return chain;
    }

    /** The lags on which a positive dobby opens no shed. */
    public static List<ShedlessLag> shedlessLags(Matrix chain) {
        return shedlessLags(chain, Action.POSITIVE);
    }

    /**
     * A lag with every hole pegged, or none, opens no shed. On a negative dobby the two cases swap
     * over, which is exactly the mistake this catches.
     */
    public static List<ShedlessLag> shedlessLags(Matrix chain, Action action) {
        Matrix plan = liftplanFromChain(chain, action);
        List<ShedlessLag> out = new ArrayList<>();
        for (int p = 0; p < plan.rows(); p += 1) {
            int raised = plan.row(p).stream().mapToInt(Integer::intValue).sum();
            if (raised == 0) {
                out.add(new ShedlessLag(p + 1, "nothing lifted"));
            } else if (raised == plan.cols()) {
                out.add(new ShedlessLag(p + 1, "everything lifted"));
            }
        }
        return List.copyOf(out);
    }

    /** The chain written out as a peg plan for the shed floor. */
    public static String renderChain(Matrix chain) {
        return chain.format('o', '.');
    }

    /** Refuse a chain that would leave a positive dobby standing with no shed at all. */
    public static Matrix assertChainWeaves(Matrix chain) {
        return assertChainWeaves(chain, Action.POSITIVE);
    }

    /** Refuse a chain that would leave the loom standing with no shed at all. */
    public static Matrix assertChainWeaves(Matrix chain, Action action) {
        if (shedlessLags(chain, action).size() == chain.rows()) {
            throw new DraftException("no lag in the chain opens a shed", Json.obj("lags", chain.rows()));
        }
        return chain;
    }
}
