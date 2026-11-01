package studypulse.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import studypulse.dao.CheckinDAO;
import studypulse.dao.NotificationDAO;
import studypulse.dao.PlanBlockDAO;
import studypulse.dao.RiskSnapshotDAO;
import studypulse.dao.StudyLogDAO;
import studypulse.dao.TaskDAO;
import studypulse.model.Checkin;
import studypulse.model.PlanBlock;
import studypulse.model.PlanBlockItem;
import studypulse.model.RiskSnapshot;
import studypulse.model.Task;
import studypulse.model.TaskStatus;
import studypulse.util.DateUtil;

/** All numbers for the home dashboard and the Analytics charts (no SQL in the forms). */
public class AnalyticsService {

    public static final int UPCOMING_COUNT = 5;
    public static final double SHORT_SLEEP_HOURS = 6.0;

    private final RiskService riskService = new RiskService();
    private final TaskDAO taskDAO = new TaskDAO();
    private final PlanBlockDAO planBlockDAO = new PlanBlockDAO();
    private final StudyLogDAO studyLogDAO = new StudyLogDAO();
    private final RiskSnapshotDAO snapshotDAO = new RiskSnapshotDAO();
    private final CheckinDAO checkinDAO = new CheckinDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();

    public DashboardSummary getDashboard(int userId) throws ServiceException {
        LocalDate today = DateUtil.today();
        LocalDateTime now = DateUtil.now();
        DashboardSummary s = new DashboardSummary();
        s.setRisk(riskService.calculateToday(userId));   // also marks overdue tasks MISSED
        try {
            List<Task> tasks = taskDAO.findAllByUser(userId);   // ordered by deadline
            Map<Integer, Task> byId = new HashMap<Integer, Task>();
            int due = 0;
            for (Task t : tasks) {
                byId.put(t.getId(), t);
                if (!isOpen(t) || t.getDeadline().isBefore(now)) {
                    continue;
                }
                if (s.getNextTask() == null) {
                    s.setNextTask(t);
                }
                if (s.getUpcoming().size() < UPCOMING_COUNT) {
                    s.getUpcoming().add(t);
                }
                if (!t.getDeadline().isAfter(now.plusDays(7))) {
                    due++;
                }
            }
            s.setDueThisWeek(due);

            for (PlanBlock b : planBlockDAO.findByUserBetween(userId, today, today)) {
                Task t = byId.get(b.getTaskId());
                s.getTodayBlocks().add(new PlanBlockItem(b, t == null ? "(deleted task)" : t.getTitle(),
                        t == null ? null : t.getDifficulty()));
            }

            LocalDateTime monday = DateUtil.mondayOf(today).atStartOfDay();
            s.setHoursThisWeek(studyLogDAO.sumMinutesBetween(userId, monday, now.plusSeconds(1)) / 60.0);
            s.setHoursLastWeek(studyLogDAO.sumMinutesBetween(userId, monday.minusDays(7), now.minusDays(7)) / 60.0);

            List<RiskSnapshot> snapshots = snapshotDAO.findByUserBetween(userId, today.minusDays(6), today);
            Collections.reverse(snapshots);   // oldest first
            for (RiskSnapshot r : snapshots) {
                s.getRiskLast7Days().put(r.getSnapshotDate(), (int) Math.round(r.getBurnoutScore()));
            }
            s.setUnreadNotifications(notificationDAO.countUnread(userId));
            return s;
        } catch (SQLException ex) {
            throw new ServiceException("Could not load the dashboard.", ex);
        }
    }

