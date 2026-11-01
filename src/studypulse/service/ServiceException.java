package studypulse.service;

import studypulse.util.ErrorLog;

/**
 * A problem the user can fix (validation, wrong password ...) or a friendly message for a
 * technical error. The message is shown in the form as it is; with a cause, the technical
 * details are written to data/error.log and the message says so.
 */
public class ServiceException extends Exception {

    public ServiceException(String message) {
        super(message);
    }

    /** message = friendly text without technical details; the cause is logged. */
    public ServiceException(String message, Throwable cause) {
        super(message + "\n" + ErrorLog.SEE_LOG, cause);
        ErrorLog.log(message, cause);
    }
}
