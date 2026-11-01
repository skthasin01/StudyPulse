package studypulse.forms;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import studypulse.engine.RecommendationEngine;
import studypulse.engine.RiskAssessment;
import studypulse.engine.RiskReason;
import studypulse.engine.WorkloadCalculator;
import studypulse.model.RiskLevel;
import studypulse.model.RiskSnapshot;
import studypulse.service.RiskReport;
import studypulse.service.RiskService;
import studypulse.service.ServiceException;
import studypulse.util.BadgeRenderer;
import studypulse.util.DateUtil;
import studypulse.util.GaugePanel;
import studypulse.util.RowColorRenderer;
import studypulse.util.Session;
import studypulse.util.TableUtil;
import studypulse.util.UITheme;

/**
 * Today's Study Load Index and burnout risk with the reasons behind every point,
 * recommendations and the last 14 days. Opened on top of another frame (DISPOSE_ON_CLOSE).
 * pnlLoadGauge / pnlRiskGauge are GaugePanels (form: Custom Creation Code).
 */
public class RiskReportFrame extends javax.swing.JFrame {

    private static final int COL_LEVEL = 4;

    private final RiskService riskService = new RiskService();
    private DefaultTableModel reasonsModel;
    private DefaultTableModel historyModel;

    /**
     * Creates new form RiskReportFrame
     */
    public RiskReportFrame() {
        initComponents();
        applyTheme();
        setupTables();
        loadReport();
    }

    private void applyTheme() {
        UITheme.applyFrame(this);
        txtRecommendations.setFont(UITheme.FONT_NORMAL);
        txtRecommendations.setMargin(new java.awt.Insets(8, 8, 8, 8));
        ((GaugePanel) pnlLoadGauge).setTitle("Study Load Index");
        ((GaugePanel) pnlRiskGauge).setTitle("Burnout Risk");
    }

