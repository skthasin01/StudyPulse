package studypulse.forms;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.JToggleButton;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import studypulse.model.Checkin;
import studypulse.service.CheckinService;
import studypulse.service.ServiceException;
import studypulse.util.DateUtil;
import studypulse.util.SidebarUtil;
import studypulse.util.TableUtil;
import studypulse.util.UITheme;
import studypulse.util.WarningCellRenderer;

/**
 * Daily stress / energy / mood / sleep check-in and the 14-day history.
 * Toggle button i (1-5) in each group stands for level i.
 */
public class CheckinFrame extends javax.swing.JFrame {

    private static final int COL_STRESS = 2;
    private static final int COL_ENERGY = 3;
    private static final int COL_SLEEP = 5;

    private final CheckinService checkinService = new CheckinService();
    private final List<Checkin> shownHistory = new ArrayList<Checkin>();
    private JToggleButton[] stressButtons;
    private JToggleButton[] energyButtons;
    private JToggleButton[] moodButtons;
    private DefaultTableModel historyModel;

    /**
     * Creates new form CheckinFrame
     */
    public CheckinFrame() {
        initComponents();
        SidebarUtil.attach(this, pnlSidebar, SidebarUtil.CHECKIN);
        stressButtons = new JToggleButton[]{tglStress1, tglStress2, tglStress3, tglStress4, tglStress5};
        energyButtons = new JToggleButton[]{tglEnergy1, tglEnergy2, tglEnergy3, tglEnergy4, tglEnergy5};
        moodButtons = new JToggleButton[]{tglMood1, tglMood2, tglMood3, tglMood4, tglMood5};
        applyTheme();
        setupFields();
        setupTable();
        loadToday();
        loadHistory();
    }

    private void applyTheme() {
        UITheme.applyFrame(this);
        UITheme.styleCard(pnlCheckin);
        UITheme.stylePrimaryButton(btnSaveCheckin);
        for (JToggleButton[] group : new JToggleButton[][]{stressButtons, energyButtons, moodButtons}) {
            for (JToggleButton b : group) {
                UITheme.styleToggle(b);
            }
        }
        for (JToggleButton b : moodButtons) {
            b.setFont(UITheme.FONT_NORMAL.deriveFont(12f));
        }
        lblSubtitle.setText(DateUtil.formatLong(DateUtil.today()) + "  ·  takes about one minute");
        txtNote.putClientProperty("JTextField.placeholderText", "e.g. Slept late because of the lab report");
    }

