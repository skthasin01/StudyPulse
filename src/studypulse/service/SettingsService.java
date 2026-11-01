package studypulse.service;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import studypulse.dao.CheckinDAO;
import studypulse.dao.RiskSnapshotDAO;
import studypulse.dao.StudyLogDAO;
import studypulse.dao.TaskDAO;
import studypulse.dao.UserDAO;
import studypulse.dao.UserSettingsDAO;
import studypulse.model.AppTheme;
import studypulse.model.Checkin;
import studypulse.model.RiskSnapshot;
import studypulse.model.StudyLog;
import studypulse.model.Task;
import studypulse.model.User;
import studypulse.model.UserSettings;
import studypulse.util.CsvUtil;
import studypulse.util.DBConnection;
import studypulse.util.DateUtil;
import studypulse.util.PasswordUtil;
import studypulse.util.Session;

/** Profile, password, study preferences, privacy (export / delete) and theme. */
public class SettingsService {

    public static final String[] EXPORT_FILES = {"tasks.csv", "checkins.csv", "study_logs.csv", "risk_history.csv"};

    private final UserDAO userDAO = new UserDAO();
    private final UserSettingsDAO settingsDAO = new UserSettingsDAO();
    private final TaskDAO taskDAO = new TaskDAO();
    private final CheckinDAO checkinDAO = new CheckinDAO();
    private final StudyLogDAO studyLogDAO = new StudyLogDAO();
    private final RiskSnapshotDAO snapshotDAO = new RiskSnapshotDAO();

    /** The logged-in user's settings; the row is created with defaults if it does not exist yet. */
    public UserSettings getSettings() throws ServiceException {
        return getSettings(Session.getUserId());
    }

    public UserSettings getSettings(int userId) throws ServiceException {
        try {
            UserSettings s = settingsDAO.findByUser(userId);
            if (s == null) {
                s = new UserSettings(userId);
                settingsDAO.insert(s);
            }
            return s;
        } catch (SQLException ex) {
            throw new ServiceException("Could not load your settings.", ex);
        }
    }

    public void saveProfile(String fullName) throws ServiceException {
        String name = fullName == null ? "" : fullName.trim();
        if (name.isEmpty()) {
            throw new ServiceException("Please enter your full name.");
        }
        try {
            User user = currentUserFromDb();
            user.setFullName(name);
            userDAO.update(user);
            Session.getUser().setFullName(name);
        } catch (SQLException ex) {
            throw new ServiceException("Could not save your profile.", ex);
        }
    }

    /** Same rules as sign-up; a new salt is created. */
    public void changePassword(char[] current, char[] newPassword, char[] confirm) throws ServiceException {
        if (current == null || current.length == 0) {
            throw new ServiceException("Please enter your current password.");
        }
        AuthService.checkNewPassword(newPassword, confirm);
        try {
            User user = currentUserFromDb();
            if (!PasswordUtil.verify(current, user.getSalt(), user.getPasswordHash())) {
                throw new ServiceException("Your current password is incorrect.");
            }
            String salt = PasswordUtil.generateSalt();
            user.setSalt(salt);
            user.setPasswordHash(PasswordUtil.hash(newPassword, salt));
            userDAO.update(user);
            Session.getUser().setSalt(user.getSalt());
            Session.getUser().setPasswordHash(user.getPasswordHash());
        } catch (SQLException ex) {
            throw new ServiceException("Could not change the password.", ex);
        }
    }

    /** Rest day (users.rest_day) and the Pomodoro / alert settings. */
    public void savePreferences(int restDay, int focus, int shortBreak, int longBreak,
            boolean notificationsOn, boolean trayOn) throws ServiceException {
        if (restDay < 1 || restDay > 7) {
            throw new ServiceException("Please choose a rest day.");
        }
        if (focus < 1 || focus > 90 || shortBreak < 1 || shortBreak > 30 || longBreak < 5 || longBreak > 60) {
            throw new ServiceException("Focus 1-90, short break 1-30 and long break 5-60 minutes.");
        }
        UserSettings s = getSettings();
        s.setFocusMinutes(focus);
        s.setShortBreak(shortBreak);
        s.setLongBreak(longBreak);
        s.setNotificationsOn(notificationsOn);
        s.setTrayOn(trayOn);
        try (java.sql.Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                User user = currentUserFromDb();
                user.setRestDay(restDay);
                userDAO.update(conn, user);
                settingsDAO.update(conn, s);
                conn.commit();
                Session.getUser().setRestDay(restDay);
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new ServiceException("Could not save your preferences.", ex);
        }
    }

    public void saveTheme(AppTheme theme) throws ServiceException {
        UserSettings s = getSettings();
        s.setTheme(theme);
        try {
            settingsDAO.update(s);
        } catch (SQLException ex) {
            throw new ServiceException("Could not save the theme.", ex);
        }
    }

    /** users.wellbeing_enabled; when switching off the check-ins can be deleted too. */
    public void setWellbeingTracking(boolean enabled, boolean deleteCheckins) throws ServiceException {
        try {
            User user = currentUserFromDb();
            user.setWellbeingEnabled(enabled);
            userDAO.update(user);
            Session.getUser().setWellbeingEnabled(enabled);
            if (!enabled && deleteCheckins) {
                checkinDAO.deleteAllByUser(user.getId());
            }
        } catch (SQLException ex) {
            throw new ServiceException("Could not change well-being tracking.", ex);
        }
    }

    /** Deletes this user's check-ins and risk snapshots. Returns {checkins, snapshots} deleted. */
    public int[] deleteWellbeingData() throws ServiceException {
        int userId = Session.getUserId();
        try {
            return new int[]{checkinDAO.deleteAllByUser(userId), snapshotDAO.deleteAllByUser(userId)};
        } catch (SQLException ex) {
            throw new ServiceException("Could not delete the data.", ex);
        }
    }

