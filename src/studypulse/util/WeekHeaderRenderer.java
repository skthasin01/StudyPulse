package studypulse.util;

import java.awt.Component;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellRenderer;
import studypulse.model.DayPlan;

/**
 * Column header of the week grid: "Mon 02 Nov" and "planned / free" (e.g. "2h 30m / 3h").
 * Reads the DayPlan from row 0 of the column; today's header is highlighted.
 */
public class WeekHeaderRenderer extends JLabel implements TableCellRenderer {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.ENGLISH);
    public WeekHeaderRenderer() {
        setOpaque(true);
        setHorizontalAlignment(SwingConstants.CENTER);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
            boolean hasFocus, int row, int column) {
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 1, UITheme.GRID),
                BorderFactory.createEmptyBorder(6, 2, 6, 2)));
        DayPlan day = null;
        if (table != null && table.getRowCount() > 0) {
            Object cell = table.getModel().getValueAt(0, table.convertColumnIndexToModel(column));
            if (cell instanceof DayPlan) {
                day = (DayPlan) cell;
            }
        }
        if (day == null) {
            setText(value == null ? "" : value.toString());
            setBackground(UITheme.HEADER_BACKGROUND);
            setForeground(UITheme.TEXT);
            return this;
        }
        boolean today = day.getDate().equals(DateUtil.today());
        String second;
        if (day.isRestDay()) {
            second = "Rest day";
        } else {
            second = DateUtil.formatMinutes(day.getPlannedMinutes()) + " / "
                    + DateUtil.formatMinutes(day.getFreeMinutes());
            if (!today && day.getPlannedMinutes() > day.getFreeMinutes()) {
                second = "<span style='color:#D64545'>" + second + "</span>";
            }
        }
        setText("<html><div style='text-align:center'><b>" + day.getDate().format(DAY_FORMAT)
                + "</b><br>" + second + "</div></html>");
        setFont(UITheme.FONT_NORMAL);
        setBackground(today ? UITheme.ACCENT : UITheme.HEADER_BACKGROUND);
        setForeground(today ? UITheme.WHITE : UITheme.TEXT);
        return this;
    }
}
