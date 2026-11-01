package studypulse.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import studypulse.model.StudyLog;
import studypulse.util.DBConnection;
import studypulse.util.DateUtil;

/** Data access for table study_logs. */
public class StudyLogDAO {

    public int insert(StudyLog log) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return insert(conn, log);
        }
    }

    /** Insert using the caller's connection (for transactions). */
    public int insert(Connection conn, StudyLog log) throws SQLException {
        String sql = "INSERT INTO study_logs (user_id, task_id, start_time, end_time, duration_minutes, "
                + "breaks_taken) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, log.getUserId());
            DaoUtil.setNullableInt(ps, 2, log.getTaskId());
            ps.setString(3, DateUtil.formatDateTime(log.getStartTime()));
            ps.setString(4, DateUtil.formatDateTime(log.getEndTime()));
            ps.setInt(5, log.getDurationMinutes());
            ps.setInt(6, log.getBreaksTaken());
            ps.executeUpdate();
            int id = DaoUtil.readGeneratedId(ps);
            log.setId(id);
            return id;
        }
    }

    public boolean update(StudyLog log) throws SQLException {
        String sql = "UPDATE study_logs SET task_id = ?, start_time = ?, end_time = ?, duration_minutes = ?, "
                + "breaks_taken = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            DaoUtil.setNullableInt(ps, 1, log.getTaskId());
            ps.setString(2, DateUtil.formatDateTime(log.getStartTime()));
            ps.setString(3, DateUtil.formatDateTime(log.getEndTime()));
            ps.setInt(4, log.getDurationMinutes());
            ps.setInt(5, log.getBreaksTaken());
            ps.setInt(6, log.getId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM study_logs WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public StudyLog findById(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM study_logs WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Newest session first. */
    public List<StudyLog> findAllByUser(int userId) throws SQLException {
        List<StudyLog> list = new ArrayList<StudyLog>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT * FROM study_logs WHERE user_id = ? ORDER BY start_time DESC, id DESC")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    /** Sessions that started at or after 'since', newest first. */
    public List<StudyLog> findByUserSince(int userId, LocalDateTime since) throws SQLException {
        List<StudyLog> list = new ArrayList<StudyLog>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM study_logs WHERE user_id = ? "
                        + "AND start_time >= ? ORDER BY start_time DESC, id DESC")) {
            ps.setInt(1, userId);
            ps.setString(2, DateUtil.formatDateTime(since));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    /** Study minutes per day (by start_time) from 'from' to 'to', both included. Days without logs are missing. */
    public Map<LocalDate, Integer> sumMinutesByDay(int userId, LocalDate from, LocalDate to) throws SQLException {
        Map<LocalDate, Integer> sums = new HashMap<LocalDate, Integer>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT substr(start_time, 1, 10), SUM(duration_minutes) "
                        + "FROM study_logs WHERE user_id = ? AND start_time >= ? AND start_time < ? "
                        + "GROUP BY substr(start_time, 1, 10)")) {
            ps.setInt(1, userId);
            ps.setString(2, DateUtil.formatDateTime(from.atStartOfDay()));
            ps.setString(3, DateUtil.formatDateTime(to.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sums.put(DateUtil.parseDate(rs.getString(1)), rs.getInt(2));
                }
            }
        }
        return sums;
    }

    /** Total study minutes of sessions that started in [from, to). */
    public int sumMinutesBetween(int userId, LocalDateTime from, LocalDateTime to) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT COALESCE(SUM(duration_minutes), 0) "
                        + "FROM study_logs WHERE user_id = ? AND start_time >= ? AND start_time < ?")) {
            ps.setInt(1, userId);
            ps.setString(2, DateUtil.formatDateTime(from));
            ps.setString(3, DateUtil.formatDateTime(to));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private StudyLog map(ResultSet rs) throws SQLException {
        StudyLog log = new StudyLog();
        log.setId(rs.getInt("id"));
        log.setUserId(rs.getInt("user_id"));
        log.setTaskId(DaoUtil.getNullableInt(rs, "task_id"));
        log.setStartTime(DateUtil.parseDateTime(rs.getString("start_time")));
        log.setEndTime(DateUtil.parseDateTime(rs.getString("end_time")));
        log.setDurationMinutes(rs.getInt("duration_minutes"));
        log.setBreaksTaken(rs.getInt("breaks_taken"));
        return log;
    }
}
