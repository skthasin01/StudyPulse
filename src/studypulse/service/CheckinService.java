package studypulse.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import studypulse.dao.CheckinDAO;
import studypulse.dao.NotificationDAO;
import studypulse.model.Checkin;
import studypulse.model.Notification;
import studypulse.model.NotificationType;
import studypulse.model.User;
import studypulse.util.DateUtil;
import studypulse.util.Session;

/** Daily stress / energy / mood / sleep check-in (one per user per day). */
public class CheckinService {

    public static final String[] MOOD_LABELS = {"Very bad", "Bad", "Okay", "Good", "Great"};
    public static final int HISTORY_DAYS = 14;
    public static final int MAX_NOTE_LENGTH = 120;
    public static final double MAX_SLEEP_HOURS = 14.0;
    /** Values shown in red in the history table. */
    public static final int HIGH_STRESS = 4;
    public static final int LOW_ENERGY = 2;
    public static final double LOW_SLEEP_HOURS = 6.0;

    static final String REMINDER_MESSAGE = "You have not done today's check-in yet. It takes about one minute.";

    private final CheckinDAO checkinDAO = new CheckinDAO();
    private final RiskService riskService = new RiskService();
    private final NotificationDAO notificationDAO = new NotificationDAO();

    /** users.wellbeing_enabled of the logged-in user. */
    public boolean isTrackingEnabled() {
        User user = Session.getUser();
        return user != null && user.isWellbeingEnabled();
    }

    /** Today's check-in or null. */
    public Checkin getToday() throws ServiceException {
        try {
            return checkinDAO.findByUserAndDate(Session.getUserId(), DateUtil.today());
        } catch (SQLException ex) {
            throw new ServiceException("Could not load today's check-in.", ex);
        }
    }

    /**
     * Saves today's check-in (insert, or update if one exists).
     * Levels are 1-5; 0 means "not selected". Returns true if it was new.
     */
    public boolean saveToday(int stress, int energy, int mood, double sleepHours, String note)
            throws ServiceException {
        if (!isTrackingEnabled()) {
            throw new ServiceException("Well-being tracking is turned off. You can turn it on in Settings.");
        }
        if (!isLevel(stress)) {
            throw new ServiceException("Please choose your stress level (1-5).");
        }
        if (!isLevel(energy)) {
            throw new ServiceException("Please choose your energy level (1-5).");
        }
        if (!isLevel(mood)) {
            throw new ServiceException("Please choose your mood.");
        }
        if (sleepHours < 0 || sleepHours > MAX_SLEEP_HOURS || Math.round(sleepHours * 2) != sleepHours * 2) {
            throw new ServiceException("Sleep must be between 0 and 14 hours in steps of 0.5.");
        }
        String text = note == null ? "" : note.trim();
        if (text.length() > MAX_NOTE_LENGTH) {
            throw new ServiceException("The note can have at most " + MAX_NOTE_LENGTH + " characters.");
        }

        Checkin c = new Checkin();
        c.setUserId(Session.getUserId());
        c.setCheckinDate(DateUtil.today());
        c.setStressLevel(stress);
        c.setEnergyLevel(energy);
        c.setMoodLevel(mood);
        c.setSleepHours(sleepHours);
        c.setNote(text.isEmpty() ? null : text);
        try {
            boolean isNew = checkinDAO.findByUserAndDate(c.getUserId(), c.getCheckinDate()) == null;
            checkinDAO.saveForDay(c);
            riskService.refreshQuietly();
            return isNew;
        } catch (SQLException ex) {
            throw new ServiceException("Could not save the check-in.", ex);
        }
    }

    /** The last 14 days (today included), newest first. */
    public List<Checkin> getHistory() throws ServiceException {
        LocalDate today = DateUtil.today();
        try {
            return checkinDAO.findByUserBetween(Session.getUserId(), today.minusDays(HISTORY_DAYS - 1), today);
        } catch (SQLException ex) {
            throw new ServiceException("Could not load the check-in history.", ex);
        }
    }

    public int getStreak() throws ServiceException {
        LocalDate today = DateUtil.today();
        try {
            return countStreak(checkinDAO.findDatesUpTo(Session.getUserId(), today), today);
        } catch (SQLException ex) {
            throw new ServiceException("Could not load the streak.", ex);
        }
    }

    /**
     * Consecutive check-in days ending today. If today has no check-in yet, the streak
     * ending yesterday still counts (it is only broken when a whole day is missed).
     *
     * @param datesNewestFirst check-in dates up to today, newest first, no duplicates
     */
    public static int countStreak(List<LocalDate> datesNewestFirst, LocalDate today) {
        if (datesNewestFirst.isEmpty()) {
            return 0;
        }
        LocalDate expected = datesNewestFirst.get(0).equals(today) ? today : today.minusDays(1);
        int streak = 0;
        for (LocalDate date : datesNewestFirst) {
            if (!date.equals(expected)) {
                break;
            }
            streak++;
            expected = expected.minusDays(1);
        }
        return streak;
    }

    /**
     * True once per day when tracking is on and today has no check-in yet.
     * The reminder is stored as an INFO notification, so it is not shown again today.
     */
    public boolean takeDailyReminder() {
        if (!isTrackingEnabled()) {
            return false;
        }
        int userId = Session.getUserId();
        try {
            if (checkinDAO.findByUserAndDate(userId, DateUtil.today()) != null
                    || notificationDAO.existsSince(userId, NotificationType.INFO, REMINDER_MESSAGE,
                            DateUtil.today().atStartOfDay())) {
                return false;
            }
            notificationDAO.insert(new Notification(userId, NotificationType.INFO, REMINDER_MESSAGE));
            return true;
        } catch (SQLException ex) {
            return false;
        }
    }

    /** "Very bad" ... "Great" for 1-5. */
    public static String moodLabel(int mood) {
        return isLevel(mood) ? MOOD_LABELS[mood - 1] : "";
    }

    private static boolean isLevel(int value) {
        return value >= 1 && value <= 5;
    }
}
