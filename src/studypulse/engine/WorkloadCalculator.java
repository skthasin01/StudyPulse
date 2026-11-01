package studypulse.engine;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import studypulse.model.Difficulty;
import studypulse.model.LoadLevel;
import studypulse.model.PlanBlock;
import studypulse.model.PlanStatus;
import studypulse.model.Task;
import studypulse.model.TaskStatus;

/**
 * Rule-based Study Load Index. Pure logic, no database access.
 * <ul>
 * <li>Difficulty weight: EASY 1.0, MEDIUM 1.5, HARD 2.0</li>
 * <li>Deadline factor by days left: ≤1 = 2.0, 2-3 = 1.5, 4-7 = 1.2, &gt;7 = 1.0</li>
 * <li>Task load (h) = remaining hours × difficulty weight × deadline factor</li>
 * <li>Index (next 7 days) = round(sum of loads of tasks due within 7 days ÷ free hours × 100), max 150</li>
 * <li>Daily load = weighted planned hours that day ÷ free hours that day × 100, max 150</li>
 * </ul>
 */
public class WorkloadCalculator {

    public static final int MAX_INDEX = 150;
    public static final int WINDOW_DAYS = 7;

    /** Index 1 = Monday ... 7 = Sunday. */
    private final int[] freeMinutes = new int[8];
    private final int restDay;

    /**
     * @param freeMinutesByDay day_of_week (1-7) -> free minutes; missing days count as 0
     * @param restDay weekly rest day (1-7) with 0 free minutes, or 0 for none
     */
    public WorkloadCalculator(Map<Integer, Integer> freeMinutesByDay, int restDay) {
        for (int day = 1; day <= 7; day++) {
            Integer minutes = freeMinutesByDay.get(day);
            freeMinutes[day] = minutes == null ? 0 : Math.max(0, minutes);
        }
        this.restDay = restDay;
    }

    public static double difficultyWeight(Difficulty difficulty) {
        if (difficulty == Difficulty.EASY) {
            return 1.0;
        }
        return difficulty == Difficulty.HARD ? 2.0 : 1.5;
    }

    public static double deadlineFactor(long daysLeft) {
        if (daysLeft <= 1) {
            return 2.0;
        }
        if (daysLeft <= 3) {
            return 1.5;
        }
        return daysLeft <= 7 ? 1.2 : 1.0;
    }

    /** remaining hours × difficulty weight × deadline factor. */
    public static double taskLoadHours(int remainingMinutes, Difficulty difficulty, long daysLeft) {
        return remainingMinutes / 60.0 * difficultyWeight(difficulty) * deadlineFactor(daysLeft);
    }

    /** Free minutes of a date (0 on the rest day). */
    public int freeMinutes(LocalDate date) {
        int day = date.getDayOfWeek().getValue();
        return day == restDay ? 0 : freeMinutes[day];
    }

    /** Free hours from today to today + 6. */
    public double freeHoursNext7Days(LocalDate today) {
        int sum = 0;
        for (int i = 0; i < WINDOW_DAYS; i++) {
            sum += freeMinutes(today.plusDays(i));
        }
        return sum / 60.0;
    }

    /** Sum of the loads (h) of open tasks due within 7 days. */
    public double loadHoursNext7Days(List<Task> tasks, Map<Integer, Integer> doneMinutesByTask, LocalDate today) {
        double load = 0;
        for (Task task : tasks) {
            if ((task.getStatus() != TaskStatus.PENDING && task.getStatus() != TaskStatus.IN_PROGRESS)
                    || task.getDeadline() == null) {
                continue;
            }
            long daysLeft = ChronoUnit.DAYS.between(today, task.getDeadline().toLocalDate());
            if (daysLeft > WINDOW_DAYS) {
                continue;
            }
            int remaining = PlanGenerator.remainingMinutes(task, doneMinutesByTask);
            load += taskLoadHours(remaining, task.getDifficulty(), daysLeft);
        }
        return load;
    }

    /** Study Load Index for the next 7 days (0-150). */
    public int studyLoadIndex(List<Task> tasks, Map<Integer, Integer> doneMinutesByTask, LocalDate today) {
        return toIndex(loadHoursNext7Days(tasks, doneMinutesByTask, today), freeHoursNext7Days(today));
    }

    /**
     * Daily load (0-150): planned hours that day (PLANNED and DONE blocks) weighted by the
     * task difficulty, divided by that day's free hours.
     */
    public int dailyLoad(List<PlanBlock> blocks, Map<Integer, Task> tasksById, LocalDate day) {
        double weighted = 0;
        for (PlanBlock block : blocks) {
            if (!day.equals(block.getPlanDate()) || block.getStatus() == PlanStatus.SKIPPED) {
                continue;
            }
            Task task = tasksById.get(block.getTaskId());
            Difficulty difficulty = task == null ? Difficulty.MEDIUM : task.getDifficulty();
            weighted += block.getPlannedMinutes() / 60.0 * difficultyWeight(difficulty);
        }
        return toIndex(weighted, freeMinutes(day) / 60.0);
    }

    /** load ÷ free × 100, rounded and capped at 150 (no free time but some load = 150). */
    static int toIndex(double loadHours, double freeHours) {
        if (loadHours <= 0) {
            return 0;
        }
        if (freeHours <= 0) {
            return MAX_INDEX;
        }
        return (int) Math.min(MAX_INDEX, Math.round(loadHours / freeHours * 100));
    }

    public static LoadLevel level(int index) {
        if (index < 50) {
            return LoadLevel.LIGHT;
        }
        if (index < 80) {
            return LoadLevel.BALANCED;
        }
        return index <= 100 ? LoadLevel.HEAVY : LoadLevel.OVERLOADED;
    }
}
