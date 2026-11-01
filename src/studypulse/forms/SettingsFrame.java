package studypulse.forms;

import java.awt.GridLayout;
import java.awt.SystemTray;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import studypulse.model.AppTheme;
import studypulse.model.User;
import studypulse.model.UserSettings;
import studypulse.service.ServiceException;
import studypulse.service.SettingsService;
import studypulse.util.DesktopAlerts;
import studypulse.util.Session;
import studypulse.util.SidebarUtil;
import studypulse.util.UITheme;

/**
 * Profile, password, study preferences, privacy (export / delete data / delete account),
 * appearance (light / dark) and About, in a JTabbedPane.
 */
public class SettingsFrame extends javax.swing.JFrame {

    private final SettingsService settingsService = new SettingsService();

    /**
     * Creates new form SettingsFrame
     */
    public SettingsFrame() {
        initComponents();
        SidebarUtil.attach(this, pnlSidebar, SidebarUtil.SETTINGS);
        applyTheme();
        setupFields();
        loadAll();
    }

    private void applyTheme() {
        UITheme.applyFrame(this);
        UITheme.stylePrimaryButton(btnSaveProfile);
        UITheme.stylePrimaryButton(btnChangePassword);
        UITheme.stylePrimaryButton(btnSavePreferences);
        UITheme.stylePrimaryButton(btnExportData);
        UITheme.styleDangerButton(btnDeleteAccount);
        for (javax.swing.AbstractButton b : new javax.swing.AbstractButton[]{chkNotifications, chkTray,
            chkWellbeing, rdoLight, rdoDark}) {
            b.setOpaque(false);
        }
        lblPrivacyInfo.setOpaque(true);
        lblPrivacyInfo.setBackground(UITheme.ROW_UNREAD);
        lblPrivacyInfo.setForeground(UITheme.TEXT);
        lblPrivacyInfo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, UITheme.ACCENT),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        tabSettings.setFont(UITheme.FONT_BOLD);
    }

    private void setupFields() {
        chkNotifications.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateTrayCheck();
            }
        });
    }

    private void loadAll() {
        User user = Session.getUser();
        if (user == null) {
            return;
        }
        txtFullName.setText(user.getFullName());
        txtEmail.setText(user.getEmail());
        cmbRestDay.setSelectedIndex(user.getRestDay() - 1);
        chkWellbeing.setSelected(user.isWellbeingEnabled());
        try {
            UserSettings s = settingsService.getSettings();
            spnFocus.setValue(s.getFocusMinutes());
            spnShortBreak.setValue(s.getShortBreak());
            spnLongBreak.setValue(s.getLongBreak());
            chkNotifications.setSelected(s.isNotificationsOn());
            chkTray.setSelected(s.isTrayOn());
            rdoDark.setSelected(s.getTheme() == AppTheme.DARK);
            rdoLight.setSelected(s.getTheme() == AppTheme.LIGHT);
        } catch (ServiceException ex) {
            showError(ex.getMessage());
        }
        updateTrayCheck();
        // Swing HTML widths come out about 25% larger: 450 fits the 640 px box with its padding
        lblPrivacyInfo.setText("<html><div style='width:450px'>"
                + "<b>What StudyPulse stores</b><br>Your account (name, email and a salted password hash, never "
                + "the password itself), tasks, study plan, study sessions, risk history, notifications and "
                + "settings. With well-being tracking on, also your daily stress, energy, mood, sleep and an "
                + "optional note.<br><br><b>Where</b><br>Only on this computer, in the file<br>"
                + settingsService.getDatabaseFile().getAbsolutePath()
                + "<br><br><b>Online</b><br>Nothing is sent online. StudyPulse has no server, no sync and no "
                + "tracking.</div></html>");
    }

    private void updateTrayCheck() {
        chkTray.setEnabled(chkNotifications.isSelected() && SystemTray.isSupported());
    }

    private void saveProfile() {
        try {
            settingsService.saveProfile(txtFullName.getText());
            showInfo("Your profile is saved.");
        } catch (ServiceException ex) {
            showWarning(ex.getMessage());
        }
    }

    private void changePassword() {
        char[] current = txtCurrentPassword.getPassword();
        char[] next = txtNewPassword.getPassword();
        char[] confirm = txtConfirmPassword.getPassword();
        try {
            settingsService.changePassword(current, next, confirm);
            txtCurrentPassword.setText("");
            txtNewPassword.setText("");
            txtConfirmPassword.setText("");
            showInfo("Your password is changed.");
        } catch (ServiceException ex) {
            showWarning(ex.getMessage());
        } finally {
            Arrays.fill(current, '\0');
            Arrays.fill(next, '\0');
            Arrays.fill(confirm, '\0');
        }
    }

    private void savePreferences() {
        for (JSpinner s : new JSpinner[]{spnFocus, spnShortBreak, spnLongBreak}) {
            try {
                s.commitEdit();
            } catch (java.text.ParseException ex) {
                // keep the last valid value
            }
        }
        try {
            settingsService.savePreferences(cmbRestDay.getSelectedIndex() + 1, (Integer) spnFocus.getValue(),
                    (Integer) spnShortBreak.getValue(), (Integer) spnLongBreak.getValue(),
                    chkNotifications.isSelected(), chkTray.isSelected());
            showInfo("Your preferences are saved.\nThe new Pomodoro times are used the next time you open the timer.");
        } catch (ServiceException ex) {
            showWarning(ex.getMessage());
        }
    }

    private void editFreeTime() {
        AvailabilityFrame frame = new AvailabilityFrame();
        frame.setOnSaved(new Runnable() {
            @Override
            public void run() {
                cmbRestDay.setSelectedIndex(Session.getUser().getRestDay() - 1);
            }
        });
        frame.setVisible(true);
    }

    /** Turning tracking off asks whether the existing check-ins should be deleted too. */
    private void wellbeingToggled() {
        boolean enable = chkWellbeing.isSelected();
        boolean deleteCheckins = false;
        if (!enable) {
            int answer = JOptionPane.showConfirmDialog(this, "Also delete your existing check-in data?",
                    "Well-being tracking", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (answer == JOptionPane.CANCEL_OPTION || answer == JOptionPane.CLOSED_OPTION) {
                chkWellbeing.setSelected(true);
                return;
            }
            deleteCheckins = answer == JOptionPane.YES_OPTION;
        }
        try {
            settingsService.setWellbeingTracking(enable, deleteCheckins);
            showInfo(enable ? "Well-being tracking is on."
                    : "Well-being tracking is off." + (deleteCheckins ? "\nYour check-ins were deleted." : ""));
        } catch (ServiceException ex) {
            chkWellbeing.setSelected(!enable);
            showError(ex.getMessage());
        }
    }

    private void exportData() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a folder for the CSV files");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            List<File> files = settingsService.exportData(chooser.getSelectedFile());
            StringBuilder names = new StringBuilder();
            for (File f : files) {
                names.append("\n").append(f.getName());
            }
            showInfo("Your data was exported to\n" + chooser.getSelectedFile().getAbsolutePath() + names);
        } catch (ServiceException ex) {
            showError(ex.getMessage());
        }
    }

    private void deleteWellbeingData() {
        int answer = JOptionPane.showConfirmDialog(this,
                "Delete all your check-ins and your risk history?\nThis cannot be undone.",
                "Delete well-being data", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            int[] deleted = settingsService.deleteWellbeingData();
            showInfo("Deleted " + deleted[0] + " check-ins and " + deleted[1] + " risk snapshots.");
        } catch (ServiceException ex) {
            showError(ex.getMessage());
        }
    }

    /** Password in a JPasswordField dialog, then the user and (by cascade) all data are deleted. */
    private void deleteAccount() {
        JPasswordField pwd = new JPasswordField(20);
        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 6));
        panel.add(new JLabel("This deletes your account and ALL your data. It cannot be undone."));
        panel.add(new JLabel("Enter your password to confirm:"));
        panel.add(pwd);
        int answer = JOptionPane.showConfirmDialog(this, panel, "Delete my account",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        char[] password = pwd.getPassword();
        try {
            if (answer != JOptionPane.OK_OPTION) {
                return;
            }
            if (!settingsService.verifyPassword(password)) {
                showWarning("The password is incorrect. Your account was not deleted.");
                return;
            }
            PomodoroFrame.closeWindow();
            DesktopAlerts.stop();
            settingsService.deleteAccount(password);
            showInfo("Your account and all your data have been deleted.");
            new LoginFrame().setVisible(true);
            dispose();
        } catch (ServiceException ex) {
            showError(ex.getMessage());
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    /** Saves the theme and switches every open window at once. */
    private void changeTheme(AppTheme theme) {
        try {
            settingsService.saveTheme(theme);
        } catch (ServiceException ex) {
            showError(ex.getMessage());
            return;
        }
        UITheme.switchTheme(theme == AppTheme.DARK);
        applyTheme();
    }

    private void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "StudyPulse", JOptionPane.INFORMATION_MESSAGE);
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

        grpTheme = new javax.swing.ButtonGroup();
        pnlSidebar = new javax.swing.JPanel();
        lblTitle = new javax.swing.JLabel();
        tabSettings = new javax.swing.JTabbedPane();
        pnlProfile = new javax.swing.JPanel();
        lblProfileTitle = new javax.swing.JLabel();
        lblFullName = new javax.swing.JLabel();
        txtFullName = new javax.swing.JTextField();
        lblEmail = new javax.swing.JLabel();
        txtEmail = new javax.swing.JTextField();
        btnSaveProfile = new javax.swing.JButton();
        lblPasswordTitle = new javax.swing.JLabel();
        lblCurrentPassword = new javax.swing.JLabel();
        txtCurrentPassword = new javax.swing.JPasswordField();
        lblNewPassword = new javax.swing.JLabel();
        txtNewPassword = new javax.swing.JPasswordField();
        lblConfirmPassword = new javax.swing.JLabel();
        txtConfirmPassword = new javax.swing.JPasswordField();
        btnChangePassword = new javax.swing.JButton();
        pnlPreferences = new javax.swing.JPanel();
        lblPrefTitle = new javax.swing.JLabel();
        lblFreeTimeInfo = new javax.swing.JLabel();
        btnEditFreeTime = new javax.swing.JButton();
        lblRestDay = new javax.swing.JLabel();
        cmbRestDay = new javax.swing.JComboBox<>();
        lblPomodoroTitle = new javax.swing.JLabel();
        lblFocus = new javax.swing.JLabel();
        lblShortBreak = new javax.swing.JLabel();
        lblLongBreak = new javax.swing.JLabel();
        spnFocus = new javax.swing.JSpinner();
        spnShortBreak = new javax.swing.JSpinner();
        spnLongBreak = new javax.swing.JSpinner();
        lblAlertsTitle = new javax.swing.JLabel();
        chkNotifications = new javax.swing.JCheckBox();
        chkTray = new javax.swing.JCheckBox();
        btnSavePreferences = new javax.swing.JButton();
        pnlPrivacy = new javax.swing.JPanel();
        lblPrivacyTitle = new javax.swing.JLabel();
        chkWellbeing = new javax.swing.JCheckBox();
        lblPrivacyInfo = new javax.swing.JLabel();
        lblDataTitle = new javax.swing.JLabel();
        btnExportData = new javax.swing.JButton();
        btnDeleteWellbeing = new javax.swing.JButton();
        btnDeleteAccount = new javax.swing.JButton();
        pnlAppearance = new javax.swing.JPanel();
        lblAppearanceTitle = new javax.swing.JLabel();
        rdoLight = new javax.swing.JRadioButton();
        rdoDark = new javax.swing.JRadioButton();
        lblThemeInfo = new javax.swing.JLabel();
        pnlAbout = new javax.swing.JPanel();
        lblAppName = new javax.swing.JLabel();
        lblVersion = new javax.swing.JLabel();
        lblAboutDesc = new javax.swing.JLabel();
        lblTeamTitle = new javax.swing.JLabel();
        lblTeam1 = new javax.swing.JLabel();
        lblTeam2 = new javax.swing.JLabel();
        lblTeam3 = new javax.swing.JLabel();
        lblTeam4 = new javax.swing.JLabel();
        lblAboutDisclaimer = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("StudyPulse – Settings");

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
        lblTitle.setText("Settings");

        pnlProfile.setBackground(new java.awt.Color(255, 255, 255));

        lblProfileTitle.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        lblProfileTitle.setForeground(new java.awt.Color(34, 48, 63));
        lblProfileTitle.setText("Profile");

        lblFullName.setText("Full name");

        lblEmail.setText("Email (cannot be changed)");

        txtEmail.setEditable(false);

        btnSaveProfile.setText("Save profile");
        btnSaveProfile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSaveProfileActionPerformed(evt);
            }
        });

        lblPasswordTitle.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        lblPasswordTitle.setForeground(new java.awt.Color(34, 48, 63));
        lblPasswordTitle.setText("Change password");

        lblCurrentPassword.setText("Current password");

        lblNewPassword.setText("New password (at least 6 characters)");

        lblConfirmPassword.setText("Confirm new password");

        btnChangePassword.setText("Change password");
        btnChangePassword.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnChangePasswordActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout pnlProfileLayout = new javax.swing.GroupLayout(pnlProfile);
        pnlProfile.setLayout(pnlProfileLayout);
        pnlProfileLayout.setHorizontalGroup(
            pnlProfileLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlProfileLayout.createSequentialGroup()
                .addGap(24, 24, 24)
                .addGroup(pnlProfileLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblProfileTitle)
                    .addComponent(lblFullName)
                    .addComponent(txtFullName, javax.swing.GroupLayout.PREFERRED_SIZE, 360, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblEmail)
                    .addComponent(txtEmail, javax.swing.GroupLayout.PREFERRED_SIZE, 360, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnSaveProfile)
                    .addComponent(lblPasswordTitle)
                    .addComponent(lblCurrentPassword)
                    .addComponent(txtCurrentPassword, javax.swing.GroupLayout.PREFERRED_SIZE, 360, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblNewPassword)
                    .addComponent(txtNewPassword, javax.swing.GroupLayout.PREFERRED_SIZE, 360, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblConfirmPassword)
                    .addComponent(txtConfirmPassword, javax.swing.GroupLayout.PREFERRED_SIZE, 360, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnChangePassword))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        pnlProfileLayout.setVerticalGroup(
            pnlProfileLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlProfileLayout.createSequentialGroup()
                .addGap(24, 24, 24)
                .addComponent(lblProfileTitle)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lblFullName)
                .addGap(4, 4, 4)
                .addComponent(txtFullName, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblEmail)
                .addGap(4, 4, 4)
                .addComponent(txtEmail, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(btnSaveProfile, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(28, 28, 28)
                .addComponent(lblPasswordTitle)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lblCurrentPassword)
                .addGap(4, 4, 4)
                .addComponent(txtCurrentPassword, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblNewPassword)
                .addGap(4, 4, 4)
                .addComponent(txtNewPassword, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblConfirmPassword)
                .addGap(4, 4, 4)
                .addComponent(txtConfirmPassword, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(btnChangePassword, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        tabSettings.addTab("Profile", pnlProfile);

        pnlPreferences.setBackground(new java.awt.Color(255, 255, 255));

        lblPrefTitle.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        lblPrefTitle.setForeground(new java.awt.Color(34, 48, 63));
        lblPrefTitle.setText("Study preferences");

        lblFreeTimeInfo.setText("Free study hours per weekday are used by the study plan.");

        btnEditFreeTime.setText("Edit my free time");
        btnEditFreeTime.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnEditFreeTimeActionPerformed(evt);
            }
        });

        lblRestDay.setText("Weekly rest day");

        cmbRestDay.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday" }));

        lblPomodoroTitle.setFont(new java.awt.Font("Segoe UI", 1, 13)); // NOI18N
        lblPomodoroTitle.setText("Pomodoro timer");

        lblFocus.setText("Focus (min)");

        lblShortBreak.setText("Short break (min)");

        lblLongBreak.setText("Long break (min)");

        spnFocus.setModel(new javax.swing.SpinnerNumberModel(25, 1, 90, 1));

        spnShortBreak.setModel(new javax.swing.SpinnerNumberModel(5, 1, 30, 1));

        spnLongBreak.setModel(new javax.swing.SpinnerNumberModel(15, 5, 60, 1));

        lblAlertsTitle.setFont(new java.awt.Font("Segoe UI", 1, 13)); // NOI18N
        lblAlertsTitle.setText("Alerts");

        chkNotifications.setText("Show desktop notifications (pop-up toasts)");

        chkTray.setText("Also show them as system tray messages");

        btnSavePreferences.setText("Save preferences");
        btnSavePreferences.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSavePreferencesActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout pnlPreferencesLayout = new javax.swing.GroupLayout(pnlPreferences);
        pnlPreferences.setLayout(pnlPreferencesLayout);
        pnlPreferencesLayout.setHorizontalGroup(
            pnlPreferencesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlPreferencesLayout.createSequentialGroup()
                .addGap(24, 24, 24)
                .addGroup(pnlPreferencesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblPrefTitle)
                    .addGroup(pnlPreferencesLayout.createSequentialGroup()
                        .addComponent(lblFreeTimeInfo)
                        .addGap(12, 12, 12)
                        .addComponent(btnEditFreeTime))
                    .addComponent(lblRestDay)
                    .addComponent(cmbRestDay, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblPomodoroTitle)
                    .addGroup(pnlPreferencesLayout.createSequentialGroup()
                        .addGroup(pnlPreferencesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblFocus)
                            .addComponent(spnFocus, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(16, 16, 16)
                        .addGroup(pnlPreferencesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblShortBreak)
                            .addComponent(spnShortBreak, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(16, 16, 16)
                        .addGroup(pnlPreferencesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblLongBreak)
                            .addComponent(spnLongBreak, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(lblAlertsTitle)
                    .addComponent(chkNotifications)
                    .addComponent(chkTray)
                    .addComponent(btnSavePreferences))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        pnlPreferencesLayout.setVerticalGroup(
            pnlPreferencesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlPreferencesLayout.createSequentialGroup()
                .addGap(24, 24, 24)
                .addComponent(lblPrefTitle)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(pnlPreferencesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblFreeTimeInfo)
                    .addComponent(btnEditFreeTime, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lblRestDay)
                .addGap(4, 4, 4)
                .addComponent(cmbRestDay, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(24, 24, 24)
                .addComponent(lblPomodoroTitle)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlPreferencesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblFocus)
                    .addComponent(lblShortBreak)
                    .addComponent(lblLongBreak))
                .addGap(4, 4, 4)
                .addGroup(pnlPreferencesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(spnFocus, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(spnShortBreak, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(spnLongBreak, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(24, 24, 24)
                .addComponent(lblAlertsTitle)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(chkNotifications)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(chkTray)
                .addGap(24, 24, 24)
                .addComponent(btnSavePreferences, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        tabSettings.addTab("Study preferences", pnlPreferences);

        pnlPrivacy.setBackground(new java.awt.Color(255, 255, 255));

        lblPrivacyTitle.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        lblPrivacyTitle.setForeground(new java.awt.Color(34, 48, 63));
        lblPrivacyTitle.setText("Privacy");

        chkWellbeing.setText("Track mood, energy and sleep (optional)");
        chkWellbeing.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chkWellbeingActionPerformed(evt);
            }
        });

        lblPrivacyInfo.setText("What is stored");

        lblDataTitle.setFont(new java.awt.Font("Segoe UI", 1, 13)); // NOI18N
        lblDataTitle.setText("Your data");

        btnExportData.setText("Export my data (CSV)");
        btnExportData.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnExportDataActionPerformed(evt);
            }
        });

        btnDeleteWellbeing.setText("Delete well-being data");
        btnDeleteWellbeing.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDeleteWellbeingActionPerformed(evt);
            }
        });

        btnDeleteAccount.setText("Delete my account");
        btnDeleteAccount.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDeleteAccountActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout pnlPrivacyLayout = new javax.swing.GroupLayout(pnlPrivacy);
        pnlPrivacy.setLayout(pnlPrivacyLayout);
        pnlPrivacyLayout.setHorizontalGroup(
            pnlPrivacyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlPrivacyLayout.createSequentialGroup()
                .addGap(24, 24, 24)
                .addGroup(pnlPrivacyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblPrivacyTitle)
                    .addComponent(chkWellbeing)
                    .addComponent(lblPrivacyInfo, javax.swing.GroupLayout.PREFERRED_SIZE, 640, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblDataTitle)
                    .addGroup(pnlPrivacyLayout.createSequentialGroup()
                        .addComponent(btnExportData)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnDeleteWellbeing)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnDeleteAccount)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        pnlPrivacyLayout.setVerticalGroup(
            pnlPrivacyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlPrivacyLayout.createSequentialGroup()
                .addGap(24, 24, 24)
                .addComponent(lblPrivacyTitle)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(chkWellbeing)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lblPrivacyInfo)
                .addGap(24, 24, 24)
                .addComponent(lblDataTitle)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlPrivacyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnExportData, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnDeleteWellbeing, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnDeleteAccount, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        tabSettings.addTab("Privacy", pnlPrivacy);

        pnlAppearance.setBackground(new java.awt.Color(255, 255, 255));

        lblAppearanceTitle.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        lblAppearanceTitle.setForeground(new java.awt.Color(34, 48, 63));
        lblAppearanceTitle.setText("Appearance");

        grpTheme.add(rdoLight);
        rdoLight.setSelected(true);
        rdoLight.setText("Light");
        rdoLight.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rdoLightActionPerformed(evt);
            }
        });

        grpTheme.add(rdoDark);
        rdoDark.setText("Dark");
        rdoDark.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rdoDarkActionPerformed(evt);
            }
        });

        lblThemeInfo.setForeground(new java.awt.Color(107, 119, 133));
        lblThemeInfo.setText("The theme changes at once, is saved and is used every time you log in.");

        javax.swing.GroupLayout pnlAppearanceLayout = new javax.swing.GroupLayout(pnlAppearance);
        pnlAppearance.setLayout(pnlAppearanceLayout);
        pnlAppearanceLayout.setHorizontalGroup(
            pnlAppearanceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlAppearanceLayout.createSequentialGroup()
                .addGap(24, 24, 24)
                .addGroup(pnlAppearanceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblAppearanceTitle)
                    .addComponent(rdoLight)
                    .addComponent(rdoDark)
                    .addComponent(lblThemeInfo))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        pnlAppearanceLayout.setVerticalGroup(
            pnlAppearanceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlAppearanceLayout.createSequentialGroup()
                .addGap(24, 24, 24)
                .addComponent(lblAppearanceTitle)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(rdoLight)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(rdoDark)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lblThemeInfo)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        tabSettings.addTab("Appearance", pnlAppearance);

        pnlAbout.setBackground(new java.awt.Color(255, 255, 255));

        lblAppName.setFont(new java.awt.Font("Segoe UI", 1, 22)); // NOI18N
        lblAppName.setForeground(new java.awt.Color(34, 48, 63));
        lblAppName.setText("StudyPulse");

        lblVersion.setForeground(new java.awt.Color(107, 119, 133));
        lblVersion.setText("Version 1.0");

        lblAboutDesc.setText("Smart Study Load & Burnout Prevention System");

        lblTeamTitle.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        lblTeamTitle.setForeground(new java.awt.Color(34, 48, 63));
        lblTeamTitle.setText("Team");

        lblTeam1.setText("Team member 1 – Name (Student ID)");

        lblTeam2.setText("Team member 2 – Name (Student ID)");

        lblTeam3.setText("Team member 3 – Name (Student ID)");

        lblTeam4.setText("Team member 4 – Name (Student ID)");

        lblAboutDisclaimer.setFont(new java.awt.Font("Segoe UI", 2, 13)); // NOI18N
        lblAboutDisclaimer.setForeground(new java.awt.Color(107, 119, 133));
        lblAboutDisclaimer.setText("StudyPulse is a productivity and early-warning tool, not a medical diagnosis system.");

        javax.swing.GroupLayout pnlAboutLayout = new javax.swing.GroupLayout(pnlAbout);
        pnlAbout.setLayout(pnlAboutLayout);
        pnlAboutLayout.setHorizontalGroup(
            pnlAboutLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlAboutLayout.createSequentialGroup()
                .addGap(24, 24, 24)
                .addGroup(pnlAboutLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblAppName)
                    .addComponent(lblVersion)
                    .addComponent(lblAboutDesc)
                    .addComponent(lblTeamTitle)
                    .addComponent(lblTeam1)
                    .addComponent(lblTeam2)
                    .addComponent(lblTeam3)
                    .addComponent(lblTeam4)
                    .addComponent(lblAboutDisclaimer))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        pnlAboutLayout.setVerticalGroup(
            pnlAboutLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlAboutLayout.createSequentialGroup()
                .addGap(24, 24, 24)
                .addComponent(lblAppName)
                .addGap(2, 2, 2)
                .addComponent(lblVersion)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblAboutDesc)
                .addGap(24, 24, 24)
                .addComponent(lblTeamTitle)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblTeam1)
                .addGap(4, 4, 4)
                .addComponent(lblTeam2)
                .addGap(4, 4, 4)
                .addComponent(lblTeam3)
                .addGap(4, 4, 4)
                .addComponent(lblTeam4)
                .addGap(24, 24, 24)
                .addComponent(lblAboutDisclaimer)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        tabSettings.addTab("About", pnlAbout);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(pnlSidebar, javax.swing.GroupLayout.PREFERRED_SIZE, 160, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(16, 16, 16)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblTitle)
                    .addComponent(tabSettings, javax.swing.GroupLayout.DEFAULT_SIZE, 1008, Short.MAX_VALUE))
                .addGap(16, 16, 16))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(pnlSidebar, javax.swing.GroupLayout.DEFAULT_SIZE, 720, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addComponent(lblTitle)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(tabSettings, javax.swing.GroupLayout.DEFAULT_SIZE, 630, Short.MAX_VALUE)
                .addGap(16, 16, 16))
        );

        setSize(new java.awt.Dimension(1200, 720));
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void btnSaveProfileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSaveProfileActionPerformed
        saveProfile();
    }//GEN-LAST:event_btnSaveProfileActionPerformed

    private void btnChangePasswordActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnChangePasswordActionPerformed
        changePassword();
    }//GEN-LAST:event_btnChangePasswordActionPerformed

    private void btnEditFreeTimeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnEditFreeTimeActionPerformed
        editFreeTime();
    }//GEN-LAST:event_btnEditFreeTimeActionPerformed

    private void btnSavePreferencesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSavePreferencesActionPerformed
        savePreferences();
    }//GEN-LAST:event_btnSavePreferencesActionPerformed

    private void chkWellbeingActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chkWellbeingActionPerformed
        wellbeingToggled();
    }//GEN-LAST:event_chkWellbeingActionPerformed

    private void btnExportDataActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnExportDataActionPerformed
        exportData();
    }//GEN-LAST:event_btnExportDataActionPerformed

    private void btnDeleteWellbeingActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDeleteWellbeingActionPerformed
        deleteWellbeingData();
    }//GEN-LAST:event_btnDeleteWellbeingActionPerformed

    private void btnDeleteAccountActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDeleteAccountActionPerformed
        deleteAccount();
    }//GEN-LAST:event_btnDeleteAccountActionPerformed

    private void rdoLightActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rdoLightActionPerformed
        changeTheme(AppTheme.LIGHT);
    }//GEN-LAST:event_rdoLightActionPerformed

    private void rdoDarkActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rdoDarkActionPerformed
        changeTheme(AppTheme.DARK);
    }//GEN-LAST:event_rdoDarkActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnChangePassword;
    private javax.swing.JButton btnDeleteAccount;
    private javax.swing.JButton btnDeleteWellbeing;
    private javax.swing.JButton btnEditFreeTime;
    private javax.swing.JButton btnExportData;
    private javax.swing.JButton btnSavePreferences;
    private javax.swing.JButton btnSaveProfile;
    private javax.swing.JCheckBox chkNotifications;
    private javax.swing.JCheckBox chkTray;
    private javax.swing.JCheckBox chkWellbeing;
    private javax.swing.JComboBox<String> cmbRestDay;
    private javax.swing.ButtonGroup grpTheme;
    private javax.swing.JLabel lblAboutDesc;
    private javax.swing.JLabel lblAboutDisclaimer;
    private javax.swing.JLabel lblAlertsTitle;
    private javax.swing.JLabel lblAppName;
    private javax.swing.JLabel lblAppearanceTitle;
    private javax.swing.JLabel lblConfirmPassword;
    private javax.swing.JLabel lblCurrentPassword;
    private javax.swing.JLabel lblDataTitle;
    private javax.swing.JLabel lblEmail;
    private javax.swing.JLabel lblFocus;
    private javax.swing.JLabel lblFreeTimeInfo;
    private javax.swing.JLabel lblFullName;
    private javax.swing.JLabel lblLongBreak;
    private javax.swing.JLabel lblNewPassword;
    private javax.swing.JLabel lblPasswordTitle;
    private javax.swing.JLabel lblPomodoroTitle;
    private javax.swing.JLabel lblPrefTitle;
    private javax.swing.JLabel lblPrivacyInfo;
    private javax.swing.JLabel lblPrivacyTitle;
    private javax.swing.JLabel lblProfileTitle;
    private javax.swing.JLabel lblRestDay;
    private javax.swing.JLabel lblShortBreak;
    private javax.swing.JLabel lblTeam1;
    private javax.swing.JLabel lblTeam2;
    private javax.swing.JLabel lblTeam3;
    private javax.swing.JLabel lblTeam4;
    private javax.swing.JLabel lblTeamTitle;
    private javax.swing.JLabel lblThemeInfo;
    private javax.swing.JLabel lblTitle;
    private javax.swing.JLabel lblVersion;
    private javax.swing.JPanel pnlAbout;
    private javax.swing.JPanel pnlAppearance;
    private javax.swing.JPanel pnlPreferences;
    private javax.swing.JPanel pnlPrivacy;
    private javax.swing.JPanel pnlProfile;
    private javax.swing.JPanel pnlSidebar;
    private javax.swing.JRadioButton rdoDark;
    private javax.swing.JRadioButton rdoLight;
    private javax.swing.JSpinner spnFocus;
    private javax.swing.JSpinner spnLongBreak;
    private javax.swing.JSpinner spnShortBreak;
    private javax.swing.JTabbedPane tabSettings;
    private javax.swing.JPasswordField txtConfirmPassword;
    private javax.swing.JPasswordField txtCurrentPassword;
    private javax.swing.JTextField txtEmail;
    private javax.swing.JTextField txtFullName;
    private javax.swing.JPasswordField txtNewPassword;
    // End of variables declaration//GEN-END:variables
}