    private void setupTables() {
        reasonsModel = new DefaultTableModel(new String[]{"Reason", "Points"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tblReasons.setModel(reasonsModel);
        UITheme.styleTableWithoutId(tblReasons);
        tblReasons.setRowHeight(26);   // 6 reasons fit without scrolling
        tblReasons.getColumnModel().getColumn(0).setPreferredWidth(370);
        tblReasons.getColumnModel().getColumn(1).setPreferredWidth(60);
        DefaultTableCellRenderer points = new DefaultTableCellRenderer();
        points.setHorizontalAlignment(SwingConstants.RIGHT);
        points.setFont(UITheme.FONT_BOLD);
        tblReasons.getColumnModel().getColumn(1).setCellRenderer(points);

        historyModel = TableUtil.install(tblRiskHistory, "Date", "Load", "Risk", "Level");
        RowColorRenderer.RowColorProvider white = null;
        new RowColorRenderer(white).installOn(tblRiskHistory);
        tblRiskHistory.getColumnModel().getColumn(COL_LEVEL).setCellRenderer(new BadgeRenderer(white,
                new BadgeRenderer.BadgeColorProvider() {
                    @Override
                    public Color badgeColor(Object value) {
                        return UITheme.riskColor(RiskLevel.fromLabel(value));
                    }
                }));
    }

    /** Recalculates today (also saves today's snapshot) and fills every part of the screen. */
    private void loadReport() {
        RiskReport report;
        try {
            report = riskService.calculateToday(Session.getUserId());
        } catch (ServiceException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "StudyPulse", JOptionPane.ERROR_MESSAGE);
            return;
        }
        RiskAssessment risk = report.getRisk();
        lblSubtitle.setText(DateUtil.formatLong(report.getDate()) + "  ·  updated now");
        ((GaugePanel) pnlLoadGauge).setGauge(report.getLoadIndex(), WorkloadCalculator.MAX_INDEX,
                report.getLoadLevel().getLabel(), UITheme.loadColor(report.getLoadLevel()));
        ((GaugePanel) pnlRiskGauge).setGauge(risk.getScore(), 100, risk.getLevel().getLabel(),
                UITheme.riskColor(risk.getLevel()));

        reasonsModel.setRowCount(0);
        for (RiskReason reason : risk.getReasons()) {
            reasonsModel.addRow(new Object[]{reason.getText(), "+" + reason.getPoints()});
        }
        if (risk.getReasons().isEmpty()) {
            reasonsModel.addRow(new Object[]{"No warning signs right now", "0"});
        }
        if (!risk.isWellbeingEnabled()) {
            reasonsModel.addRow(new Object[]{"Well-being tracking is off: only workload rules are used", ""});
        }
        lblTotal.setText(risk.getTotalLine() + "  ·  " + risk.getLevel().getLabel());
        lblTotal.setForeground(UITheme.riskColor(risk.getLevel()));

        StringBuilder text = new StringBuilder();
        for (String rec : report.getRecommendations()) {
            if (RecommendationEngine.DISCLAIMER.equals(rec)) {
                continue;
            }
            if (text.length() > 0) {
                text.append("\n\n");
            }
            text.append("•  ").append(rec);
        }
        txtRecommendations.setText(text.toString());
        txtRecommendations.setCaretPosition(0);
        // Swing HTML widths come out about 25% larger: 310 fits the ~420 px column
        lblDisclaimer.setText("<html><div style='width:310px'>" + RecommendationEngine.DISCLAIMER + "</div></html>");

        loadHistory();
    }

    private void loadHistory() {
        historyModel.setRowCount(0);
        try {
            List<RiskSnapshot> history = riskService.getHistory(Session.getUserId());
            for (RiskSnapshot s : history) {
                historyModel.addRow(new Object[]{s.getId(), DateUtil.formatDay(s.getSnapshotDate()),
                    String.valueOf(Math.round(s.getWorkloadIndex())), String.valueOf(Math.round(s.getBurnoutScore())),
                    s.getRiskLevel().getLabel()});
            }
        } catch (ServiceException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "StudyPulse", JOptionPane.ERROR_MESSAGE);
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

        lblTitle = new javax.swing.JLabel();
        lblSubtitle = new javax.swing.JLabel();
        pnlLoadGauge = new studypulse.util.GaugePanel();
        pnlRiskGauge = new studypulse.util.GaugePanel();
        lblWhyTitle = new javax.swing.JLabel();
        scrReasons = new javax.swing.JScrollPane();
        tblReasons = new javax.swing.JTable();
        lblTotal = new javax.swing.JLabel();
        lblRecTitle = new javax.swing.JLabel();
        scrRecommendations = new javax.swing.JScrollPane();
        txtRecommendations = new javax.swing.JTextArea();
        lblDisclaimer = new javax.swing.JLabel();
        lblHistoryTitle = new javax.swing.JLabel();
        scrRiskHistory = new javax.swing.JScrollPane();
        tblRiskHistory = new javax.swing.JTable();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("StudyPulse – Risk Report");

        lblTitle.setFont(new java.awt.Font("Segoe UI", 1, 22)); // NOI18N
        lblTitle.setForeground(new java.awt.Color(34, 48, 63));
        lblTitle.setText("Risk Report");

        lblSubtitle.setForeground(new java.awt.Color(107, 119, 133));
        lblSubtitle.setText("Today");

        javax.swing.GroupLayout pnlLoadGaugeLayout = new javax.swing.GroupLayout(pnlLoadGauge);
        pnlLoadGauge.setLayout(pnlLoadGaugeLayout);
        pnlLoadGaugeLayout.setHorizontalGroup(
            pnlLoadGaugeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 200, Short.MAX_VALUE)
        );
        pnlLoadGaugeLayout.setVerticalGroup(
            pnlLoadGaugeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 220, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout pnlRiskGaugeLayout = new javax.swing.GroupLayout(pnlRiskGauge);
        pnlRiskGauge.setLayout(pnlRiskGaugeLayout);
        pnlRiskGaugeLayout.setHorizontalGroup(
            pnlRiskGaugeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 200, Short.MAX_VALUE)
        );
        pnlRiskGaugeLayout.setVerticalGroup(
            pnlRiskGaugeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 220, Short.MAX_VALUE)
        );

