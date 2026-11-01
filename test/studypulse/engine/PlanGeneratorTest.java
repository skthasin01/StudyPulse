package studypulse.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import studypulse.model.Difficulty;
import studypulse.model.PlanBlock;
import studypulse.model.Priority;
import studypulse.model.Task;
import studypulse.model.TaskStatus;
import studypulse.model.TaskType;

/** Rules of the rule-based study plan generator. */
public class PlanGeneratorTest {

    private static final int FRIDAY = 5;
    /** A Monday, so the week layout of every test is known. */
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

    private Map<Integer, Integer> free;
    private int nextId;

    @Before
    public void setUp() {
        free = new HashMap<Integer, Integer>();
        for (int day = 1; day <= 7; day++) {
            free.put(day, 180);
        }
        nextId = 1;
    }

    private Task task(String title, TaskType type, Difficulty difficulty, int minutes, LocalDate deadlineDay) {
        Task t = new Task();
        t.setId(nextId++);
        t.setUserId(1);
        t.setTitle(title);
        t.setTaskType(type);
        t.setDifficulty(difficulty);
        t.setPriority(Priority.MEDIUM);
        t.setStatus(TaskStatus.PENDING);
        t.setEstimatedMinutes(minutes);
        t.setDeadline(deadlineDay.atTime(LocalTime.of(23, 59)));
        return t;
    }

    private PlanResult generate(List<Task> tasks) {
        return new PlanGenerator(free, FRIDAY).generate(tasks, new HashMap<Integer, Integer>(),
                new HashMap<LocalDate, Integer>(), TODAY);
    }

    private static Map<LocalDate, Integer> minutesPerDay(PlanResult result) {
        Map<LocalDate, Integer> sums = new HashMap<LocalDate, Integer>();
        for (PlanBlock b : result.getBlocks()) {
            Integer old = sums.get(b.getPlanDate());
            sums.put(b.getPlanDate(), (old == null ? 0 : old) + b.getPlannedMinutes());
        }
        return sums;
    }

    @Test
    public void noDayGoesOverCapacity() {
        free.put(2, 60);    // a short Tuesday
        List<Task> tasks = Arrays.asList(
                task("Hard essay", TaskType.ASSIGNMENT, Difficulty.HARD, 600, TODAY.plusDays(6)),
                task("Lab report", TaskType.ASSIGNMENT, Difficulty.MEDIUM, 420, TODAY.plusDays(5)),
                task("Quiz prep", TaskType.PREPARATION, Difficulty.EASY, 300, TODAY.plusDays(9)),
                task("Midterm", TaskType.EXAM, Difficulty.HARD, 480, TODAY.plusDays(3)));
        PlanResult result = generate(tasks);
        PlanGenerator generator = new PlanGenerator(free, FRIDAY);

        assertFalse(result.getBlocks().isEmpty());
        for (Map.Entry<LocalDate, Integer> e : minutesPerDay(result).entrySet()) {
            assertTrue("Over capacity on " + e.getKey() + ": " + e.getValue(),
                    e.getValue() <= generator.capacity(e.getKey()));
        }
    }

    @Test
    public void noTaskGetsMoreThan120MinutesOnOneDay() {
        PlanResult result = generate(Arrays.asList(
                task("Big project", TaskType.PROJECT, Difficulty.HARD, 900, TODAY.plusDays(14))));
        for (PlanBlock b : result.getBlocks()) {
            assertTrue(b.getPlannedMinutes() <= PlanGenerator.MAX_TASK_MINUTES_PER_DAY);
            assertFalse("No blocks before today", b.getPlanDate().isBefore(TODAY));
        }
    }

    @Test
    public void restDayStaysEmpty() {
        PlanResult result = generate(Arrays.asList(
                task("Project", TaskType.PROJECT, Difficulty.MEDIUM, 1500, TODAY.plusDays(20)),
                task("Exam", TaskType.EXAM, Difficulty.HARD, 600, TODAY.plusDays(10))));
        assertFalse(result.getBlocks().isEmpty());
        for (PlanBlock b : result.getBlocks()) {
            assertTrue("Block on the rest day " + b.getPlanDate(),
                    b.getPlanDate().getDayOfWeek() != DayOfWeek.FRIDAY);
        }
    }