    /** Note: at most 120 characters, with a live counter. */
    private void setupFields() {
        ((AbstractDocument) txtNote.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
                    throws BadLocationException {
                replace(fb, offset, 0, text, attr);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                    throws BadLocationException {
                String add = text == null ? "" : text;
                int room = CheckinService.MAX_NOTE_LENGTH - (fb.getDocument().getLength() - length);
                if (add.length() > room) {
                    add = add.substring(0, Math.max(0, room));
                }
                super.replace(fb, offset, length, add, attrs);
            }
        });
        txtNote.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateNoteCount();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateNoteCount();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateNoteCount();
            }
        });
    }

    private void setupTable() {
        historyModel = TableUtil.install(tblHistory, "Date", "Stress", "Energy", "Mood", "Sleep (h)", "Note");
        new WarningCellRenderer(null, new WarningCellRenderer.WarningProvider() {
            @Override
            public boolean isWarning(int modelRow, int modelColumn) {
                Checkin c = shownHistory.get(modelRow);
                switch (modelColumn) {
                    case COL_STRESS:
                        return c.getStressLevel() >= CheckinService.HIGH_STRESS;
                    case COL_ENERGY:
                        return c.getEnergyLevel() <= CheckinService.LOW_ENERGY;
                    case COL_SLEEP:
                        return c.getSleepHours() < CheckinService.LOW_SLEEP_HOURS;
                    default:
                        return false;
                }
            }
        }).installOn(tblHistory);
        int[] widths = {45, 95, 60, 60, 75, 70, 260};
        TableColumnModel columns = tblHistory.getColumnModel();
        for (int i = 1; i < widths.length; i++) {
            columns.getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    /** Fills the form with today's check-in, or shows the "turned off" message. */
    private void loadToday() {
        boolean enabled = checkinService.isTrackingEnabled();
        setFormEnabled(enabled);
        if (!enabled) {
            // Swing HTML widths come out about 25% larger, so 210 fits the 288 px column
            lblDisabled.setText("<html><div style='width:210px'>Well-being tracking is turned off. "
                    + "You can turn it on in Settings.</div></html>");
            lblDisabled.setVisible(true);
            return;
        }
        lblDisabled.setVisible(false);
        try {
            Checkin today = checkinService.getToday();
            if (today == null) {
                btnSaveCheckin.setText("Save check-in");
                return;
            }
            select(stressButtons, today.getStressLevel());
            select(energyButtons, today.getEnergyLevel());
            select(moodButtons, today.getMoodLevel());
            spnSleep.setValue(today.getSleepHours());
            txtNote.setText(today.getNote() == null ? "" : today.getNote());
            btnSaveCheckin.setText("Update check-in");
        } catch (ServiceException ex) {
            showError(ex.getMessage());
        }
    }

    private void loadHistory() {
        historyModel.setRowCount(0);
        shownHistory.clear();
        try {
            for (Checkin c : checkinService.getHistory()) {
                shownHistory.add(c);
                historyModel.addRow(new Object[]{c.getId(), DateUtil.formatDay(c.getCheckinDate()),
                    c.getStressLevel(), c.getEnergyLevel(), CheckinService.moodLabel(c.getMoodLevel()),
                    formatHours(c.getSleepHours()), c.getNote() == null ? "" : c.getNote()});
            }
            int streak = checkinService.getStreak();
            lblStreak.setText("Check-in streak: " + streak + (streak == 1 ? " day" : " days"));
        } catch (ServiceException ex) {
            showError(ex.getMessage());
        }
    }

    private void saveCheckin() {
        try {
            spnSleep.commitEdit();
        } catch (java.text.ParseException ex) {
            // keep the last valid value
        }
        try {
            boolean isNew = checkinService.saveToday(selected(stressButtons), selected(energyButtons),
                    selected(moodButtons), ((Number) spnSleep.getValue()).doubleValue(), txtNote.getText());
            btnSaveCheckin.setText("Update check-in");
            loadHistory();
            JOptionPane.showMessageDialog(this, isNew ? "Check-in saved. Thank you!" : "Check-in updated.",
                    "StudyPulse", JOptionPane.INFORMATION_MESSAGE);
        } catch (ServiceException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "StudyPulse", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void setFormEnabled(boolean enabled) {
        for (JToggleButton[] group : new JToggleButton[][]{stressButtons, energyButtons, moodButtons}) {
            for (JToggleButton b : group) {
                b.setEnabled(enabled);
            }
        }
        spnSleep.setEnabled(enabled);
        txtNote.setEnabled(enabled);
        btnSaveCheckin.setEnabled(enabled);
    }

    private void updateNoteCount() {
        int length = txtNote.getText().length();
        lblNoteCount.setText(length + " / " + CheckinService.MAX_NOTE_LENGTH);
        lblNoteCount.setForeground(length >= CheckinService.MAX_NOTE_LENGTH ? UITheme.DANGER : UITheme.TEXT_MUTED);
    }

    /** Level 1-5 of the selected button, 0 if none is selected. */
    private static int selected(JToggleButton[] buttons) {
        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i].isSelected()) {
                return i + 1;
            }
        }
        return 0;
    }

    private static void select(JToggleButton[] buttons, int level) {
        if (level >= 1 && level <= buttons.length) {
            buttons[level - 1].setSelected(true);
        }
    }

    /** 7.0 -> "7", 6.5 -> "6.5". */
    private static String formatHours(double hours) {
        return hours == Math.floor(hours) ? String.valueOf((int) hours) : String.valueOf(hours);
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

        grpStress = new javax.swing.ButtonGroup();
        grpEnergy = new javax.swing.ButtonGroup();
        grpMood = new javax.swing.ButtonGroup();
        pnlSidebar = new javax.swing.JPanel();
        lblTitle = new javax.swing.JLabel();
        lblSubtitle = new javax.swing.JLabel();
        pnlCheckin = new javax.swing.JPanel();
        lblPanelTitle = new javax.swing.JLabel();
        lblDisabled = new javax.swing.JLabel();
        lblStress = new javax.swing.JLabel();
        tglStress1 = new javax.swing.JToggleButton();
        tglStress2 = new javax.swing.JToggleButton();
        tglStress3 = new javax.swing.JToggleButton();
        tglStress4 = new javax.swing.JToggleButton();
        tglStress5 = new javax.swing.JToggleButton();
        lblEnergy = new javax.swing.JLabel();
        tglEnergy1 = new javax.swing.JToggleButton();
        tglEnergy2 = new javax.swing.JToggleButton();
        tglEnergy3 = new javax.swing.JToggleButton();
        tglEnergy4 = new javax.swing.JToggleButton();
        tglEnergy5 = new javax.swing.JToggleButton();
        lblMood = new javax.swing.JLabel();
        tglMood1 = new javax.swing.JToggleButton();
        tglMood2 = new javax.swing.JToggleButton();
        tglMood3 = new javax.swing.JToggleButton();
        tglMood4 = new javax.swing.JToggleButton();
        tglMood5 = new javax.swing.JToggleButton();
        lblSleep = new javax.swing.JLabel();
        spnSleep = new javax.swing.JSpinner();
        lblNote = new javax.swing.JLabel();
        lblNoteCount = new javax.swing.JLabel();
        txtNote = new javax.swing.JTextField();
        btnSaveCheckin = new javax.swing.JButton();
        lblHistoryTitle = new javax.swing.JLabel();
        scrHistory = new javax.swing.JScrollPane();
        tblHistory = new javax.swing.JTable();
        lblStreak = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("StudyPulse – Daily Check-in");

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
        lblTitle.setText("Daily Check-in");

        lblSubtitle.setForeground(new java.awt.Color(107, 119, 133));
        lblSubtitle.setText("Today · takes about one minute");

        pnlCheckin.setBackground(new java.awt.Color(255, 255, 255));

        lblPanelTitle.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        lblPanelTitle.setForeground(new java.awt.Color(34, 48, 63));
        lblPanelTitle.setText("Today's check-in");

        lblDisabled.setForeground(new java.awt.Color(214, 69, 69));
        lblDisabled.setText("Well-being tracking is turned off.");

        lblStress.setText("Stress level (1 very low – 5 very high)");

        grpStress.add(tglStress1);
        tglStress1.setText("1");

        grpStress.add(tglStress2);
        tglStress2.setText("2");

        grpStress.add(tglStress3);
        tglStress3.setText("3");

        grpStress.add(tglStress4);
        tglStress4.setText("4");

        grpStress.add(tglStress5);
        tglStress5.setText("5");

        lblEnergy.setText("Energy level (1 exhausted – 5 full)");

        grpEnergy.add(tglEnergy1);
        tglEnergy1.setText("1");

        grpEnergy.add(tglEnergy2);
        tglEnergy2.setText("2");

        grpEnergy.add(tglEnergy3);
        tglEnergy3.setText("3");

        grpEnergy.add(tglEnergy4);
        tglEnergy4.setText("4");

        grpEnergy.add(tglEnergy5);
        tglEnergy5.setText("5");

        lblMood.setText("Mood");

        grpMood.add(tglMood1);
        tglMood1.setText("Very bad");

        grpMood.add(tglMood2);
        tglMood2.setText("Bad");

        grpMood.add(tglMood3);
        tglMood3.setText("Okay");

        grpMood.add(tglMood4);
        tglMood4.setText("Good");

        grpMood.add(tglMood5);
        tglMood5.setText("Great");

        lblSleep.setText("Sleep last night (hours)");

        spnSleep.setModel(new javax.swing.SpinnerNumberModel(7.0d, 0.0d, 14.0d, 0.5d));

        lblNote.setText("Note (optional)");

        lblNoteCount.setForeground(new java.awt.Color(107, 119, 133));
        lblNoteCount.setText("0 / 120");

        btnSaveCheckin.setText("Save check-in");
        btnSaveCheckin.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSaveCheckinActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout pnlCheckinLayout = new javax.swing.GroupLayout(pnlCheckin);
        pnlCheckin.setLayout(pnlCheckinLayout);
        pnlCheckinLayout.setHorizontalGroup(
            pnlCheckinLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlCheckinLayout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addGroup(pnlCheckinLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblPanelTitle)
                    .addComponent(lblDisabled, javax.swing.GroupLayout.DEFAULT_SIZE, 288, Short.MAX_VALUE)
                    .addComponent(lblStress)
                    .addGroup(pnlCheckinLayout.createSequentialGroup()
                        .addComponent(tglStress1, 0, 52, Short.MAX_VALUE)
                        .addGap(6, 6, 6)
                        .addComponent(tglStress2, 0, 52, Short.MAX_VALUE)
                        .addGap(6, 6, 6)
                        .addComponent(tglStress3, 0, 52, Short.MAX_VALUE)
                        .addGap(6, 6, 6)
                        .addComponent(tglStress4, 0, 52, Short.MAX_VALUE)
                        .addGap(6, 6, 6)
                        .addComponent(tglStress5, 0, 52, Short.MAX_VALUE))
                    .addComponent(lblEnergy)
                    .addGroup(pnlCheckinLayout.createSequentialGroup()
                        .addComponent(tglEnergy1, 0, 52, Short.MAX_VALUE)
                        .addGap(6, 6, 6)
                        .addComponent(tglEnergy2, 0, 52, Short.MAX_VALUE)
                        .addGap(6, 6, 6)
                        .addComponent(tglEnergy3, 0, 52, Short.MAX_VALUE)
                        .addGap(6, 6, 6)
                        .addComponent(tglEnergy4, 0, 52, Short.MAX_VALUE)
                        .addGap(6, 6, 6)
                        .addComponent(tglEnergy5, 0, 52, Short.MAX_VALUE))
                    .addComponent(lblMood)
                    .addGroup(pnlCheckinLayout.createSequentialGroup()
                        .addComponent(tglMood1, 0, 54, Short.MAX_VALUE)
                        .addGap(4, 4, 4)
                        .addComponent(tglMood2, 0, 54, Short.MAX_VALUE)
                        .addGap(4, 4, 4)
                        .addComponent(tglMood3, 0, 54, Short.MAX_VALUE)
                        .addGap(4, 4, 4)
                        .addComponent(tglMood4, 0, 54, Short.MAX_VALUE)
                        .addGap(4, 4, 4)
                        .addComponent(tglMood5, 0, 54, Short.MAX_VALUE))
                    .addComponent(lblSleep)
                    .addComponent(spnSleep, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(pnlCheckinLayout.createSequentialGroup()
                        .addComponent(lblNote)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(lblNoteCount))
                    .addComponent(txtNote, javax.swing.GroupLayout.DEFAULT_SIZE, 288, Short.MAX_VALUE)
                    .addComponent(btnSaveCheckin, javax.swing.GroupLayout.DEFAULT_SIZE, 288, Short.MAX_VALUE))
                .addGap(16, 16, 16))
        );
        pnlCheckinLayout.setVerticalGroup(
            pnlCheckinLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlCheckinLayout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addComponent(lblPanelTitle)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblDisabled)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lblStress)
                .addGap(6, 6, 6)
                .addGroup(pnlCheckinLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tglStress1, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tglStress2, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tglStress3, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tglStress4, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tglStress5, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(16, 16, 16)
                .addComponent(lblEnergy)
                .addGap(6, 6, 6)
                .addGroup(pnlCheckinLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tglEnergy1, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tglEnergy2, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tglEnergy3, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tglEnergy4, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tglEnergy5, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(16, 16, 16)
                .addComponent(lblMood)
                .addGap(6, 6, 6)
                .addGroup(pnlCheckinLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tglMood1, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tglMood2, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tglMood3, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tglMood4, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tglMood5, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(16, 16, 16)
                .addComponent(lblSleep)
                .addGap(6, 6, 6)
                .addComponent(spnSleep, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(16, 16, 16)
                .addGroup(pnlCheckinLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblNote)
                    .addComponent(lblNoteCount))
                .addGap(6, 6, 6)
                .addComponent(txtNote, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(20, 20, 20)
                .addComponent(btnSaveCheckin, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        lblHistoryTitle.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        lblHistoryTitle.setForeground(new java.awt.Color(34, 48, 63));
        lblHistoryTitle.setText("Last 14 days");

        tblHistory.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "ID", "Date", "Stress", "Energy", "Mood", "Sleep (h)", "Note"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.Integer.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, false, false, false, false, false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        scrHistory.setViewportView(tblHistory);

        lblStreak.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        lblStreak.setForeground(new java.awt.Color(46, 125, 154));
        lblStreak.setText("Check-in streak: 0 days");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(pnlSidebar, javax.swing.GroupLayout.PREFERRED_SIZE, 160, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(16, 16, 16)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblTitle)
                    .addComponent(lblSubtitle)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(pnlCheckin, javax.swing.GroupLayout.PREFERRED_SIZE, 320, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(16, 16, 16)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblHistoryTitle)
                            .addComponent(scrHistory, javax.swing.GroupLayout.DEFAULT_SIZE, 672, Short.MAX_VALUE)
                            .addComponent(lblStreak))))
                .addGap(16, 16, 16))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(pnlSidebar, javax.swing.GroupLayout.DEFAULT_SIZE, 720, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addComponent(lblTitle)
                .addGap(2, 2, 2)
                .addComponent(lblSubtitle)
                .addGap(16, 16, 16)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(pnlCheckin, javax.swing.GroupLayout.DEFAULT_SIZE, 612, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lblHistoryTitle)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(scrHistory, javax.swing.GroupLayout.DEFAULT_SIZE, 560, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(lblStreak)))
                .addGap(16, 16, 16))
        );

        setSize(new java.awt.Dimension(1200, 720));
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void btnSaveCheckinActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSaveCheckinActionPerformed
        saveCheckin();
    }//GEN-LAST:event_btnSaveCheckinActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnSaveCheckin;
    private javax.swing.ButtonGroup grpEnergy;
    private javax.swing.ButtonGroup grpMood;
    private javax.swing.ButtonGroup grpStress;
    private javax.swing.JLabel lblDisabled;
    private javax.swing.JLabel lblEnergy;
    private javax.swing.JLabel lblHistoryTitle;
    private javax.swing.JLabel lblMood;
    private javax.swing.JLabel lblNote;
    private javax.swing.JLabel lblNoteCount;
    private javax.swing.JLabel lblPanelTitle;
    private javax.swing.JLabel lblSleep;
    private javax.swing.JLabel lblStreak;
    private javax.swing.JLabel lblStress;
    private javax.swing.JLabel lblSubtitle;
    private javax.swing.JLabel lblTitle;
    private javax.swing.JPanel pnlCheckin;
    private javax.swing.JPanel pnlSidebar;
    private javax.swing.JScrollPane scrHistory;
    private javax.swing.JSpinner spnSleep;
    private javax.swing.JTable tblHistory;
    private javax.swing.JToggleButton tglEnergy1;
    private javax.swing.JToggleButton tglEnergy2;
    private javax.swing.JToggleButton tglEnergy3;
    private javax.swing.JToggleButton tglEnergy4;
    private javax.swing.JToggleButton tglEnergy5;
    private javax.swing.JToggleButton tglMood1;
    private javax.swing.JToggleButton tglMood2;
    private javax.swing.JToggleButton tglMood3;
    private javax.swing.JToggleButton tglMood4;
    private javax.swing.JToggleButton tglMood5;
    private javax.swing.JToggleButton tglStress1;
    private javax.swing.JToggleButton tglStress2;
    private javax.swing.JToggleButton tglStress3;
    private javax.swing.JToggleButton tglStress4;
    private javax.swing.JToggleButton tglStress5;
    private javax.swing.JTextField txtNote;
    // End of variables declaration//GEN-END:variables
}