        lblWhyTitle.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        lblWhyTitle.setForeground(new java.awt.Color(34, 48, 63));
        lblWhyTitle.setText("Why this score?");

        tblReasons.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Reason", "Points"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        scrReasons.setViewportView(tblReasons);

        lblTotal.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        lblTotal.setText("Total: 0 / 100");

        lblRecTitle.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        lblRecTitle.setForeground(new java.awt.Color(34, 48, 63));
        lblRecTitle.setText("Recommendations");

        txtRecommendations.setColumns(20);
        txtRecommendations.setEditable(false);
        txtRecommendations.setLineWrap(true);
        txtRecommendations.setRows(5);
        txtRecommendations.setWrapStyleWord(true);
        scrRecommendations.setViewportView(txtRecommendations);

        lblDisclaimer.setFont(new java.awt.Font("Segoe UI", 2, 12)); // NOI18N
        lblDisclaimer.setForeground(new java.awt.Color(107, 119, 133));
        lblDisclaimer.setText("Disclaimer");

        lblHistoryTitle.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        lblHistoryTitle.setForeground(new java.awt.Color(34, 48, 63));
        lblHistoryTitle.setText("Last 14 days");

        tblRiskHistory.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "ID", "Date", "Load", "Risk", "Level"
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
        scrRiskHistory.setViewportView(tblRiskHistory);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblTitle)
                    .addComponent(lblSubtitle)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(pnlLoadGauge, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(8, 8, 8)
                        .addComponent(pnlRiskGauge, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(16, 16, 16)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblWhyTitle)
                            .addComponent(scrReasons, javax.swing.GroupLayout.DEFAULT_SIZE, 436, Short.MAX_VALUE)
                            .addComponent(lblTotal)))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblRecTitle)
                            .addComponent(scrRecommendations, javax.swing.GroupLayout.DEFAULT_SIZE, 424, Short.MAX_VALUE)
                            .addComponent(lblDisclaimer))
                        .addGap(16, 16, 16)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblHistoryTitle)
                            .addComponent(scrRiskHistory, javax.swing.GroupLayout.DEFAULT_SIZE, 424, Short.MAX_VALUE))))
                .addGap(16, 16, 16))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addComponent(lblTitle)
                .addGap(2, 2, 2)
                .addComponent(lblSubtitle)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(pnlLoadGauge, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(pnlRiskGauge, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lblWhyTitle)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(scrReasons, javax.swing.GroupLayout.DEFAULT_SIZE, 200, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lblTotal)))
                .addGap(16, 16, 16)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lblRecTitle)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(scrRecommendations, javax.swing.GroupLayout.DEFAULT_SIZE, 200, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lblDisclaimer))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lblHistoryTitle)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(scrRiskHistory, javax.swing.GroupLayout.DEFAULT_SIZE, 240, Short.MAX_VALUE)))
                .addGap(16, 16, 16))
        );

        setSize(new java.awt.Dimension(900, 640));
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel lblDisclaimer;
    private javax.swing.JLabel lblHistoryTitle;
    private javax.swing.JLabel lblRecTitle;
    private javax.swing.JLabel lblSubtitle;
    private javax.swing.JLabel lblTitle;
    private javax.swing.JLabel lblTotal;
    private javax.swing.JLabel lblWhyTitle;
    private javax.swing.JPanel pnlLoadGauge;
    private javax.swing.JPanel pnlRiskGauge;
    private javax.swing.JScrollPane scrReasons;
    private javax.swing.JScrollPane scrRecommendations;
    private javax.swing.JScrollPane scrRiskHistory;
    private javax.swing.JTable tblReasons;
    private javax.swing.JTable tblRiskHistory;
    private javax.swing.JTextArea txtRecommendations;
    // End of variables declaration//GEN-END:variables
}
