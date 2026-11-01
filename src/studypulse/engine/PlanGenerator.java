package studypulse.engine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import studypulse.model.Difficulty;
import studypulse.model.PlanBlock;
import studypulse.model.Task;
import studypulse.model.TaskStatus;
import studypulse.model.TaskType;

/**
 * Rule-based study plan generator. Pure logic, no database access.
 * <ul>
 * <li>Only PENDING / IN_PROGRESS tasks; remaining = estimated - minutes already done.</li>
 * <li>Order: deadline (earliest first), priority (High first), difficulty (Hard first).</li>
 * <li>Window: today .. day before the deadline (EXAM: up to and including the deadline day).</li>
 * <li>30-minute chunks (the last one may be shorter), at most 120 minutes of one task per day.</li>
 * <li>Day capacity = free minutes of that weekday (rest day = 0) minus minutes already used.</li>
 * <li>Hard tasks are packed into the start of their window; others are spread round-robin.</li>
 * <li>What does not fit is reported in {@link PlanResult#getOverloaded()}.</li>
 * </ul>
 */
public class PlanGenerator {

    public static final int CHUNK_MINUTES = 30;
    public static final int MAX_TASK_MINUTES_PER_DAY = 120;

    /** Index 1 = Monday ... 7 = Sunday. */
    private final int[] freeMinutes = new int[8];
    private final int restDay;

    /**
     * @param freeMinutesByDay day_of_week (1-7) -> free minutes; missing days count as 0
     * @param restDay day_of_week with no study at all (1-7, or 0 for none)
     */
    public PlanGenerator(Map<Integer, Integer> freeMinutesByDay, int restDay) {
        for (int day = 1; day <= 7; day++) {
            Integer minutes = freeMinutesByDay.get(day);
            freeMinutes[day] = minutes == null ? 0 : Math.max(0, minutes);
        }
        this.restDay = restDay;
    }

    /** Free minutes of a date before anything is planned (0 on the rest day). */
    public int capacity(LocalDate date) {
        int day = date.getDayOfWeek().getValue();
        return day == restDay ? 0 : freeMinutes[day];
    }

    /**
     * @param tasks the user's tasks (any status; only PENDING / IN_PROGRESS are planned)
     * @param doneMinutesByTask task id -> minutes of DONE plan blocks
     * @param usedMinutesByDate date -> minutes already used (e.g. DONE blocks today)
     * @param today first day that may be planned
     */
    public PlanResult generate(List<Task> tasks, Map<Integer, Integer> doneMinutesByTask,
            Map<LocalDate, Integer> usedMinutesByDate, LocalDate today) {
        Map<LocalDate, Integer> used = new HashMap<LocalDate, Integer>();
        if (usedMinutesByDate != null) {
            used.putAll(usedMinutesByDate);
        }
        PlanResult result = new PlanResult();
        int plannedTasks = 0;

        for (Task task : sortedOpenTasks(tasks)) {
            int remaining = remainingMinutes(task, doneMinutesByTask);
            if (remaining <= 0) {
                continue;
            }
            List<LocalDate> window = window(task, today);
            Map<LocalDate, Integer> perDay = new LinkedHashMap<LocalDate, Integer>();
            if (task.getDifficulty() == Difficulty.HARD) {
                // pack into the earliest days
                for (LocalDate day : window) {
                    while (remaining > 0 && canAdd(day, perDay, used, remaining)) {
                        remaining -= addChunk(day, perDay, used, remaining);
                    }
                }
            } else {
                // one chunk per day per round, spread over the window
                boolean added = true;
                while (remaining > 0 && added) {
                    added = false;
                    for (LocalDate day : window) {
                        if (remaining > 0 && canAdd(day, perDay, used, remaining)) {
                            remaining -= addChunk(day, perDay, used, remaining);
                            added = true;
                        }
                    }
                }
            }
            List<LocalDate> days = new ArrayList<LocalDate>(perDay.keySet());
            Collections.sort(days);
            for (LocalDate day : days) {
                PlanBlock block = new PlanBlock(task.getUserId(), task.getId(), day, perDay.get(day));
                result.getBlocks().add(block);
            }
            if (!perDay.isEmpty()) {
                plannedTasks++;
            }
            if (remaining > 0) {
                result.getOverloaded().add(new OverloadedTask(task, remaining));
            }
        }
        Collections.sort(result.getBlocks(), new Comparator<PlanBlock>() {
            @Override
            public int compare(PlanBlock a, PlanBlock b) {
                return a.getPlanDate().compareTo(b.getPlanDate());
            }
        });
        result.setPlannedTaskCount(plannedTasks);
        return result;
    }

    /** estimated - done minutes (never below 0). */
    public static int remainingMinutes(Task task, Map<Integer, Integer> doneMinutesByTask) {
        Integer done = doneMinutesByTask == null ? null : doneMinutesByTask.get(task.getId());
        return Math.max(0, task.getEstimatedMinutes() - (done == null ? 0 : done));
    }

    /** Days the task may be studied: today .. deadline - 1 (EXAM: .. deadline). */
    public static List<LocalDate> window(Task task, LocalDate today) {
        LocalDate deadlineDay = task.getDeadline().toLocalDate();
        LocalDate last = task.getTaskType() == TaskType.EXAM ? deadlineDay : deadlineDay.minusDays(1);
        List<LocalDate> days = new ArrayList<LocalDate>();
        for (LocalDate d = today; !d.isAfter(last); d = d.plusDays(1)) {
            days.add(d);
        }
        return days;
    }

    private List<Task> sortedOpenTasks(List<Task> tasks) {
        List<Task> open = new ArrayList<Task>();
        for (Task t : tasks) {
            if ((t.getStatus() == TaskStatus.PENDING || t.getStatus() == TaskStatus.IN_PROGRESS)
                    && t.getDeadline() != null) {
                open.add(t);
            }
        }
        Collections.sort(open, new Comparator<Task>() {
            @Override
            public int compare(Task a, Task b) {
                int c = a.getDeadline().compareTo(b.getDeadline());
                if (c != 0) {
                    return c;
                }
                c = b.getPriority().ordinal() - a.getPriority().ordinal();      // HIGH first
                if (c != 0) {
                    return c;
                }
                return b.getDifficulty().ordinal() - a.getDifficulty().ordinal(); // HARD first
            }
        });
        return open;
    }

    private boolean canAdd(LocalDate day, Map<LocalDate, Integer> perDay, Map<LocalDate, Integer> used,
            int remaining) {
        int chunk = Math.min(CHUNK_MINUTES, remaining);
        int taskToday = perDay.containsKey(day) ? perDay.get(day) : 0;
        int usedToday = used.containsKey(day) ? used.get(day) : 0;
        return taskToday + chunk <= MAX_TASK_MINUTES_PER_DAY && usedToday + chunk <= capacity(day);
    }

    private int addChunk(LocalDate day, Map<LocalDate, Integer> perDay, Map<LocalDate, Integer> used,
            int remaining) {
        int chunk = Math.min(CHUNK_MINUTES, remaining);
        perDay.put(day, (perDay.containsKey(day) ? perDay.get(day) : 0) + chunk);
        used.put(day, (used.containsKey(day) ? used.get(day) : 0) + chunk);
        return chunk;
    }
}
