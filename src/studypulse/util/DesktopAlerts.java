package studypulse.util;

import java.awt.AWTException;
import java.awt.Graphics2D;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Timer;
import studypulse.forms.ToastFrame;
import studypulse.model.Notification;
import studypulse.model.NotificationType;
import studypulse.model.UserSettings;
import studypulse.service.NotificationService;
import studypulse.service.ServiceException;
import studypulse.service.SettingsService;

/**
 * Desktop alerts while the app is open: runs NotificationService checks once right after login
 * and then every 15 minutes (javax.swing.Timer), shows new unread notifications as ToastFrames
 * and, if supported and allowed (user_settings.tray_on), as system tray messages.
 * user_settings.notifications_on = 0 turns toasts and tray messages off.
 * Listeners (e.g. the dashboard bell) are told when new notifications arrived.
 */
public final class DesktopAlerts {

    public static final int CHECK_INTERVAL_MS = 15 * 60 * 1000;
    private static final int FIRST_CHECK_DELAY_MS = 2000;
    /** More new notifications than this at once are shown as one summary toast. */
    private static final int MAX_TOASTS = 3;

    private static final NotificationService SERVICE = new NotificationService();
    private static final SettingsService SETTINGS = new SettingsService();
    private static final List<Runnable> LISTENERS = new ArrayList<Runnable>();
    private static Timer timer;
    private static TrayIcon trayIcon;
    /** Highest notification id already shown as an alert. */
    private static int lastShownId;

    private DesktopAlerts() {
    }

    /** Call after login: first check after 2 seconds, then every 15 minutes. */
    public static void start() {
        stop();
        lastShownId = 0;
        timer = new Timer(CHECK_INTERVAL_MS, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runChecks();
            }
        });
        timer.setInitialDelay(FIRST_CHECK_DELAY_MS);
        timer.start();
    }

    /** Call on logout. */
    public static void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        if (trayIcon != null && SystemTray.isSupported()) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
        }
    }

    /** Runs the checks now and alerts about new unread notifications of today. */
    public static void runChecks() {
        if (!Session.isLoggedIn()) {
            return;
        }
        int userId = Session.getUserId();
        try {
            SERVICE.runChecks(userId);
            List<Notification> fresh = SERVICE.getNewUnread(userId, lastShownId);
            alert(fresh);
        } catch (ServiceException ex) {
            // details are already in data/error.log (ServiceException)
        }
    }

    /** Shows one notification (e.g. the Pomodoro break reminder). */
    public static void alert(Notification n) {
        if (n != null) {
            List<Notification> one = new ArrayList<Notification>();
            one.add(n);
            alert(one);
        }
    }

    /** A plain toast that is not stored as a notification (e.g. "Focus round finished"). */
    public static void toast(String title, String message) {
        ToastFrame.showToast(title, message, UITheme.ACCENT);
    }

    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }

    private static void alert(List<Notification> fresh) {
        if (fresh.isEmpty()) {
            return;
        }
        for (Notification n : fresh) {
            lastShownId = Math.max(lastShownId, n.getId());
        }
        UserSettings settings = currentSettings();
        // user_settings.notifications_on = 0: the notifications are still stored, just not shown
        if (settings.isNotificationsOn()) {
            if (fresh.size() <= MAX_TOASTS) {
                for (Notification n : fresh) {
                    ToastFrame.showToast(n.getType().getLabel(), n.getMessage(), UITheme.notificationColor(n.getType()));
                }
            } else {
                ToastFrame.showToast("Notifications", "You have " + fresh.size() + " new notifications.", UITheme.ACCENT);
            }
            if (settings.isTrayOn()) {
                Notification latest = fresh.get(fresh.size() - 1);
                trayMessage(fresh.size() == 1 ? latest.getType().getLabel() : "StudyPulse",
                        fresh.size() == 1 ? latest.getMessage() : fresh.size() + " new notifications",
                        latest.getType() == NotificationType.BURNOUT || latest.getType() == NotificationType.WORKLOAD);
            }
        }
        for (Runnable r : new ArrayList<Runnable>(LISTENERS)) {
            r.run();
        }
    }

    /** The logged-in user's settings; defaults if they cannot be read. */
    private static UserSettings currentSettings() {
        try {
            return SETTINGS.getSettings();
        } catch (ServiceException ex) {
            return new UserSettings(Session.getUserId());
        }
    }

    private static void trayMessage(String title, String message, boolean warning) {
        if (!Session.isLoggedIn() || !SystemTray.isSupported()) {
            return;
        }
        try {
            if (trayIcon == null) {
                BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = image.createGraphics();
                UITheme.circleBadge("S", 16, UITheme.ACCENT).paintIcon(null, g, 0, 0);
                g.dispose();
                trayIcon = new TrayIcon(image, "StudyPulse");
                trayIcon.setImageAutoSize(true);
                SystemTray.getSystemTray().add(trayIcon);
            }
            trayIcon.displayMessage(title, message, warning ? TrayIcon.MessageType.WARNING : TrayIcon.MessageType.INFO);
        } catch (AWTException | RuntimeException ex) {
            ErrorLog.log("Tray message not shown", ex);
        }
    }
}
