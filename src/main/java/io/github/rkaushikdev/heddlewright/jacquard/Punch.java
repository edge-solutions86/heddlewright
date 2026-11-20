package io.github.rkaushikdev.heddlewright.jacquard;

import io.github.rkaushikdev.heddlewright.util.Check;
import io.github.rkaushikdev.heddlewright.util.DraftException;
import io.github.rkaushikdev.heddlewright.util.Indexing;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.Matrix;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Turning a design into a punching job.
 *
 * <p>A design is drawn at the full width of the cloth, but the machine only has so many hooks, so
 * the harness tie decides which ends move together. Every end a hook pulls has to agree on every
 * pick, because one hook can only be up or down. When they do not agree the design cannot be woven
 * on that tie, and saying so before the cards are cut is the whole point of this module.
 */
public final class Punch {

    /** Two ends on one hook that disagree about a pick. */
    public record Conflict(int pick, int hook, List<Integer> ends, List<Integer> states) {
        /** Copies the lists. */
        public Conflict {
            ends = List.copyOf(ends);
            states = List.copyOf(states);
        }
    }

    /** A punching job: its shape, the hook plan, the cards, and how many hooks it uses. */
    public record Job(String machine, int hooks, int picks, int ends, Matrix hookPlan, List<Card> deck, int hooksUsed) {
        /** Copies the deck. */
        public Job {
            deck = List.copyOf(deck);
        }
    }

    private record Decided(int cell, int end) {}

    private Punch() {}

    /**
     * Ends that share a hook but disagree about a pick. Each entry names the pick, the hook, and the
     * ends on both sides of the disagreement.
     */
    public static List<Conflict> tieConflicts(Matrix design, List<Integer> tie, int hooks) {
        Check.required(design, "design");
        Check.array(tie, "tie", 1);
        Check.positiveInteger(hooks, "hooks");
        if (tie.size() != design.cols()) {
            throw new DraftException(
                    "the tie does not cover the design width",
                    Json.obj("designEnds", design.cols(), "tieEnds", tie.size()));
        }
        List<Conflict> conflicts = new ArrayList<>();
        for (int p = 0; p < design.rows(); p += 1) {
            Map<Integer, Decided> decided = new HashMap<>();
            for (int e = 0; e < tie.size(); e += 1) {
                int hook = tie.get(e);
                if (hook < 1 || hook > hooks) {
                    throw new DraftException(
                            "end " + (e + 1) + " is tied to a hook the machine does not have",
                            Json.obj("end", e + 1, "hook", hook, "hooks", hooks));
                }
                int cell = design.get(p, e);
                Decided first = decided.get(hook);
                if (first == null) {
                    decided.put(hook, new Decided(cell, e + 1));
                    continue;
                }
                if (first.cell() != cell) {
                    conflicts.add(new Conflict(p + 1, hook, List.of(first.end(), e + 1), List.of(first.cell(), cell)));
                }
            }
        }
        return List.copyOf(conflicts);
    }

    /** The liftplan a design and a tie produce, once the design is known to agree. */
    public static Matrix hookPlanFromDesign(Matrix design, List<Integer> tie, int hooks) {
        List<Conflict> conflicts = tieConflicts(design, tie, hooks);
        if (!conflicts.isEmpty()) {
            throw new DraftException(
                    "the design cannot be woven on that tie",
                    Json.obj("conflicts", conflicts.size(), "first", conflicts.get(0)));
        }
        int[][] plan = new int[design.rows()][hooks];
        for (int p = 0; p < design.rows(); p += 1) {
            for (int e = 0; e < tie.size(); e += 1) {
                plan[p][tie.get(e) - 1] = design.get(p, e);
            }
        }
        return Matrix.of(plan);
    }

    /** The whole job: the hook plan, the cards, and what was checked on the way. */
    public static Job punchJob(Machine machine, Matrix design, List<Integer> tie) {
        Matrix plan = hookPlanFromDesign(design, tie, machine.hooks());
        List<Card> deck = Deck.deckFromLiftplan(machine, plan);
        return new Job(
                machine.name(),
                machine.hooks(),
                design.rows(),
                design.cols(),
                plan,
                deck,
                usedHooks(plan).size());
    }

    /** Hooks the plan ever lifts, 1-based. */
    public static List<Integer> usedHooks(Matrix hookPlan) {
        Check.required(hookPlan, "hookPlan");
        TreeSet<Integer> used = new TreeSet<>();
        for (List<Integer> row : hookPlan.allRows()) {
            used.addAll(Indexing.setPositions(row));
        }
        return List.copyOf(used);
    }

    /** Weave the design back out of a hook plan and a tie, to check the cutting. */
    public static Matrix designFromHookPlan(Matrix hookPlan, List<Integer> tie) {
        Check.required(hookPlan, "hookPlan");
        Check.array(tie, "tie", 1);
        int[][] design = new int[hookPlan.rows()][tie.size()];
        for (int p = 0; p < hookPlan.rows(); p += 1) {
            for (int e = 0; e < tie.size(); e += 1) {
                int hook = tie.get(e);
                if (hook < 1 || hook > hookPlan.cols()) {
                    throw new DraftException(
                            "end " + (e + 1) + " is tied to a hook the plan has no column for",
                            Json.obj("end", e + 1, "hook", hook, "hooks", hookPlan.cols()));
                }
                design[p][e] = hookPlan.get(p, hook - 1);
            }
        }
        return Matrix.of(design);
    }

    /**
     * Cut a design down to one repeat of the tie, which is the part that actually has to be punched.
     * The rest of the width is the harness repeating it.
     */
    public static Matrix designRepeat(Matrix design, List<Integer> tie) {
        Check.required(design, "design");
        Check.array(tie, "tie", 1);
        int width = Math.min(repeatWidthOfTie(tie), design.cols());
        int[][] out = new int[design.rows()][width];
        for (int p = 0; p < design.rows(); p += 1) {
            for (int e = 0; e < width; e += 1) {
                out[p][e] = design.get(p, e);
            }
        }
        return Matrix.of(out);
    }

    /** How wide one pass of the tie is before it starts over. */
    public static int repeatWidthOfTie(List<Integer> tie) {
        Check.array(tie, "tie", 1);
        for (int width = 1; width <= tie.size(); width += 1) {
            if (tie.size() % width != 0) {
                continue;
            }
            boolean matches = true;
            for (int i = width; i < tie.size() && matches; i += 1) {
                if (!tie.get(i).equals(tie.get(i % width))) {
                    matches = false;
                }
            }
            if (matches) {
                return width;
            }
        }
        return tie.size();
    }
}
