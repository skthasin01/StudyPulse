package studypulse.forms;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import org.jfree.data.category.DefaultCategoryDataset;
import studypulse.model.PlanBlockItem;
import studypulse.model.PlanStatus;
import studypulse.model.Task;
import studypulse.model.User;
import studypulse.service.AnalyticsService;
import studypulse.service.CheckinService;
import studypulse.service.DashboardSummary;
import studypulse.service.NotificationService;
import studypulse.service.RiskReport;
import studypulse.service.ServiceException;
import studypulse.service.TaskService;
import studypulse.util.ChartUtil;
import studypulse.util.DateUtil;
import studypulse.util.DesktopAlerts;
import studypulse.util.RowColorRenderer;
import studypulse.util.Session;
import studypulse.util.SidebarUtil;
import studypulse.util.TableUtil;
import studypulse.util.UITheme;
import studypulse.util.WeekDayRenderer;

/**
 * Home screen: summary cards, today's plan, upcoming deadlines and the 7-day risk chart.
 * Everything is reloaded whenever the window becomes active (formWindowActivated),
 * e.g. after closing the Risk Report.
 */
public class DashboardFrame extends javax.swing.JFrame {

    private final AnalyticsService analyticsService = new AnalyticsService();
    private final CheckinService checkinService = new CheckinService();
    private final TaskService taskService = new TaskService();
    private final List<PlanBlockItem> shownBlocks = new ArrayList<PlanBlockItem>();
    private final List<Task> shownUpcoming = new ArrayList<Task>();
    private DefaultTableModel planModel;
    private DefaultTableModel upcomingModel;
    /** Shows a load error only once, so the error dialog cannot re-trigger windowActivated forever. */
    private boolean loadErrorShown;
    private final NotificationService notificationService = new NotificationService();
    /** New desktop alerts (DesktopAlerts) update the bell badge while the dashboard is open. */
    private final Runnable bellUpdater = new Runnable() {
        @Override
        public void run() {
            refreshBell();
        }
    };

