package studypulse.util;

import java.awt.Component;
import java.text.ParseException;
import javax.swing.AbstractCellEditor;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.TableCellEditor;

/** Table cell editor with a number spinner, e.g. free hours 0 - 12 in steps of 0.5. */
public class SpinnerCellEditor extends AbstractCellEditor implements TableCellEditor {

    private final JSpinner spinner;
    private final double min;
    private final double max;

    public SpinnerCellEditor(double min, double max, double step) {
        this.min = min;
        this.max = max;
        spinner = new JSpinner(new SpinnerNumberModel(min, min, max, step));
        spinner.setBorder(null);
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected,
            int row, int column) {
        double v = value instanceof Number ? ((Number) value).doubleValue() : min;
        spinner.setValue(Math.max(min, Math.min(max, v)));
        return spinner;
    }

    @Override
    public Object getCellEditorValue() {
        return ((Number) spinner.getValue()).doubleValue();
    }

    @Override
    public boolean stopCellEditing() {
        try {
            spinner.commitEdit();
        } catch (ParseException ex) {
            // keep the last valid value
        }
        return super.stopCellEditing();
    }
}
