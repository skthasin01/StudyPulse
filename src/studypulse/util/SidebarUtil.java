package studypulse.util;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import studypulse.forms.LoginFrame;
import studypulse.forms.PomodoroFrame;

/**
 * Fills the pnlSidebar of a frame with the navigation menu.
 * Call in the frame constructor right after initComponents():
 * <pre>SidebarUtil.attach(this, pnlSidebar, SidebarUtil.TASKS);</pre>
 * Each menu item opens the JFrame Form named in ITEMS (package studypulse.forms)
 * and disposes the current frame. Frames that do not exist yet show a message,
 * so a new screen works in the menu as soon as its class is created.
 */
public final class SidebarUtil {

    public static final String DASHBOARD = "Dashboard";
    public static final String TASKS = "Tasks";
    public static final String STUDY_PLAN = "Study Plan";
    public static final String CHECKIN = "Daily Check-in";
    public static final String ANALYTICS = "Analytics";
    public static final String NOTIFICATIONS = "Notifications";
    public static final String POMODORO = "Pomodoro";
    public static final String SETTINGS = "Settings";
    public static final String LOGOUT = "Logout";

    /** Menu text and the JFrame Form class it opens. */
    private static final String[][] ITEMS = {
        {DASHBOARD, "DashboardFrame"},
        {TASKS, "TaskFrame"},
        {STUDY_PLAN, "StudyPlanFrame"},
        {CHECKIN, "CheckinFrame"},
        {ANALYTICS, "AnalyticsFrame"},
        {NOTIFICATIONS, "NotificationFrame"},
        {POMODORO, "PomodoroFrame"},
        {SETTINGS, "SettingsFrame"}
    };

    private static final String FORMS_PACKAGE = "studypulse.forms.";
    private static final Color ITEM_TEXT = new Color(0xC9, 0xD3, 0xDD);
    private static final Color ITEM_HOVER = new Color(0x2E, 0x3F, 0x52);

    private SidebarUtil() {
    }

    public static void attach(final JFrame current, JPanel pnlSidebar, String activeItem) {
        pnlSidebar.removeAll();
        pnlSidebar.setBackground(UITheme.SIDEBAR);
        pnlSidebar.setLayout(new BoxLayout(pnlSidebar, BoxLayout.Y_AXIS));
        pnlSidebar.setBorder(BorderFactory.createEmptyBorder(22, 0, 16, 0));

        JLabel lblApp = new JLabel("StudyPulse");
        lblApp.setForeground(UITheme.WHITE);
        lblApp.setFont(new Font(UITheme.FONT_NAME, Font.BOLD, 18));
        lblApp.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 0));
        lblApp.setAlignmentX(Component.LEFT_ALIGNMENT);
        pnlSidebar.add(lblApp);
        pnlSidebar.add(Box.createVerticalStrut(24));

        for (String[] item : ITEMS) {
            final String className = item[1];
            JButton button = createButton(item[0], item[0].equals(activeItem));
            button.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    open(current, className);
                }
            });
            pnlSidebar.add(button);
            pnlSidebar.add(Box.createVerticalStrut(2));
        }

        pnlSidebar.add(Box.createVerticalGlue());
        JButton btnLogout = createButton(LOGOUT, false);
        btnLogout.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                logout(current);
            }
        });
        pnlSidebar.add(btnLogout);

        pnlSidebar.revalidate();
        pnlSidebar.repaint();
    }

    private static JButton createButton(String text, boolean active) {
        JButton button = new JButton(text);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        button.setPreferredSize(new Dimension(160, 40));
        button.setFont(active ? UITheme.FONT_BOLD : UITheme.FONT_NORMAL);
        button.setForeground(active ? UITheme.WHITE : ITEM_TEXT);
        button.setBackground(active ? UITheme.ACCENT : UITheme.SIDEBAR);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(8, 18, 8, 10));
        // FlatLaf: square corners, no border, hover colours
        button.putClientProperty("FlatLaf.style", "arc: 0; borderWidth: 0; focusWidth: 0; innerFocusWidth: 0; "
                + "hoverBackground: " + toHex(active ? UITheme.ACCENT : ITEM_HOVER) + "; "
                + "pressedBackground: " + toHex(UITheme.ACCENT));
        return button;
    }

    /**
     * Opens the screen of a menu item (e.g. SidebarUtil.NOTIFICATIONS) like the sidebar does,
     * for buttons outside the sidebar (dashboard bell, "Start Pomodoro").
     */
    public static void navigate(JFrame current, String item) {
        for (String[] entry : ITEMS) {
            if (entry[0].equals(item)) {
                open(current, entry[1]);
                return;
            }
        }
    }

    /** Opens studypulse.forms.&lt;className&gt; and disposes the current frame. */
    private static void open(JFrame current, String className) {
        if (current.getClass().getSimpleName().equals(className)) {
            return;
        }
        if ("PomodoroFrame".equals(className)) {
            // small helper window: opens on top, the current screen stays open
            PomodoroFrame.showWindow(null);
            return;
        }
        try {
            Class<?> frameClass = Class.forName(FORMS_PACKAGE + className);
            JFrame next = (JFrame) frameClass.newInstance();
            next.setVisible(true);
            current.dispose();
        } catch (ClassNotFoundException ex) {
            JOptionPane.showMessageDialog(current, "This screen is not available.", "StudyPulse",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            ErrorLog.log("Could not open " + className, ex);
            JOptionPane.showMessageDialog(current, "Could not open the screen.\n" + ErrorLog.SEE_LOG,
                    "StudyPulse", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void logout(JFrame current) {
        int answer = JOptionPane.showConfirmDialog(current, "Do you want to log out?", "StudyPulse",
                JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        String email = Session.isLoggedIn() ? Session.getUser().getEmail() : "";
        PomodoroFrame.closeWindow();
        DesktopAlerts.stop();
        Session.logout();
        new LoginFrame(email).setVisible(true);
        current.dispose();
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
