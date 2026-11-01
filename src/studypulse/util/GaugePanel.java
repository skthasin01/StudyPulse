package studypulse.util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;

/**
 * Circular gauge drawn with Graphics2D: a 270° arc filled up to value / max,
 * the value in the middle, a coloured caption (level) under it and a title at the bottom.
 * Used in forms through NetBeans "Custom Creation Code": new studypulse.util.GaugePanel()
 */
public class GaugePanel extends JPanel {

    private static final int START_ANGLE = 225;
    private static final int SWEEP = 270;

    private int value;
    private int max = 100;
    private String title = "";
    private String caption = "";
    private Color color = UITheme.ACCENT;

    public GaugePanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(200, 220));
    }

    /** Sets everything at once and repaints. */
    public void setGauge(int value, int max, String caption, Color color) {
        this.value = value;
        this.max = Math.max(1, max);
        this.caption = caption == null ? "" : caption;
        this.color = color == null ? UITheme.ACCENT : color;
        repaint();
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
        repaint();
    }

    public int getValue() {
        return value;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int titleHeight = 26;
        int stroke = 16;
        int size = Math.min(getWidth(), getHeight() - titleHeight) - stroke - 8;
        if (size <= 20) {
            g2.dispose();
            return;
        }
        int x = (getWidth() - size) / 2;
        int y = 4 + stroke / 2;

        g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(UITheme.TRACK);
        g2.drawArc(x, y, size, size, START_ANGLE, -SWEEP);
        double fraction = Math.max(0, Math.min(1.0, value / (double) max));
        if (fraction > 0) {
            g2.setColor(color);
            g2.drawArc(x, y, size, size, START_ANGLE, -(int) Math.round(SWEEP * fraction));
        }

        int cx = getWidth() / 2;
        int cy = y + size / 2;
        g2.setColor(UITheme.TEXT);
        g2.setFont(new Font(UITheme.FONT_NAME, Font.BOLD, Math.max(18, size / 4)));
        FontMetrics fm = g2.getFontMetrics();
        String number = String.valueOf(value);
        g2.drawString(number, cx - fm.stringWidth(number) / 2, cy + fm.getAscent() / 3);

        g2.setColor(color);
        g2.setFont(UITheme.FONT_HEADING);
        fm = g2.getFontMetrics();
        g2.drawString(caption, cx - fm.stringWidth(caption) / 2, cy + size / 4 + fm.getAscent() / 2);

        g2.setColor(UITheme.TEXT_MUTED);
        g2.setFont(UITheme.FONT_BOLD);
        fm = g2.getFontMetrics();
        g2.drawString(title, cx - fm.stringWidth(title) / 2, getHeight() - 8);
        g2.dispose();
    }
}
