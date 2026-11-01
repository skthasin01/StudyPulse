package studypulse.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import studypulse.dao.AvailabilityDAO;
import studypulse.dao.UserDAO;
import studypulse.model.Availability;
import studypulse.model.User;
import studypulse.util.DBConnection;
import studypulse.util.Session;

/** Free study time per weekday and the weekly rest day. */
public class AvailabilityService {

    public static final int MAX_FREE_MINUTES = 12 * 60;
    public static final int STEP_MINUTES = 30;

    private final AvailabilityDAO availabilityDAO = new AvailabilityDAO();
    private final UserDAO userDAO = new UserDAO();

    /** The 7 rows Monday (1) .. Sunday (7); missing rows are created with 0 minutes. */
    public List<Availability> getAvailability() throws ServiceException {
        int userId = Session.getUserId();
        try {
            List<Availability> rows = availabilityDAO.findAllByUser(userId);
            if (rows.size() < 7) {
                for (int day = 1; day <= 7; day++) {
                    if (availabilityDAO.findByUserAndDay(userId, day) == null) {
                        availabilityDAO.insert(new Availability(userId, day, 0));
                    }
                }
                rows = availabilityDAO.findAllByUser(userId);
            }
            return rows;
        } catch (SQLException ex) {
            throw new ServiceException("Could not load your free time.", ex);
        }
    }

    /** day_of_week -> free minutes. */
    public Map<Integer, Integer> getFreeMinutesByDay() throws ServiceException {
        Map<Integer, Integer> map = new HashMap<Integer, Integer>();
        for (Availability a : getAvailability()) {
            map.put(a.getDayOfWeek(), a.getFreeMinutes());
        }
        return map;
    }

    public int getRestDay() {
        User user = Session.getUser();
        return user == null ? AuthService.DEFAULT_REST_DAY : user.getRestDay();
    }

    /** Saves free minutes of all rows and users.rest_day in one transaction. */
    public void save(List<Availability> rows, int restDay) throws ServiceException {
        int userId = Session.getUserId();
        if (restDay < 1 || restDay > 7) {
            throw new ServiceException("Please choose a rest day.");
        }
        for (Availability a : rows) {
            if (a.getUserId() != userId) {
                throw new ServiceException("Free time row not found.");
            }
            if (a.getFreeMinutes() < 0 || a.getFreeMinutes() > MAX_FREE_MINUTES
                    || a.getFreeMinutes() % STEP_MINUTES != 0) {
                throw new ServiceException("Free hours must be between 0 and 12 in steps of 0.5.");
            }
        }
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                for (Availability a : rows) {
                    availabilityDAO.update(conn, a);
                }
                User user = userDAO.findById(userId);
                user.setRestDay(restDay);
                userDAO.update(conn, user);
                conn.commit();
                Session.getUser().setRestDay(restDay);
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new ServiceException("Could not save your free time.", ex);
        }
    }
}
