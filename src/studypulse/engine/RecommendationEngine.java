package studypulse.engine;

import java.util.ArrayList;
import java.util.List;
import studypulse.model.RiskLevel;
import studypulse.model.Task;
import studypulse.model.TaskStatus;

/** Short, rule-based advice for a risk level and its reasons. Pure logic. */
public class RecommendationEngine {

    public static final String DISCLAIMER =
            "StudyPulse is a study planning and early-warning tool, not a medical diagnosis.";

    /**
     * @param taskToMove task suggested for next week (see {@link #pickTaskToMove}), may be null
     * @param restDayName e.g. "Friday"
     * @return recommendations; the last item is always {@link #DISCLAIMER}
     */
    public List<String> recommend(RiskAssessment risk, Task taskToMove, String restDayName) {
        List<String> list = new ArrayList<String>();
        RiskLevel level = risk.getLevel();
        if (level == RiskLevel.LOW) {
            list.add("Keep your current balance.");
        } else {
            // MODERATE and above
            list.add("Work in 25/5 Pomodoro sessions: 25 minutes of focus, then a 5-minute break.");
            if (risk.hasReason(RiskReason.Kind.SLEEP)) {
                list.add("Your sleep is low: go to bed on time and aim for 7-8 hours tonight.");
            } else {
                list.add("Keep a regular sleep time and aim for 7-8 hours of sleep.");
            }
            if (risk.hasReason(RiskReason.Kind.LONG_SESSION)) {
                list.add("Take a short break at least every 50 minutes instead of studying for hours.");
            }
            if (risk.hasReason(RiskReason.Kind.MISSED_TASKS)) {
                list.add("Look at your missed tasks and give them new, realistic deadlines.");
            }
        }
        if (level == RiskLevel.HIGH || level == RiskLevel.SEVERE) {
            if (taskToMove != null) {
                list.add("Move \"" + taskToMove.getTitle()
                        + "\" (lowest priority, latest deadline) to next week.");
            } else {
                list.add("Move your lowest-priority task to next week.");
            }
            list.add("Keep your rest day (" + restDayName + ") free of study.");
            list.add("Re-generate your study plan after these changes.");
        }
        if (level == RiskLevel.SEVERE) {
            list.add("Consider talking to a teacher or counselor about your workload.");
        }
        list.add(DISCLAIMER);
        return list;
    }

    /** Open task with the lowest priority; among those the one with the latest deadline. */
    public static Task pickTaskToMove(List<Task> tasks) {
        Task best = null;
        for (Task t : tasks) {
            if ((t.getStatus() != TaskStatus.PENDING && t.getStatus() != TaskStatus.IN_PROGRESS)
                    || t.getDeadline() == null) {
                continue;
            }
            if (best == null
                    || t.getPriority().ordinal() < best.getPriority().ordinal()
                    || (t.getPriority() == best.getPriority() && t.getDeadline().isAfter(best.getDeadline()))) {
                best = t;
            }
        }
        return best;
    }
}
