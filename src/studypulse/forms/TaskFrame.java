package studypulse.forms;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import studypulse.model.Difficulty;
import studypulse.model.Priority;
import studypulse.model.Task;
import studypulse.model.TaskStatus;
import studypulse.model.TaskType;
import studypulse.service.ServiceException;
import studypulse.service.TaskService;
import studypulse.util.BadgeRenderer;
import studypulse.util.DateUtil;
import studypulse.util.RowColorRenderer;
import studypulse.util.SidebarUtil;
import studypulse.util.TableUtil;
import studypulse.util.UITheme;

/**
 * Add, edit, delete, search and complete study tasks.
 * Combo box items are in the same order as the enums (index = ordinal);
 * the filter combos have "All ..." at index 0.
 */
public class TaskFrame extends javax.swing.JFrame {

    private static final int COL_DIFFICULTY = 4;
    private static final LocalTime DEFAULT_DEADLINE_TIME = LocalTime.of(23, 59);

    private final TaskService taskService = new TaskService();
    private List<Task> allTasks = new ArrayList<Task>();
    private final List<Task> shownTasks = new ArrayList<Task>();
    private DefaultTableModel tableModel;
    private int selectedTaskId = -1;
    private boolean fillingTable;

    /**
     * Creates new form TaskFrame
     */
    public TaskFrame() {
        initComponents();
        SidebarUtil.attach(this, pnlSidebar, SidebarUtil.TASKS);
        applyTheme();
        setupFields();
        setupTable();
        clearForm();
        refreshTasks();
    }

    private void applyTheme() {
        UITheme.applyFrame(this);
        UITheme.styleCard(pnlForm);
        UITheme.stylePrimaryButton(btnSave);
        UITheme.stylePrimaryButton(btnUpdate);
        UITheme.styleDangerButton(btnDelete);
        btnMarkDone.setBackground(UITheme.SUCCESS);
        btnMarkDone.setForeground(UITheme.WHITE);
        btnMarkDone.setFont(UITheme.FONT_BOLD);
        txtSearch.putClientProperty("JTextField.placeholderText", "Search title or course");
        txtSearch.putClientProperty("JTextField.showClearButton", true);
        txtTitle.putClientProperty("JTextField.placeholderText", "e.g. Database assignment 2");
        txtCourse.putClientProperty("JTextField.placeholderText", "e.g. CSE 3101");
    }

