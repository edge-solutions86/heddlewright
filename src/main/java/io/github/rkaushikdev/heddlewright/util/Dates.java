package io.github.rkaushikdev.heddlewright.util;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Dates, kept as plain day strings.
 *
 * <p>A shed works in days and shifts, not in instants, and an instant carries a time zone that
 * turns a delivery date into the day before it somewhere. So a date here is the ten characters of
 * a calendar day, as it is written on an order and stored in a record, and the arithmetic is done
 * on whole days, which cannot drift.
 */
public final class Dates {

    /** The days a shed normally works. */
    public static final List<String> WORKING_WEEK = List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday");

    /** Which days a shed works and which it does not, whatever the week says. */
    public record Calendar(List<String> week, List<String> holidays) {

        /** The usual week with no holidays. */
        public static final Calendar STANDARD = new Calendar(WORKING_WEEK, List.of());

        /** Copies the lists. */
        public Calendar {
            week = List.copyOf(week);
            holidays = List.copyOf(holidays);
        }

        /** The usual week with these holidays. */
        public static Calendar withHolidays(List<String> holidays) {
            return new Calendar(WORKING_WEEK, holidays);
        }

        /** This week with no holidays. */
        public static Calendar ofWeek(List<String> week) {
            return new Calendar(week, List.of());
        }
    }

    private static final Pattern PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private Dates() {}

    /** Check a day string and hand it back. */
    public static String assertDay(Object day) {
        return assertDay(day, "day");
    }

    /** Check a day string and hand it back, naming the field in any complaint. */
    public static String assertDay(Object day, String field) {
        String text = Check.nonEmptyString(day, field);
        if (!PATTERN.matcher(text).matches()) {
            throw new InvalidArgumentException(
                    field + " must be written as a calendar day", Json.obj("field", field, "got", text));
        }
        try {
            LocalDate.parse(text);
        } catch (DateTimeException err) {
            throw new InvalidArgumentException(field + " is not a real day", Json.obj("field", field, "got", text));
        }
        return text;
    }

    private static LocalDate read(String day, String field) {
        return LocalDate.parse(assertDay(day, field));
    }

    /** The day a number of days after another. */
    public static String addDays(String day, int count) {
        return read(day, "day").plusDays(count).toString();
    }

    /** How many days from one to another, which is negative when it is backwards. */
    public static int daysBetween(String from, String to) {
        return (int) ChronoUnit.DAYS.between(read(from, "from"), read(to, "to"));
    }

    /** Which day of the week a date falls on, by its English name. */
    public static String weekday(String day) {
        return read(day, "day").getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    /** Whether a day is one the shed works in the usual week. */
    public static boolean isWorkingDay(String day) {
        return isWorkingDay(day, Calendar.STANDARD);
    }

    /** Whether a day is one the shed works, given a week and a holiday list. */
    public static boolean isWorkingDay(String day, Calendar calendar) {
        Check.array(calendar.week(), "week", 1);
        if (calendar.holidays().contains(assertDay(day))) {
            return false;
        }
        return calendar.week().contains(weekday(day));
    }

    /** The next working day on or after a date in the usual week. */
    public static String nextWorkingDay(String day) {
        return nextWorkingDay(day, Calendar.STANDARD);
    }

    /** The next working day on or after a date. */
    public static String nextWorkingDay(String day, Calendar calendar) {
        String at = assertDay(day);
        for (int guard = 0; guard < 30; guard += 1) {
            if (isWorkingDay(at, calendar)) {
                return at;
            }
            at = addDays(at, 1);
        }
        throw new InvalidArgumentException("no working day inside a month of that date", Json.obj("day", day));
    }

    /** The working days between two dates in the usual week, counting the first but not the last. */
    public static int workingDaysBetween(String from, String to) {
        return workingDaysBetween(from, to, Calendar.STANDARD);
    }

    /** The working days between two dates, counting the first but not the last. */
    public static int workingDaysBetween(String from, String to, Calendar calendar) {
        int span = daysBetween(from, to);
        if (span < 0) {
            throw new InvalidArgumentException(
                    "the second day comes before the first", Json.obj("from", from, "to", to));
        }
        int count = 0;
        for (int step = 0; step < span; step += 1) {
            if (isWorkingDay(addDays(from, step), calendar)) {
                count += 1;
            }
        }
        return count;
    }

    /** The day a run of working days lands on in the usual week. */
    public static String addWorkingDays(String day, int count) {
        return addWorkingDays(day, count, Calendar.STANDARD);
    }

    /** The day a run of working days lands on. */
    public static String addWorkingDays(String day, int count, Calendar calendar) {
        Check.integer(count, "count", Bounds.atLeast(0));
        String at = nextWorkingDay(day, calendar);
        for (int step = 0; step < count; step += 1) {
            at = nextWorkingDay(addDays(at, 1), calendar);
        }
        return at;
    }

    /** When a job of so many hours finishes, on sixteen hour days in the usual week. */
    public static String finishDay(String start, double hours) {
        return finishDay(start, hours, 16, Calendar.STANDARD);
    }

    /** When a job finishes, from the hours it takes and the hours a working day holds. */
    public static String finishDay(String start, double hours, double hoursPerDay) {
        return finishDay(start, hours, hoursPerDay, Calendar.STANDARD);
    }

    /**
     * When a job finishes, from the hours it takes and the hours a working day holds. It is the
     * calculation behind every delivery date a shed quotes.
     */
    public static String finishDay(String start, double hours, double hoursPerDay, Calendar calendar) {
        Check.number(hours, "hours", Bounds.atLeast(0));
        Check.number(hoursPerDay, "hoursPerDay", Bounds.atLeast(0.1));
        int days = (int) Math.max(0, Math.ceil(hours / hoursPerDay) - 1);
        return addWorkingDays(start, days, calendar);
    }
}
