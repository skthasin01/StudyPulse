package studypulse.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import studypulse.model.Checkin;
import studypulse.model.RiskLevel;
import studypulse.model.StudyLog;

/** Burnout risk rules with fixed sample data. */
public class BurnoutRiskCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    private final BurnoutRiskCalculator calculator = new BurnoutRiskCalculator();

    private static Checkin checkin(int daysAgo, double sleep, int stress, int energy, int mood) {
        Checkin c = new Checkin();
        c.setUserId(1);
        c.setCheckinDate(TODAY.minusDays(daysAgo));
        c.setSleepHours(sleep);
        c.setStressLevel(stress);
        c.setEnergyLevel(energy);
        c.setMoodLevel(mood);
        return c;
    }

    private static StudyLog log(int minutes, int breaks) {
        StudyLog l = new StudyLog();
        l.setUserId(1);
        l.setStartTime(LocalDateTime.of(2026, 11, 1, 14, 0));
        l.setEndTime(l.getStartTime().plusMinutes(minutes));
        l.setDurationMinutes(minutes);
        l.setBreaksTaken(breaks);
        return l;
    }

    private static List<Integer> none() {
        return new ArrayList<Integer>();
    }

    /** Every point of the score must come from a listed reason (when not capped or scaled). */
    private static void assertPointsHaveReasons(RiskAssessment a) {
        int sum = 0;
        for (RiskReason r : a.getReasons()) {
            assertTrue("Reason without points: " + r.getText(), r.getPoints() > 0);
            assertFalse(r.getText().trim().isEmpty());
            sum += r.getPoints();
        }
        assertEquals(sum, a.getRawPoints());
    }

    @Test
    public void goodWeekIsLow() {
        RiskAssessment a = calculator.calculate(true,
                Arrays.asList(checkin(0, 7.5, 2, 4, 4), checkin(1, 8, 2, 4, 5), checkin(2, 7, 3, 3, 4)),
                Arrays.asList(log(90, 1), log(50, 0)), 0, 45, none());
        assertEquals(0, a.getScore());
        assertEquals(RiskLevel.LOW, a.getLevel());
        assertTrue(a.getReasons().isEmpty());
    }

    @Test
    public void badSleepHighStressAndHeavyLoadIsHigh() {
        RiskAssessment a = calculator.calculate(true,
                Arrays.asList(checkin(0, 4, 4, 3, 3), checkin(1, 5, 5, 3, 3), checkin(2, 4.5, 4, 3, 3)),
                new ArrayList<StudyLog>(), 0, 90, none());
        // sleep 4.5 h < 5 -> 25, stress 4.3 -> 20, load 90 -> 15
        assertEquals(60, a.getScore());
        assertEquals(RiskLevel.HIGH, a.getLevel());
        assertEquals(3, a.getReasons().size());
        assertPointsHaveReasons(a);
    }

    @Test
    public void sleepBelowSixGivesFifteenWithReadableReason() {
        RiskAssessment a = calculator.calculate(true,
                Arrays.asList(checkin(0, 5.5, 2, 4, 4), checkin(1, 6, 2, 4, 4), checkin(2, 5.6, 2, 4, 4)),
                new ArrayList<StudyLog>(), 0, 20, none());
        assertEquals(15, a.getScore());
        assertEquals("Average sleep in last 3 days is 5.7 h (below 6 h)  +15", a.getReasons().get(0).toString());
    }

    @Test
    public void lowerSleepRaisesTheScore() {
        List<StudyLog> logs = new ArrayList<StudyLog>();
        int fine = calculator.calculate(true, Arrays.asList(checkin(0, 7, 3, 3, 3)), logs, 0, 50, none()).getScore();
        int short6 = calculator.calculate(true, Arrays.asList(checkin(0, 5.5, 3, 3, 3)), logs, 0, 50, none()).getScore();
        int short5 = calculator.calculate(true, Arrays.asList(checkin(0, 4, 3, 3, 3)), logs, 0, 50, none()).getScore();
        assertTrue(fine < short6);
        assertTrue(short6 < short5);
    }

    @Test
    public void allRulesTogetherAreCappedAt100() {
        RiskAssessment a = calculator.calculate(true,
                Arrays.asList(checkin(0, 4, 5, 1, 1), checkin(1, 4, 5, 2, 2), checkin(2, 4, 4, 1, 1)),
                Arrays.asList(log(150, 0)), 3, 95, Arrays.asList(70, 80, 88));
        // 25 + 20 + 15 + 10 + 10 + 10 + 15 + 5 = 110 -> 100
        assertEquals(110, a.getRawPoints());
        assertEquals(100, a.getScore());
        assertEquals(RiskLevel.SEVERE, a.getLevel());
        assertEquals(8, a.getReasons().size());
        assertPointsHaveReasons(a);
        assertTrue(a.getTotalLine().contains("capped"));
    }

    @Test
    public void longSessionNeedsZeroBreaksAndOver120Minutes() {
        List<Checkin> none = new ArrayList<Checkin>();
        assertEquals(0, calculator.calculate(true, none, Arrays.asList(log(120, 0)), 0, 0, none()).getRawPoints());
        assertEquals(0, calculator.calculate(true, none, Arrays.asList(log(180, 2)), 0, 0, none()).getRawPoints());
        assertEquals(10, calculator.calculate(true, none, Arrays.asList(log(121, 0)), 0, 0, none()).getRawPoints());
    }

    @Test
    public void risingLoadAddsFiveOnlyWhenLoadIsHigh() {
        List<Checkin> none = new ArrayList<Checkin>();
        List<StudyLog> noLogs = new ArrayList<StudyLog>();
        assertEquals(20, calculator.calculate(true, none, noLogs, 0, 90, Arrays.asList(70, 75, 82)).getScore());
        assertEquals(15, calculator.calculate(true, none, noLogs, 0, 90, Arrays.asList(70, 95, 85)).getScore());
        assertEquals(0, calculator.calculate(true, none, noLogs, 0, 60, Arrays.asList(30, 40, 50)).getScore());
    }

    @Test
    public void wellbeingOffUsesOnlyWorkloadRulesScaledTo100() {
        // check-ins would add 25 + 20, but tracking is off
        RiskAssessment a = calculator.calculate(false,
                Arrays.asList(checkin(0, 4, 5, 3, 3)), Arrays.asList(log(150, 0)), 2, 85, none());
        // 10 + 10 + 15 = 35 -> 35 × 100 / 40 = 87.5 -> 88
        assertEquals(35, a.getRawPoints());
        assertEquals(88, a.getScore());
        assertEquals(RiskLevel.SEVERE, a.getLevel());
        assertPointsHaveReasons(a);
    }

    @Test
    public void levelBoundaries() {
        assertEquals(RiskLevel.LOW, BurnoutRiskCalculator.level(29));
        assertEquals(RiskLevel.MODERATE, BurnoutRiskCalculator.level(30));
        assertEquals(RiskLevel.MODERATE, BurnoutRiskCalculator.level(54));
        assertEquals(RiskLevel.HIGH, BurnoutRiskCalculator.level(55));
        assertEquals(RiskLevel.HIGH, BurnoutRiskCalculator.level(74));
        assertEquals(RiskLevel.SEVERE, BurnoutRiskCalculator.level(75));
    }

    @Test
    public void recommendationsFollowTheLevelAndAlwaysEndWithDisclaimer() {
        RecommendationEngine engine = new RecommendationEngine();
        RiskAssessment low = calculator.calculate(true, new ArrayList<Checkin>(), new ArrayList<StudyLog>(), 0, 10, none());
        List<String> lowRecs = engine.recommend(low, null, "Friday");
        assertEquals("Keep your current balance.", lowRecs.get(0));
        assertEquals(RecommendationEngine.DISCLAIMER, lowRecs.get(lowRecs.size() - 1));

        RiskAssessment severe = calculator.calculate(true,
                Arrays.asList(checkin(0, 4, 5, 1, 1)), Arrays.asList(log(150, 0)), 3, 95, none());
        List<String> recs = engine.recommend(severe, null, "Friday");
        String all = recs.toString();
        assertTrue(all.contains("25/5 Pomodoro"));
        assertTrue(all.contains("rest day (Friday)"));
        assertTrue(all.contains("counselor"));
        assertEquals(RecommendationEngine.DISCLAIMER, recs.get(recs.size() - 1));
    }
}
