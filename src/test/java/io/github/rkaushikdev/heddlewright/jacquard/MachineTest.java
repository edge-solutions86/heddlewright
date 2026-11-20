package io.github.rkaushikdev.heddlewright.jacquard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.heddlewright.util.CapacityException;
import io.github.rkaushikdev.heddlewright.util.InvalidArgumentException;
import io.github.rkaushikdev.heddlewright.util.Json;
import io.github.rkaushikdev.heddlewright.util.JsonArray;
import io.github.rkaushikdev.heddlewright.util.NotFoundException;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MachineTest {

    @Nested
    class ListMachines {

        @Test
        void listsThemSmallestFirst() {
            assertThat(Machine.listMachines()).extracting(Machine::hooks).isSorted();
        }

        @Test
        void everyMachineHasALayoutThatMultipliesOutToItsHookCount() {
            for (Machine machine : Machine.listMachines()) {
                assertThat(machine.rows() * machine.columns()).isEqualTo(machine.hooks());
            }
        }

        @Test
        void handsBackFrozenRecords() {
            // A record has no setters, so nothing can change one after it is built.
            assertThat(Machine.listMachines().get(0).getClass().isRecord()).isTrue();
            assertThatThrownBy(() ->
                            Machine.listMachines().add(Machine.listMachines().get(0)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class MachineByName {

        @Test
        void findsAMachineAndReportsItsLayout() {
            Machine machine = Machine.machineByName("400 hook");
            assertThat(machine.hooks()).isEqualTo(400);
            assertThat(machine.rows()).isEqualTo(8);
            assertThat(machine.columns()).isEqualTo(50);
        }

        @Test
        void namesWhatItDoesKnowWhenItCannotFindOne() {
            NotFoundException err =
                    catchThrowableOfType(NotFoundException.class, () -> Machine.machineByName("900 hook"));
            assertThat(err).isNotNull();
            assertThat(((JsonArray) err.detail().get("known")).items()).contains(Json.of("400 hook"));
        }
    }

    @Nested
    class SmallestMachineFor {

        @Test
        void picksTheFirstMachineLargeEnough() {
            assertThat(Machine.smallestMachineFor(390).hooks()).isEqualTo(400);
            assertThat(Machine.smallestMachineFor(400).hooks()).isEqualTo(400);
            assertThat(Machine.smallestMachineFor(401).hooks()).isEqualTo(600);
        }

        @Test
        void refusesARepeatPastTheLargestMachine() {
            assertThatThrownBy(() -> Machine.smallestMachineFor(5000)).isInstanceOf(CapacityException.class);
        }
    }

    @Nested
    class DefineMachine {

        @Test
        void worksTheHookCountOutFromTheBoard() {
            Machine machine = Machine.defineMachine("shed spare", 6, 30);
            assertThat(machine.hooks()).isEqualTo(180);
            assertThat(machine.lift()).isEqualTo(Machine.Lift.SINGLE);
        }

        @Test
        void namesTheTwoLiftsAndRefusesAnythingElse() {
            assertThat(Machine.LIFT_TYPES).extracting(Object::toString).containsExactly("single", "double");
            assertThatThrownBy(() -> Machine.defineMachine("x", 2, 2, Machine.Lift.parse("triple"), 8))
                    .isInstanceOf(InvalidArgumentException.class);
        }
    }

    @Nested
    class HookPositionAndHookAt {

        private final Machine machine = Machine.machineByName("400 hook");

        @Test
        void readsAlongTheRowsOfTheBoard() {
            assertThat(Machine.hookPosition(machine, 1)).isEqualTo(new Machine.Position(1, 1));
            assertThat(Machine.hookPosition(machine, 50)).isEqualTo(new Machine.Position(1, 50));
            assertThat(Machine.hookPosition(machine, 51)).isEqualTo(new Machine.Position(2, 1));
            assertThat(Machine.hookPosition(machine, 400)).isEqualTo(new Machine.Position(8, 50));
        }

        @Test
        void roundTripsEveryHookThroughItsPosition() {
            for (int hook : List.of(1, 7, 50, 51, 231, 400)) {
                Machine.Position position = Machine.hookPosition(machine, hook);
                assertThat(Machine.hookAt(machine, position.row(), position.column()))
                        .isEqualTo(hook);
            }
        }

        @Test
        void refusesAHookPastTheMachineAndAPositionOffTheBoard() {
            assertThatThrownBy(() -> Machine.hookPosition(machine, 401)).isInstanceOf(CapacityException.class);
            assertThatThrownBy(() -> Machine.hookAt(machine, 9, 1)).isInstanceOf(CapacityException.class);
            assertThatThrownBy(() -> Machine.hookAt(machine, 1, 51)).isInstanceOf(CapacityException.class);
        }
    }

    @Nested
    class RepeatsAcross {

        private final Machine machine = Machine.machineByName("400 hook");

        @Test
        void countsWholeRepeatsAndWhatIsLeftOver() {
            assertThat(Machine.repeatsAcross(machine, 1600, 400)).isEqualTo(new Machine.Repeats(4, 0, 0));
        }

        @Test
        void reportsTheEndsThatDoNotFillARepeat() {
            assertThat(Machine.repeatsAcross(machine, 1000, 300)).isEqualTo(new Machine.Repeats(3, 100, 100));
        }

        @Test
        void refusesARepeatWiderThanTheMachine() {
            assertThatThrownBy(() -> Machine.repeatsAcross(machine, 1600, 500)).isInstanceOf(CapacityException.class);
        }
    }

    @Nested
    class AssertFits {

        @Test
        void passesARepeatThatFits() {
            Machine machine = Machine.machineByName("600 hook");
            assertThat(Machine.assertFits(machine, 600)).isSameAs(machine);
        }

        @Test
        void saysHowManyHooksShortItIs() {
            CapacityException err = catchThrowableOfType(
                    CapacityException.class, () -> Machine.assertFits(Machine.machineByName("400 hook"), 460));
            assertThat(err.detail().get("hooks")).isEqualTo(Json.of(400));
            assertThat(err.detail().get("hooksNeeded")).isEqualTo(Json.of(460));
            assertThat(err.detail().get("short")).isEqualTo(Json.of(60));
        }
    }

    @Nested
    class CardsPerCycle {

        @Test
        void isOneOnASingleLiftAndTwoOnADouble() {
            assertThat(Machine.cardsPerCycle(Machine.machineByName("400 hook"))).isEqualTo(1);
            assertThat(Machine.cardsPerCycle(Machine.machineByName("600 hook"))).isEqualTo(2);
        }
    }
}
