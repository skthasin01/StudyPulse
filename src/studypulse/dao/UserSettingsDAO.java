package studypulse.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import studypulse.model.AppTheme;
import studypulse.model.UserSettings;
import studypulse.util.DBConnection;

/** Data access for table user_settings (one row per user, UNIQUE user_id). */
public class UserSettingsDAO {

    public int insert(UserSettings s) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return insert(conn, s);
        }
    }

    /** Insert using the caller's connection (for transactions). */
    public int insert(Connection conn, UserSettings s) throws SQLException {
        String sql = "INSERT INTO user_settings (user_id, focus_minutes, short_break, long_break, "
                + "notifications_on, tray_on, theme) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, s.getUserId());
            ps.setInt(2, s.getFocusMinutes());
            ps.setInt(3, s.getShortBreak());
            ps.setInt(4, s.getLongBreak());
            ps.setInt(5, s.isNotificationsOn() ? 1 : 0);
            ps.setInt(6, s.isTrayOn() ? 1 : 0);
            ps.setString(7, s.getTheme().name());
            ps.executeUpdate();
            int id = DaoUtil.readGeneratedId(ps);
            s.setId(id);
            return id;
        }
    }

    public boolean update(UserSettings s) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return update(conn, s);
        }
    }

    /** Update using the caller's connection (for transactions). */
    public boolean update(Connection conn, UserSettings s) throws SQLException {
        String sql = "UPDATE user_settings SET focus_minutes = ?, short_break = ?, long_break = ?, "
                + "notifications_on = ?, tray_on = ?, theme = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, s.getFocusMinutes());
            ps.setInt(2, s.getShortBreak());
            ps.setInt(3, s.getLongBreak());
            ps.setInt(4, s.isNotificationsOn() ? 1 : 0);
            ps.setInt(5, s.isTrayOn() ? 1 : 0);
            ps.setString(6, s.getTheme().name());
            ps.setInt(7, s.getId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM user_settings WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public UserSettings findById(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM user_settings WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** The user's settings row, or null if it does not exist yet. */
    public UserSettings findByUser(int userId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM user_settings WHERE user_id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** At most one row (UNIQUE user_id); a list for the same API as the other DAOs. */
    public List<UserSettings> findAllByUser(int userId) throws SQLException {
        List<UserSettings> list = new ArrayList<UserSettings>();
        UserSettings s = findByUser(userId);
        if (s != null) {
            list.add(s);
        }
        return list;
    }

    private UserSettings map(ResultSet rs) throws SQLException {
        UserSettings s = new UserSettings();
        s.setId(rs.getInt("id"));
        s.setUserId(rs.getInt("user_id"));
        s.setFocusMinutes(rs.getInt("focus_minutes"));
        s.setShortBreak(rs.getInt("short_break"));
        s.setLongBreak(rs.getInt("long_break"));
        s.setNotificationsOn(rs.getInt("notifications_on") == 1);
        s.setTrayOn(rs.getInt("tray_on") == 1);
        s.setTheme(AppTheme.valueOf(rs.getString("theme")));
        return s;
    }
}
