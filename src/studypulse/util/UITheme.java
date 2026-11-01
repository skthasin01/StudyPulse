package studypulse.util;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.table.JTableHeader;
import studypulse.model.DeadlineState;
import studypulse.model.Difficulty;
import studypulse.model.LoadLevel;
import studypulse.model.NotificationType;
import studypulse.model.RiskLevel;

/**
 * Colours, fonts and styling helpers shared by every screen.
 * Accent / status colours are fixed. Surface, text and row colours (non-final fields below)
 * change with {@link #switchTheme}; always read them at use time, never copy them into constants.
 */
public final class UITheme {

    public static final Color ACCENT = new Color(0x2E, 0x7D, 0x9A);       // #2E7D9A
    public static final Color SUCCESS = new Color(0x2E, 0x9E, 0x5B);      // #2E9E5B
    public static final Color WARNING = new Color(0xE0, 0xA1, 0x00);      // #E0A100
    public static final Color DANGER = new Color(0xD6, 0x45, 0x45);       // #D64545
    public static final Color ORANGE_RED = new Color(0xE8, 0x69, 0x2E);   // #E8692E (Heavy / High)
    public static final Color PURPLE = new Color(0x7B, 0x5C, 0xC4);       // #7B5CC4 (Deadline)
    public static final Color TEAL = new Color(0x1A, 0x9E, 0x9E);         // #1A9E9E (Break)
    public static final Color TOAST_BACKGROUND = new Color(0x22, 0x30, 0x3F);
    /** Pure white, for text on accent / coloured buttons. Use SURFACE for white backgrounds. */
    public static final Color WHITE = Color.WHITE;

    // ---- light values (also the colours written into the .form files) ----
    private static final Color LIGHT_SIDEBAR = new Color(0x22, 0x30, 0x3F);      // #22303F
    private static final Color LIGHT_BACKGROUND = new Color(0xF4, 0xF6, 0xF9);   // #F4F6F9
    private static final Color LIGHT_SURFACE = Color.WHITE;
    private static final Color LIGHT_TEXT = new Color(0x22, 0x30, 0x3F);
    private static final Color LIGHT_TEXT_MUTED = new Color(0x6B, 0x77, 0x85);
    // ---- dark values ----
    private static final Color DARK_SIDEBAR = new Color(0x16, 0x1C, 0x23);
    private static final Color DARK_BACKGROUND = new Color(0x1E, 0x22, 0x27);
    private static final Color DARK_SURFACE = new Color(0x2B, 0x31, 0x38);
    private static final Color DARK_TEXT = new Color(0xE4, 0xE9, 0xEF);
    private static final Color DARK_TEXT_MUTED = new Color(0x9A, 0xA7, 0xB4);

    /** Theme colours (change with switchTheme). */
    public static Color SIDEBAR;
    public static Color BACKGROUND;
    /** Cards, panels and table rows. */
    public static Color SURFACE;
    public static Color TEXT;
    public static Color TEXT_MUTED;
    public static Color GRID;
    public static Color HEADER_BACKGROUND;
    /** Gauge track, empty bars. */
    public static Color TRACK;
    /** Table row backgrounds. */
    public static Color ROW_OVERDUE;
    public static Color ROW_DUE_SOON;
    public static Color ROW_DONE;
    public static Color ROW_UNREAD;
    public static Color ROW_SKIPPED;
    public static Color REST_DAY_BACKGROUND;
    public static Color TODAY_BACKGROUND;

    private static boolean dark;

    public static final String FONT_NAME = "Segoe UI";
    public static final Font FONT_NORMAL = new Font(FONT_NAME, Font.PLAIN, 13);
    public static final Font FONT_BOLD = new Font(FONT_NAME, Font.BOLD, 13);
    public static final Font FONT_HEADING = new Font(FONT_NAME, Font.BOLD, 16);
    public static final Font FONT_TITLE = new Font(FONT_NAME, Font.BOLD, 22);

    public static final int TABLE_ROW_HEIGHT = 28;

    private UITheme() {
    }

    public static boolean isDark() {
        return dark;
    }

