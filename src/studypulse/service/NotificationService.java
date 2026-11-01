package studypulse.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import studypulse.dao.AvailabilityDAO;
import studypulse.dao.NotificationDAO;
import studypulse.dao.PlanBlockDAO;
import studypulse.dao.RiskSnapshotDAO;
import studypulse.dao.TaskDAO;
import studypulse.dao.UserDAO;
import studypulse.engine.WorkloadCalculator;
import studypulse.model.Availability;
import studypulse.model.Notification;
import studypulse.model.NotificationType;
import studypulse.model.RiskSnapshot;
import studypulse.model.Task;
import studypulse.model.TaskStatus;
import studypulse.model.User;
import studypulse.util.DateUtil;
import studypulse.util.ErrorLog;
import studypulse.util.Session;

/**
 * Creates warning / info notifications and manages the notification list.
 * A message is never created twice on the same day ({@link #createOnce}).
 */
public class NotificationService {

    public static final String HIGH_WORKLOAD_TOMORROW = "High workload detected for tomorrow.";
    public static final String RISK_INCREASING = "Your burnout risk appears to be increasing.";
    public static final String TAKE_A_BREAK = "Consider taking a short break.";
    public static final int HIGH_DAILY_LOAD = 100;
    public static final int DEADLINE_HOURS = 48;
    /** Missed tasks older than this are not announced any more. */
    public static final int MISSED_LOOKBACK_DAYS = 7;

    private final NotificationDAO notificationDAO = new NotificationDAO();
    private final TaskDAO taskDAO = new TaskDAO();
    private final PlanBlockDAO planBlockDAO = new PlanBlockDAO();
    private final AvailabilityDAO availabilityDAO = new AvailabilityDAO();
    private final RiskSnapshotDAO snapshotDAO = new RiskSnapshotDAO();
    private final UserDAO userDAO = new UserDAO();

    /**
     * Runs all checks for the user (every 15 minutes and right after login).
     * Returns the notifications created by this run.
     */
    public List<Notification> runChecks(int userId) throws ServiceException {
        List<Notification> created = new ArrayList<Notification>();
        LocalDateTime now = DateUtil.now();
        LocalDate today = now.toLocalDate();
        try {
            User user = userDAO.findById(userId);
            if (user == null) {
                return created;
            }
            taskDAO.markOverdueAsMissed(userId, now);
            List<Task> tasks = taskDAO.findAllByUser(userId);

            // 1. high workload tomorrow (daily load of tomorrow's plan)
            Map<Integer, Integer> free = new HashMap<Integer, Integer>();
            for (Availability a : availabilityDAO.findAllByUser(userId)) {
                free.put(a.getDayOfWeek(), a.getFreeMinutes());
            }
            Map<Integer, Task> byId = new HashMap<Integer, Task>();
            for (Task t : tasks) {
                byId.put(t.getId(), t);
            }
            LocalDate tomorrow = today.plusDays(1);
            int tomorrowLoad = new WorkloadCalculator(free, user.getRestDay())
                    .dailyLoad(planBlockDAO.findByUserBetween(userId, tomorrow, tomorrow), byId, tomorrow);
            if (tomorrowLoad >= HIGH_DAILY_LOAD) {
                add(created, createOnce(userId, NotificationType.WORKLOAD, HIGH_WORKLOAD_TOMORROW));
            }

            // 2. burnout risk rose 3 days in a row (4 snapshots, each higher than the one before)
            List<RiskSnapshot> snaps = snapshotDAO.findByUserBetween(userId, today.minusDays(3), today);
            if (snaps.size() == 4 && risingNewestFirst(snaps)) {
                add(created, createOnce(userId, NotificationType.BURNOUT, RISK_INCREASING));
            }

            // 3. deadlines in the next 48 hours, 4. tasks that became MISSED
            int due = 0;
            for (Task t : tasks) {
                if (t.getDeadline() == null) {
                    continue;
                }
                boolean open = t.getStatus() == TaskStatus.PENDING || t.getStatus() == TaskStatus.IN_PROGRESS;
                if (open && !t.getDeadline().isBefore(now) && t.getDeadline().isBefore(now.plusHours(DEADLINE_HOURS))) {
                    due++;
                }
                if (t.getStatus() == TaskStatus.MISSED
                        && !t.getDeadline().isBefore(now.minusDays(MISSED_LOOKBACK_DAYS))) {
                    String message = missedMessage(t.getTitle());
                    // a missed task is announced only once, not once per day
                    if (!notificationDAO.existsSince(userId, NotificationType.DEADLINE, message,
                            now.minusDays(MISSED_LOOKBACK_DAYS + 1))) {
                        add(created, createOnce(userId, NotificationType.DEADLINE, message));
                    }
                }
            }
            if (due > 0) {
                add(created, createOnce(userId, NotificationType.DEADLINE, deadlineMessage(due)));
            }
            return created;
        } catch (SQLException ex) {
            throw new ServiceException("Could not check for notifications.", ex);
        }
    }

