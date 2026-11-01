package studypulse.dao;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import studypulse.model.User;
import studypulse.util.DBConnection;
import studypulse.util.PasswordUtil;

/** A fresh temporary SQLite file per test; data/studypulse.db is never touched. */
final class TestDatabase {

    private TestDatabase() {
    }

    static File create() throws IOException, SQLException {
        File file = File.createTempFile("studypulse-test-", ".db");
        file.deleteOnExit();
        DBConnection.useDatabaseFile(file);
        DBConnection.initDatabase();
        return file;
    }

    static void delete(File file) {
        DBConnection.useDatabaseFile(null);
        if (file != null && !file.delete()) {
            file.deleteOnExit();
        }
    }

    static User insertUser(String email) throws SQLException {
        String salt = PasswordUtil.generateSalt();
        User u = new User("Test Student", email, PasswordUtil.hash("secret1".toCharArray(), salt), salt);
        new UserDAO().insert(u);
        return u;
    }
}