    @Test
    public void impossibleDeadlineIsOverloaded() {
        // due tomorrow -> only today can be used: at most 120 minutes of 600
        Task impossible = task("SE Project", TaskType.PROJECT, Difficulty.HARD, 600, TODAY.plusDays(1));
        PlanResult result = generate(Arrays.asList(impossible));

        assertEquals(1, result.getOverloaded().size());
        OverloadedTask over = result.getOverloaded().get(0);
        assertEquals(impossible.getId(), over.getTask().getId());
        assertEquals(600 - 120, over.getMissingMinutes());
        assertEquals(120, (int) minutesPerDay(result).get(TODAY));
    }

    @Test
    public void taskThatFitsIsNotOverloaded() {
        PlanResult result = generate(Arrays.asList(
                task("Small task", TaskType.ASSIGNMENT, Difficulty.EASY, 90, TODAY.plusDays(4))));
        assertTrue(result.getOverloaded().isEmpty());
        int total = 0;
        for (PlanBlock b : result.getBlocks()) {
            total += b.getPlannedMinutes();
        }
        assertEquals(90, total);
    }

    @Test
    public void examMayUseTheDeadlineDayButAssignmentMayNot() {
        LocalDate deadline = TODAY.plusDays(2);
        assertEquals(deadline, last(PlanGenerator.window(
                task("Exam", TaskType.EXAM, Difficulty.EASY, 60, deadline), TODAY)));
        assertEquals(deadline.minusDays(1), last(PlanGenerator.window(
                task("Essay", TaskType.ASSIGNMENT, Difficulty.EASY, 60, deadline), TODAY)));
    }

    @Test
    public void doneMinutesAreNotPlannedAgain() {
        Task t = task("Report", TaskType.ASSIGNMENT, Difficulty.MEDIUM, 240, TODAY.plusDays(6));
        Map<Integer, Integer> done = new HashMap<Integer, Integer>();
        done.put(t.getId(), 180);
        PlanResult result = new PlanGenerator(free, FRIDAY).generate(Arrays.asList(t), done,
                new HashMap<LocalDate, Integer>(), TODAY);
        int total = 0;
        for (PlanBlock b : result.getBlocks()) {
            total += b.getPlannedMinutes();
        }
        assertEquals(60, total);
    }

    @Test
    public void onlyPendingAndInProgressTasksArePlanned() {
        Task done = task("Done", TaskType.ASSIGNMENT, Difficulty.EASY, 60, TODAY.plusDays(5));
        done.setStatus(TaskStatus.DONE);
        Task missed = task("Missed", TaskType.ASSIGNMENT, Difficulty.EASY, 60, TODAY.plusDays(5));
        missed.setStatus(TaskStatus.MISSED);
        Task inProgress = task("Started", TaskType.ASSIGNMENT, Difficulty.EASY, 60, TODAY.plusDays(5));
        inProgress.setStatus(TaskStatus.IN_PROGRESS);
        PlanResult result = generate(new ArrayList<Task>(Arrays.asList(done, missed, inProgress)));
        for (PlanBlock b : result.getBlocks()) {
            assertEquals(inProgress.getId(), b.getTaskId());
        }
        assertEquals(1, result.getPlannedTaskCount());
    }

    @Test
    public void hardTasksStartEarlierThanSpreadTasks() {
        Task hard = task("Hard", TaskType.PROJECT, Difficulty.HARD, 240, TODAY.plusDays(8));
        PlanResult result = generate(Arrays.asList(hard));
        Map<LocalDate, Integer> perDay = minutesPerDay(result);
        // 240 minutes packed into the first two days (120 + 120)
        assertEquals(120, (int) perDay.get(TODAY));
        assertEquals(120, (int) perDay.get(TODAY.plusDays(1)));
        assertEquals(2, perDay.size());
    }

    private static LocalDate last(List<LocalDate> days) {
        assertNotNull(days);
        assertFalse(days.isEmpty());
        return days.get(days.size() - 1);
    }
}
