package studypulse.util;

import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;

/**
 * Helpers for JTables that show database rows.
 * Rule: column 0 is always "ID" (narrow and centred).
 */
public final class TableUtil {

    public static final String ID_COLUMN = "ID";
    public static final int ID_COLUMN_WIDTH = 55;

    private TableUtil() {
    }

    /** Read-only model whose first column is "ID" (Integer), followed by the given columns. */
    public static DefaultTableModel createModel(String... columns) {
        String[] all = new String[columns.length + 1];
        all[0] = ID_COLUMN;
        System.arraycopy(columns, 0, all, 1, columns.length);
        return new DefaultTableModel(all, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int column) {
                return column == 0 ? Integer.class : Object.class;
            }
        };
    }

    /**
     * Like createModel, but one column (index in the full model, ID = 0) can be edited
     * and has the given class, e.g. Double.class for a number editor.
     */
    public static DefaultTableModel createModel(final int editableColumn, final Class<?> editableClass,
            String... columns) {
        String[] all = new String[columns.length + 1];
        all[0] = ID_COLUMN;
        System.arraycopy(columns, 0, all, 1, columns.length);
        return new DefaultTableModel(all, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == editableColumn;
            }

            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 0) {
                    return Integer.class;
                }
                return column == editableColumn ? editableClass : Object.class;
            }
        };
    }

    /** Makes column 0 narrow and centred. */
    public static void configureIdColumn(JTable table) {
        if (table.getColumnModel().getColumnCount() == 0) {
            return;
        }
        TableColumn idColumn = table.getColumnModel().getColumn(0);
        idColumn.setMinWidth(40);
        idColumn.setPreferredWidth(ID_COLUMN_WIDTH);
        idColumn.setMaxWidth(70);
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        idColumn.setCellRenderer(center);
    }

    /** Sets a new model (ID first) and re-applies the theme. */
    public static DefaultTableModel install(JTable table, String... columns) {
        DefaultTableModel model = createModel(columns);
        table.setModel(model);
        UITheme.styleTable(table);
        return model;
    }

    public static void clear(JTable table) {
        ((DefaultTableModel) table.getModel()).setRowCount(0);
    }

    /** Id in column 0 of the selected row, or -1 if nothing is selected. Works with sorting. */
    public static int getSelectedId(JTable table) {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return -1;
        }
        Object value = table.getModel().getValueAt(table.convertRowIndexToModel(viewRow), 0);
        return value == null ? -1 : Integer.parseInt(value.toString());
    }
}
