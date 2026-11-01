package studypulse.engine;

import java.util.List;
import java.util.Locale;
import studypulse.model.Checkin;
import studypulse.model.RiskLevel;
import studypulse.model.StudyLog;

/**
 * Rule-based burnout risk score 0-100. Pure logic, no database access.
 * Every point comes from a {@link RiskReason}.
 * <pre>
 * +25 average sleep of the last 3 check-ins below 5 h (or +15 below 6 h)
 * +20 average stress 4 or more
 * +15 average energy 2 or less
 * +10 average mood 2 or less
 * +10 a study session over 120 min with 0 breaks in the last 3 days
 * +10 2 or more MISSED tasks in the last 7 days
 * +15 Study Load Index 80 or more (+5 more if it rose 3 days in a row)
 * </pre>
 * Capped at 100. With well-being tracking off only the last three rules are used and the
 * points are scaled: score × 100 ÷ 40.
 * Level: 0-29 LOW, 30-54 MODERATE, 55-74 HIGH, 75-100 SEVERE.
 */
public class BurnoutRiskCalculator {

    public static final int WORKLOAD_ONLY_MAX = 40;
    public static final int LONG_SESSION_MINUTES = 120;
    public static final int HIGH_LOAD_INDEX = 80;

    /**
     * @param wellbeingEnabled users.wellbeing_enabled
     * @param lastCheckins the last (up to) 3 check-ins, any order
     * @param logsLast3Days study sessions of the last 3 days
     * @param missedLast7Days tasks that became MISSED in the last 7 days
     * @param loadIndex today's Study Load Index
     * @param previousLoads load index of the 3 days before today, oldest first (may be shorter)
     */
    public RiskAssessment calculate(boolean wellbeingEnabled, List<Checkin> lastCheckins,
            List<StudyLog> logsLast3Days, int missedLast7Days, int loadIndex, List<Integer> previousLoads) {
        RiskAssessment result = new RiskAssessment(wellbeingEnabled);
        List<RiskReason> reasons = result.getReasons();

        if (wellbeingEnabled && lastCheckins != null && !lastCheckins.isEmpty()) {
            int n = Math.min(3, lastCheckins.size());
            double sleep = 0;
            double stress = 0;
            double energy = 0;
            double mood = 0;
            for (int i = 0; i < n; i++) {
                Checkin c = lastCheckins.get(i);
                sleep += c.getSleepHours();
                stress += c.getStressLevel();
                energy += c.getEnergyLevel();
                mood += c.getMoodLevel();
            }
            sleep /= n;
            stress /= n;
            energy /= n;
            mood /= n;
            String period = n == 3 ? "last 3 days" : "last " + n + (n == 1 ? " check-in" : " check-ins");
            if (sleep < 5) {
                reasons.add(new RiskReason(RiskReason.Kind.SLEEP,
                        "Average sleep in " + period + " is " + one(sleep) + " h (below 5 h)", 25));
            } else if (sleep < 6) {
                reasons.add(new RiskReason(RiskReason.Kind.SLEEP,
                        "Average sleep in " + period + " is " + one(sleep) + " h (below 6 h)", 15));
            }
            if (stress >= 4) {
                reasons.add(new RiskReason(RiskReason.Kind.STRESS,
                        "Average stress in " + period + " is " + one(stress) + " (4 or more)", 20));
            }
            if (energy <= 2) {
                reasons.add(new RiskReason(RiskReason.Kind.ENERGY,
                        "Average energy in " + period + " is " + one(energy) + " (2 or less)", 15));
            }
            if (mood <= 2) {
                reasons.add(new RiskReason(RiskReason.Kind.MOOD,
                        "Average mood in " + period + " is " + one(mood) + " (2 or less)", 10));
            }
        }

        int longest = 0;
        if (logsLast3Days != null) {
            for (StudyLog log : logsLast3Days) {
                if (log.getDurationMinutes() > LONG_SESSION_MINUTES && log.getBreaksTaken() == 0) {
                    longest = Math.max(longest, log.getDurationMinutes());
                }
            }
        }
        if (longest > 0) {
            reasons.add(new RiskReason(RiskReason.Kind.LONG_SESSION,
                    "Study session of " + longest + " min without a break in the last 3 days", 10));
        }
        if (missedLast7Days >= 2) {
            reasons.add(new RiskReason(RiskReason.Kind.MISSED_TASKS,
                    missedLast7Days + " tasks missed in the last 7 days", 10));
        }
        if (loadIndex >= HIGH_LOAD_INDEX) {
            reasons.add(new RiskReason(RiskReason.Kind.HIGH_LOAD,
                    "Study Load Index is " + loadIndex + " (80 or more)", 15));
            if (roseThreeDays(previousLoads, loadIndex)) {
                reasons.add(new RiskReason(RiskReason.Kind.RISING_LOAD,
                        "Study Load Index rose 3 days in a row", 5));
            }
        }

        int raw = result.getRawPoints();
        int score = wellbeingEnabled ? raw : (int) Math.round(raw * 100.0 / WORKLOAD_ONLY_MAX);
        score = Math.min(100, score);
        result.setScore(score);
        result.setLevel(level(score));
        return result;
    }

    public static RiskLevel level(int score) {
        if (score < 30) {
            return RiskLevel.LOW;
        }
        if (score < 55) {
            return RiskLevel.MODERATE;
        }
        return score < 75 ? RiskLevel.HIGH : RiskLevel.SEVERE;
    }

    /** previous = [day-3, day-2, day-1]; true if each day is higher than the one before, up to today. */
    static boolean roseThreeDays(List<Integer> previous, int today) {
        if (previous == null || previous.size() < 3) {
            return false;
        }
        int a = previous.get(previous.size() - 3);
        int b = previous.get(previous.size() - 2);
        int c = previous.get(previous.size() - 1);
        return a < b && b < c && c < today;
    }

    private static String one(double value) {
        return String.format(Locale.ENGLISH, "%.1f", value);
    }
}
