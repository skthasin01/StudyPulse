package studypulse;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.EventQueue;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import studypulse.forms.LoginFrame;
import studypulse.service.DatabaseService;
import studypulse.util.ErrorLog;
import studypulse.util.UITheme;

/** Starts StudyPulse: look and feel, error handling, database + schema check, first screen. */
public class Main {

    public static void main(String[] args) {
        installErrorHandler();
        setupLookAndFeel();
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                DatabaseService database = new DatabaseService();
                try {
                    database.initialize();
                    for (String line : database.checkSchema()) {
                        System.out.println(line);
                    }
                } catch (Exception ex) {
                    ErrorLog.log("Could not create the database", ex);
                    JOptionPane.showMessageDialog(null,
                            "StudyPulse could not open its database.\n" + ErrorLog.SEE_LOG,
                            "StudyPulse", JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                    return;
                }
                new LoginFrame().setVisible(true);
            }
        });
    }

    /** Any unexpected error: details to data/error.log, a short friendly message to the user. */
    private static void installErrorHandler() {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, final Throwable error) {
                ErrorLog.log("Unexpected error in thread " + thread.getName(), error);
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        JOptionPane.showMessageDialog(null,
                                "Something went wrong, but your data is safe.\n" + ErrorLog.SEE_LOG,
                                "StudyPulse", JOptionPane.ERROR_MESSAGE);
                    }
                });
            }
        });
    }

    private static void setupLookAndFeel() {
        Map<String, String> defaults = new HashMap<String, String>();
        defaults.put("@accentColor", "#2E7D9A");
        FlatLaf.setGlobalExtraDefaults(defaults);
        if (!FlatLightLaf.setup()) {
            ErrorLog.log("FlatLaf could not be installed, using the default look and feel.");
        }
        UIManager.put("defaultFont", UITheme.FONT_NORMAL);
        UIManager.put("Button.arc", 8);
        UIManager.put("Component.arc", 8);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("Table.showHorizontalLines", true);
    }
}
