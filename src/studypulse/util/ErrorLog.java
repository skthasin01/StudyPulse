package studypulse.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Technical error details go to data/error.log (next to the database), never to the user.
 * Users only see the friendly message of a ServiceException or a JOptionPane.
 */
public final class ErrorLog {

    public static final String FILE_NAME = "error.log";
    /** Shown to users after something unexpected went wrong. */
    public static final String SEE_LOG = "Details were saved to data/" + FILE_NAME + ".";

    private ErrorLog() {
    }

    /** Appends a time-stamped entry with the stack trace. Never throws. */
    public static synchronized void log(String what, Throwable error) {
        File file = logFile();
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
            out.println("[" + DateUtil.formatDateTime(DateUtil.now()) + "] " + what);
            if (error != null) {
                error.printStackTrace(out);
            }
            out.println();
        } catch (IOException | RuntimeException ex) {
            // logging must never break the app
        }
    }

    public static void log(String what) {
        log(what, null);
    }

    public static File logFile() {
        File folder;
        try {
            folder = DBConnection.getDataFolder();
        } catch (RuntimeException ex) {
            folder = new File(System.getProperty("user.dir"));
        }
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return new File(folder, FILE_NAME);
    }
}
