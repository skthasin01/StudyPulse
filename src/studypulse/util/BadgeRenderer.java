package studypulse.util;

import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JTable;

/**
 * Shows a cell value as a small coloured pill (e.g. difficulty Easy / Medium / Hard),
 * on top of the row colour of RowColorRenderer.
 */
public class BadgeRenderer extends RowColorRenderer {

    /** Colour of the badge for a cell value. */
    public interface BadgeColorProvider {
        Color badgeColor(Object value);
    }

    private final BadgeColorProvider badgeColors;
    private Color badgeColor = UITheme.ACCENT;
    private Color cellBackground = UITheme.SURFACE;
    private String text = "";

    public BadgeRenderer(RowColorProvider rowColors, BadgeColorProvider badgeColors) {
        super(rowColors);
        this.badgeColors = badgeColors;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
            boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        text = value == null ? "" : value.toString();
        badgeColor = value == null ? UITheme.TEXT_MUTED : badgeColors.badgeColor(value);
        cellBackground = backgroundFor(table, row, isSelected);
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(cellBackground);
        g2.fillRect(0, 0, getWidth(), getHeight());
        if (!text.isEmpty()) {
            g2.setFont(UITheme.FONT_BOLD.deriveFont(12f));
            FontMetrics fm = g2.getFontMetrics();
            int w = fm.stringWidth(text) + 14;
            int h = 20;
            int x = 4;
            int y = (getHeight() - h) / 2;
            g2.setColor(UITheme.SURFACE);
            g2.fillRoundRect(x, y, w, h, h, h);
            g2.setColor(mix(badgeColor, UITheme.SURFACE, UITheme.isDark() ? 0.30f : 0.18f));
            g2.fillRoundRect(x, y, w, h, h, h);
            g2.setColor(badgeColor);
            g2.drawRoundRect(x, y, w - 1, h - 1, h, h);
            g2.drawString(text, x + 7, y + (h - fm.getHeight()) / 2 + fm.getAscent());
        }
        g2.dispose();
    }

    /** amount of 'color' over 'base' (0..1). */
    private static Color mix(Color color, Color base, float amount) {
        int r = Math.round(color.getRed() * amount + base.getRed() * (1 - amount));
        int g = Math.round(color.getGreen() * amount + base.getGreen() * (1 - amount));
        int b = Math.round(color.getBlue() * amount + base.getBlue() * (1 - amount));
        return new Color(r, g, b);
    }
}
