package io.github.rkaushikdev.heddlewright.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DatesTest {

    @Nested
    class AssertDay {

        @Test
        void takesACalendarDayAndHandsItBack() {
            assertThat(Dates.assertDay("2026-03-09")).isEqualTo("2026-03-09");
        }

        @Test
        void refusesAnythingThatIsNotOne() {
            assertThatThrownBy(() -> Dates.assertDay("9 March 2026")).hasMessageContaining("calendar day");
            assertThatThrownBy(() -> Dates.assertDay("2026-3-9")).isInstanceOf(InvalidArgumentException.class);
            assertThatThrownBy(() -> Dates.assertDay("")).isInstanceOf(InvalidArgumentException.class);
        }

        @Test
        void refusesADayTheCalendarDoesNotHave() {
            assertThatThrownBy(() -> Dates.assertDay("2026-02-30")).hasMessageContaining("not a real day");
            assertThatThrownBy(() -> Dates.assertDay("2025-02-29")).hasMessageContaining("not a real day");
        }

        @Test
        void takesTheLeapDayInALeapYear() {
            assertThat(Dates.assertDay("2024-02-29")).isEqualTo("2024-02-29");
        }
    }

    @Nested
    class AddDaysAndDaysBetween {

        @Test
        void countsForwardsAndBackwards() {
            assertThat(Dates.addDays("2026-03-09", 1)).isEqualTo("2026-03-10");
            assertThat(Dates.addDays("2026-03-09", -1)).isEqualTo("2026-03-08");
            assertThat(Dates.addDays("2026-03-09", 0)).isEqualTo("2026-03-09");
        }

        @Test
        void crossesAMonthAndAYearEnd() {
            assertThat(Dates.addDays("2026-01-31", 1)).isEqualTo("2026-02-01");
            assertThat(Dates.addDays("2026-12-31", 1)).isEqualTo("2027-01-01");
        }

        @Test
        void crossesALeapDay() {
            assertThat(Dates.addDays("2024-02-28", 1)).isEqualTo("2024-02-29");
            assertThat(Dates.addDays("2025-02-28", 1)).isEqualTo("2025-03-01");
        }

        @Test
        void measuresTheGapEitherWay() {
            assertThat(Dates.daysBetween("2026-03-09", "2026-03-16")).isEqualTo(7);
            assertThat(Dates.daysBetween("2026-03-16", "2026-03-09")).isEqualTo(-7);
            assertThat(Dates.daysBetween("2026-03-09", "2026-03-09")).isEqualTo(0);
        }

        @Test
        void isNotThrownByASummerTimeChange() {
            assertThat(Dates.daysBetween("2026-03-28", "2026-03-30")).isEqualTo(2);
            assertThat(Dates.daysBetween("2026-10-24", "2026-10-26")).isEqualTo(2);
        }
    }

    @Nested
    class Weekday {

        @Test
        void namesTheDay() {
            assertThat(Dates.weekday("2026-03-09")).isEqualTo("Monday");
            assertThat(Dates.weekday("2026-03-14")).isEqualTo("Saturday");
            assertThat(Dates.weekday("2026-03-15")).isEqualTo("Sunday");
        }
    }

    @Nested
    class IsWorkingDay {

        @Test
        void worksTheUsualWeekAndNotTheWeekend() {
            assertThat(Dates.WORKING_WEEK).containsExactly("Monday", "Tuesday", "Wednesday", "Thursday", "Friday");
            assertThat(Dates.isWorkingDay("2026-03-09")).isTrue();
            assertThat(Dates.isWorkingDay("2026-03-14")).isFalse();
        }

        @Test
        void doesNotWorkAHoliday() {
            assertThat(Dates.isWorkingDay("2026-03-09", Dates.Calendar.withHolidays(List.of("2026-03-09"))))
                    .isFalse();
        }

        @Test
        void takesAShedThatWorksSaturdays() {
            List<String> week = new ArrayList<>(Dates.WORKING_WEEK);
            week.add("Saturday");
            assertThat(Dates.isWorkingDay("2026-03-14", Dates.Calendar.ofWeek(week)))
                    .isTrue();
        }
    }

    @Nested
    class NextWorkingDay {

        @Test
        void staysPutOnAWorkingDay() {
            assertThat(Dates.nextWorkingDay("2026-03-09")).isEqualTo("2026-03-09");
        }

        @Test
        void skipsTheWeekend() {
            assertThat(Dates.nextWorkingDay("2026-03-14")).isEqualTo("2026-03-16");
        }

        @Test
        void skipsAHolidayThatFallsOnTheMonday() {
            assertThat(Dates.nextWorkingDay("2026-03-14", Dates.Calendar.withHolidays(List.of("2026-03-16"))))
                    .isEqualTo("2026-03-17");
        }

        @Test
        void refusesAWeekThatHasNoWorkingDaysAtAll() {
            assertThatThrownBy(() -> Dates.nextWorkingDay("2026-03-09", Dates.Calendar.ofWeek(List.of("Neverday"))))
                    .hasMessageContaining("no working day");
        }
    }

    @Nested
    class WorkingDaysBetween {

        @Test
        void countsTheFirstDayButNotTheLast() {
            assertThat(Dates.workingDaysBetween("2026-03-09", "2026-03-16")).isEqualTo(5);
        }

        @Test
        void isNothingAcrossAWeekendAlone() {
            assertThat(Dates.workingDaysBetween("2026-03-14", "2026-03-16")).isEqualTo(0);
        }

        @Test
        void refusesASpanThatRunsBackwards() {
            assertThatThrownBy(() -> Dates.workingDaysBetween("2026-03-16", "2026-03-09"))
                    .hasMessageContaining("before the first");
        }
    }

    @Nested
    class AddWorkingDays {

        @Test
        void stepsOverTheWeekend() {
            assertThat(Dates.addWorkingDays("2026-03-13", 1)).isEqualTo("2026-03-16");
            assertThat(Dates.addWorkingDays("2026-03-09", 4)).isEqualTo("2026-03-13");
        }

        @Test
        void landsOnTheNextWorkingDayWhenItStartsOnAWeekend() {
            assertThat(Dates.addWorkingDays("2026-03-14", 0)).isEqualTo("2026-03-16");
        }
    }

    @Nested
    class FinishDay {

        @Test
        void finishesTheSameDayWhenTheWorkFitsInOne() {
            assertThat(Dates.finishDay("2026-03-09", 8)).isEqualTo("2026-03-09");
            assertThat(Dates.finishDay("2026-03-09", 16)).isEqualTo("2026-03-09");
        }

        @Test
        void rollsOnToTheNextWorkingDayWhenItDoesNot() {
            assertThat(Dates.finishDay("2026-03-09", 17)).isEqualTo("2026-03-10");
            assertThat(Dates.finishDay("2026-03-13", 17)).isEqualTo("2026-03-16");
        }

        @Test
        void takesAShorterWorkingDay() {
            assertThat(Dates.finishDay("2026-03-09", 16, 8)).isEqualTo("2026-03-10");
        }

        @Test
        void refusesAWorkingDayOfNoHours() {
            assertThatThrownBy(() -> Dates.finishDay("2026-03-09", 8, 0)).isInstanceOf(InvalidArgumentException.class);
        }
    }
}
