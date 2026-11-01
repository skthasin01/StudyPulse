package studypulse.forms;

import java.awt.Color;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.JOptionPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import studypulse.engine.PlanResult;
import studypulse.model.DayPlan;
import studypulse.model.Difficulty;
import studypulse.model.PlanBlockItem;
import studypulse.model.PlanStatus;
import studypulse.model.Task;
import studypulse.model.TaskStatus;
import studypulse.service.ServiceException;
import studypulse.service.StudyPlanService;
import studypulse.util.BadgeRenderer;
import studypulse.util.DateUtil;
import studypulse.util.RowColorRenderer;
import studypulse.util.SidebarUtil;
import studypulse.util.TableUtil;
import studypulse.util.UITheme;
import studypulse.util.WeekDayRenderer;
import studypulse.util.WeekHeaderRenderer;

/**
 * Week view of the study plan (Mon..Sun) with plan generation.
 * tblWeek has one row; each cell is a DayPlan painted by WeekDayRenderer.
 * The bottom table lists the blocks of the selected day.
 */
public class StudyPlanFrame extends javax.swing.JFrame {

    private static final String[] DAY_COLUMNS = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    private static final int COL_DIFFICULTY = 2;
    private static final DateTimeFormatter DAY_TITLE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, dd MMM", Locale.ENGLISH);

    private final StudyPlanService planService = new StudyPlanService();
    private LocalDate monday = DateUtil.mondayOf(DateUtil.today());
    private List<DayPlan> week = new ArrayList<DayPlan>();
    private final List<PlanBlockItem> shownBlocks = new ArrayList<PlanBlockItem>();
    private DefaultTableModel weekModel;
    private DefaultTableModel blocksModel;
    private int selectedDay;

    /**
     * Creates new form StudyPlanFrame
     */
    public StudyPlanFrame() {
        initComponents();
        SidebarUtil.attach(this, pnlSidebar, SidebarUtil.STUDY_PLAN);
        applyTheme();
        setupWeekTable();
        setupBlocksTable();
        selectedDay = DateUtil.today().getDayOfWeek().getValue() - 1;
        loadWeek();
    }

    private void applyTheme() {
        UITheme.applyFrame(this);
        UITheme.stylePrimaryButton(btnGeneratePlan);
        btnMarkBlockDone.setBackground(UITheme.SUCCESS);
        btnMarkBlockDone.setForeground(UITheme.WHITE);
        btnMarkBlockDone.setFont(UITheme.FONT_BOLD);
        btnPrevWeek.setFont(UITheme.FONT_HEADING);
        btnNextWeek.setFont(UITheme.FONT_HEADING);
    }

