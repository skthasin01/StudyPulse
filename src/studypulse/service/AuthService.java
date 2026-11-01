package studypulse.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.regex.Pattern;
import studypulse.dao.AvailabilityDAO;
import studypulse.dao.UserDAO;
import studypulse.dao.UserSettingsDAO;
import studypulse.model.Availability;
import studypulse.model.User;
import studypulse.model.UserSettings;
import studypulse.util.DBConnection;
import studypulse.util.PasswordUtil;
import studypulse.util.Session;

/** Sign-up and login. */
public class AuthService {

    public static final int MIN_PASSWORD_LENGTH = 6;
    public static final int DEFAULT_REST_DAY = 5;            // Friday
    public static final int DEFAULT_FREE_MINUTES = 180;

    private static final String LOGIN_FAILED = "Email or password is incorrect.";
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserDAO userDAO = new UserDAO();
    private final RiskService riskService = new RiskService();
    private final AvailabilityDAO availabilityDAO = new AvailabilityDAO();
    private final UserSettingsDAO settingsDAO = new UserSettingsDAO();

    /**
     * Validates the input, creates the user and 7 availability rows
     * (180 minutes every day, 0 on the rest day Friday) and logs the user in.
     */
    public User register(String fullName, String email, char[] password, char[] confirmPassword,
            boolean wellbeingEnabled) throws ServiceException {
        String name = fullName == null ? "" : fullName.trim();
        String mail = normalizeEmail(email);

        if (name.isEmpty() || mail.isEmpty() || password == null || password.length == 0
                || confirmPassword == null || confirmPassword.length == 0) {
            throw new ServiceException("Please fill in all fields.");
        }
        if (!EMAIL_PATTERN.matcher(mail).matches()) {
            throw new ServiceException("Please enter a valid email address.");
        }
        checkNewPassword(password, confirmPassword);

        try {
            if (userDAO.findByEmail(mail) != null) {
                throw new ServiceException("This email is already registered.");
            }
            String salt = PasswordUtil.generateSalt();
            User user = new User(name, mail, PasswordUtil.hash(password, salt), salt);
            user.setWellbeingEnabled(wellbeingEnabled);
            user.setRestDay(DEFAULT_REST_DAY);

            try (Connection conn = DBConnection.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    userDAO.insert(conn, user);
                    // Monday (1) ... Sunday (7)
                    for (int day = 1; day <= 7; day++) {
                        int minutes = day == DEFAULT_REST_DAY ? 0 : DEFAULT_FREE_MINUTES;
                        availabilityDAO.insert(conn, new Availability(user.getId(), day, minutes));
                    }
                    settingsDAO.insert(conn, new UserSettings(user.getId()));
                    conn.commit();
                } catch (SQLException ex) {
                    conn.rollback();
                    throw ex;
                }
            }
            Session.setUser(user);
            riskService.refreshQuietly();
            return user;
        } catch (SQLException ex) {
            throw new ServiceException("Could not create the account.", ex);
        }
    }

    /** Checks email and password and stores the user in the Session. */
    public User login(String email, char[] password) throws ServiceException {
        String mail = normalizeEmail(email);
        if (mail.isEmpty() || password == null || password.length == 0) {
            throw new ServiceException("Please enter your email and password.");
        }
        try {
            User user = userDAO.findByEmail(mail);
            if (user == null || !PasswordUtil.verify(password, user.getSalt(), user.getPasswordHash())) {
                throw new ServiceException(LOGIN_FAILED);
            }
            Session.setUser(user);
            riskService.refreshQuietly();
            return user;
        } catch (SQLException ex) {
            throw new ServiceException("Could not log in.", ex);
        }
    }

    public void logout() {
        Session.logout();
    }

    /** Sign-up password rules (also used to change the password): 6+ characters, both equal. */
    public static void checkNewPassword(char[] password, char[] confirmPassword) throws ServiceException {
        if (password == null || password.length == 0 || confirmPassword == null || confirmPassword.length == 0) {
            throw new ServiceException("Please fill in all password fields.");
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            throw new ServiceException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (!Arrays.equals(password, confirmPassword)) {
            throw new ServiceException("Passwords do not match.");
        }
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