    /**
     * Creates new form DashboardFrame
     */
    public DashboardFrame() {
        initComponents();
        SidebarUtil.attach(this, pnlSidebar, SidebarUtil.DASHBOARD);
        applyTheme();
        setupTables();
        loadDashboard();
        DesktopAlerts.addListener(bellUpdater);
        // after the frame is visible
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                remindCheckin();
            }
        });
    }

    private void applyTheme() {
        UITheme.applyFrame(this);
        btnBell.putClientProperty("JButton.buttonType", "toolBarButton");
        btnBell.setIcon(UITheme.bellIcon(0));
        UITheme.styleLinkButton(btnRiskDetails);
        UITheme.stylePrimaryButton(btnStartPomodoro);
        UITheme.styleSummaryCard(pnlCardLoad, UITheme.ACCENT);
        UITheme.styleSummaryCard(pnlCardRisk, UITheme.ACCENT);
        UITheme.styleSummaryCard(pnlCardDue, UITheme.ACCENT);
        UITheme.styleSummaryCard(pnlCardHours, UITheme.ACCENT);
    }

    private void setupTables() {
        planModel = TableUtil.install(tblTodayPlan, "Task", "Planned", "Status");
        new RowColorRenderer(new RowColorRenderer.RowColorProvider() {
            @Override
            public Color rowColor(int modelRow) {
                PlanStatus status = shownBlocks.get(modelRow).getBlock().getStatus();
                if (status == PlanStatus.DONE) {
                    return UITheme.ROW_DONE;
                }
                return status == PlanStatus.SKIPPED ? UITheme.ROW_SKIPPED : UITheme.SURFACE;
            }
        }).installOn(tblTodayPlan);
        tblTodayPlan.getColumnModel().getColumn(1).setPreferredWidth(260);

        upcomingModel = TableUtil.install(tblUpcoming, "Task", "Deadline", "Left");
        new RowColorRenderer(new RowColorRenderer.RowColorProvider() {
            @Override
            public Color rowColor(int modelRow) {
                return UITheme.deadlineColor(taskService.getDeadlineState(shownUpcoming.get(modelRow)));
            }
        }).installOn(tblUpcoming);
        tblUpcoming.getColumnModel().getColumn(1).setPreferredWidth(220);
    }

    /** Reads everything from AnalyticsService and fills the screen. */
    private void loadDashboard() {
        DashboardSummary s;
        try {
            s = analyticsService.getDashboard(Session.getUserId());
            loadErrorShown = false;
        } catch (ServiceException ex) {
            if (!loadErrorShown) {
                loadErrorShown = true;
                JOptionPane.showMessageDialog(this, ex.getMessage(), "StudyPulse", JOptionPane.ERROR_MESSAGE);
            }
            return;
        }
        User user = Session.getUser();
        lblWelcome.setText(user == null ? "Welcome" : "Welcome, " + firstName(user.getFullName()));
        lblToday.setText(DateUtil.formatLong(DateUtil.today()));
        btnBell.setIcon(UITheme.bellIcon(s.getUnreadNotifications()));
        btnBell.setToolTipText(s.getUnreadNotifications() + " unread notification"
                + (s.getUnreadNotifications() == 1 ? "" : "s"));

        RiskReport risk = s.getRisk();
        Color loadColor = UITheme.loadColor(risk.getLoadLevel());
        lblLoadValue.setText(String.valueOf(risk.getLoadIndex()));
        lblLoadValue.setForeground(loadColor);
        lblLoadLevel.setText(risk.getLoadLevel().getLabel() + " · next 7 days");
        lblLoadLevel.setForeground(loadColor);
        UITheme.styleSummaryCard(pnlCardLoad, loadColor);

        Color riskColor = UITheme.riskColor(risk.getRisk().getLevel());
        lblRiskValue.setText(String.valueOf(risk.getRisk().getScore()));
        lblRiskValue.setForeground(riskColor);
        lblRiskLevel.setText(risk.getRisk().getLevel().getLabel());
        lblRiskLevel.setForeground(riskColor);
        UITheme.styleSummaryCard(pnlCardRisk, riskColor);

        lblDueValue.setText(String.valueOf(s.getDueThisWeek()));
        Task next = s.getNextTask();
        lblDueNext.setText(next == null ? "No open tasks"
                : "Next: " + next.getTitle() + " (" + taskService.formatTimeLeft(next) + ")");
        lblDueNext.setToolTipText(next == null ? null : next.getTitle());
        UITheme.styleSummaryCard(pnlCardDue, UITheme.ACCENT);

        double diff = s.getHoursThisWeek() - s.getHoursLastWeek();
        lblHoursValue.setText(String.format(Locale.ENGLISH, "%.1f h", s.getHoursThisWeek()));
        lblHoursDiff.setText(String.format(Locale.ENGLISH, "%s%.1f h vs last week",
                diff >= 0 ? "+" : "−", Math.abs(diff)));
        lblHoursDiff.setForeground(diff >= 0 ? UITheme.SUCCESS : UITheme.DANGER);
        UITheme.styleSummaryCard(pnlCardHours, UITheme.SUCCESS);

        fillTodayPlan(s.getTodayBlocks());
        fillUpcoming(s.getUpcoming());
        showRiskChart(s.getRiskLast7Days());
    }

    private void fillTodayPlan(List<PlanBlockItem> blocks) {
        planModel.setRowCount(0);
        shownBlocks.clear();
        for (PlanBlockItem item : blocks) {
            shownBlocks.add(item);
            planModel.addRow(new Object[]{item.getBlock().getId(), item.getTaskTitle(),
                DateUtil.formatMinutes(item.getBlock().getPlannedMinutes()),
                item.getBlock().getStatus().getLabel()});
        }
    }

    private void fillUpcoming(List<Task> tasks) {
        upcomingModel.setRowCount(0);
        shownUpcoming.clear();
        for (Task t : tasks) {
            shownUpcoming.add(t);
            upcomingModel.addRow(new Object[]{t.getId(), t.getTitle(), DateUtil.formatCompact(t.getDeadline()),
                taskService.formatTimeLeft(t)});
        }
    }

    /** Line chart with area fill; the latest point is marked. Empty -> "Not enough data yet". */
    private void showRiskChart(Map<java.time.LocalDate, Integer> riskByDay) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (Map.Entry<java.time.LocalDate, Integer> e : riskByDay.entrySet()) {
            dataset.addValue(e.getValue(), "Risk score", DateUtil.formatDay(e.getKey()));
        }
        pnlRiskChart.removeAll();
        pnlRiskChart.add(ChartUtil.panel(ChartUtil.riskTrendChart("Burnout risk – last 7 days", dataset)),
                BorderLayout.CENTER);
        pnlRiskChart.revalidate();
        pnlRiskChart.repaint();
    }

    /** Once per day: well-being tracking on and no check-in yet today. */
    private void remindCheckin() {
        if (!isDisplayable() || !checkinService.takeDailyReminder()) {
            return;
        }
        Object[] options = {"Check in now", "Later"};
        int choice = JOptionPane.showOptionDialog(this,
                "You have not done today's check-in yet.\nIt takes about one minute.",
                "Daily check-in", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                options, options[0]);
        if (choice == 0) {
            new CheckinFrame().setVisible(true);
            dispose();
        }
    }

    private void openRiskReport() {
        new RiskReportFrame().setVisible(true);
    }

    /** Opens the Pomodoro timer with the selected (or first) task of today's plan. */
    private void startPomodoro() {
        Integer taskId = null;
        int row = tblTodayPlan.getSelectedRow();
        if (row >= 0) {
            taskId = shownBlocks.get(tblTodayPlan.convertRowIndexToModel(row)).getBlock().getTaskId();
        } else {
            for (PlanBlockItem item : shownBlocks) {
                if (item.getBlock().getStatus() == PlanStatus.PLANNED) {
                    taskId = item.getBlock().getTaskId();
                    break;
                }
            }
        }
        PomodoroFrame.showWindow(taskId);
    }

    private void refreshBell() {
        try {
            int unread = notificationService.countUnread();
            btnBell.setIcon(UITheme.bellIcon(unread));
            btnBell.setToolTipText(unread + " unread notification" + (unread == 1 ? "" : "s"));
        } catch (ServiceException ex) {
            // keep the old badge
        }
    }

    @Override
    public void dispose() {
        DesktopAlerts.removeListener(bellUpdater);
        super.dispose();
    }

    private static String firstName(String fullName) {
        String name = fullName == null ? "" : fullName.trim();
        int space = name.indexOf(' ');
        return space > 0 ? name.substring(0, space) : name;
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
        lblWelcome = new javax.swing.JLabel();
        lblToday = new javax.swing.JLabel();
        btnBell = new javax.swing.JButton();
        pnlCardLoad = new javax.swing.JPanel();
        lblLoadTitle = new javax.swing.JLabel();
        lblLoadValue = new javax.swing.JLabel();
        lblLoadLevel = new javax.swing.JLabel();
        pnlCardRisk = new javax.swing.JPanel();
        lblRiskTitle = new javax.swing.JLabel();
        lblRiskValue = new javax.swing.JLabel();
        lblRiskLevel = new javax.swing.JLabel();
        btnRiskDetails = new javax.swing.JButton();
        pnlCardDue = new javax.swing.JPanel();
        lblDueTitle = new javax.swing.JLabel();
        lblDueValue = new javax.swing.JLabel();
        lblDueNext = new javax.swing.JLabel();
        pnlCardHours = new javax.swing.JPanel();
        lblHoursTitle = new javax.swing.JLabel();
        lblHoursValue = new javax.swing.JLabel();
        lblHoursDiff = new javax.swing.JLabel();
        lblTodayPlanTitle = new javax.swing.JLabel();
        btnStartPomodoro = new javax.swing.JButton();
        lblUpcomingTitle = new javax.swing.JLabel();
        scrTodayPlan = new javax.swing.JScrollPane();
        tblTodayPlan = new javax.swing.JTable();
        scrUpcoming = new javax.swing.JScrollPane();
        tblUpcoming = new javax.swing.JTable();
        pnlRiskChart = new javax.swing.JPanel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("StudyPulse – Dashboard");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowActivated(java.awt.event.WindowEvent evt) {
                formWindowActivated(evt);
            }
        });

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

        lblWelcome.setFont(new java.awt.Font("Segoe UI", 1, 22)); // NOI18N
        lblWelcome.setForeground(new java.awt.Color(34, 48, 63));
        lblWelcome.setText("Welcome, Student");

        lblToday.setFont(new java.awt.Font("Segoe UI", 0, 13)); // NOI18N
        lblToday.setForeground(new java.awt.Color(107, 119, 133));
        lblToday.setText("Today");

        btnBell.setToolTipText("Notifications");
        btnBell.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnBellActionPerformed(evt);
            }
        });

        pnlCardLoad.setBackground(new java.awt.Color(255, 255, 255));

        lblLoadTitle.setFont(new java.awt.Font("Segoe UI", 1, 13)); // NOI18N
        lblLoadTitle.setForeground(new java.awt.Color(107, 119, 133));
        lblLoadTitle.setText("Study Load Index");

        lblLoadValue.setFont(new java.awt.Font("Segoe UI", 1, 28)); // NOI18N
        lblLoadValue.setForeground(new java.awt.Color(34, 48, 63));
        lblLoadValue.setText("0");

        lblLoadLevel.setFont(new java.awt.Font("Segoe UI", 0, 13)); // NOI18N
        lblLoadLevel.setText("Light");

        javax.swing.GroupLayout pnlCardLoadLayout = new javax.swing.GroupLayout(pnlCardLoad);
        pnlCardLoad.setLayout(pnlCardLoadLayout);
        pnlCardLoadLayout.setHorizontalGroup(
            pnlCardLoadLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlCardLoadLayout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addGroup(pnlCardLoadLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblLoadTitle)
                    .addComponent(lblLoadValue)
                    .addComponent(lblLoadLevel, 0, 204, Short.MAX_VALUE))
                .addGap(16, 16, 16))
        );
        pnlCardLoadLayout.setVerticalGroup(
            pnlCardLoadLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlCardLoadLayout.createSequentialGroup()
                .addGap(14, 14, 14)
                .addComponent(lblLoadTitle)
                .addGap(2, 2, 2)
                .addComponent(lblLoadValue)
                .addGap(2, 2, 2)
                .addComponent(lblLoadLevel)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pnlCardRisk.setBackground(new java.awt.Color(255, 255, 255));

        lblRiskTitle.setFont(new java.awt.Font("Segoe UI", 1, 13)); // NOI18N
        lblRiskTitle.setForeground(new java.awt.Color(107, 119, 133));
        lblRiskTitle.setText("Burnout Risk");

        lblRiskValue.setFont(new java.awt.Font("Segoe UI", 1, 28)); // NOI18N
        lblRiskValue.setForeground(new java.awt.Color(34, 48, 63));
        lblRiskValue.setText("0");

        lblRiskLevel.setFont(new java.awt.Font("Segoe UI", 0, 13)); // NOI18N
        lblRiskLevel.setText("Low");

        btnRiskDetails.setText("View details");
        btnRiskDetails.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRiskDetailsActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout pnlCardRiskLayout = new javax.swing.GroupLayout(pnlCardRisk);
        pnlCardRisk.setLayout(pnlCardRiskLayout);
        pnlCardRiskLayout.setHorizontalGroup(
            pnlCardRiskLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlCardRiskLayout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addGroup(pnlCardRiskLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblRiskTitle)
                    .addComponent(lblRiskValue)
                    .addGroup(pnlCardRiskLayout.createSequentialGroup()
                        .addComponent(lblRiskLevel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnRiskDetails)))
                .addGap(16, 16, 16))
        );
        pnlCardRiskLayout.setVerticalGroup(
            pnlCardRiskLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlCardRiskLayout.createSequentialGroup()
                .addGap(14, 14, 14)
                .addComponent(lblRiskTitle)
                .addGap(2, 2, 2)
                .addComponent(lblRiskValue)
                .addGap(2, 2, 2)
                .addGroup(pnlCardRiskLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblRiskLevel)
                    .addComponent(btnRiskDetails))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pnlCardDue.setBackground(new java.awt.Color(255, 255, 255));

        lblDueTitle.setFont(new java.awt.Font("Segoe UI", 1, 13)); // NOI18N
        lblDueTitle.setForeground(new java.awt.Color(107, 119, 133));
        lblDueTitle.setText("Due this week");

        lblDueValue.setFont(new java.awt.Font("Segoe UI", 1, 28)); // NOI18N
        lblDueValue.setForeground(new java.awt.Color(34, 48, 63));
        lblDueValue.setText("0");

        lblDueNext.setFont(new java.awt.Font("Segoe UI", 0, 13)); // NOI18N
        lblDueNext.setText("No open tasks");

        javax.swing.GroupLayout pnlCardDueLayout = new javax.swing.GroupLayout(pnlCardDue);
        pnlCardDue.setLayout(pnlCardDueLayout);
        pnlCardDueLayout.setHorizontalGroup(
            pnlCardDueLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlCardDueLayout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addGroup(pnlCardDueLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblDueTitle)
                    .addComponent(lblDueValue)
                    .addComponent(lblDueNext, 0, 204, Short.MAX_VALUE))
                .addGap(16, 16, 16))
        );
        pnlCardDueLayout.setVerticalGroup(
            pnlCardDueLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlCardDueLayout.createSequentialGroup()
                .addGap(14, 14, 14)
                .addComponent(lblDueTitle)
                .addGap(2, 2, 2)
                .addComponent(lblDueValue)
                .addGap(2, 2, 2)
                .addComponent(lblDueNext)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pnlCardHours.setBackground(new java.awt.Color(255, 255, 255));

        lblHoursTitle.setFont(new java.awt.Font("Segoe UI", 1, 13)); // NOI18N
        lblHoursTitle.setForeground(new java.awt.Color(107, 119, 133));
        lblHoursTitle.setText("Study hours this week");

        lblHoursValue.setFont(new java.awt.Font("Segoe UI", 1, 28)); // NOI18N
        lblHoursValue.setForeground(new java.awt.Color(34, 48, 63));
        lblHoursValue.setText("0.0 h");

        lblHoursDiff.setFont(new java.awt.Font("Segoe UI", 0, 13)); // NOI18N
        lblHoursDiff.setText("+0.0 h vs last week");

        javax.swing.GroupLayout pnlCardHoursLayout = new javax.swing.GroupLayout(pnlCardHours);
        pnlCardHours.setLayout(pnlCardHoursLayout);
        pnlCardHoursLayout.setHorizontalGroup(
            pnlCardHoursLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlCardHoursLayout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addGroup(pnlCardHoursLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblHoursTitle)
                    .addComponent(lblHoursValue)
                    .addComponent(lblHoursDiff, 0, 204, Short.MAX_VALUE))
                .addGap(16, 16, 16))
        );
        pnlCardHoursLayout.setVerticalGroup(
            pnlCardHoursLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlCardHoursLayout.createSequentialGroup()
                .addGap(14, 14, 14)
                .addComponent(lblHoursTitle)
                .addGap(2, 2, 2)
                .addComponent(lblHoursValue)
                .addGap(2, 2, 2)
                .addComponent(lblHoursDiff)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        lblTodayPlanTitle.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        lblTodayPlanTitle.setForeground(new java.awt.Color(34, 48, 63));
        lblTodayPlanTitle.setText("Today's plan");

        btnStartPomodoro.setText("Start Pomodoro");
        btnStartPomodoro.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnStartPomodoroActionPerformed(evt);
            }
        });

        lblUpcomingTitle.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        lblUpcomingTitle.setForeground(new java.awt.Color(34, 48, 63));
        lblUpcomingTitle.setText("Upcoming deadlines");

        tblTodayPlan.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "ID", "Task", "Planned", "Status"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.Integer.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, false, false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        scrTodayPlan.setViewportView(tblTodayPlan);

        tblUpcoming.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "ID", "Task", "Deadline", "Left"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.Integer.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, false, false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        scrUpcoming.setViewportView(tblUpcoming);

        pnlRiskChart.setBackground(new java.awt.Color(255, 255, 255));
        pnlRiskChart.setLayout(new java.awt.BorderLayout());

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(pnlSidebar, javax.swing.GroupLayout.PREFERRED_SIZE, 160, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(24, 24, 24)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblWelcome)
                            .addComponent(lblToday))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnBell, javax.swing.GroupLayout.PREFERRED_SIZE, 48, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(pnlCardLoad, javax.swing.GroupLayout.DEFAULT_SIZE, 236, Short.MAX_VALUE)
                        .addGap(16, 16, 16)
                        .addComponent(pnlCardRisk, javax.swing.GroupLayout.DEFAULT_SIZE, 236, Short.MAX_VALUE)
                        .addGap(16, 16, 16)
                        .addComponent(pnlCardDue, javax.swing.GroupLayout.DEFAULT_SIZE, 236, Short.MAX_VALUE)
                        .addGap(16, 16, 16)
                        .addComponent(pnlCardHours, javax.swing.GroupLayout.DEFAULT_SIZE, 236, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(lblTodayPlanTitle)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(btnStartPomodoro))
                            .addComponent(scrTodayPlan, javax.swing.GroupLayout.DEFAULT_SIZE, 488, Short.MAX_VALUE))
                        .addGap(16, 16, 16)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblUpcomingTitle)
                            .addComponent(scrUpcoming, javax.swing.GroupLayout.DEFAULT_SIZE, 488, Short.MAX_VALUE)))
                    .addComponent(pnlRiskChart, javax.swing.GroupLayout.DEFAULT_SIZE, 992, Short.MAX_VALUE))
                .addGap(24, 24, 24))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(pnlSidebar, javax.swing.GroupLayout.DEFAULT_SIZE, 720, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addGap(20, 20, 20)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lblWelcome)
                        .addGap(2, 2, 2)
                        .addComponent(lblToday))
                    .addComponent(btnBell, javax.swing.GroupLayout.PREFERRED_SIZE, 44, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(16, 16, 16)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(pnlCardLoad, javax.swing.GroupLayout.PREFERRED_SIZE, 112, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(pnlCardRisk, javax.swing.GroupLayout.PREFERRED_SIZE, 112, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(pnlCardDue, javax.swing.GroupLayout.PREFERRED_SIZE, 112, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(pnlCardHours, javax.swing.GroupLayout.PREFERRED_SIZE, 112, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(16, 16, 16)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblTodayPlanTitle)
                    .addComponent(btnStartPomodoro, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblUpcomingTitle))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(scrTodayPlan, javax.swing.GroupLayout.DEFAULT_SIZE, 160, Short.MAX_VALUE)
                    .addComponent(scrUpcoming, javax.swing.GroupLayout.DEFAULT_SIZE, 160, Short.MAX_VALUE))
                .addGap(16, 16, 16)
                .addComponent(pnlRiskChart, javax.swing.GroupLayout.DEFAULT_SIZE, 210, Short.MAX_VALUE)
                .addGap(20, 20, 20))
        );

        setSize(new java.awt.Dimension(1200, 720));
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void formWindowActivated(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowActivated
        loadDashboard();
    }//GEN-LAST:event_formWindowActivated

    private void btnBellActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnBellActionPerformed
        SidebarUtil.navigate(this, SidebarUtil.NOTIFICATIONS);
    }//GEN-LAST:event_btnBellActionPerformed

    private void btnRiskDetailsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRiskDetailsActionPerformed
        openRiskReport();
    }//GEN-LAST:event_btnRiskDetailsActionPerformed

    private void btnStartPomodoroActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnStartPomodoroActionPerformed
        startPomodoro();
    }//GEN-LAST:event_btnStartPomodoroActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnBell;
    private javax.swing.JButton btnRiskDetails;
    private javax.swing.JButton btnStartPomodoro;
    private javax.swing.JLabel lblDueNext;
    private javax.swing.JLabel lblDueTitle;
    private javax.swing.JLabel lblDueValue;
    private javax.swing.JLabel lblHoursDiff;
    private javax.swing.JLabel lblHoursTitle;
    private javax.swing.JLabel lblHoursValue;
    private javax.swing.JLabel lblLoadLevel;
    private javax.swing.JLabel lblLoadTitle;
    private javax.swing.JLabel lblLoadValue;
    private javax.swing.JLabel lblRiskLevel;
    private javax.swing.JLabel lblRiskTitle;
    private javax.swing.JLabel lblRiskValue;
    private javax.swing.JLabel lblToday;
    private javax.swing.JLabel lblTodayPlanTitle;
    private javax.swing.JLabel lblUpcomingTitle;
    private javax.swing.JLabel lblWelcome;
    private javax.swing.JPanel pnlCardDue;
    private javax.swing.JPanel pnlCardHours;
    private javax.swing.JPanel pnlCardLoad;
    private javax.swing.JPanel pnlCardRisk;
    private javax.swing.JPanel pnlRiskChart;
    private javax.swing.JPanel pnlSidebar;
    private javax.swing.JScrollPane scrTodayPlan;
    private javax.swing.JScrollPane scrUpcoming;
    private javax.swing.JTable tblTodayPlan;
    private javax.swing.JTable tblUpcoming;
    // End of variables declaration//GEN-END:variables
}
