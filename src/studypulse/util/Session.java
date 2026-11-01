package studypulse.util;

import studypulse.model.User;

/** Holds the user who is logged in while the application runs. */
public final class Session {

    private static User user;

    private Session() {
    }

    public static User getUser() {
        return user;
    }

    public static void setUser(User loggedInUser) {
        user = loggedInUser;
    }

    public static void logout() {
        user = null;
    }

    public static boolean isLoggedIn() {
        return user != null;
    }

    /** Id of the logged-in user, or -1 when nobody is logged in. */
    public static int getUserId() {
        return user == null ? -1 : user.getId();
    }
}
