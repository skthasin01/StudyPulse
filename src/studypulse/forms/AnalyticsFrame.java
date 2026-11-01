package studypulse.forms;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import studypulse.model.TaskStatus;
import studypulse.service.AnalyticsData;
import studypulse.service.AnalyticsService;
import studypulse.service.ServiceException;
import studypulse.util.ChartUtil;
import studypulse.util.ErrorLog;
import studypulse.util.Session;
import studypulse.util.SidebarUtil;
import studypulse.util.UITheme;

/**
 * Four JFreeChart charts for the last 7 days or 4 weeks, an insight line and PNG export.
 * pnlCharts has a 2x2 GridLayout in the form; the ChartPanels are added in loadCharts().
 */
public class AnalyticsFrame extends javax.swing.JFrame {

    private static final String[] FILE_NAMES = {"study-hours", "load-vs-risk", "tasks-by-status", "sleep-vs-stress"};
    private static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEE dd", Locale.ENGLISH);

    private final AnalyticsService analyticsService = new AnalyticsService();
    private final List<JFreeChart> charts = new ArrayList<JFreeChart>();

    /**
     * Creates new form AnalyticsFrame
     */
    public AnalyticsFrame() {
        initComponents();
        SidebarUtil.attach(this, pnlSidebar, SidebarUtil.ANALYTICS);
        applyTheme();
        loadCharts();
    }