    // {light, dark} pairs of the other theme backgrounds (rows, boxes, headers)
    private static final Color[] PAIR_GRID = {new Color(0xE1, 0xE5, 0xEB), new Color(0x3A, 0x42, 0x4B)};
    private static final Color[] PAIR_HEADER = {new Color(0xF7, 0xF8, 0xFA), new Color(0x25, 0x2B, 0x31)};
    private static final Color[] PAIR_TRACK = {new Color(0xE6, 0xEA, 0xF0), new Color(0x3A, 0x42, 0x4B)};
    private static final Color[] PAIR_OVERDUE = {new Color(0xFD, 0xE2, 0xE2), new Color(0x55, 0x2C, 0x30)};
    private static final Color[] PAIR_DUE_SOON = {new Color(0xFF, 0xF4, 0xCC), new Color(0x52, 0x46, 0x1E)};
    private static final Color[] PAIR_DONE = {new Color(0xDF, 0xF3, 0xE6), new Color(0x22, 0x45, 0x30)};
    private static final Color[] PAIR_UNREAD = {new Color(0xEE, 0xF6, 0xF9), new Color(0x1F, 0x3A, 0x46)};
    private static final Color[] PAIR_SKIPPED = {new Color(0xEC, 0xEF, 0xF3), new Color(0x34, 0x3B, 0x44)};
    private static final Color[] PAIR_TODAY = {new Color(0xEA, 0xF4, 0xF8), new Color(0x22, 0x39, 0x47)};

    /** Backgrounds that recolor() swaps between light and dark. */
    private static final Color[][] BACKGROUND_PAIRS = {
        {LIGHT_BACKGROUND, DARK_BACKGROUND}, {LIGHT_SURFACE, DARK_SURFACE}, {LIGHT_SIDEBAR, DARK_SIDEBAR},
        PAIR_HEADER, PAIR_OVERDUE, PAIR_DUE_SOON, PAIR_DONE, PAIR_UNREAD, PAIR_SKIPPED, PAIR_TODAY
    };
    /** Foregrounds that recolor() swaps between light and dark. */
    private static final Color[][] FOREGROUND_PAIRS = {
        {LIGHT_TEXT, DARK_TEXT}, {LIGHT_TEXT_MUTED, DARK_TEXT_MUTED}
    };

    static {
        setColors(false);   // after the colour pairs above, so they are set
    }

    private static void setColors(boolean useDark) {
        dark = useDark;
        int i = useDark ? 1 : 0;
        SIDEBAR = useDark ? DARK_SIDEBAR : LIGHT_SIDEBAR;
        BACKGROUND = useDark ? DARK_BACKGROUND : LIGHT_BACKGROUND;
        SURFACE = useDark ? DARK_SURFACE : LIGHT_SURFACE;
        TEXT = useDark ? DARK_TEXT : LIGHT_TEXT;
        TEXT_MUTED = useDark ? DARK_TEXT_MUTED : LIGHT_TEXT_MUTED;
        GRID = PAIR_GRID[i];
        HEADER_BACKGROUND = PAIR_HEADER[i];
        TRACK = PAIR_TRACK[i];
        ROW_OVERDUE = PAIR_OVERDUE[i];       // red tint
        ROW_DUE_SOON = PAIR_DUE_SOON[i];     // yellow tint
        ROW_DONE = PAIR_DONE[i];             // green tint
        ROW_UNREAD = PAIR_UNREAD[i];         // accent tint
        ROW_SKIPPED = PAIR_SKIPPED[i];
        REST_DAY_BACKGROUND = ROW_SKIPPED;
        TODAY_BACKGROUND = PAIR_TODAY[i];
    }

    /**
     * Switches FlatLaf (FlatLightLaf / FlatDarkLaf) and the theme colours, then updates every
     * open window at once: FlatLaf.updateUI() and {@link #recolor} for colours set in forms.
     */
    public static void switchTheme(boolean useDark) {
        setColors(useDark);
        if (useDark) {
            com.formdev.flatlaf.FlatDarkLaf.setup();
        } else {
            com.formdev.flatlaf.FlatLightLaf.setup();
        }
        com.formdev.flatlaf.FlatLaf.updateUI();
        for (java.awt.Window w : java.awt.Window.getWindows()) {
            if (w instanceof JFrame) {
                ((JFrame) w).getContentPane().setBackground(BACKGROUND);
            }
            recolor(w);
            w.repaint();
        }
    }

