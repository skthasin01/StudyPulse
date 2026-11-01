package studypulse.service;

import static org.junit.Assert.assertEquals;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Check-in streak counting. */
public class CheckinServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

    private static List<LocalDate> daysAgo(int... offsets) {
        List<LocalDate> dates = new ArrayList<LocalDate>();
        for (int o : offsets) {
            dates.add(TODAY.minusDays(o));
        }
        return dates;
    }

    @Test
    public void noCheckinsMeansNoStreak() {
        assertEquals(0, CheckinService.countStreak(new ArrayList<LocalDate>(), TODAY));
    }

    @Test
    public void sevenDaysInARowIncludingToday() {
        assertEquals(7, CheckinService.countStreak(daysAgo(0, 1, 2, 3, 4, 5, 6), TODAY));
    }

    @Test
    public void gapEndsTheStreak() {
        assertEquals(3, CheckinService.countStreak(daysAgo(0, 1, 2, 4, 5), TODAY));
    }

    @Test
    public void todayNotDoneYetKeepsYesterdaysStreak() {
        assertEquals(2, CheckinService.countStreak(daysAgo(1, 2, 5), TODAY));
    }

    @Test
    public void lastCheckinTwoDaysAgoMeansStreakIsBroken() {
        assertEquals(0, CheckinService.countStreak(daysAgo(2, 3, 4), TODAY));
    }

    @Test
    public void onlyTodayIsOneDay() {
        assertEquals(1, CheckinService.countStreak(Arrays.asList(TODAY), TODAY));
    }
}