    /** Chart data for the last 'days' days (7 or 28), today included. */
    public AnalyticsData getAnalytics(int userId, int days) throws ServiceException {
        LocalDate to = DateUtil.today();
        LocalDate from = to.minusDays(days - 1);
        AnalyticsData data = new AnalyticsData(days, from, to);
        try {
            Map<LocalDate, Integer> minutes = studyLogDAO.sumMinutesByDay(userId, from, to);
            int total = 0;
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                Integer m = minutes.get(d);
                data.getHoursByDay().put(d, m == null ? 0.0 : m / 60.0);
                total += m == null ? 0 : m;
            }
            data.setTotalHours(total / 60.0);
            data.setPreviousHours(studyLogDAO.sumMinutesBetween(userId, from.minusDays(days).atStartOfDay(),
                    from.atStartOfDay()) / 60.0);

            List<RiskSnapshot> snapshots = snapshotDAO.findByUserBetween(userId, from, to);
            Collections.reverse(snapshots);
            for (RiskSnapshot r : snapshots) {
                data.getLoadByDay().put(r.getSnapshotDate(), (int) Math.round(r.getWorkloadIndex()));
                data.getRiskByDay().put(r.getSnapshotDate(), (int) Math.round(r.getBurnoutScore()));
            }

            List<Checkin> checkins = checkinDAO.findByUserBetween(userId, from, to);
            Collections.reverse(checkins);
            for (Checkin c : checkins) {
                data.getSleepByDay().put(c.getCheckinDate(), c.getSleepHours());
                data.getStressByDay().put(c.getCheckinDate(), c.getStressLevel());
            }

            for (TaskStatus st : TaskStatus.values()) {
                data.getStatusCounts().put(st, 0);
            }
            for (Task t : taskDAO.findAllByUser(userId)) {
                if (t.getDeadline() != null && !t.getDeadline().toLocalDate().isBefore(from)) {
                    data.getStatusCounts().put(t.getStatus(), data.getStatusCounts().get(t.getStatus()) + 1);
                }
            }

            data.getInsights().add(hoursInsight(days, data.getTotalHours(), data.getPreviousHours()));
            String second = sleepRiskInsight(data.getSleepByDay(), data.getRiskByDay());
            if (second == null) {
                second = fallbackInsight(data);
            }
            if (second != null) {
                data.getInsights().add(second);
            }
            return data;
        } catch (SQLException ex) {
            throw new ServiceException("Could not load the analytics.", ex);
        }
    }

    /** "You studied 14.5 h in the last 7 days (+3.0 h vs the 7 days before)." */
    public static String hoursInsight(int days, double hours, double previousHours) {
        String period = days == 7 ? "the last 7 days" : "the last " + (days / 7) + " weeks";
        if (hours <= 0 && previousHours <= 0) {
            return "No study sessions recorded in " + period + " yet.";
        }
        String before = days == 7 ? "the 7 days before" : "the " + (days / 7) + " weeks before";
        double diff = hours - previousHours;
        String sign = diff >= 0 ? "+" : "−";
        return "You studied " + one(hours) + " h in " + period + " (" + sign + one(Math.abs(diff))
                + " h vs " + before + ").";
    }

    /**
     * Compares the risk score on days after less than 6 h of sleep (sleep of that day's
     * check-in) with the other days. Null when one of the groups has no days or there is no clear difference.
     */
    public static String sleepRiskInsight(Map<LocalDate, Double> sleepByDay, Map<LocalDate, Integer> riskByDay) {
        double shortSum = 0;
        int shortDays = 0;
        double otherSum = 0;
        int otherDays = 0;
        for (Map.Entry<LocalDate, Double> e : sleepByDay.entrySet()) {
            Integer risk = riskByDay.get(e.getKey());
            if (risk == null) {
                continue;
            }
            if (e.getValue() < SHORT_SLEEP_HOURS) {
                shortSum += risk;
                shortDays++;
            } else {
                otherSum += risk;
                otherDays++;
            }
        }
        if (shortDays == 0 || otherDays == 0) {
            return null;
        }
        long shortAvg = Math.round(shortSum / shortDays);
        long otherAvg = Math.round(otherSum / otherDays);
        if (shortAvg <= otherAvg) {
            return null;
        }
        return "Your risk score is highest on days after less than 6 h of sleep (average " + shortAvg
                + " vs " + otherAvg + ").";
    }

    private static String fallbackInsight(AnalyticsData data) {
        Integer missed = data.getStatusCounts().get(TaskStatus.MISSED);
        if (missed != null && missed > 0) {
            return missed + (missed == 1 ? " task was" : " tasks were") + " missed in this period.";
        }
        LocalDate peakDay = null;
        int peak = -1;
        for (Map.Entry<LocalDate, Integer> e : data.getLoadByDay().entrySet()) {
            if (e.getValue() > peak) {
                peak = e.getValue();
                peakDay = e.getKey();
            }
        }
        if (peakDay != null && peak > 0) {
            return "Your highest Study Load Index was " + peak + " on " + DateUtil.formatDay(peakDay) + ".";
        }
        if (!data.hasCheckins()) {
            return "Do the daily check-in to see how sleep and stress affect your risk score.";
        }
        return null;
    }

    private static boolean isOpen(Task t) {
        return (t.getStatus() == TaskStatus.PENDING || t.getStatus() == TaskStatus.IN_PROGRESS)
                && t.getDeadline() != null;
    }

    private static String one(double value) {
        return String.format(Locale.ENGLISH, "%.1f", value);
    }
}
