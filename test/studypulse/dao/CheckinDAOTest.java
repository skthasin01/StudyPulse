package studypulse.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import studypulse.model.Checkin;
import studypulse.model.User;

/** CheckinDAO against a temporary database file. */
public class CheckinDAOTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);

    private File dbFile;
    private User user;
    private final CheckinDAO dao = new CheckinDAO();

    @Before
    public void setUp() throws Exception {
        dbFile = TestDatabase.create();
        user = TestDatabase.insertUser("checkins@test.com");
    }

    @After
    public void tearDown() {
        TestDatabase.delete(dbFile);
    }

    private Checkin checkin(LocalDate date, int stress, double sleep) {
        Checkin c = new Checkin();
        c.setUserId(user.getId());
        c.setCheckinDate(date);
        c.setStressLevel(stress);
        c.setEnergyLevel(3);
        c.setMoodLevel(3);
        c.setSleepHours(sleep);
        c.setNote("Note, with \"quotes\"");
        return c;
    }

    @Test
    public void saveForDayInsertsOnceThenUpdatesTheSameRow() throws SQLException {
        int first = dao.saveForDay(checkin(DAY, 2, 7.5));
        int second = dao.saveForDay(checkin(DAY, 5, 4.5));
        assertEquals(first, second);
        List<Checkin> all = dao.findAllByUser(user.getId());
        assertEquals(1, all.size());
        assertEquals(5, all.get(0).getStressLevel());
        assertEquals(4.5, all.get(0).getSleepHours(), 0.001);
        assertEquals("Note, with \"quotes\"", all.get(0).getNote());
    }

    @Test(expected = SQLException.class)
    public void uniqueUserAndDateRejectsASecondInsert() throws SQLException {
        dao.insert(checkin(DAY, 2, 7));
        dao.insert(checkin(DAY, 3, 6));
    }

    @Test(expected = SQLException.class)
    public void checkConstraintRejectsLevelSix() throws SQLException {
        dao.insert(checkin(DAY, 6, 7));
    }

    @Test
    public void betweenAndLatestAreNewestFirst() throws SQLException {
        for (int i = 0; i < 5; i++) {
            dao.insert(checkin(DAY.plusDays(i), 1 + i, 8 - i));
        }
        List<Checkin> range = dao.findByUserBetween(user.getId(), DAY.plusDays(1), DAY.plusDays(3));
        assertEquals(3, range.size());
        assertEquals(DAY.plusDays(3), range.get(0).getCheckinDate());
        List<Checkin> latest = dao.findLatest(user.getId(), 3, DAY);
        assertEquals(3, latest.size());
        assertEquals(DAY.plusDays(4), latest.get(0).getCheckinDate());
        assertEquals(5, dao.findDatesUpTo(user.getId(), DAY.plusDays(10)).size());
    }

    @Test
    public void deleteAllByUserAndCascadeFromUsers() throws SQLException {
        dao.insert(checkin(DAY, 2, 7));
        dao.insert(checkin(DAY.plusDays(1), 2, 7));
        assertEquals(2, dao.deleteAllByUser(user.getId()));
        int id = dao.insert(checkin(DAY.plusDays(2), 2, 7));
        new UserDAO().delete(user.getId());
        assertNull("checkins are deleted with their user (ON DELETE CASCADE)", dao.findById(id));
    }
}