    /**
     * Creates the notification unless the same message already exists today.
     * Returns the new notification, or null when it was a duplicate.
     */
    public Notification createOnce(int userId, NotificationType type, String message) throws SQLException {
        if (notificationDAO.existsSince(userId, type, message, DateUtil.today().atStartOfDay())) {
            return null;
        }
        Notification n = new Notification(userId, type, message);
        notificationDAO.insert(n);
        return n;
    }

    /** Info message such as "Study plan generated: 14 blocks for 5 tasks." (errors only logged). */
    public void info(int userId, String message) {
        try {
            createOnce(userId, NotificationType.INFO, message);
        } catch (SQLException ex) {
            ErrorLog.log("Could not save notification", ex);
        }
    }

    /** "Consider taking a short break." for the logged-in user; null if already sent today. */
    public Notification remindBreak() throws ServiceException {
        try {
            return createOnce(Session.getUserId(), NotificationType.BREAK, TAKE_A_BREAK);
        } catch (SQLException ex) {
            throw new ServiceException("Could not save the break reminder.", ex);
        }
    }

    /** Newest first; type null = all types. */
    public List<Notification> getNotifications(NotificationType type) throws ServiceException {
        try {
            return notificationDAO.findByUserAndType(Session.getUserId(), type);
        } catch (SQLException ex) {
            throw new ServiceException("Could not load notifications.", ex);
        }
    }

    /** Unread notifications of today newer than afterId (for desktop alerts), oldest first. */
    public List<Notification> getNewUnread(int userId, int afterId) throws ServiceException {
        try {
            return notificationDAO.findUnreadAfter(userId, afterId, DateUtil.today().atStartOfDay());
        } catch (SQLException ex) {
            throw new ServiceException("Could not load notifications.", ex);
        }
    }

    public int countUnread() throws ServiceException {
        try {
            return notificationDAO.countUnread(Session.getUserId());
        } catch (SQLException ex) {
            throw new ServiceException("Could not count notifications.", ex);
        }
    }

    public void markRead(List<Integer> ids) throws ServiceException {
        try {
            for (int id : ids) {
                checkOwner(id);
                notificationDAO.markRead(id);
            }
        } catch (SQLException ex) {
            throw new ServiceException("Could not update notifications.", ex);
        }
    }

    public int markAllRead() throws ServiceException {
        try {
            return notificationDAO.markAllRead(Session.getUserId());
        } catch (SQLException ex) {
            throw new ServiceException("Could not update notifications.", ex);
        }
    }

    public void delete(List<Integer> ids) throws ServiceException {
        try {
            for (int id : ids) {
                checkOwner(id);
                notificationDAO.delete(id);
            }
        } catch (SQLException ex) {
            throw new ServiceException("Could not delete notifications.", ex);
        }
    }

    /** "You have 1 deadline in the next 48 hours." / "You have 3 deadlines ...". */
    public static String deadlineMessage(int count) {
        return "You have " + count + (count == 1 ? " deadline" : " deadlines") + " in the next 48 hours.";
    }

    public static String missedMessage(String taskTitle) {
        return taskTitle + " was missed.";
    }

    /** Snapshots newest first; true if every day is higher than the day before. */
    static boolean risingNewestFirst(List<RiskSnapshot> newestFirst) {
        for (int i = 0; i < newestFirst.size() - 1; i++) {
            RiskSnapshot newer = newestFirst.get(i);
            RiskSnapshot older = newestFirst.get(i + 1);
            if (!newer.getSnapshotDate().equals(older.getSnapshotDate().plusDays(1))
                    || newer.getBurnoutScore() <= older.getBurnoutScore()) {
                return false;
            }
        }
        return newestFirst.size() > 1;
    }

    private void checkOwner(int id) throws SQLException, ServiceException {
        Notification n = notificationDAO.findById(id);
        if (n == null || n.getUserId() != Session.getUserId()) {
            throw new ServiceException("Notification not found.");
        }
    }

    private static void add(List<Notification> list, Notification n) {
        if (n != null) {
            list.add(n);
        }
    }
}
