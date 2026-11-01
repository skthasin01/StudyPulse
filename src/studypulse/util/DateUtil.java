package studypulse.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;
import java.util.Locale;

/** Converts dates to and from the text formats stored in the database. */
public final class DateUtil {

    public static final String DATE_PATTERN = "yyyy-MM-dd";
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern(DATE_PATTERN);
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
    private static final DateTimeFormatter LONG_DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.ENGLISH);
    private static final DateTimeFormatter COMPACT_DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM HH:mm", Locale.ENGLISH);

    private DateUtil() {
    }

    public static String formatDate(LocalDate date) {
        return date == null ? null : date.format(DATE_FORMAT);
    }

    public static LocalDate parseDate(String text) {
        return text == null || text.trim().isEmpty() ? null : LocalDate.parse(text.trim(), DATE_FORMAT);
    }

    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(DATE_TIME_FORMAT);
    }

    public static LocalDateTime parseDateTime(String text) {
        return text == null || text.trim().isEmpty() ? null : LocalDateTime.parse(text.trim(), DATE_TIME_FORMAT);
    }

    /** For display, e.g. "Monday, 02 Nov 2026". */
    public static String formatLong(LocalDate date) {
        return date == null ? "" : date.format(LONG_DATE_FORMAT);
    }

    public static LocalDate today() {
        return LocalDate.now();
    }

    /** Current date-time without milliseconds, ready to store. */
    public static LocalDateTime now() {
        return LocalDateTime.now().withNano(0);
    }

    /** For JDateChooser.setDate(...). */
    public static Date toDate(LocalDate date) {
        return date == null ? null : Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /** For JDateChooser.getDate(). */
    public static LocalDate fromDate(Date date) {
        return date == null ? null : date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /** For a time JSpinner (SpinnerDateModel): today's date at the given time. */
    public static Date toDate(LocalTime time) {
        return time == null ? null : Date.from(LocalDate.now().atTime(time).atZone(ZoneId.systemDefault()).toInstant());
    }

    /** Hours and minutes of a time JSpinner value. */
    public static LocalTime toLocalTime(Date date) {
        return date == null ? null
                : date.toInstant().atZone(ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0);
    }

    /** For tables, e.g. "2026-11-05 23:59". */
    public static String formatShort(LocalDateTime dateTime) {
        return dateTime == null ? "" : dateTime.format(SHORT_DATE_TIME_FORMAT);
    }

    /** Monday of the week that contains 'date'. */
    public static LocalDate mondayOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** "02 Nov – 08 Nov 2026" (the year is shown twice only when the week spans New Year). */
    public static String formatWeekRange(LocalDate monday) {
        LocalDate sunday = monday.plusDays(6);
        DateTimeFormatter dayMonth = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
        DateTimeFormatter full = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
        String start = monday.getYear() == sunday.getYear() ? monday.format(dayMonth) : monday.format(full);
        return start + " – " + sunday.format(full);
    }

    /** For tables, e.g. "Sun 01 Nov". */
    public static String formatDay(LocalDate date) {
        return date == null ? "" : date.format(DAY_FORMAT);
    }

    /** "Monday" ... "Sunday" for day_of_week 1-7. */
    public static String dayName(int dayOfWeek) {
        return DayOfWeek.of(dayOfWeek).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    /** "Today 14:05", "Yesterday 09:00" or "Sat 31 Oct 10:15". */
    public static String formatRelative(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        String time = dateTime.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH));
        LocalDate day = dateTime.toLocalDate();
        if (day.equals(today())) {
            return "Today " + time;
        }
        if (day.equals(today().minusDays(1))) {
            return "Yesterday " + time;
        }
        return formatDay(day) + " " + time;
    }

    /** For narrow table columns, e.g. "05 Nov 23:59". */
    public static String formatCompact(LocalDateTime dateTime) {
        return dateTime == null ? "" : dateTime.format(COMPACT_DATE_TIME_FORMAT);
    }

    /** 150 -> "2h 30m", 45 -> "45m", 120 -> "2h". */
    public static String formatMinutes(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        if (h == 0) {
            return m + "m";
        }
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }
}
