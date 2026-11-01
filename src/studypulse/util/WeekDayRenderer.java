package studypulse.util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import studypulse.model.DayPlan;
import studypulse.model.PlanBlockItem;
import studypulse.model.PlanStatus;

/**
 * Paints one day of the study plan week grid (cell value = DayPlan): a coloured box per
 * plan block ("CN Mid-term – 90 m"), Easy green / Medium amber / Hard red.
 * DONE and SKIPPED blocks are struck through; the rest day is shaded.
 */
public class WeekDayRenderer extends JComponent implements TableCellRenderer {

    private static final Color SKIPPED_COLOR = new Color(0x9A, 0xA4, 0xAF);
    private static final int BOX_HEIGHT = 26;
    private static final int BOX_GAP = 5;
    private static final int PADDING = 6;

    private DayPlan day;
    private boolean selected;

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
            boolean hasFocus, int row, int column) {
        day = value instanceof DayPlan ? (DayPlan) value : null;
        selected = isSelected;
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        Color bg = UITheme.SURFACE;
        if (day != null && day.isRestDay()) {
            bg = UITheme.REST_DAY_BACKGROUND;
        } else if (day != null && day.getDate().equals(DateUtil.today())) {
            bg = UITheme.TODAY_BACKGROUND;
        }
        g2.setColor(bg);
        g2.fillRect(0, 0, w, h);
        if (day == null) {
            g2.dispose();
            return;
        }

        int y = PADDING + 2;
        if (day.isRestDay()) {
            g2.setFont(UITheme.FONT_BOLD);
            g2.setColor(UITheme.TEXT_MUTED);
            drawCentered(g2, "Rest day", y + g2.getFontMetrics().getAscent());
            y += 24;
        } else if (day.getItems().isEmpty()) {
            g2.setFont(UITheme.FONT_NORMAL.deriveFont(12f));
            g2.setColor(UITheme.TEXT_MUTED);
            drawCentered(g2, day.getFreeMinutes() == 0 ? "No free time" : "Nothing planned",
                    y + g2.getFontMetrics().getAscent());
        }

        Font font = UITheme.FONT_NORMAL.deriveFont(12f);
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        int count = day.getItems().size();
        for (int i = 0; i < count; i++) {
            boolean lastFits = y + BOX_HEIGHT <= h - PADDING;
            boolean roomForMore = y + 2 * BOX_HEIGHT + BOX_GAP <= h - PADDING;
            if (!lastFits || (i < count - 1 && !roomForMore)) {
                g2.setColor(UITheme.TEXT_MUTED);
                g2.drawString("+" + (count - i) + " more", PADDING + 4, y + fm.getAscent());
                break;
            }
            paintBox(g2, day.getItems().get(i), y, w, fm);
            y += BOX_HEIGHT + BOX_GAP;
        }

        if (selected) {
            g2.setColor(UITheme.ACCENT);
            g2.setStroke(new BasicStroke(2f));
            g2.drawRect(1, 1, w - 2, h - 2);
        }
        g2.dispose();
    }

    private void paintBox(Graphics2D g2, PlanBlockItem item, int y, int w, FontMetrics fm) {
        PlanStatus status = item.getBlock().getStatus();
        Color color = status == PlanStatus.SKIPPED || item.getDifficulty() == null
                ? SKIPPED_COLOR : UITheme.difficultyColor(item.getDifficulty());
        int x = PADDING;
        int bw = w - 2 * PADDING;
        g2.setColor(mix(color, UITheme.SURFACE, status == PlanStatus.PLANNED ? (UITheme.isDark() ? 0.30f : 0.16f) : 0.08f));
        g2.fillRoundRect(x, y, bw, BOX_HEIGHT, 8, 8);
        g2.setColor(color);
        g2.fillRoundRect(x, y, 4, BOX_HEIGHT, 4, 4);

        String suffix = " – " + item.getBlock().getPlannedMinutes() + " m";
        int available = bw - 14;
        String title = fit(item.getTaskTitle(), fm, available - fm.stringWidth(suffix));
        String text = title + suffix;
        int tx = x + 10;
        int ty = y + (BOX_HEIGHT - fm.getHeight()) / 2 + fm.getAscent();
        g2.setColor(status == PlanStatus.PLANNED ? UITheme.TEXT : UITheme.TEXT_MUTED);
        g2.drawString(text, tx, ty);
        if (status != PlanStatus.PLANNED) {
            int lineY = ty - fm.getAscent() / 3;
            g2.drawLine(tx, lineY, tx + fm.stringWidth(text), lineY);
        }
    }

    private void drawCentered(Graphics2D g2, String text, int baseline) {
        int x = (getWidth() - g2.getFontMetrics().stringWidth(text)) / 2;
        g2.drawString(text, x, baseline);
    }

    /** Cuts the text and adds "…" so it fits in maxWidth. */
    private static String fit(String text, FontMetrics fm, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int end = text.length();
        while (end > 1 && fm.stringWidth(text.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return text.substring(0, end).trim() + ellipsis;
    }

    private static Color mix(Color color, Color base, float amount) {
        int r = Math.round(color.getRed() * amount + base.getRed() * (1 - amount));
        int g = Math.round(color.getGreen() * amount + base.getGreen() * (1 - amount));
        int b = Math.round(color.getBlue() * amount + base.getBlue() * (1 - amount));
        return new Color(r, g, b);
    }
}
