package studypulse.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import studypulse.model.Availability;
import studypulse.util.DBConnection;

/** Data access for table availability. */
public class AvailabilityDAO {

    public int insert(Availability a) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return insert(conn, a);
        }
    }

    /** Insert using the caller's connection (for transactions). */
    public int insert(Connection conn, Availability a) throws SQLException {
        String sql = "INSERT INTO availability (user_id, day_of_week, free_minutes) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, a.getUserId());
            ps.setInt(2, a.getDayOfWeek());
            ps.setInt(3, a.getFreeMinutes());
            ps.executeUpdate();
            int id = DaoUtil.readGeneratedId(ps);
            a.setId(id);
            return id;
        }
    }

    public boolean update(Availability a) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return update(conn, a);
        }
    }

    /** Update using the caller's connection (for transactions). */
    public boolean update(Connection conn, Availability a) throws SQLException {
        String sql = "UPDATE availability SET user_id = ?, day_of_week = ?, free_minutes = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, a.getUserId());
            ps.setInt(2, a.getDayOfWeek());
            ps.setInt(3, a.getFreeMinutes());
            ps.setInt(4, a.getId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM availability WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public Availability findById(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM availability WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public Availability findByUserAndDay(int userId, int dayOfWeek) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT * FROM availability WHERE user_id = ? AND day_of_week = ?")) {
            ps.setInt(1, userId);
            ps.setInt(2, dayOfWeek);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Ordered Monday (1) to Sunday (7). */
    public List<Availability> findAllByUser(int userId) throws SQLException {
        List<Availability> list = new ArrayList<Availability>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT * FROM availability WHERE user_id = ? ORDER BY day_of_week")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    private Availability map(ResultSet rs) throws SQLException {
        Availability a = new Availability();
        a.setId(rs.getInt("id"));
        a.setUserId(rs.getInt("user_id"));
        a.setDayOfWeek(rs.getInt("day_of_week"));
        a.setFreeMinutes(rs.getInt("free_minutes"));
        return a;
    }
}