    private void setupWeekTable() {
        weekModel = new DefaultTableModel(DAY_COLUMNS, 1) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tblWeek.setModel(weekModel);
        tblWeek.setDefaultRenderer(Object.class, new WeekDayRenderer());
        tblWeek.getTableHeader().setDefaultRenderer(new WeekHeaderRenderer());
        tblWeek.getTableHeader().setReorderingAllowed(false);
        tblWeek.getTableHeader().setResizingAllowed(false);
        tblWeek.setCellSelectionEnabled(true);
        tblWeek.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tblWeek.setGridColor(UITheme.GRID);
        tblWeek.setShowGrid(true);
        tblWeek.setFillsViewportHeight(true);
        tblWeek.getColumnModel().getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                int column = tblWeek.getSelectedColumn();
                if (!e.getValueIsAdjusting() && column >= 0 && column != selectedDay) {
                    selectedDay = column;
                    showDayBlocks();
                }
            }
        });
        // the single row fills the whole visible height
        scrWeek.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                tblWeek.setRowHeight(Math.max(120, scrWeek.getViewport().getHeight()));
            }
        });
    }

    private void setupBlocksTable() {
        blocksModel = TableUtil.install(tblDayBlocks, "Task", "Difficulty", "Planned", "Status");
        RowColorRenderer.RowColorProvider rowColors = new RowColorRenderer.RowColorProvider() {
            @Override
            public Color rowColor(int modelRow) {
                PlanStatus status = shownBlocks.get(modelRow).getBlock().getStatus();
                if (status == PlanStatus.DONE) {
                    return UITheme.ROW_DONE;
                }
                return status == PlanStatus.SKIPPED ? UITheme.ROW_SKIPPED : UITheme.SURFACE;
            }
        };
        new RowColorRenderer(rowColors).installOn(tblDayBlocks);
        tblDayBlocks.getColumnModel().getColumn(COL_DIFFICULTY).setCellRenderer(new BadgeRenderer(rowColors,
                new BadgeRenderer.BadgeColorProvider() {
                    @Override
                    public Color badgeColor(Object value) {
                        return UITheme.difficultyColor(Difficulty.fromLabel(value));
                    }
                }));
        tblDayBlocks.getColumnModel().getColumn(1).setPreferredWidth(420);
        tblDayBlocks.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    updateBlockButtons();
                }
            }
        });
    }

    /** Reloads the shown week from the database. */
    private void loadWeek() {
        lblWeekRange.setText(DateUtil.formatWeekRange(monday));
        try {
            week = planService.getWeek(monday);
        } catch (ServiceException ex) {
            showError(ex.getMessage());
            week = new ArrayList<DayPlan>();
        }
        for (int i = 0; i < 7; i++) {
            weekModel.setValueAt(i < week.size() ? week.get(i) : null, 0, i);
        }
        tblWeek.getTableHeader().revalidate();
        tblWeek.getTableHeader().repaint();
        tblWeek.changeSelection(0, selectedDay, false, false);
        showDayBlocks();
    }

    private void showDayBlocks() {
        blocksModel.setRowCount(0);
        shownBlocks.clear();
        if (selectedDay < 0 || selectedDay >= week.size()) {
            lblDayTitle.setText("Blocks");
            updateBlockButtons();
            return;
        }
        DayPlan day = week.get(selectedDay);
        lblDayTitle.setText("Blocks on " + day.getDate().format(DAY_TITLE_FORMAT)
                + (day.isRestDay() ? "  (rest day)" : ""));
        for (PlanBlockItem item : day.getItems()) {
            shownBlocks.add(item);
            blocksModel.addRow(new Object[]{item.getBlock().getId(), item.getTaskTitle(),
                item.getDifficulty() == null ? "" : item.getDifficulty().getLabel(),
                DateUtil.formatMinutes(item.getBlock().getPlannedMinutes()),
                item.getBlock().getStatus().getLabel()});
        }
        updateBlockButtons();
    }

    /** Mark Done works on planned or skipped blocks, Skip only on planned ones. */
    private void updateBlockButtons() {
        PlanBlockItem item = selectedBlock();
        PlanStatus status = item == null ? null : item.getBlock().getStatus();
        btnMarkBlockDone.setEnabled(status == PlanStatus.PLANNED || status == PlanStatus.SKIPPED);
        btnSkipBlock.setEnabled(status == PlanStatus.PLANNED);
    }

    private PlanBlockItem selectedBlock() {
        int row = tblDayBlocks.getSelectedRow();
        return row < 0 ? null : shownBlocks.get(tblDayBlocks.convertRowIndexToModel(row));
    }

    private void changeWeek(int weeks) {
        monday = monday.plusWeeks(weeks);
        loadWeek();
    }

    private void openFreeTime() {
        AvailabilityFrame frame = new AvailabilityFrame();
        frame.setOnSaved(new Runnable() {
            @Override
            public void run() {
                loadWeek();
            }
        });
        frame.setVisible(true);
    }

    private void generatePlan() {
        PlanResult result;
        try {
            result = planService.generatePlan();
        } catch (ServiceException ex) {
            showError(ex.getMessage());
            return;
        }
        monday = DateUtil.mondayOf(DateUtil.today());
        selectedDay = DateUtil.today().getDayOfWeek().getValue() - 1;
        loadWeek();
        JOptionPane.showMessageDialog(this, planService.summarize(result), "Study plan",
                result.getOverloaded().isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
    }

    private void markBlockDone() {
        PlanBlockItem item = selectedBlock();
        if (item == null) {
            showWarning("Please select a block in the table first.");
            return;
        }
        int blockId = item.getBlock().getId();
        try {
            int remaining = planService.markBlockDone(blockId);
            if (remaining <= 0) {
                Task task = planService.getTaskOfBlock(blockId);
                if (task.getStatus() != TaskStatus.DONE) {
                    int answer = JOptionPane.showConfirmDialog(this,
                            "All estimated time for \"" + task.getTitle() + "\" is studied.\n"
                            + "Mark the task as done?", "Task finished?", JOptionPane.YES_NO_OPTION);
                    if (answer == JOptionPane.YES_OPTION) {
                        planService.completeTask(task.getId());
                    }
                }
            }
        } catch (ServiceException ex) {
            showWarning(ex.getMessage());
        }
        loadWeek();
    }

    private void skipBlock() {
        PlanBlockItem item = selectedBlock();
        if (item == null) {
            showWarning("Please select a block in the table first.");
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Skip this block?\nIts minutes are planned again the next time you generate the plan.",
                "Skip block", JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            planService.skipBlock(item.getBlock().getId());
        } catch (ServiceException ex) {
            showWarning(ex.getMessage());
        }
        loadWeek();
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
        lblTitle = new javax.swing.JLabel();
        btnPrevWeek = new javax.swing.JButton();
        lblWeekRange = new javax.swing.JLabel();
        btnNextWeek = new javax.swing.JButton();
        btnMyFreeTime = new javax.swing.JButton();
        btnGeneratePlan = new javax.swing.JButton();
        scrWeek = new javax.swing.JScrollPane();
        tblWeek = new javax.swing.JTable();
        lblDayTitle = new javax.swing.JLabel();
        btnSkipBlock = new javax.swing.JButton();
        btnMarkBlockDone = new javax.swing.JButton();
        scrDayBlocks = new javax.swing.JScrollPane();
        tblDayBlocks = new javax.swing.JTable();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("StudyPulse – Study Plan");

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

        lblTitle.setFont(new java.awt.Font("Segoe UI", 1, 22)); // NOI18N
        lblTitle.setForeground(new java.awt.Color(34, 48, 63));
        lblTitle.setText("Study Plan");

        btnPrevWeek.setText("<");
        btnPrevWeek.setToolTipText("Previous week");
        btnPrevWeek.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPrevWeekActionPerformed(evt);
            }
        });

        lblWeekRange.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        lblWeekRange.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblWeekRange.setText("02 Nov – 08 Nov 2026");

        btnNextWeek.setText(">");
        btnNextWeek.setToolTipText("Next week");
        btnNextWeek.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnNextWeekActionPerformed(evt);
            }
        });

        btnMyFreeTime.setText("My Free Time");
        btnMyFreeTime.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnMyFreeTimeActionPerformed(evt);
            }
        });

        btnGeneratePlan.setText("Generate Plan");
        btnGeneratePlan.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnGeneratePlanActionPerformed(evt);
            }
        });

        tblWeek.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null, null, null, null}
            },
            new String [] {
                "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false, false, false, false, false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        scrWeek.setViewportView(tblWeek);

        lblDayTitle.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        lblDayTitle.setForeground(new java.awt.Color(34, 48, 63));
        lblDayTitle.setText("Blocks on");

        btnSkipBlock.setText("Skip Block");
        btnSkipBlock.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSkipBlockActionPerformed(evt);
            }
        });

        btnMarkBlockDone.setText("Mark Block Done");
        btnMarkBlockDone.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnMarkBlockDoneActionPerformed(evt);
            }
        });

        tblDayBlocks.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "ID", "Task", "Difficulty", "Planned", "Status"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.Integer.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, false, false, false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        scrDayBlocks.setViewportView(tblDayBlocks);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(pnlSidebar, javax.swing.GroupLayout.PREFERRED_SIZE, 160, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(16, 16, 16)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lblTitle)
                        .addGap(24, 24, 24)
                        .addComponent(btnPrevWeek, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lblWeekRange, javax.swing.GroupLayout.PREFERRED_SIZE, 210, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnNextWeek, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnMyFreeTime)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnGeneratePlan))
                    .addComponent(scrWeek, javax.swing.GroupLayout.DEFAULT_SIZE, 1008, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lblDayTitle)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnSkipBlock)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnMarkBlockDone))
                    .addComponent(scrDayBlocks, javax.swing.GroupLayout.DEFAULT_SIZE, 1008, Short.MAX_VALUE))
                .addGap(16, 16, 16))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(pnlSidebar, javax.swing.GroupLayout.DEFAULT_SIZE, 720, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblTitle)
                    .addComponent(btnPrevWeek, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblWeekRange)
                    .addComponent(btnNextWeek, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnMyFreeTime, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnGeneratePlan, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(scrWeek, javax.swing.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE)
                .addGap(16, 16, 16)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblDayTitle)
                    .addComponent(btnSkipBlock, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnMarkBlockDone, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(scrDayBlocks, javax.swing.GroupLayout.PREFERRED_SIZE, 170, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(16, 16, 16))
        );

        setSize(new java.awt.Dimension(1200, 720));
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void btnPrevWeekActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnPrevWeekActionPerformed
        changeWeek(-1);
    }//GEN-LAST:event_btnPrevWeekActionPerformed

    private void btnNextWeekActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnNextWeekActionPerformed
        changeWeek(1);
    }//GEN-LAST:event_btnNextWeekActionPerformed

    private void btnMyFreeTimeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnMyFreeTimeActionPerformed
        openFreeTime();
    }//GEN-LAST:event_btnMyFreeTimeActionPerformed

    private void btnGeneratePlanActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnGeneratePlanActionPerformed
        generatePlan();
    }//GEN-LAST:event_btnGeneratePlanActionPerformed

    private void btnSkipBlockActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSkipBlockActionPerformed
        skipBlock();
    }//GEN-LAST:event_btnSkipBlockActionPerformed

    private void btnMarkBlockDoneActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnMarkBlockDoneActionPerformed
        markBlockDone();
    }//GEN-LAST:event_btnMarkBlockDoneActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnGeneratePlan;
    private javax.swing.JButton btnMarkBlockDone;
    private javax.swing.JButton btnMyFreeTime;
    private javax.swing.JButton btnNextWeek;
    private javax.swing.JButton btnPrevWeek;
    private javax.swing.JButton btnSkipBlock;
    private javax.swing.JLabel lblDayTitle;
    private javax.swing.JLabel lblTitle;
    private javax.swing.JLabel lblWeekRange;
    private javax.swing.JPanel pnlSidebar;
    private javax.swing.JScrollPane scrDayBlocks;
    private javax.swing.JScrollPane scrWeek;
    private javax.swing.JTable tblDayBlocks;
    private javax.swing.JTable tblWeek;
    // End of variables declaration//GEN-END:variables
}
