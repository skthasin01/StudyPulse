package studypulse.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import studypulse.model.Difficulty;
import studypulse.model.PlanBlock;
import studypulse.model.Task;
import studypulse.model.TaskStatus;
import studypulse.model.TaskType;
import studypulse.model.User;

/** TaskDAO against a temporary database file. */
public class TaskDAOTest {

    private File dbFile;
    private User user;
    private final TaskDAO taskDAO = new TaskDAO();

    @Before
    public void setUp() throws Exception {
        dbFile = TestDatabase.create();
        user = TestDatabase.insertUser("tasks@test.com");
    }

    @After
    public void tearDown() {
        TestDatabase.delete(dbFile);
    }

    private Task task(String title, int daysFromNow) {
        Task t = new Task();
        t.setUserId(user.getId());
        t.setTitle(title);
        t.setTaskType(TaskType.ASSIGNMENT);
        t.setDifficulty(Difficulty.MEDIUM);
        t.setEstimatedMinutes(90);
        t.setDeadline(LocalDateTime.of(2026, 11, 10, 23, 0).plusDays(daysFromNow));
        t.setCreatedAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        return t;
    }

    @Test
    public void idsAreGivenByTheDatabaseInOrder() throws SQLException {
        assertEquals(1, taskDAO.insert(task("A", 0)));
        assertEquals(2, taskDAO.insert(task("B", 1)));
        taskDAO.delete(2);
        assertEquals("ids are never reused (AUTOINCREMENT)", 3, taskDAO.insert(task("C", 2)));
    }

    @Test
    public void insertFindUpdateDelete() throws SQLException {
        Task t = task("DBMS Lab Report, part 1", 3);
        int id = taskDAO.insert(t);
        Task loaded = taskDAO.findById(id);
        assertNotNull(loaded);
        assertEquals("DBMS Lab Report, part 1", loaded.getTitle());
        assertEquals(t.getDeadline(), loaded.getDeadline());
        assertEquals(TaskStatus.PENDING, loaded.getStatus());

        loaded.setStatus(TaskStatus.DONE);
        loaded.setActualMinutes(110);
        loaded.setCompletedAt(LocalDateTime.of(2026, 11, 12, 20, 0));
        assertTrue(taskDAO.update(loaded));
        Task updated = taskDAO.findById(id);
        assertEquals(TaskStatus.DONE, updated.getStatus());
        assertEquals(110, updated.getActualMinutes());

        assertTrue(taskDAO.delete(id));
        assertNull(taskDAO.findById(id));
    }

    @Test
    public void findAllByUserIsSortedByDeadlineAndOnlyForThatUser() throws SQLException {
        taskDAO.insert(task("Later", 5));
        taskDAO.insert(task("Sooner", 1));
        User other = TestDatabase.insertUser("other@test.com");
        Task foreign = task("Not mine", 0);
        foreign.setUserId(other.getId());
        taskDAO.insert(foreign);

        List<Task> mine = taskDAO.findAllByUser(user.getId());
        assertEquals(2, mine.size());
        assertEquals("Sooner", mine.get(0).getTitle());
        assertEquals("Later", mine.get(1).getTitle());
    }

    @Test
    public void overdueOpenTasksBecomeMissed() throws SQLException {
        int overdue = taskDAO.insert(task("Overdue", -5));
        Task done = task("Done", -5);
        done.setStatus(TaskStatus.DONE);
        int doneId = taskDAO.insert(done);
        int future = taskDAO.insert(task("Future", 5));

        assertEquals(1, taskDAO.markOverdueAsMissed(user.getId(), LocalDateTime.of(2026, 11, 10, 12, 0)));
        assertEquals(TaskStatus.MISSED, taskDAO.findById(overdue).getStatus());
        assertEquals(TaskStatus.DONE, taskDAO.findById(doneId).getStatus());
        assertEquals(TaskStatus.PENDING, taskDAO.findById(future).getStatus());
    }

    @Test
    public void deletingATaskCascadesToItsPlanBlocks() throws SQLException {
        int id = taskDAO.insert(task("With blocks", 4));
        PlanBlockDAO blocks = new PlanBlockDAO();
        blocks.insert(new PlanBlock(user.getId(), id, LocalDate.of(2026, 11, 11), 60));
        assertEquals(1, blocks.findAllByUser(user.getId()).size());
        taskDAO.delete(id);
        assertEquals(0, blocks.findAllByUser(user.getId()).size());
    }

    @Test(expected = SQLException.class)
    public void checkConstraintRejectsZeroMinutes() throws SQLException {
        Task t = task("Bad", 1);
        t.setEstimatedMinutes(0);
        taskDAO.insert(t);
    }
}
