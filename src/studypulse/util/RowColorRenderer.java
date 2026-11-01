package studypulse.util;

import java.awt.Color;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;

/**
 * Cell renderer that colours whole rows. Column 0 (ID) is centred.
 * The frame says which colour each model row gets:
 * <pre>RowColorRenderer.install(tblTasks, row -> UITheme.deadlineColor(...));</pre>
 */
public class RowColorRenderer extends DefaultTableCellRenderer {

    /** Background colour of a row (model index); null provider or null colour = white. */
    public interface RowColorProvider {
        Color rowColor(int modelRow);
    }

    /** True if a row (model index) should use a bold font, e.g. unread notifications. */
    public interface BoldRowProvider {
        boolean isBold(int modelRow);
    }

    private final RowColorProvider rowColors;
    private BoldRowProvider boldRows;

    public RowColorRenderer(RowColorProvider rowColors) {
        this.rowColors = rowColors;
    }

    /** Optional: bold rows. */
    public void setBoldRows(BoldRowProvider boldRows) {
        this.boldRows = boldRows;
    }

    /** Uses this renderer for every column of the table. */
    public void installOn(JTable table) {
        TableColumnModel columns = table.getColumnModel();
        for (int i = 0; i < columns.getColumnCount(); i++) {
            columns.getColumn(i).setCellRenderer(this);
        }
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
            boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
        setHorizontalAlignment(table.convertColumnIndexToModel(column) == 0
                ? SwingConstants.CENTER : SwingConstants.LEFT);
        setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        if (boldRows != null) {
            setFont(boldRows.isBold(table.convertRowIndexToModel(row)) ? UITheme.FONT_BOLD : UITheme.FONT_NORMAL);
        }
        if (!isSelected) {
            setBackground(backgroundFor(table, row, false));
            setForeground(UITheme.TEXT);
        }
        return this;
    }

    /** Row colour for a view row. */
    protected Color backgroundFor(JTable table, int row, boolean isSelected) {
        if (isSelected) {
            return table.getSelectionBackground();
        }
        Color bg = rowColors == null ? null : rowColors.rowColor(table.convertRowIndexToModel(row));
        return bg == null ? table.getBackground() : bg;
    }
}
