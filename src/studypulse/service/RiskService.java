package studypulse.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import studypulse.dao.AvailabilityDAO;
import studypulse.dao.CheckinDAO;
import studypulse.dao.NotificationDAO;
import studypulse.dao.PlanBlockDAO;
import studypulse.dao.RiskSnapshotDAO;
import studypulse.dao.StudyLogDAO;
import studypulse.dao.TaskDAO;
import studypulse.dao.UserDAO;
import studypulse.engine.BurnoutRiskCalculator;
import studypulse.engine.RecommendationEngine;
import studypulse.engine.RiskAssessment;
import studypulse.engine.RiskReason;
import studypulse.engine.WorkloadCalculator;
import studypulse.model.Availability;
import studypulse.model.LoadLevel;
import studypulse.model.Notification;
import studypulse.model.NotificationType;
import studypulse.model.RiskLevel;
import studypulse.model.RiskSnapshot;
import studypulse.model.Task;
import studypulse.model.User;
import studypulse.util.DateUtil;
import studypulse.util.Session;

/**
 * Collects the data, runs WorkloadCalculator, BurnoutRiskCalculator and RecommendationEngine,
 * stores today's risk_snapshots row and creates warning notifications.
 */
public class RiskService {

    public static final int HISTORY_DAYS = 14;

    private final UserDAO userDAO = new UserDAO();
    private final TaskDAO taskDAO = new TaskDAO();
    private final AvailabilityDAO availabilityDAO = new AvailabilityDAO();
    private final PlanBlockDAO planBlockDAO = new PlanBlockDAO();
    private final CheckinDAO checkinDAO = new CheckinDAO();
    private final StudyLogDAO studyLogDAO = new StudyLogDAO();
    private final RiskSnapshotDAO snapshotDAO = new RiskSnapshotDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();

    /** Calculates today's values, saves (replaces) today's snapshot and creates notifications. */
    public RiskReport calculateToday(int userId) throws ServiceException {
        LocalDate today = DateUtil.today();
        LocalDateTime now = DateUtil.now();
        try {
            User user = userDAO.findById(userId);
            if (user == null) {
                throw new ServiceException("User not found.");
            }
            taskDAO.markOverdueAsMissed(userId, now);

            Map<Integer, Integer> free = new HashMap<Integer, Integer>();
            for (Availability a : availabilityDAO.findAllByUser(userId)) {
                free.put(a.getDayOfWeek(), a.getFreeMinutes());
            }
            List<Task> tasks = taskDAO.findAllByUser(userId);
            int loadIndex = new WorkloadCalculator(free, user.getRestDay())
                    .studyLoadIndex(tasks, planBlockDAO.sumDoneMinutesByTask(userId), today);

            List<Integer> previousLoads = new ArrayList<Integer>();
            List<RiskSnapshot> last3 = snapshotDAO.findByUserBetween(userId, today.minusDays(3), today.minusDays(1));
            if (last3.size() == 3) {
                for (int i = last3.size() - 1; i >= 0; i--) {      // oldest first
                    previousLoads.add((int) Math.round(last3.get(i).getWorkloadIndex()));
                }
            }

            RiskAssessment risk = new BurnoutRiskCalculator().calculate(
                    user.isWellbeingEnabled(),
                    checkinDAO.findLatest(userId, 3, today.minusDays(6)),
                    studyLogDAO.findByUserSince(userId, today.minusDays(2).atStartOfDay()),
                    taskDAO.countMissedBetween(userId, today.minusDays(7).atStartOfDay(), now),
                    loadIndex, previousLoads);
            List<String> recommendations = new RecommendationEngine().recommend(risk,
                    RecommendationEngine.pickTaskToMove(tasks), DateUtil.dayName(user.getRestDay()));

            RiskSnapshot snapshot = new RiskSnapshot();
            snapshot.setUserId(userId);
            snapshot.setSnapshotDate(today);
            snapshot.setWorkloadIndex(loadIndex);
            snapshot.setBurnoutScore(risk.getScore());
            snapshot.setRiskLevel(risk.getLevel());
            snapshot.setReasons(reasonsText(risk));
            int snapshotId = snapshotDAO.saveForDay(snapshot);

            createNotifications(userId, loadIndex, risk, today);
            return new RiskReport(today, loadIndex, WorkloadCalculator.level(loadIndex), risk,
                    recommendations, snapshotId);
        } catch (SQLException ex) {
            throw new ServiceException("Could not calculate the risk report.", ex);
        }
    }

    /**
     * Recalculates for the logged-in user and ignores errors. Called after login, saving a task,
     * a check-in and generating the plan, so a risk problem never blocks those actions.
     */
    public void refreshQuietly() {
        if (!Session.isLoggedIn()) {
            return;
        }
        try {
            calculateToday(Session.getUserId());
        } catch (ServiceException ex) {
            // already written to data/error.log by ServiceException; a risk problem must not block the user
        }
    }

    /** Snapshots of the last 14 days, newest first. */
    public List<RiskSnapshot> getHistory(int userId) throws ServiceException {
        LocalDate today = DateUtil.today();
        try {
            return snapshotDAO.findByUserBetween(userId, today.minusDays(HISTORY_DAYS - 1), today);
        } catch (SQLException ex) {
            throw new ServiceException("Could not load the risk history.", ex);
        }
    }

    /** At most one BURNOUT and one WORKLOAD notification per day. */
    private void createNotifications(int userId, int loadIndex, RiskAssessment risk, LocalDate today)
            throws SQLException {
        LocalDateTime startOfDay = today.atStartOfDay();
        if ((risk.getLevel() == RiskLevel.HIGH || risk.getLevel() == RiskLevel.SEVERE)
                && !notificationDAO.existsTypeSince(userId, NotificationType.BURNOUT, startOfDay)) {
            notificationDAO.insert(new Notification(userId, NotificationType.BURNOUT,
                    "Burnout risk is " + risk.getLevel().getLabel().toUpperCase() + " today ("
                    + risk.getScore() + "/100). Open the Risk Report to see why."));
        }
        if (loadIndex >= BurnoutRiskCalculator.HIGH_LOAD_INDEX
                && !notificationDAO.existsTypeSince(userId, NotificationType.WORKLOAD, startOfDay)) {
            LoadLevel level = WorkloadCalculator.level(loadIndex);
            notificationDAO.insert(new Notification(userId, NotificationType.WORKLOAD,
                    "Your Study Load Index for the next 7 days is " + loadIndex + " (" + level.getLabel()
                    + "). Consider moving a task or adding free time."));
        }
    }

    /** One line per reason: "+15 Average sleep in last 3 days is 5.7 h (below 6 h)". */
    private static String reasonsText(RiskAssessment risk) {
        StringBuilder sb = new StringBuilder();
        for (RiskReason r : risk.getReasons()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append('+').append(r.getPoints()).append(' ').append(r.getText());
        }
        return sb.toString();
    }
}