    /** Writes tasks.csv, checkins.csv, study_logs.csv and risk_history.csv (ID first, header row). */
    public List<File> exportData(File folder) throws ServiceException {
        int userId = Session.getUserId();
        List<File> files = new ArrayList<File>();
        try {
            List<Task> tasks = taskDAO.findAllByUser(userId);
            Map<Integer, String> titles = new HashMap<Integer, String>();
            List<Object[]> rows = new ArrayList<Object[]>();
            for (Task t : tasks) {
                titles.put(t.getId(), t.getTitle());
                rows.add(new Object[]{t.getId(), t.getTitle(), t.getDescription(), t.getTaskType().name(),
                    t.getCourseName(), t.getDifficulty().name(), t.getEstimatedMinutes(), t.getActualMinutes(),
                    DateUtil.formatDateTime(t.getDeadline()), t.getPriority().name(), t.getStatus().name(),
                    DateUtil.formatDateTime(t.getCreatedAt()), DateUtil.formatDateTime(t.getCompletedAt())});
            }
            files.add(write(folder, EXPORT_FILES[0], new String[]{"ID", "Title", "Description", "Type", "Course",
                "Difficulty", "Estimated minutes", "Actual minutes", "Deadline", "Priority", "Status",
                "Created at", "Completed at"}, rows));

            rows = new ArrayList<Object[]>();
            List<Checkin> checkins = checkinDAO.findAllByUser(userId);
            for (int i = checkins.size() - 1; i >= 0; i--) {   // oldest first
                Checkin c = checkins.get(i);
                rows.add(new Object[]{c.getId(), DateUtil.formatDate(c.getCheckinDate()), c.getStressLevel(),
                    c.getEnergyLevel(), c.getMoodLevel(), c.getSleepHours(), c.getNote()});
            }
            files.add(write(folder, EXPORT_FILES[1], new String[]{"ID", "Date", "Stress (1-5)", "Energy (1-5)",
                "Mood (1-5)", "Sleep hours", "Note"}, rows));

            rows = new ArrayList<Object[]>();
            List<StudyLog> logs = studyLogDAO.findAllByUser(userId);
            for (int i = logs.size() - 1; i >= 0; i--) {
                StudyLog l = logs.get(i);
                rows.add(new Object[]{l.getId(), l.getTaskId(), l.getTaskId() == null ? "" : titles.get(l.getTaskId()),
                    DateUtil.formatDateTime(l.getStartTime()), DateUtil.formatDateTime(l.getEndTime()),
                    l.getDurationMinutes(), l.getBreaksTaken()});
            }
            files.add(write(folder, EXPORT_FILES[2], new String[]{"ID", "Task ID", "Task", "Start", "End",
                "Duration minutes", "Breaks taken"}, rows));

            rows = new ArrayList<Object[]>();
            for (RiskSnapshot s : snapshotDAO.findAllByUser(userId)) {
                rows.add(new Object[]{s.getId(), DateUtil.formatDate(s.getSnapshotDate()),
                    Math.round(s.getWorkloadIndex()), Math.round(s.getBurnoutScore()), s.getRiskLevel().name(),
                    reasonsForCsv(s.getReasons())});
            }
            files.add(write(folder, EXPORT_FILES[3], new String[]{"ID", "Date", "Study Load Index",
                "Burnout risk score", "Risk level", "Reasons"}, rows));
            return files;
        } catch (SQLException ex) {
            throw new ServiceException("Could not read your data.", ex);
        } catch (IOException ex) {
            throw new ServiceException("Could not write the files.", ex);
        }
    }

    /** True if the password is the logged-in user's password. */
    public boolean verifyPassword(char[] password) throws ServiceException {
        try {
            User user = currentUserFromDb();
            return password != null && password.length > 0
                    && PasswordUtil.verify(password, user.getSalt(), user.getPasswordHash());
        } catch (SQLException ex) {
            throw new ServiceException("Could not check the password.", ex);
        }
    }

    /** Checks the password and deletes the user; all other rows go by ON DELETE CASCADE. */
    public void deleteAccount(char[] password) throws ServiceException {
        try {
            User user = currentUserFromDb();
            if (password == null || !PasswordUtil.verify(password, user.getSalt(), user.getPasswordHash())) {
                throw new ServiceException("The password is incorrect. Your account was not deleted.");
            }
            userDAO.delete(user.getId());
            Session.logout();
        } catch (SQLException ex) {
            throw new ServiceException("Could not delete the account.", ex);
        }
    }

    /** Where the database file is, for the privacy information. */
    public File getDatabaseFile() {
        return DBConnection.getDatabaseFile();
    }

    /** "+15 Average sleep ..." lines -> "Average sleep ... (+15); ..." (a leading + looks like a formula to Excel). */
    static String reasonsForCsv(String reasons) {
        if (reasons == null || reasons.trim().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : reasons.split("\n")) {
            String text = line.trim();
            int space = text.indexOf(' ');
            if (text.startsWith("+") && space > 1) {
                text = text.substring(space + 1) + " (" + text.substring(0, space) + ")";
            }
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(text);
        }
        return sb.toString();
    }

    private static File write(File folder, String name, String[] header, List<Object[]> rows) throws IOException {
        File file = new File(folder, name);
        CsvUtil.write(file, header, rows);
        return file;
    }

    private User currentUserFromDb() throws SQLException, ServiceException {
        User user = userDAO.findById(Session.getUserId());
        if (user == null) {
            throw new ServiceException("User not found.");
        }
        return user;
    }
}
