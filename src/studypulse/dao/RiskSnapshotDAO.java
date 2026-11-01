package studypulse.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import studypulse.model.RiskLevel;
import studypulse.model.RiskSnapshot;
import studypulse.util.DBConnection;
import studypulse.util.DateUtil;

/** Data access for table risk_snapshots. */
public class RiskSnapshotDAO {

    public int insert(RiskSnapshot s) throws SQLException {
        String sql = "INSERT INTO risk_snapshots (user_id, snapshot_date, workload_index, burnout_score, "
                + "risk_level, reasons) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, s.getUserId());
            ps.setString(2, DateUtil.formatDate(s.getSnapshotDate()));
            ps.setDouble(3, s.getWorkloadIndex());
            ps.setDouble(4, s.getBurnoutScore());
            ps.setString(5, s.getRiskLevel().name());
            ps.setString(6, s.getReasons());
            ps.executeUpdate();
            int id = DaoUtil.readGeneratedId(ps);
            s.setId(id);
            return id;
        }
    }

    public boolean update(RiskSnapshot s) throws SQLException {
        String sql = "UPDATE risk_snapshots SET snapshot_date = ?, workload_index = ?, burnout_score = ?, "
                + "risk_level = ?, reasons = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, DateUtil.formatDate(s.getSnapshotDate()));
            ps.setDouble(2, s.getWorkloadIndex());
            ps.setDouble(3, s.getBurnoutScore());
            ps.setString(4, s.getRiskLevel().name());
            ps.setString(5, s.getReasons());
            ps.setInt(6, s.getId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM risk_snapshots WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public RiskSnapshot findById(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM risk_snapshots WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** The snapshot of one day, or null (one per user per day). */
    public RiskSnapshot findByUserAndDate(int userId, LocalDate date) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT * FROM risk_snapshots WHERE user_id = ? AND snapshot_date = ?")) {
            ps.setInt(1, userId);
            ps.setString(2, DateUtil.formatDate(date));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Oldest day first (ready for charts). */
    public List<RiskSnapshot> findAllByUser(int userId) throws SQLException {
        List<RiskSnapshot> list = new ArrayList<RiskSnapshot>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT * FROM risk_snapshots WHERE user_id = ? ORDER BY snapshot_date")) {
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
     * Insert or replace by the UNIQUE (user_id, snapshot_date) pair:
     * one snapshot per user per day. Returns the row id (kept when replacing).
     */
    public int saveForDay(RiskSnapshot s) throws SQLException {
        RiskSnapshot existing = findByUserAndDate(s.getUserId(), s.getSnapshotDate());
        if (existing == null) {
            return insert(s);
        }
        s.setId(existing.getId());
        update(s);
        return existing.getId();
    }

    /** Snapshots from 'from' to 'to' (both included), newest first. */
    public List<RiskSnapshot> findByUserBetween(int userId, LocalDate from, LocalDate to) throws SQLException {
        List<RiskSnapshot> list = new ArrayList<RiskSnapshot>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM risk_snapshots WHERE user_id = ? "
                        + "AND snapshot_date >= ? AND snapshot_date <= ? ORDER BY snapshot_date DESC")) {
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

    /** Deletes all risk snapshots of the user (privacy). Returns the number of rows. */
    public int deleteAllByUser(int userId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM risk_snapshots WHERE user_id = ?")) {
            ps.setInt(1, userId);
            return ps.executeUpdate();
        }
    }

    private RiskSnapshot map(ResultSet rs) throws SQLException {
        RiskSnapshot s = new RiskSnapshot();
        s.setId(rs.getInt("id"));
        s.setUserId(rs.getInt("user_id"));
        s.setSnapshotDate(DateUtil.parseDate(rs.getString("snapshot_date")));
        s.setWorkloadIndex(rs.getDouble("workload_index"));
        s.setBurnoutScore(rs.getDouble("burnout_score"));
        s.setRiskLevel(RiskLevel.valueOf(rs.getString("risk_level")));
        s.setReasons(rs.getString("reasons"));
        return s;
    }
}