    private void applyTheme() {
        UITheme.applyFrame(this);
        pnlCharts.setOpaque(false);
        UITheme.stylePrimaryButton(btnExportPng);
        lblInsight.setOpaque(true);
        lblInsight.setBackground(UITheme.SURFACE);
        lblInsight.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, UITheme.ACCENT),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
    }

    /** 7 or 28 days, from cmbRange. */
    private int selectedDays() {
        return cmbRange.getSelectedIndex() == 1 ? 28 : 7;
    }

    private void loadCharts() {
        AnalyticsData data;
        try {
            data = analyticsService.getAnalytics(Session.getUserId(), selectedDays());
        } catch (ServiceException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "StudyPulse", JOptionPane.ERROR_MESSAGE);
            return;
        }
        charts.clear();
        charts.add(studyHoursChart(data));
        charts.add(loadRiskChart(data));
        charts.add(statusChart(data));
        charts.add(sleepStressChart(data));
        pnlCharts.removeAll();
        for (JFreeChart chart : charts) {
            pnlCharts.add(ChartUtil.panel(chart));
        }
        pnlCharts.revalidate();
        pnlCharts.repaint();

        StringBuilder html = new StringBuilder("<html><div style='width:740px'>");
        for (int i = 0; i < data.getInsights().size(); i++) {
            html.append(i == 0 ? "<b>" : "<br>").append(data.getInsights().get(i)).append(i == 0 ? "</b>" : "");
        }
        lblInsight.setText(html.append("</div></html>").toString());
    }

    /** Chart 1: study hours per day (bar). */
    private JFreeChart studyHoursChart(AnalyticsData data) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        if (data.hasStudyHours()) {
            for (Map.Entry<LocalDate, Double> e : data.getHoursByDay().entrySet()) {
                dataset.addValue(e.getValue(), "Study hours", dayLabel(e.getKey(), data.getDays()));
            }
        }
        return ChartUtil.barChart("Study hours per day", "Hours", dataset, UITheme.ACCENT);
    }

    /** Chart 2: Study Load Index vs Burnout Risk Score (line, 2 series). */
    private JFreeChart loadRiskChart(AnalyticsData data) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        if (data.hasRiskData()) {
            for (Map.Entry<LocalDate, Integer> e : data.getLoadByDay().entrySet()) {
                String day = dayLabel(e.getKey(), data.getDays());
                dataset.addValue(e.getValue(), "Study Load Index", day);
                dataset.addValue(data.getRiskByDay().get(e.getKey()), "Burnout risk", day);
            }
        }
        return ChartUtil.lineChart("Study Load Index vs Burnout Risk", "Score", dataset,
                UITheme.ACCENT, UITheme.DANGER);
    }

    /** Chart 3: tasks by status (pie). */
    private JFreeChart statusChart(AnalyticsData data) {
        DefaultPieDataset<String> dataset = new DefaultPieDataset<String>();
        if (data.hasTasks()) {
            for (Map.Entry<TaskStatus, Integer> e : data.getStatusCounts().entrySet()) {
                if (e.getValue() > 0) {
                    dataset.setValue(e.getKey().getLabel(), e.getValue());
                }
            }
        }
        Map<String, Color> colors = new LinkedHashMap<String, Color>();
        colors.put(TaskStatus.PENDING.getLabel(), UITheme.ACCENT);
        colors.put(TaskStatus.IN_PROGRESS.getLabel(), UITheme.WARNING);
        colors.put(TaskStatus.DONE.getLabel(), UITheme.SUCCESS);
        colors.put(TaskStatus.MISSED.getLabel(), UITheme.DANGER);
        return ChartUtil.pieChart("Tasks by status", dataset, colors);
    }

    /** Chart 4: sleep hours vs stress level (line, 2 series). */
    private JFreeChart sleepStressChart(AnalyticsData data) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        if (data.hasCheckins()) {
            for (Map.Entry<LocalDate, Double> e : data.getSleepByDay().entrySet()) {
                String day = dayLabel(e.getKey(), data.getDays());
                dataset.addValue(e.getValue(), "Sleep (h)", day);
                dataset.addValue(data.getStressByDay().get(e.getKey()), "Stress (1-5)", day);
            }
        }
        return ChartUtil.lineChart("Sleep hours vs stress level", null, dataset, UITheme.ACCENT, UITheme.DANGER);
    }

    /** "Mon 02" for 7 days, "02 Nov" for 4 weeks (short enough for half-width charts). */
    private static String dayLabel(LocalDate date, int days) {
        return date.format(days == 7 ? WEEKDAY : SHORT_DAY);
    }

    /** Saves the four charts as 900x600 PNG files into a chosen folder. */
    private void exportPng() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a folder for the chart images");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File folder = chooser.getSelectedFile();
        String range = selectedDays() == 7 ? "7days" : "4weeks";
        StringBuilder names = new StringBuilder();
        try {
            for (int i = 0; i < charts.size(); i++) {
                File file = new File(folder, "studypulse-" + FILE_NAMES[i] + "-" + range + ".png");
                ChartUtil.savePng(charts.get(i), file);
                names.append("\n").append(file.getName());
            }
            JOptionPane.showMessageDialog(this, "Saved " + charts.size() + " charts (" + ChartUtil.EXPORT_WIDTH
                    + "x" + ChartUtil.EXPORT_HEIGHT + ") to\n" + folder.getAbsolutePath() + names,
                    "Export PNG", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            ErrorLog.log("Could not save chart images", ex);
            JOptionPane.showMessageDialog(this, "Could not save the images. Please choose a folder you can write to.\n"
                    + ErrorLog.SEE_LOG, "Export PNG", JOptionPane.ERROR_MESSAGE);
        }
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
        cmbRange = new javax.swing.JComboBox<>();
        btnExportPng = new javax.swing.JButton();
        pnlCharts = new javax.swing.JPanel();
        lblInsight = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("StudyPulse – Analytics");

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
        lblTitle.setText("Analytics");

        cmbRange.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Last 7 days", "Last 4 weeks" }));
        cmbRange.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmbRangeActionPerformed(evt);
            }
        });

        btnExportPng.setText("Export PNG");
        btnExportPng.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnExportPngActionPerformed(evt);
            }
        });

        pnlCharts.setLayout(new java.awt.GridLayout(2, 2, 16, 16));

        lblInsight.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        lblInsight.setForeground(new java.awt.Color(34, 48, 63));
        lblInsight.setText("Insight");

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
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(cmbRange, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnExportPng))
                    .addComponent(pnlCharts, javax.swing.GroupLayout.DEFAULT_SIZE, 1008, Short.MAX_VALUE)
                    .addComponent(lblInsight, javax.swing.GroupLayout.DEFAULT_SIZE, 1008, Short.MAX_VALUE))
                .addGap(16, 16, 16))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(pnlSidebar, javax.swing.GroupLayout.DEFAULT_SIZE, 720, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblTitle)
                    .addComponent(cmbRange, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnExportPng, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(pnlCharts, javax.swing.GroupLayout.DEFAULT_SIZE, 560, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lblInsight)
                .addGap(16, 16, 16))
        );

        setSize(new java.awt.Dimension(1200, 720));
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void cmbRangeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmbRangeActionPerformed
        loadCharts();
    }//GEN-LAST:event_cmbRangeActionPerformed

    private void btnExportPngActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnExportPngActionPerformed
        exportPng();
    }//GEN-LAST:event_btnExportPngActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnExportPng;
    private javax.swing.JComboBox<String> cmbRange;
    private javax.swing.JLabel lblInsight;
    private javax.swing.JLabel lblTitle;
    private javax.swing.JPanel pnlCharts;
    private javax.swing.JPanel pnlSidebar;
    // End of variables declaration//GEN-END:variables
}
