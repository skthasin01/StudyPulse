package studypulse.util;

import java.awt.Component;
import javax.swing.JTable;

/**
 * Row renderer that shows single cells in red bold when the frame says they are a
 * warning (e.g. stress 4-5, energy 1-2, sleep below 6 hours).
 */
public class WarningCellRenderer extends RowColorRenderer {

    /** True if the cell (model row and column) should be red bold. */
    public interface WarningProvider {
        boolean isWarning(int modelRow, int modelColumn);
    }

    private final WarningProvider warnings;

    public WarningCellRenderer(RowColorProvider rowColors, WarningProvider warnings) {
        super(rowColors);
        this.warnings = warnings;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
            boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        boolean warning = warnings.isWarning(table.convertRowIndexToModel(row),
                table.convertColumnIndexToModel(column));
        setFont(warning ? UITheme.FONT_BOLD : UITheme.FONT_NORMAL);
        if (warning && !isSelected) {
            setForeground(UITheme.DANGER);
        }
        return this;
    }
}