    private void setupFields() {
        spnDeadlineTime.setModel(new SpinnerDateModel(DateUtil.toDate(DEFAULT_DEADLINE_TIME), null, null,
                Calendar.MINUTE));
        spnDeadlineTime.setEditor(new JSpinner.DateEditor(spnDeadlineTime, "HH:mm"));
        txtSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });
    }

    private void setupTable() {
        tableModel = TableUtil.install(tblTasks, "Title", "Course", "Type", "Difficulty", "Est.",
                "Deadline", "Priority", "Status", "Left");
        RowColorRenderer.RowColorProvider rowColors = new RowColorRenderer.RowColorProvider() {
            @Override
            public java.awt.Color rowColor(int modelRow) {
                return UITheme.deadlineColor(taskService.getDeadlineState(shownTasks.get(modelRow)));
            }
        };
        new RowColorRenderer(rowColors).installOn(tblTasks);
        tblTasks.getColumnModel().getColumn(COL_DIFFICULTY).setCellRenderer(new BadgeRenderer(rowColors,
                new BadgeRenderer.BadgeColorProvider() {
                    @Override
                    public java.awt.Color badgeColor(Object value) {
                        return UITheme.difficultyColor(Difficulty.fromLabel(value));
                    }
                }));
        // fits the ~700 px table width without cutting the short columns
        int[] widths = {40, 94, 66, 80, 76, 54, 94, 56, 60, 78};
        TableColumnModel columns = tblTasks.getColumnModel();
        for (int i = 1; i < widths.length; i++) {
            columns.getColumn(i).setPreferredWidth(widths[i]);
        }
        tblTasks.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting() && !fillingTable) {
                    loadSelectedTask();
                }
            }
        });
    }

    /** Marks overdue tasks as MISSED, reloads all tasks and redraws the table. */
    private void refreshTasks() {
        try {
            taskService.markOverdueAsMissed();
            allTasks = taskService.getTasks();
        } catch (ServiceException ex) {
            showError(ex.getMessage());
        }
        applyFilter();
        updateSummary();
    }

    /** Search text + status filter + difficulty filter. */
    private void applyFilter() {
        int statusIndex = cmbFilterStatus.getSelectedIndex();
        int difficultyIndex = cmbFilterDifficulty.getSelectedIndex();
        TaskStatus status = statusIndex <= 0 ? null : TaskStatus.values()[statusIndex - 1];
        Difficulty difficulty = difficultyIndex <= 0 ? null : Difficulty.values()[difficultyIndex - 1];

        fillingTable = true;
        shownTasks.clear();
        shownTasks.addAll(taskService.filter(allTasks, txtSearch.getText(), status, difficulty));
        tableModel.setRowCount(0);
        int selectRow = -1;
        for (Task t : shownTasks) {
            if (t.getId() == selectedTaskId) {
                selectRow = tableModel.getRowCount();
            }
            tableModel.addRow(new Object[]{
                t.getId(), t.getTitle(), t.getCourseName(), t.getTaskType().getLabel(),
                t.getDifficulty().getLabel(), DateUtil.formatMinutes(t.getEstimatedMinutes()),
                DateUtil.formatCompact(t.getDeadline()), t.getPriority().getLabel(),
                t.getStatus().getLabel(), taskService.formatTimeLeft(t)});
        }
        if (selectRow >= 0) {
            tblTasks.setRowSelectionInterval(selectRow, selectRow);
        }
        fillingTable = false;
    }

    private void updateSummary() {
        Map<TaskStatus, Integer> counts = taskService.countByStatus(allTasks);
        lblSummary.setText("Total: " + allTasks.size()
                + " | Pending: " + counts.get(TaskStatus.PENDING)
                + " | In progress: " + counts.get(TaskStatus.IN_PROGRESS)
                + " | Done: " + counts.get(TaskStatus.DONE)
                + " | Missed: " + counts.get(TaskStatus.MISSED));
    }

    private void loadSelectedTask() {
        int id = TableUtil.getSelectedId(tblTasks);
        if (id < 0) {
            return;
        }
        Task task = findLoaded(id);
        if (task == null) {
            return;
        }
        selectedTaskId = id;
        txtTitle.setText(task.getTitle());
        txtCourse.setText(task.getCourseName());
        cmbType.setSelectedIndex(task.getTaskType().ordinal());
        cmbDifficulty.setSelectedIndex(task.getDifficulty().ordinal());
        spnHours.setValue(Math.min(100, task.getEstimatedMinutes() / 60));
        spnMinutes.setValue((task.getEstimatedMinutes() % 60) / 5 * 5);
        dateDeadline.setDate(DateUtil.toDate(task.getDeadline().toLocalDate()));
        spnDeadlineTime.setValue(DateUtil.toDate(task.getDeadline().toLocalTime()));
        cmbPriority.setSelectedIndex(task.getPriority().ordinal());
        txtDescription.setText(task.getDescription());
        txtDescription.setCaretPosition(0);
        updateButtons();
        updateEstimateHint();
    }

    /** Copies the form fields into the task. */
    private void readForm(Task task) {
        commitSpinners();
        task.setTitle(txtTitle.getText());
        task.setCourseName(txtCourse.getText());
        task.setTaskType(TaskType.values()[cmbType.getSelectedIndex()]);
        task.setDifficulty(Difficulty.values()[cmbDifficulty.getSelectedIndex()]);
        task.setEstimatedMinutes((Integer) spnHours.getValue() * 60 + (Integer) spnMinutes.getValue());
        LocalDate date = DateUtil.fromDate(dateDeadline.getDate());
        LocalTime time = DateUtil.toLocalTime((java.util.Date) spnDeadlineTime.getValue());
        task.setDeadline(date == null ? null : LocalDateTime.of(date, time));
        task.setPriority(Priority.values()[cmbPriority.getSelectedIndex()]);
        task.setDescription(txtDescription.getText().trim());
    }

    private void saveTask() {
        Task task = new Task();
        readForm(task);
        try {
            int id = taskService.addTask(task);
            clearForm();
            refreshTasks();
            JOptionPane.showMessageDialog(this, "Task saved (ID " + id + ").", "StudyPulse",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (ServiceException ex) {
            showWarning(ex.getMessage());
        }
    }

    private void updateTask() {
        if (!requireSelection()) {
            return;
        }
        try {
            Task task = taskService.getTask(selectedTaskId);
            readForm(task);
            taskService.updateTask(task);
            refreshTasks();
            JOptionPane.showMessageDialog(this, "Task updated.", "StudyPulse", JOptionPane.INFORMATION_MESSAGE);
        } catch (ServiceException ex) {
            showWarning(ex.getMessage());
        }
    }

    private void deleteTask() {
        if (!requireSelection()) {
            return;
        }
        Task task = findLoaded(selectedTaskId);
        String title = task == null ? "this task" : "\"" + task.getTitle() + "\"";
        int answer = JOptionPane.showConfirmDialog(this,
                "Delete " + title + "?\nIts planned study blocks are deleted too.",
                "Delete task", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            taskService.deleteTask(selectedTaskId);
            clearForm();
            refreshTasks();
        } catch (ServiceException ex) {
            showError(ex.getMessage());
        }
    }

    private void markDone() {
        if (!requireSelection()) {
            return;
        }
        Task task;
        try {
            task = taskService.markDone(selectedTaskId);
        } catch (ServiceException ex) {
            showWarning(ex.getMessage());
            return;
        }
        refreshTasks();
        Integer minutes = askActualMinutes(task.getEstimatedMinutes());
        if (minutes != null) {
            try {
                taskService.saveActualMinutes(task.getId(), minutes);
                refreshTasks();
                updateEstimateHint();
            } catch (ServiceException ex) {
                showError(ex.getMessage());
            }
        }
    }

    /** Asks until a whole number above 0 is entered; null if cancelled. */
    private Integer askActualMinutes(int estimated) {
        String input = String.valueOf(estimated);
        while (true) {
            input = (String) JOptionPane.showInputDialog(this,
                    "How many minutes did this task really take?\n(Estimated: " + estimated + " minutes)",
                    "Task done", JOptionPane.QUESTION_MESSAGE, null, null, input);
            if (input == null) {
                return null;
            }
            try {
                int minutes = Integer.parseInt(input.trim());
                if (minutes > 0) {
                    return minutes;
                }
            } catch (NumberFormatException ex) {
                // ask again
            }
            showWarning("Please enter a whole number of minutes above 0.");
        }
    }

    private void clearForm() {
        selectedTaskId = -1;
        tblTasks.clearSelection();
        txtTitle.setText("");
        txtCourse.setText("");
        cmbType.setSelectedIndex(TaskType.ASSIGNMENT.ordinal());
        cmbDifficulty.setSelectedIndex(Difficulty.MEDIUM.ordinal());
        spnHours.setValue(1);
        spnMinutes.setValue(0);
        dateDeadline.setDate(DateUtil.toDate(DateUtil.today()));
        spnDeadlineTime.setValue(DateUtil.toDate(DEFAULT_DEADLINE_TIME));
        cmbPriority.setSelectedIndex(Priority.MEDIUM.ordinal());
        txtDescription.setText("");
        updateButtons();
        updateEstimateHint();
        txtTitle.requestFocusInWindow();
    }

    /** Save is for new tasks; Update / Delete / Mark Done need a selected task. */
    private void updateButtons() {
        boolean selected = selectedTaskId > 0;
        btnSave.setEnabled(!selected);
        btnUpdate.setEnabled(selected);
        btnDelete.setEnabled(selected);
        btnMarkDone.setEnabled(selected);
    }

    private void updateEstimateHint() {
        if (cmbType.getSelectedIndex() < 0 || cmbDifficulty.getSelectedIndex() < 0) {
            return;
        }
        String hint = taskService.suggestEstimate(Difficulty.values()[cmbDifficulty.getSelectedIndex()],
                TaskType.values()[cmbType.getSelectedIndex()]);
        if (hint == null) {
            lblEstimateHint.setVisible(false);
        } else {
            // Swing HTML widths come out about 25% larger, so 175 fits the 228 px column
            lblEstimateHint.setText("<html><div style='width:175px'>" + hint + "</div></html>");
            lblEstimateHint.setVisible(true);
        }
    }

    private boolean requireSelection() {
        if (selectedTaskId <= 0) {
            showWarning("Please select a task in the table first.");
            return false;
        }
        return true;
    }

    private Task findLoaded(int id) {
        for (Task t : allTasks) {
            if (t.getId() == id) {
                return t;
            }
        }
        return null;
    }

    /** Typed spinner text counts even without pressing Enter. */
    private void commitSpinners() {
        JSpinner[] spinners = {spnHours, spnMinutes, spnDeadlineTime};
        for (JSpinner s : spinners) {
            try {
                s.commitEdit();
            } catch (java.text.ParseException ex) {
                // keep the last valid value
            }
        }
    }

    private void showWarning(String message) {
        JOptionPane.showMessageDialog(this, message, "StudyPulse", JOptionPane.WARNING_MESSAGE);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "StudyPulse", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        pnlSidebar = new javax.swing.JPanel();
        pnlForm = new javax.swing.JPanel();
        lblFormTitle = new javax.swing.JLabel();
        lblTaskTitle = new javax.swing.JLabel();
        txtTitle = new javax.swing.JTextField();
        lblCourse = new javax.swing.JLabel();
        txtCourse = new javax.swing.JTextField();
        lblType = new javax.swing.JLabel();
        cmbType = new javax.swing.JComboBox<>();
        lblDifficulty = new javax.swing.JLabel();
        cmbDifficulty = new javax.swing.JComboBox<>();
        lblEstimate = new javax.swing.JLabel();
        spnHours = new javax.swing.JSpinner();
        lblHours = new javax.swing.JLabel();
        spnMinutes = new javax.swing.JSpinner();
        lblMinutes = new javax.swing.JLabel();
        lblEstimateHint = new javax.swing.JLabel();
        lblDeadline = new javax.swing.JLabel();
        dateDeadline = new com.toedter.calendar.JDateChooser();
        lblDeadlineTime = new javax.swing.JLabel();
        spnDeadlineTime = new javax.swing.JSpinner();
        lblPriority = new javax.swing.JLabel();
        cmbPriority = new javax.swing.JComboBox<>();
        lblDescription = new javax.swing.JLabel();
        scrDescription = new javax.swing.JScrollPane();
        txtDescription = new javax.swing.JTextArea();
        btnSave = new javax.swing.JButton();
        btnUpdate = new javax.swing.JButton();
        btnDelete = new javax.swing.JButton();
        btnClear = new javax.swing.JButton();
        btnMarkDone = new javax.swing.JButton();
        lblTitle = new javax.swing.JLabel();
        txtSearch = new javax.swing.JTextField();
        cmbFilterStatus = new javax.swing.JComboBox<>();
        cmbFilterDifficulty = new javax.swing.JComboBox<>();
        scrTasks = new javax.swing.JScrollPane();
        tblTasks = new javax.swing.JTable();
        lblSummary = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("StudyPulse – Tasks");

        pnlSidebar.setBackground(new java.awt.Color(34, 48, 63));

        javax.swing.GroupLayout pnlSidebarLayout = new javax.swing.GroupLayout(pnlSidebar);
        pnlSidebar.setLayout(pnlSidebarLayout);
        pnlSidebarLayout.setHorizontalGroup(
            pnlSidebarLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 160, Short.MAX_VALUE)
        );
        pnlSidebarLayout.setVerticalGroup(
            pnlSidebarLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 720, Short.MAX_VALUE)
        );

        pnlForm.setBackground(new java.awt.Color(255, 255, 255));

        lblFormTitle.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        lblFormTitle.setForeground(new java.awt.Color(34, 48, 63));
        lblFormTitle.setText("Add / Edit Task");

        lblTaskTitle.setText("Title");

        lblCourse.setText("Course");

        lblType.setText("Type");

        cmbType.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Assignment", "Exam", "Project", "Preparation" }));
        cmbType.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmbTypeActionPerformed(evt);
            }
        });

        lblDifficulty.setText("Difficulty");

        cmbDifficulty.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Easy", "Medium", "Hard" }));
        cmbDifficulty.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmbDifficultyActionPerformed(evt);
            }
        });

        lblEstimate.setText("Estimated time");

        spnHours.setModel(new javax.swing.SpinnerNumberModel(1, 0, 100, 1));

        lblHours.setText("h");

        spnMinutes.setModel(new javax.swing.SpinnerNumberModel(0, 0, 55, 5));

        lblMinutes.setText("min");

        lblEstimateHint.setFont(new java.awt.Font("Segoe UI", 2, 12)); // NOI18N
        lblEstimateHint.setForeground(new java.awt.Color(46, 125, 154));
        lblEstimateHint.setText("Estimate hint");

        lblDeadline.setText("Deadline");

        dateDeadline.setDateFormatString("yyyy-MM-dd");

        lblDeadlineTime.setText("Time");

        lblPriority.setText("Priority");

        cmbPriority.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Low", "Medium", "High" }));

        lblDescription.setText("Description");

        txtDescription.setColumns(20);
        txtDescription.setLineWrap(true);
        txtDescription.setRows(3);
        txtDescription.setWrapStyleWord(true);
        scrDescription.setViewportView(txtDescription);

        btnSave.setText("Save");
        btnSave.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSaveActionPerformed(evt);
            }
        });

        btnUpdate.setText("Update");
        btnUpdate.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnUpdateActionPerformed(evt);
            }
        });

        btnDelete.setText("Delete");
        btnDelete.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDeleteActionPerformed(evt);
            }
        });

        btnClear.setText("Clear");
        btnClear.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnClearActionPerformed(evt);
            }
        });

        btnMarkDone.setText("Mark as Done");
        btnMarkDone.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnMarkDoneActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout pnlFormLayout = new javax.swing.GroupLayout(pnlForm);
        pnlForm.setLayout(pnlFormLayout);
        pnlFormLayout.setHorizontalGroup(
            pnlFormLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlFormLayout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addGroup(pnlFormLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblFormTitle)
                    .addComponent(lblTaskTitle)
                    .addComponent(txtTitle, javax.swing.GroupLayout.DEFAULT_SIZE, 228, Short.MAX_VALUE)
                    .addComponent(lblCourse)
                    .addComponent(txtCourse, javax.swing.GroupLayout.DEFAULT_SIZE, 228, Short.MAX_VALUE)
                    .addGroup(pnlFormLayout.createSequentialGroup()
                        .addGroup(pnlFormLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblType)
                            .addComponent(cmbType, 0, 110, Short.MAX_VALUE))
                        .addGap(8, 8, 8)
                        .addGroup(pnlFormLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblDifficulty)
                            .addComponent(cmbDifficulty, 0, 110, Short.MAX_VALUE)))
                    .addComponent(lblEstimate)
                    .addGroup(pnlFormLayout.createSequentialGroup()
                        .addComponent(spnHours, javax.swing.GroupLayout.DEFAULT_SIZE, 70, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lblHours)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(spnMinutes, javax.swing.GroupLayout.DEFAULT_SIZE, 70, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lblMinutes))
                    .addComponent(lblEstimateHint, javax.swing.GroupLayout.DEFAULT_SIZE, 228, Short.MAX_VALUE)
                    .addGroup(pnlFormLayout.createSequentialGroup()
                        .addGroup(pnlFormLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblDeadline)
                            .addComponent(dateDeadline, javax.swing.GroupLayout.DEFAULT_SIZE, 130, Short.MAX_VALUE))
                        .addGap(8, 8, 8)
                        .addGroup(pnlFormLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblDeadlineTime)
                            .addComponent(spnDeadlineTime, javax.swing.GroupLayout.DEFAULT_SIZE, 90, Short.MAX_VALUE)))
                    .addComponent(lblPriority)
                    .addComponent(cmbPriority, 0, 228, Short.MAX_VALUE)
                    .addComponent(lblDescription)
                    .addComponent(scrDescription, javax.swing.GroupLayout.DEFAULT_SIZE, 228, Short.MAX_VALUE)
                    .addGroup(pnlFormLayout.createSequentialGroup()
                        .addComponent(btnSave, javax.swing.GroupLayout.DEFAULT_SIZE, 110, Short.MAX_VALUE)
                        .addGap(8, 8, 8)
                        .addComponent(btnUpdate, javax.swing.GroupLayout.DEFAULT_SIZE, 110, Short.MAX_VALUE))
                    .addGroup(pnlFormLayout.createSequentialGroup()
                        .addComponent(btnDelete, javax.swing.GroupLayout.DEFAULT_SIZE, 110, Short.MAX_VALUE)
                        .addGap(8, 8, 8)
                        .addComponent(btnClear, javax.swing.GroupLayout.DEFAULT_SIZE, 110, Short.MAX_VALUE))
                    .addComponent(btnMarkDone, javax.swing.GroupLayout.DEFAULT_SIZE, 228, Short.MAX_VALUE))
                .addGap(16, 16, 16))
        );
        pnlFormLayout.setVerticalGroup(
            pnlFormLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlFormLayout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addComponent(lblFormTitle)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lblTaskTitle)
                .addGap(4, 4, 4)
                .addComponent(txtTitle, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblCourse)
                .addGap(4, 4, 4)
                .addComponent(txtCourse, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlFormLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblType)
                    .addComponent(lblDifficulty))
                .addGap(4, 4, 4)
                .addGroup(pnlFormLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cmbType, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(cmbDifficulty, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblEstimate)
                .addGap(4, 4, 4)
                .addGroup(pnlFormLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(spnHours, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblHours)
                    .addComponent(spnMinutes, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblMinutes))
                .addGap(4, 4, 4)
                .addComponent(lblEstimateHint)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlFormLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblDeadline)
                    .addComponent(lblDeadlineTime))
                .addGap(4, 4, 4)
                .addGroup(pnlFormLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(dateDeadline, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(spnDeadlineTime, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblPriority)
                .addGap(4, 4, 4)
                .addComponent(cmbPriority, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblDescription)
                .addGap(4, 4, 4)
                .addComponent(scrDescription, javax.swing.GroupLayout.DEFAULT_SIZE, 60, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(pnlFormLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnSave, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnUpdate, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlFormLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnDelete, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnClear, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnMarkDone, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(16, 16, 16))
        );

        lblTitle.setFont(new java.awt.Font("Segoe UI", 1, 22)); // NOI18N
        lblTitle.setForeground(new java.awt.Color(34, 48, 63));
        lblTitle.setText("Tasks");

        cmbFilterStatus.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "All status", "Pending", "In Progress", "Done", "Missed" }));
        cmbFilterStatus.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmbFilterStatusActionPerformed(evt);
            }
        });

        cmbFilterDifficulty.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "All difficulty", "Easy", "Medium", "Hard" }));
        cmbFilterDifficulty.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmbFilterDifficultyActionPerformed(evt);
            }
        });

        tblTasks.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "ID", "Title", "Course", "Type", "Difficulty", "Est.", "Deadline", "Priority", "Status", "Left"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.Integer.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, false, false, false, false, false, false, false, false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        scrTasks.setViewportView(tblTasks);

        lblSummary.setForeground(new java.awt.Color(107, 119, 133));
        lblSummary.setText("Total: 0 | Pending: 0 | In progress: 0 | Done: 0 | Missed: 0");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(pnlSidebar, javax.swing.GroupLayout.PREFERRED_SIZE, 160, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(16, 16, 16)
                .addComponent(pnlForm, javax.swing.GroupLayout.PREFERRED_SIZE, 260, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(16, 16, 16)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lblTitle)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(txtSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cmbFilterStatus, javax.swing.GroupLayout.PREFERRED_SIZE, 130, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cmbFilterDifficulty, javax.swing.GroupLayout.PREFERRED_SIZE, 140, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(scrTasks, javax.swing.GroupLayout.DEFAULT_SIZE, 732, Short.MAX_VALUE)
                    .addComponent(lblSummary, javax.swing.GroupLayout.DEFAULT_SIZE, 732, Short.MAX_VALUE))
                .addGap(16, 16, 16))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(pnlSidebar, javax.swing.GroupLayout.DEFAULT_SIZE, 720, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addComponent(pnlForm, javax.swing.GroupLayout.DEFAULT_SIZE, 688, Short.MAX_VALUE)
                .addGap(16, 16, 16))
            .addGroup(layout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblTitle)
                    .addComponent(txtSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(cmbFilterStatus, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(cmbFilterDifficulty, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(scrTasks, javax.swing.GroupLayout.DEFAULT_SIZE, 600, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblSummary)
                .addGap(16, 16, 16))
        );

        setSize(new java.awt.Dimension(1200, 720));
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void cmbTypeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmbTypeActionPerformed
        updateEstimateHint();
    }//GEN-LAST:event_cmbTypeActionPerformed

    private void cmbDifficultyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmbDifficultyActionPerformed
        updateEstimateHint();
    }//GEN-LAST:event_cmbDifficultyActionPerformed

    private void btnSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSaveActionPerformed
        saveTask();
    }//GEN-LAST:event_btnSaveActionPerformed

    private void btnUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnUpdateActionPerformed
        updateTask();
    }//GEN-LAST:event_btnUpdateActionPerformed

    private void btnDeleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDeleteActionPerformed
        deleteTask();
    }//GEN-LAST:event_btnDeleteActionPerformed

    private void btnClearActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnClearActionPerformed
        clearForm();
    }//GEN-LAST:event_btnClearActionPerformed

    private void btnMarkDoneActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnMarkDoneActionPerformed
        markDone();
    }//GEN-LAST:event_btnMarkDoneActionPerformed

    private void cmbFilterStatusActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmbFilterStatusActionPerformed
        applyFilter();
    }//GEN-LAST:event_cmbFilterStatusActionPerformed

    private void cmbFilterDifficultyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmbFilterDifficultyActionPerformed
        applyFilter();
    }//GEN-LAST:event_cmbFilterDifficultyActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnClear;
    private javax.swing.JButton btnDelete;
    private javax.swing.JButton btnMarkDone;
    private javax.swing.JButton btnSave;
    private javax.swing.JButton btnUpdate;
    private javax.swing.JComboBox<String> cmbDifficulty;
    private javax.swing.JComboBox<String> cmbFilterDifficulty;
    private javax.swing.JComboBox<String> cmbFilterStatus;
    private javax.swing.JComboBox<String> cmbPriority;
    private javax.swing.JComboBox<String> cmbType;
    private com.toedter.calendar.JDateChooser dateDeadline;
    private javax.swing.JLabel lblCourse;
    private javax.swing.JLabel lblDeadline;
    private javax.swing.JLabel lblDeadlineTime;
    private javax.swing.JLabel lblDescription;
    private javax.swing.JLabel lblDifficulty;
    private javax.swing.JLabel lblEstimate;
    private javax.swing.JLabel lblEstimateHint;
    private javax.swing.JLabel lblFormTitle;
    private javax.swing.JLabel lblHours;
    private javax.swing.JLabel lblMinutes;
    private javax.swing.JLabel lblPriority;
    private javax.swing.JLabel lblSummary;
    private javax.swing.JLabel lblTaskTitle;
    private javax.swing.JLabel lblTitle;
    private javax.swing.JLabel lblType;
    private javax.swing.JPanel pnlForm;
    private javax.swing.JPanel pnlSidebar;
    private javax.swing.JScrollPane scrDescription;
    private javax.swing.JScrollPane scrTasks;
    private javax.swing.JSpinner spnDeadlineTime;
    private javax.swing.JSpinner spnHours;
    private javax.swing.JSpinner spnMinutes;
    private javax.swing.JTable tblTasks;
    private javax.swing.JTextArea txtDescription;
    private javax.swing.JTextField txtCourse;
    private javax.swing.JTextField txtSearch;
    private javax.swing.JTextField txtTitle;
    // End of variables declaration//GEN-END:variables
}
