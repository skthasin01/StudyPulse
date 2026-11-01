package studypulse.model;

/** Per-user app settings (table user_settings, one row per user). */
public class UserSettings {

    public static final int DEFAULT_FOCUS = 25;
    public static final int DEFAULT_SHORT_BREAK = 5;
    public static final int DEFAULT_LONG_BREAK = 15;

    private int id;
    private int userId;
    private int focusMinutes = DEFAULT_FOCUS;
    private int shortBreak = DEFAULT_SHORT_BREAK;
    private int longBreak = DEFAULT_LONG_BREAK;
    private boolean notificationsOn = true;
    private boolean trayOn = true;
    private AppTheme theme = AppTheme.LIGHT;

    public UserSettings() {
    }

    public UserSettings(int userId) {
        this.userId = userId;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getFocusMinutes() {
        return focusMinutes;
    }

    public void setFocusMinutes(int focusMinutes) {
        this.focusMinutes = focusMinutes;
    }

    public int getShortBreak() {
        return shortBreak;
    }

    public void setShortBreak(int shortBreak) {
        this.shortBreak = shortBreak;
    }

    public int getLongBreak() {
        return longBreak;
    }

    public void setLongBreak(int longBreak) {
        this.longBreak = longBreak;
    }

    /** Desktop toasts for notifications. */
    public boolean isNotificationsOn() {
        return notificationsOn;
    }

    public void setNotificationsOn(boolean notificationsOn) {
        this.notificationsOn = notificationsOn;
    }

    /** System tray messages (only used when notifications are on). */
    public boolean isTrayOn() {
        return trayOn;
    }

    public void setTrayOn(boolean trayOn) {
        this.trayOn = trayOn;
    }

    public AppTheme getTheme() {
        return theme;
    }

    public void setTheme(AppTheme theme) {
        this.theme = theme;
    }
}
