package studypulse.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import studypulse.model.User;
import studypulse.util.DBConnection;
import studypulse.util.DateUtil;

/** Data access for table users. */
public class UserDAO {

    public int insert(User user) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return insert(conn, user);
        }
    }

    /** Insert using the caller's connection (for transactions). */
    public int insert(Connection conn, User user) throws SQLException {
        String sql = "INSERT INTO users (full_name, email, password_hash, salt, created_at, "
                + "wellbeing_enabled, rest_day) VALUES (?, ?, ?, ?, ?, ?, ?)";
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(DateUtil.now());
        }
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getSalt());
            ps.setString(5, DateUtil.formatDateTime(user.getCreatedAt()));
            ps.setInt(6, user.isWellbeingEnabled() ? 1 : 0);
            ps.setInt(7, user.getRestDay());
            ps.executeUpdate();
            int id = DaoUtil.readGeneratedId(ps);
            user.setId(id);
            return id;
        }
    }

    public boolean update(User user) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return update(conn, user);
        }
    }

    /** Update using the caller's connection (for transactions). */
    public boolean update(Connection conn, User user) throws SQLException {
        String sql = "UPDATE users SET full_name = ?, email = ?, password_hash = ?, salt = ?, "
                + "wellbeing_enabled = ?, rest_day = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getSalt());
            ps.setInt(5, user.isWellbeingEnabled() ? 1 : 0);
            ps.setInt(6, user.getRestDay());
            ps.setInt(7, user.getId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public User findById(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Email lookup is case-insensitive. */
    public User findByEmail(String email) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT * FROM users WHERE email = ? COLLATE NOCASE")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public List<User> findAll() throws SQLException {
        List<User> list = new ArrayList<User>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM users ORDER BY id");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    private User map(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getInt("id"));
        user.setFullName(rs.getString("full_name"));
        user.setEmail(rs.getString("email"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setSalt(rs.getString("salt"));
        user.setCreatedAt(DateUtil.parseDateTime(rs.getString("created_at")));
        user.setWellbeingEnabled(rs.getInt("wellbeing_enabled") == 1);
        user.setRestDay(rs.getInt("rest_day"));
        return user;
    }
}
