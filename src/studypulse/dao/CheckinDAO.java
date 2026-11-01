package studypulse.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import studypulse.model.Checkin;
import studypulse.util.DBConnection;
import studypulse.util.DateUtil;

/** Data access for table checkins. */
public class CheckinDAO {

    public int insert(Checkin c) throws SQLException {
        String sql = "INSERT INTO checkins (user_id, checkin_date, stress_level, energy_level, mood_level, "
                + "sleep_hours, note) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, c.getUserId());
            ps.setString(2, DateUtil.formatDate(c.getCheckinDate()));
            ps.setInt(3, c.getStressLevel());
            ps.setInt(4, c.getEnergyLevel());
            ps.setInt(5, c.getMoodLevel());
            ps.setDouble(6, c.getSleepHours());
            ps.setString(7, c.getNote());
            ps.executeUpdate();
            int id = DaoUtil.readGeneratedId(ps);
            c.setId(id);
            return id;
        }
    }

    public boolean update(Checkin c) throws SQLException {
        String sql = "UPDATE checkins SET checkin_date = ?, stress_level = ?, energy_level = ?, mood_level = ?, "
                + "sleep_hours = ?, note = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, DateUtil.formatDate(c.getCheckinDate()));
            ps.setInt(2, c.getStressLevel());
            ps.setInt(3, c.getEnergyLevel());
            ps.setInt(4, c.getMoodLevel());
            ps.setDouble(5, c.getSleepHours());
            ps.setString(6, c.getNote());
            ps.setInt(7, c.getId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM checkins WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public Checkin findById(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM checkins WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** The check-in of one day, or null (one per user per day). */
    public Checkin findByUserAndDate(int userId, LocalDate date) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT * FROM checkins WHERE user_id = ? AND checkin_date = ?")) {
            ps.setInt(1, userId);
            ps.setString(2, DateUtil.formatDate(date));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Newest day first. */
    public List<Checkin> findAllByUser(int userId) throws SQLException {
        List<Checkin> list = new ArrayList<Checkin>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT * FROM checkins WHERE user_id = ? ORDER BY checkin_date DESC")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    /**
     * Insert or update by the UNIQUE (user_id, checkin_date) pair:
     * one check-in per user per day. Returns the row id.
     */
    public int saveForDay(Checkin c) throws SQLException {
        Checkin existing = findByUserAndDate(c.getUserId(), c.getCheckinDate());
        if (existing == null) {
            return insert(c);
        }
        c.setId(existing.getId());
        update(c);
        return existing.getId();
    }

    /** Check-ins from 'from' to 'to' (both included), newest first. */
    public List<Checkin> findByUserBetween(int userId, LocalDate from, LocalDate to) throws SQLException {
        List<Checkin> list = new ArrayList<Checkin>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM checkins WHERE user_id = ? "
                        + "AND checkin_date >= ? AND checkin_date <= ? ORDER BY checkin_date DESC")) {
            ps.setInt(1, userId);
            ps.setString(2, DateUtil.formatDate(from));
            ps.setString(3, DateUtil.formatDate(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    /** Dates with a check-in up to 'upTo', newest first (for the streak). */
    public List<LocalDate> findDatesUpTo(int userId, LocalDate upTo) throws SQLException {
        List<LocalDate> dates = new ArrayList<LocalDate>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT checkin_date FROM checkins "
                        + "WHERE user_id = ? AND checkin_date <= ? ORDER BY checkin_date DESC")) {
            ps.setInt(1, userId);
            ps.setString(2, DateUtil.formatDate(upTo));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    dates.add(DateUtil.parseDate(rs.getString(1)));
                }
            }
        }
        return dates;
    }

    /** The newest 'limit' check-ins on or after 'since', newest first. */
    public List<Checkin> findLatest(int userId, int limit, LocalDate since) throws SQLException {
        List<Checkin> list = new ArrayList<Checkin>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM checkins WHERE user_id = ? "
                        + "AND checkin_date >= ? ORDER BY checkin_date DESC LIMIT ?")) {
            ps.setInt(1, userId);
            ps.setString(2, DateUtil.formatDate(since));
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    /** Deletes all check-ins of the user (privacy). Returns the number of rows. */
    public int deleteAllByUser(int userId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM checkins WHERE user_id = ?")) {
            ps.setInt(1, userId);
            return ps.executeUpdate();
        }
    }

    private Checkin map(ResultSet rs) throws SQLException {
        Checkin c = new Checkin();
        c.setId(rs.getInt("id"));
        c.setUserId(rs.getInt("user_id"));
        c.setCheckinDate(DateUtil.parseDate(rs.getString("checkin_date")));
        c.setStressLevel(rs.getInt("stress_level"));
        c.setEnergyLevel(rs.getInt("energy_level"));
        c.setMoodLevel(rs.getInt("mood_level"));
        c.setSleepHours(rs.getDouble("sleep_hours"));
        c.setNote(rs.getString("note"));
        return c;
    }
}
