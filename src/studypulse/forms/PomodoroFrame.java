package studypulse.forms;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JOptionPane;
import javax.swing.Timer;
import studypulse.engine.PomodoroCycle;
import studypulse.model.Notification;
import studypulse.model.PomodoroPhase;
import studypulse.model.Task;
import studypulse.model.UserSettings;
import studypulse.service.NotificationService;
import studypulse.service.PomodoroService;
import studypulse.service.ServiceException;
import studypulse.service.SettingsService;
import studypulse.util.DateUtil;
import studypulse.util.DesktopAlerts;
import studypulse.util.UITheme;

/**
 * Pomodoro timer (one window at a time, opened on top of other screens with
 * {@link #showWindow}). Focus 25 / short break 5 / long break 15 minutes by default,
 * long break after 4 focus rounds. Every finished focus round is saved as a study_logs row.
 */
public class PomodoroFrame extends javax.swing.JFrame {

    private static PomodoroFrame instance;

    private final PomodoroService pomodoroService = new PomodoroService();
    private final NotificationService notificationService = new NotificationService();
    private final SettingsService settingsService = new SettingsService();
    private final PomodoroCycle cycle = new PomodoroCycle();
    /** Task id per cmbTask index; index 0 = "No task" (null). */
    private final List<Integer> taskIds = new ArrayList<Integer>();
    private Timer ticker;
    private int phaseSeconds;
    private int remainingSeconds;
    private boolean running;
    /** Start of the current focus round (null until it is started). */
    private LocalDateTime focusStart;
    /** Seconds actually counted in the current focus round (pauses not included). */
    private int focusElapsedSeconds;
    /** Saved focus minutes since the last finished (not skipped) break. */
    private int minutesWithoutBreak;
    private boolean breakReminded;

