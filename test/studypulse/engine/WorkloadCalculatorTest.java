package studypulse.engine;

import static org.junit.Assert.assertEquals;

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
import studypulse.model.LoadLevel;
import studypulse.model.PlanBlock;
import studypulse.model.PlanStatus;
import studypulse.model.Priority;
import studypulse.model.Task;
import studypulse.model.TaskStatus;
import studypulse.model.TaskType;

/** Study Load Index rules with fixed sample data. */
public class WorkloadCalculatorTest {

    private static final double DELTA = 0.0001;
    /** A Monday: with 3 h free per day and Friday as rest day the next 7 days have 18 free hours. */
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

    private WorkloadCalculator calculator;
    private int nextId;

    @Before
    public void setUp() {
        Map<Integer, Integer> free = new HashMap<Integer, Integer>();
        for (int day = 1; day <= 7; day++) {
            free.put(day, 180);
        }
        calculator = new WorkloadCalculator(free, 5);
        nextId = 1;
    }

    private Task task(Difficulty difficulty, int minutes, int daysLeft) {
        Task t = new Task();
        t.setId(nextId++);
        t.setUserId(1);
        t.setTitle("Task " + t.getId());
        t.setTaskType(TaskType.ASSIGNMENT);
        t.setDifficulty(difficulty);
        t.setPriority(Priority.MEDIUM);
        t.setStatus(TaskStatus.PENDING);
        t.setEstimatedMinutes(minutes);
        t.setDeadline(TODAY.plusDays(daysLeft).atTime(LocalTime.of(23, 59)));
        return t;
    }

    @Test
    public void difficultyWeights() {
        assertEquals(1.0, WorkloadCalculator.difficultyWeight(Difficulty.EASY), DELTA);
        assertEquals(1.5, WorkloadCalculator.difficultyWeight(Difficulty.MEDIUM), DELTA);
        assertEquals(2.0, WorkloadCalculator.difficultyWeight(Difficulty.HARD), DELTA);
    }

    @Test
    public void deadlineFactors() {
        assertEquals(2.0, WorkloadCalculator.deadlineFactor(0), DELTA);
        assertEquals(2.0, WorkloadCalculator.deadlineFactor(1), DELTA);
        assertEquals(1.5, WorkloadCalculator.deadlineFactor(2), DELTA);
        assertEquals(1.5, WorkloadCalculator.deadlineFactor(3), DELTA);
        assertEquals(1.2, WorkloadCalculator.deadlineFactor(4), DELTA);
        assertEquals(1.2, WorkloadCalculator.deadlineFactor(7), DELTA);
        assertEquals(1.0, WorkloadCalculator.deadlineFactor(8), DELTA);
    }

    @Test
    public void taskLoadIsHoursTimesWeightTimesFactor() {
        // 4 h × 2.0 (hard) × 1.5 (2 days left) = 12 h
        assertEquals(12.0, WorkloadCalculator.taskLoadHours(240, Difficulty.HARD, 2), DELTA);
    }

    @Test
    public void restDayHasNoFreeTime() {
        assertEquals(18.0, calculator.freeHoursNext7Days(TODAY), DELTA);
        assertEquals(0, calculator.freeMinutes(TODAY.plusDays(4)));   // Friday
    }

    @Test
    public void studyLoadIndexForSampleWeek() {
        List<Task> tasks = Arrays.asList(
                task(Difficulty.HARD, 240, 2),     // 4 × 2.0 × 1.5 = 12.0
                task(Difficulty.MEDIUM, 120, 6),   // 2 × 1.5 × 1.2 = 3.6
                task(Difficulty.EASY, 600, 10));   // due later than 7 days: not counted
        // 15.6 / 18 × 100 = 86.7 -> 87
        int index = calculator.studyLoadIndex(tasks, new HashMap<Integer, Integer>(), TODAY);
        assertEquals(87, index);
        assertEquals(LoadLevel.HEAVY, WorkloadCalculator.level(index));
    }

    @Test
    public void doneMinutesAndClosedTasksDoNotCount() {
        Task hard = task(Difficulty.HARD, 240, 2);
        Task done = task(Difficulty.HARD, 600, 1);
        done.setStatus(TaskStatus.DONE);
        Map<Integer, Integer> doneMinutes = new HashMap<Integer, Integer>();
        doneMinutes.put(hard.getId(), 120);
        // 2 h × 2.0 × 1.5 = 6 -> 6 / 18 = 33
        assertEquals(33, calculator.studyLoadIndex(Arrays.asList(hard, done), doneMinutes, TODAY));
    }

    @Test
    public void indexIsCappedAt150() {
        List<Task> tasks = Arrays.asList(task(Difficulty.HARD, 1200, 1), task(Difficulty.HARD, 900, 3));
        assertEquals(150, calculator.studyLoadIndex(tasks, new HashMap<Integer, Integer>(), TODAY));
    }

    @Test
    public void noTasksIsZeroAndLight() {
        int index = calculator.studyLoadIndex(new ArrayList<Task>(), new HashMap<Integer, Integer>(), TODAY);
        assertEquals(0, index);
        assertEquals(LoadLevel.LIGHT, WorkloadCalculator.level(index));
    }

    @Test
    public void levelBoundaries() {
        assertEquals(LoadLevel.LIGHT, WorkloadCalculator.level(49));
        assertEquals(LoadLevel.BALANCED, WorkloadCalculator.level(50));
        assertEquals(LoadLevel.BALANCED, WorkloadCalculator.level(79));
        assertEquals(LoadLevel.HEAVY, WorkloadCalculator.level(80));
        assertEquals(LoadLevel.HEAVY, WorkloadCalculator.level(100));
        assertEquals(LoadLevel.OVERLOADED, WorkloadCalculator.level(101));
    }

    @Test
    public void dailyLoadUsesWeightedPlannedHours() {
        Task hard = task(Difficulty.HARD, 300, 5);
        Task easy = task(Difficulty.EASY, 300, 5);
        Map<Integer, Task> byId = new HashMap<Integer, Task>();
        byId.put(hard.getId(), hard);
        byId.put(easy.getId(), easy);
        PlanBlock skipped = new PlanBlock(1, easy.getId(), TODAY, 120);
        skipped.setStatus(PlanStatus.SKIPPED);
        List<PlanBlock> blocks = Arrays.asList(
                new PlanBlock(1, hard.getId(), TODAY, 60),    // 1 h × 2.0
                new PlanBlock(1, easy.getId(), TODAY, 60),    // 1 h × 1.0
                skipped,                                      // not counted
                new PlanBlock(1, easy.getId(), TODAY.plusDays(1), 60));   // other day
        // 3 weighted hours / 3 free hours = 100
        assertEquals(100, calculator.dailyLoad(blocks, byId, TODAY));
        // study planned on the rest day (0 free hours) -> 150
        assertEquals(150, calculator.dailyLoad(
                Arrays.asList(new PlanBlock(1, easy.getId(), TODAY.plusDays(4), 30)), byId, TODAY.plusDays(4)));
    }
}
