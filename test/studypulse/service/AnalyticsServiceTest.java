package studypulse.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/** Insight sentences of the Analytics page. */
public class AnalyticsServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);

    @Test
    public void hoursInsightShowsDifference() {
        assertEquals("You studied 14.5 h in the last 7 days (+3.0 h vs the 7 days before).",
                AnalyticsService.hoursInsight(7, 14.5, 11.5));
        assertEquals("You studied 40.0 h in the last 4 weeks (−5.5 h vs the 4 weeks before).",
                AnalyticsService.hoursInsight(28, 40, 45.5));
        assertEquals("No study sessions recorded in the last 7 days yet.", AnalyticsService.hoursInsight(7, 0, 0));
    }

    @Test
    public void riskIsHighestAfterShortSleep() {
        Map<LocalDate, Double> sleep = new HashMap<LocalDate, Double>();
        Map<LocalDate, Integer> risk = new HashMap<LocalDate, Integer>();
        sleep.put(DAY, 5.0);
        risk.put(DAY, 60);
        sleep.put(DAY.plusDays(1), 4.5);
        risk.put(DAY.plusDays(1), 70);
        sleep.put(DAY.plusDays(2), 8.0);
        risk.put(DAY.plusDays(2), 30);
        sleep.put(DAY.plusDays(3), 7.0);   // no snapshot that day: ignored
        assertEquals("Your risk score is highest on days after less than 6 h of sleep (average 65 vs 30).",
                AnalyticsService.sleepRiskInsight(sleep, risk));
    }

    @Test
    public void noSleepInsightWithoutBothGroupsOrWhenRiskIsNotHigher() {
        Map<LocalDate, Double> sleep = new HashMap<LocalDate, Double>();
        Map<LocalDate, Integer> risk = new HashMap<LocalDate, Integer>();
        sleep.put(DAY, 8.0);
        risk.put(DAY, 40);
        assertNull(AnalyticsService.sleepRiskInsight(sleep, risk));   // no short nights
        sleep.put(DAY.plusDays(1), 5.0);
        risk.put(DAY.plusDays(1), 20);
        assertNull(AnalyticsService.sleepRiskInsight(sleep, risk));   // short night but lower risk
    }
}