    /**
     * Creates new form PomodoroFrame
     */
    public PomodoroFrame() {
        initComponents();
        ticker = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                tick();
            }
        });
        applyTheme();
        loadDefaults();
        loadTasks();
        preparePhase();
    }

    /** Opens (or brings to front) the single Pomodoro window; taskId may be null. */
    public static void showWindow(Integer taskId) {
        if (instance == null || !instance.isDisplayable()) {
            instance = new PomodoroFrame();
        }
        if (taskId != null) {
            instance.selectTask(taskId);
        }
        instance.setVisible(true);
        instance.toFront();
    }

    /** Closes the window (e.g. on logout), asking to save a running focus round first. */
    public static void closeWindow() {
        if (instance != null && instance.isDisplayable()) {
            instance.askToSaveRunningFocus();
            instance.dispose();
        }
    }

    private void applyTheme() {
        UITheme.applyFrame(this);
        UITheme.stylePrimaryButton(btnStart);
        chkOnTop.setOpaque(false);
        prgTime.putClientProperty("JProgressBar.largeHeight", Boolean.TRUE);
    }

    /** Focus / break minutes from user_settings (Settings → Study preferences). */
    private void loadDefaults() {
        try {
            UserSettings s = settingsService.getSettings();
            spnFocus.setValue(s.getFocusMinutes());
            spnShortBreak.setValue(s.getShortBreak());
            spnLongBreak.setValue(s.getLongBreak());
        } catch (ServiceException ex) {
            // keep the form defaults 25 / 5 / 15
        }
    }

    private void loadTasks() {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<String>();
        model.addElement("No task");
        taskIds.clear();
        taskIds.add(null);
        try {
            for (Task t : pomodoroService.getTodayTasks()) {
                model.addElement(t.getTitle());
                taskIds.add(t.getId());
            }
        } catch (ServiceException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "StudyPulse", JOptionPane.WARNING_MESSAGE);
        }
        cmbTask.setModel(model);
    }

    /** Selects a task; tasks that are not in today's plan are added to the list. */
    private void selectTask(int taskId) {
        int index = taskIds.indexOf(taskId);
        if (index < 0) {
            Task t = pomodoroService.findTask(taskId);
            if (t == null) {
                return;
            }
            ((DefaultComboBoxModel<String>) cmbTask.getModel()).addElement(t.getTitle());
            taskIds.add(t.getId());
            index = taskIds.size() - 1;
        }
        if (focusStart == null) {
            cmbTask.setSelectedIndex(index);
        }
    }

    private Integer selectedTaskId() {
        int index = cmbTask.getSelectedIndex();
        return index < 0 ? null : taskIds.get(index);
    }

    /** Sets the full time of the current phase (from the spinners). */
    private void preparePhase() {
        phaseSeconds = minutesFor(cycle.getPhase()) * 60;
        remainingSeconds = phaseSeconds;
        updateDisplay();
    }

    private int minutesFor(PomodoroPhase phase) {
        switch (phase) {
            case SHORT_BREAK:
                return (Integer) spnShortBreak.getValue();
            case LONG_BREAK:
                return (Integer) spnLongBreak.getValue();
            default:
                return (Integer) spnFocus.getValue();
        }
    }

    private boolean phaseStarted() {
        return running || remainingSeconds < phaseSeconds;
    }

    private void updateDisplay() {
        PomodoroPhase phase = cycle.getPhase();
        lblPhase.setText(phase.getLabel());
        lblPhase.setForeground(phase.isBreak() ? UITheme.TEAL : UITheme.ACCENT);
        lblTimer.setText(String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60));
        prgTime.setMaximum(Math.max(1, phaseSeconds));
        prgTime.setValue(phaseSeconds - remainingSeconds);
        prgTime.setForeground(phase.isBreak() ? UITheme.TEAL : UITheme.ACCENT);
        lblRounds.setText("Round " + cycle.getRound() + " of " + PomodoroCycle.ROUNDS_BEFORE_LONG_BREAK);
        btnStart.setText(running ? "Pause" : (phaseStarted() ? "Resume" : "Start"));
        boolean idle = !phaseStarted();
        spnFocus.setEnabled(idle);
        spnShortBreak.setEnabled(idle);
        spnLongBreak.setEnabled(idle);
        cmbTask.setEnabled(focusStart == null);
    }

    /** Start / Pause toggle. */
    private void startOrPause() {
        if (running) {
            ticker.stop();
            running = false;
        } else {
            if (cycle.getPhase() == PomodoroPhase.FOCUS && focusStart == null) {
                focusStart = LocalDateTime.now();
            }
            ticker.start();
            running = true;
        }
        updateDisplay();
    }

    private void tick() {
        remainingSeconds--;
        if (cycle.getPhase() == PomodoroPhase.FOCUS) {
            focusElapsedSeconds++;
            if (!breakReminded && minutesWithoutBreak + focusElapsedSeconds / 60
                    > PomodoroService.MAX_MINUTES_WITHOUT_BREAK) {
                remindBreak();
            }
        }
        if (remainingSeconds <= 0) {
            finishPhase(false);
        } else {
            updateDisplay();
        }
    }

    /** End of a phase (time is up, or Skip): saves focus time, moves to the next phase. */
    private void finishPhase(boolean skipped) {
        ticker.stop();
        running = false;
        if (!skipped) {
            Toolkit.getDefaultToolkit().beep();
        }
        if (cycle.getPhase() == PomodoroPhase.FOCUS) {
            int minutes = saveFocus();
            minutesWithoutBreak += minutes;
            PomodoroPhase next = cycle.completeFocus();
            DesktopAlerts.toast(skipped ? "Focus round ended" : "Focus round finished",
                    (minutes > 0 ? "Saved " + minutes + " min. " : "")
                    + "Time for a " + minutesFor(next) + "-minute " + next.getLabel().toLowerCase() + ".");
            if (!breakReminded && minutesWithoutBreak > PomodoroService.MAX_MINUTES_WITHOUT_BREAK) {
                remindBreak();
            }
        } else {
            if (!skipped) {
                // a real break: the "without a break" counter starts again
                minutesWithoutBreak = 0;
                breakReminded = false;
            }
            cycle.completeBreak();
            DesktopAlerts.toast(skipped ? "Break skipped" : "Break is over",
                    "Ready for round " + cycle.getRound() + " of " + PomodoroCycle.ROUNDS_BEFORE_LONG_BREAK + "?");
        }
        preparePhase();
    }

    /** Saves the current focus round as a study log; returns the saved minutes (0 if none). */
    private int saveFocus() {
        int minutes = Math.round(focusElapsedSeconds / 60f);
        LocalDateTime start = focusStart;
        focusStart = null;
        focusElapsedSeconds = 0;
        if (start == null || minutes < 1) {
            return 0;
        }
        try {
            pomodoroService.saveFocusRound(selectedTaskId(), start, LocalDateTime.now(), minutes, 0);
            return minutes;
        } catch (ServiceException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "StudyPulse", JOptionPane.ERROR_MESSAGE);
            return 0;
        }
    }

    /** More than 120 minutes of focus without a real break: notification + toast. */
    private void remindBreak() {
        breakReminded = true;
        try {
            Notification n = notificationService.remindBreak();
            if (n != null) {
                DesktopAlerts.alert(n);
            } else {
                DesktopAlerts.toast("Break", NotificationService.TAKE_A_BREAK);
            }
        } catch (ServiceException ex) {
            DesktopAlerts.toast("Break", NotificationService.TAKE_A_BREAK);
        }
    }

    /** Reset the current phase; a started focus round may be saved first. */
    private void resetPhase() {
        if (cycle.getPhase() == PomodoroPhase.FOCUS && focusElapsedSeconds >= 60) {
            int answer = JOptionPane.showConfirmDialog(this, "Save the time you studied so far?",
                    "Reset", JOptionPane.YES_NO_CANCEL_OPTION);
            if (answer == JOptionPane.CANCEL_OPTION || answer == JOptionPane.CLOSED_OPTION) {
                return;
            }
            if (answer == JOptionPane.YES_OPTION) {
                minutesWithoutBreak += saveFocus();
            }
        }
        ticker.stop();
        running = false;
        focusStart = null;
        focusElapsedSeconds = 0;
        preparePhase();
    }

    /** Closing during a focus round: "Save the time you studied so far?" */
    private void askToSaveRunningFocus() {
        ticker.stop();
        running = false;
        if (cycle.getPhase() == PomodoroPhase.FOCUS && focusElapsedSeconds >= 60) {
            int answer = JOptionPane.showConfirmDialog(this, "Save the time you studied so far ("
                    + DateUtil.formatMinutes(focusElapsedSeconds / 60) + ")?", "Pomodoro", JOptionPane.YES_NO_OPTION);
            if (answer == JOptionPane.YES_OPTION) {
                saveFocus();
            }
        }
        focusStart = null;
        focusElapsedSeconds = 0;
    }

    @Override
    public void dispose() {
        if (ticker != null) {
            ticker.stop();
        }
        if (instance == this) {
            instance = null;
        }
        super.dispose();
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
        lblTaskCaption = new javax.swing.JLabel();
        cmbTask = new javax.swing.JComboBox<>();
        lblPhase = new javax.swing.JLabel();
        lblTimer = new javax.swing.JLabel();
        prgTime = new javax.swing.JProgressBar();
        lblRounds = new javax.swing.JLabel();
        btnStart = new javax.swing.JButton();
        btnReset = new javax.swing.JButton();
        btnSkip = new javax.swing.JButton();
        lblFocus = new javax.swing.JLabel();
        lblShortBreak = new javax.swing.JLabel();
        lblLongBreak = new javax.swing.JLabel();
        spnFocus = new javax.swing.JSpinner();
        spnShortBreak = new javax.swing.JSpinner();
        spnLongBreak = new javax.swing.JSpinner();
        chkOnTop = new javax.swing.JCheckBox();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("StudyPulse – Pomodoro");
        setResizable(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        lblTitle.setFont(new java.awt.Font("Segoe UI", 1, 22)); // NOI18N
        lblTitle.setForeground(new java.awt.Color(34, 48, 63));
        lblTitle.setText("Pomodoro");

        lblTaskCaption.setText("Task");

        cmbTask.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "No task" }));

        lblPhase.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        lblPhase.setForeground(new java.awt.Color(46, 125, 154));
        lblPhase.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblPhase.setText("Focus");

        lblTimer.setFont(new java.awt.Font("Segoe UI", 1, 56)); // NOI18N
        lblTimer.setForeground(new java.awt.Color(34, 48, 63));
        lblTimer.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblTimer.setText("25:00");

        lblRounds.setForeground(new java.awt.Color(107, 119, 133));
        lblRounds.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblRounds.setText("Round 1 of 4");

        btnStart.setText("Start");
        btnStart.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnStartActionPerformed(evt);
            }
        });

        btnReset.setText("Reset");
        btnReset.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnResetActionPerformed(evt);
            }
        });

        btnSkip.setText("Skip");
        btnSkip.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSkipActionPerformed(evt);
            }
        });

        lblFocus.setText("Focus (min)");

        lblShortBreak.setText("Short break");

        lblLongBreak.setText("Long break");

        spnFocus.setModel(new javax.swing.SpinnerNumberModel(25, 1, 90, 1));
        spnFocus.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                spnFocusStateChanged(evt);
            }
        });

        spnShortBreak.setModel(new javax.swing.SpinnerNumberModel(5, 1, 30, 1));
        spnShortBreak.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                spnShortBreakStateChanged(evt);
            }
        });

        spnLongBreak.setModel(new javax.swing.SpinnerNumberModel(15, 5, 60, 1));
        spnLongBreak.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                spnLongBreakStateChanged(evt);
            }
        });

        chkOnTop.setText("Keep window on top");
        chkOnTop.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chkOnTopActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(20, 20, 20)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblTitle)
                    .addComponent(lblTaskCaption)
                    .addComponent(cmbTask, 0, 304, Short.MAX_VALUE)
                    .addComponent(lblPhase, javax.swing.GroupLayout.DEFAULT_SIZE, 304, Short.MAX_VALUE)
                    .addComponent(lblTimer, javax.swing.GroupLayout.DEFAULT_SIZE, 304, Short.MAX_VALUE)
                    .addComponent(prgTime, javax.swing.GroupLayout.DEFAULT_SIZE, 304, Short.MAX_VALUE)
                    .addComponent(lblRounds, javax.swing.GroupLayout.DEFAULT_SIZE, 304, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(btnStart, javax.swing.GroupLayout.DEFAULT_SIZE, 136, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnReset, javax.swing.GroupLayout.DEFAULT_SIZE, 78, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnSkip, javax.swing.GroupLayout.DEFAULT_SIZE, 78, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblFocus)
                            .addComponent(spnFocus, javax.swing.GroupLayout.DEFAULT_SIZE, 96, Short.MAX_VALUE))
                        .addGap(8, 8, 8)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblShortBreak)
                            .addComponent(spnShortBreak, javax.swing.GroupLayout.DEFAULT_SIZE, 96, Short.MAX_VALUE))
                        .addGap(8, 8, 8)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblLongBreak)
                            .addComponent(spnLongBreak, javax.swing.GroupLayout.DEFAULT_SIZE, 96, Short.MAX_VALUE)))
                    .addComponent(chkOnTop))
                .addGap(20, 20, 20))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addComponent(lblTitle)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lblTaskCaption)
                .addGap(4, 4, 4)
                .addComponent(cmbTask, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(20, 20, 20)
                .addComponent(lblPhase)
                .addGap(2, 2, 2)
                .addComponent(lblTimer)
                .addGap(6, 6, 6)
                .addComponent(prgTime, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(6, 6, 6)
                .addComponent(lblRounds)
                .addGap(20, 20, 20)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnStart, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnReset, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnSkip, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(20, 20, 20)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblFocus)
                    .addComponent(lblShortBreak)
                    .addComponent(lblLongBreak))
                .addGap(4, 4, 4)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(spnFocus, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(spnShortBreak, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(spnLongBreak, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(chkOnTop)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        setSize(new java.awt.Dimension(360, 560));
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        askToSaveRunningFocus();
    }//GEN-LAST:event_formWindowClosing

    private void btnStartActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnStartActionPerformed
        startOrPause();
    }//GEN-LAST:event_btnStartActionPerformed

    private void btnResetActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnResetActionPerformed
        resetPhase();
    }//GEN-LAST:event_btnResetActionPerformed

    private void btnSkipActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSkipActionPerformed
        finishPhase(true);
    }//GEN-LAST:event_btnSkipActionPerformed

    private void spnFocusStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_spnFocusStateChanged
        if (!phaseStarted()) {
            preparePhase();
        }
    }//GEN-LAST:event_spnFocusStateChanged

    private void spnShortBreakStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_spnShortBreakStateChanged
        if (!phaseStarted()) {
            preparePhase();
        }
    }//GEN-LAST:event_spnShortBreakStateChanged

    private void spnLongBreakStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_spnLongBreakStateChanged
        if (!phaseStarted()) {
            preparePhase();
        }
    }//GEN-LAST:event_spnLongBreakStateChanged

    private void chkOnTopActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chkOnTopActionPerformed
        setAlwaysOnTop(chkOnTop.isSelected());
    }//GEN-LAST:event_chkOnTopActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnReset;
    private javax.swing.JButton btnSkip;
    private javax.swing.JButton btnStart;
    private javax.swing.JCheckBox chkOnTop;
    private javax.swing.JComboBox<String> cmbTask;
    private javax.swing.JLabel lblFocus;
    private javax.swing.JLabel lblLongBreak;
    private javax.swing.JLabel lblPhase;
    private javax.swing.JLabel lblRounds;
    private javax.swing.JLabel lblShortBreak;
    private javax.swing.JLabel lblTaskCaption;
    private javax.swing.JLabel lblTimer;
    private javax.swing.JLabel lblTitle;
    private javax.swing.JProgressBar prgTime;
    private javax.swing.JSpinner spnFocus;
    private javax.swing.JSpinner spnLongBreak;
    private javax.swing.JSpinner spnShortBreak;
    // End of variables declaration//GEN-END:variables
}
