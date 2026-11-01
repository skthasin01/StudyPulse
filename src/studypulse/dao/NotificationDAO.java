package studypulse.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import studypulse.model.Notification;
import studypulse.model.NotificationType;
import studypulse.util.DBConnection;
import studypulse.util.DateUtil;

/** Data access for table notifications. */
public class NotificationDAO {

    public int insert(Notification n) throws SQLException {
        String sql = "INSERT INTO notifications (user_id, type, message, created_at, is_read) "
                + "VALUES (?, ?, ?, ?, ?)";
        if (n.getCreatedAt() == null) {
            n.setCreatedAt(DateUtil.now());
        }
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, n.getUserId());
            ps.setString(2, n.getType().name());
            ps.setString(3, n.getMessage());
            ps.setString(4, DateUtil.formatDateTime(n.getCreatedAt()));
            ps.setInt(5, n.isRead() ? 1 : 0);
            ps.executeUpdate();
            int id = DaoUtil.readGeneratedId(ps);
            n.setId(id);
            return id;
        }
    }

    public boolean update(Notification n) throws SQLException {
        String sql = "UPDATE notifications SET type = ?, message = ?, is_read = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, n.getType().name());
            ps.setString(2, n.getMessage());
            ps.setInt(3, n.isRead() ? 1 : 0);
            ps.setInt(4, n.getId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM notifications WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public Notification findById(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM notifications WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Newest first. */
    public List<Notification> findAllByUser(int userId) throws SQLException {
        List<Notification> list = new ArrayList<Notification>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC, id DESC")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    public int countUnread(int userId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = 0")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** True if the user already got this exact notification at or after 'since'. */
    public boolean existsSince(int userId, NotificationType type, String message, LocalDateTime since)
            throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM notifications WHERE user_id = ? "
                        + "AND type = ? AND message = ? AND created_at >= ? LIMIT 1")) {
            ps.setInt(1, userId);
            ps.setString(2, type.name());
            ps.setString(3, message);
            ps.setString(4, DateUtil.formatDateTime(since));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** True if the user already got a notification of this type at or after 'since'. */
    public boolean existsTypeSince(int userId, NotificationType type, LocalDateTime since) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM notifications WHERE user_id = ? "
                        + "AND type = ? AND created_at >= ? LIMIT 1")) {
            ps.setInt(1, userId);
            ps.setString(2, type.name());
            ps.setString(3, DateUtil.formatDateTime(since));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Newest first; type null = all types. */
    public List<Notification> findByUserAndType(int userId, NotificationType type) throws SQLException {
        List<Notification> list = new ArrayList<Notification>();
        String sql = "SELECT * FROM notifications WHERE user_id = ?" + (type == null ? "" : " AND type = ?")
                + " ORDER BY created_at DESC, id DESC";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            if (type != null) {
                ps.setString(2, type.name());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    /** Unread notifications with id &gt; afterId created at or after 'since', oldest first. */
    public List<Notification> findUnreadAfter(int userId, int afterId, LocalDateTime since) throws SQLException {
        List<Notification> list = new ArrayList<Notification>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM notifications WHERE user_id = ? "
                        + "AND is_read = 0 AND id > ? AND created_at >= ? ORDER BY id")) {
            ps.setInt(1, userId);
            ps.setInt(2, afterId);
            ps.setString(3, DateUtil.formatDateTime(since));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    public boolean markRead(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("UPDATE notifications SET is_read = 1 WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public int markAllRead(int userId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE notifications SET is_read = 1 WHERE user_id = ? AND is_read = 0")) {
            ps.setInt(1, userId);
            return ps.executeUpdate();
        }
    }

    private Notification map(ResultSet rs) throws SQLException {
        Notification n = new Notification();
        n.setId(rs.getInt("id"));
        n.setUserId(rs.getInt("user_id"));
        n.setType(NotificationType.valueOf(rs.getString("type")));
        n.setMessage(rs.getString("message"));
        n.setCreatedAt(DateUtil.parseDateTime(rs.getString("created_at")));
        n.setRead(rs.getInt("is_read") == 1);
        return n;
    }
}