    /**
     * Replaces the theme colours that forms set directly (e.g. title labels 34,48,63, white
     * card panels) by the colours of the current theme, for the whole component tree.
     * Colours that come from the look and feel (UIResource) are left alone.
     */
    public static void recolor(Component c) {
        Color bg = c.getBackground();
        if (bg != null && !(bg instanceof javax.swing.plaf.UIResource)) {
            Color mapped = map(bg, true);
            if (mapped != null) {
                c.setBackground(mapped);
            }
        }
        Color fg = c.getForeground();
        if (fg != null && !(fg instanceof javax.swing.plaf.UIResource)) {
            Color mapped = map(fg, false);
            if (mapped != null) {
                c.setForeground(mapped);
            }
        }
        if (c instanceof JTable) {
            ((JTable) c).setGridColor(GRID);
        }
        if (c instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) c).getComponents()) {
                recolor(child);
            }
        }
    }

    /** Light colour -> current theme colour (or dark -> light when switching back); null = keep. */
    private static Color map(Color c, boolean background) {
        for (Color[] pair : background ? BACKGROUND_PAIRS : FOREGROUND_PAIRS) {
            Color from = dark ? pair[0] : pair[1];
            if (from.getRGB() == c.getRGB()) {
                return dark ? pair[1] : pair[0];
            }
        }
        return null;
    }

    /** Easy green, Medium amber, Hard red. */
    public static Color difficultyColor(Difficulty difficulty) {
        if (difficulty == Difficulty.EASY) {
            return SUCCESS;
        }
        return difficulty == Difficulty.HARD ? DANGER : WARNING;
    }

    /** Workload orange, Burnout red, Deadline purple, Break teal, Info grey. */
    public static Color notificationColor(NotificationType type) {
        switch (type) {
            case WORKLOAD:
                return ORANGE_RED;
            case BURNOUT:
                return DANGER;
            case DEADLINE:
                return PURPLE;
            case BREAK:
                return TEAL;
            default:
                return TEXT_MUTED;
        }
    }

    /** Light green, Balanced amber, Heavy orange-red, Overloaded red. */
    public static Color loadColor(LoadLevel level) {
        switch (level) {
            case LIGHT:
                return SUCCESS;
            case BALANCED:
                return WARNING;
            case HEAVY:
                return ORANGE_RED;
            default:
                return DANGER;
        }
    }

    /** Low green, Moderate amber, High orange-red, Severe red. */
    public static Color riskColor(RiskLevel level) {
        switch (level) {
            case LOW:
                return SUCCESS;
            case MODERATE:
                return WARNING;
            case HIGH:
                return ORANGE_RED;
            default:
                return DANGER;
        }
    }

    /** Row background for a task's deadline state. */
    public static Color deadlineColor(DeadlineState state) {
        switch (state) {
            case OVERDUE:
                return ROW_OVERDUE;
            case DUE_SOON:
                return ROW_DUE_SOON;
            case DONE:
                return ROW_DONE;
            default:
                return SURFACE;
        }
    }

    /** Toggle button (rating scale): accent background with white text when selected. */
    public static void styleToggle(JToggleButton toggle) {
        toggle.setFocusPainted(false);
        toggle.setMargin(new Insets(4, 2, 4, 2));
        toggle.putClientProperty("JComponent.minimumWidth", 0);
        toggle.putClientProperty("FlatLaf.style", "selectedBackground: " + hex(ACCENT)
                + "; selectedForeground: #ffffff; arc: 8");
    }

    /** "#2e7d9a" for FlatLaf style strings. */
    public static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    /** Dashboard summary card: white with a 3 px coloured top border. */
    public static void styleSummaryCard(JPanel card, Color topColor) {
        card.setBackground(SURFACE);
        card.setBorder(BorderFactory.createMatteBorder(3, 0, 0, 0, topColor));
    }

    /** Bell icon (28x28) with a red badge for the unread count (none when 0, "9+" above 9). */
    public static Icon bellIcon(final int unread) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int cx = x + 12;
                g2.setColor(TEXT);
                java.awt.geom.GeneralPath bell = new java.awt.geom.GeneralPath();
                bell.moveTo(cx - 8, y + 19);
                bell.lineTo(cx - 7, y + 12);
                bell.curveTo(cx - 7, y + 5, cx + 7, y + 5, cx + 7, y + 12);
                bell.lineTo(cx + 8, y + 19);
                bell.lineTo(cx + 10, y + 21);
                bell.lineTo(cx - 10, y + 21);
                bell.closePath();
                g2.fill(bell);
                g2.fillOval(cx - 2, y + 4, 4, 4);
                g2.fillOval(cx - 3, y + 21, 6, 5);
                if (unread > 0) {
                    String text = unread > 9 ? "9+" : String.valueOf(unread);
                    g2.setColor(DANGER);
                    g2.fillOval(x + 13, y, 15, 15);
                    g2.setColor(WHITE);
                    g2.setFont(new Font(FONT_NAME, Font.BOLD, 10));
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(text, x + 13 + (15 - fm.stringWidth(text)) / 2,
                            y + (15 - fm.getHeight()) / 2 + fm.getAscent());
                }
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return 28;
            }

            @Override
            public int getIconHeight() {
                return 28;
            }
        };
    }

    /** White card look for a form panel. */
    public static void styleCard(JPanel panel) {
        panel.setBackground(SURFACE);
        panel.putClientProperty("FlatLaf.style", "arc: 12");
    }

    /** Theme background for the frame and theme colours for everything the form set (see recolor). */
    public static void applyFrame(JFrame frame) {
        frame.getContentPane().setBackground(BACKGROUND);
        recolor(frame.getContentPane());
    }

    /** Filled accent button for the main action of a screen. */
    public static void stylePrimaryButton(JButton button) {
        button.setBackground(ACCENT);
        button.setForeground(WHITE);
        button.setFont(FONT_BOLD);
        button.setFocusPainted(false);
    }

    /** Filled red button for delete / logout actions. */
    public static void styleDangerButton(JButton button) {
        button.setBackground(DANGER);
        button.setForeground(WHITE);
        button.setFont(FONT_BOLD);
        button.setFocusPainted(false);
    }

    /** Borderless button that looks like a hyperlink (accent text, hand cursor). */
    public static void styleLinkButton(JButton button) {
        button.putClientProperty("JButton.buttonType", "borderless");
        button.setForeground(ACCENT);
        button.setFont(FONT_BOLD);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /** Red error label, hidden until showError is called. */
    public static void styleErrorLabel(JLabel label) {
        label.setForeground(DANGER);
        label.setFont(FONT_NORMAL);
        label.setText(" ");
        label.setVisible(false);
    }

    public static void showError(JLabel label, String message) {
        label.setText(message);
        label.setVisible(true);
    }

    public static void hideError(JLabel label) {
        label.setText(" ");
        label.setVisible(false);
    }

    /** Filled circle with centred text, e.g. the "SP" logo. */
    public static Icon circleBadge(final String text, final int size, final Color background) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(background);
                g2.fillOval(x, y, size, size);
                g2.setColor(WHITE);
                g2.setFont(new Font(FONT_NAME, Font.BOLD, size * 2 / 5));
                FontMetrics fm = g2.getFontMetrics();
                int tx = x + (size - fm.stringWidth(text)) / 2;
                int ty = y + (size - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(text, tx, ty);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /**
     * Row height 28, bold header, accent selection colour, and a narrow,
     * centred ID column (column 0, see TableUtil).
     */
    public static void styleTable(JTable table) {
        table.setFont(FONT_NORMAL);
        table.setRowHeight(TABLE_ROW_HEIGHT);
        table.setSelectionBackground(ACCENT);
        table.setSelectionForeground(WHITE);
        table.setGridColor(GRID);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);

        JTableHeader header = table.getTableHeader();
        header.setFont(FONT_BOLD);
        header.setReorderingAllowed(false);

        TableUtil.configureIdColumn(table);
    }

    /** Same look as styleTable, for tables that are not database rows (no ID column). */
    public static void styleTableWithoutId(JTable table) {
        table.setFont(FONT_NORMAL);
        table.setRowHeight(TABLE_ROW_HEIGHT);
        table.setSelectionBackground(ACCENT);
        table.setSelectionForeground(WHITE);
        table.setGridColor(GRID);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setFont(FONT_BOLD);
        table.getTableHeader().setReorderingAllowed(false);
    }
}
