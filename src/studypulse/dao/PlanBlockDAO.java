package studypulse.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import studypulse.model.PlanBlock;
import studypulse.model.PlanStatus;
import studypulse.util.DBConnection;
import studypulse.util.DateUtil;

/** Data access for table plan_blocks. */
public class PlanBlockDAO {

    public int insert(PlanBlock block) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return insert(conn, block);
        }
    }

    /** Insert using the caller's connection (for transactions). */
    public int insert(Connection conn, PlanBlock block) throws SQLException {
        String sql = "INSERT INTO plan_blocks (user_id, task_id, plan_date, planned_minutes, status) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, block.getUserId());
            ps.setInt(2, block.getTaskId());
            ps.setString(3, DateUtil.formatDate(block.getPlanDate()));
            ps.setInt(4, block.getPlannedMinutes());
            ps.setString(5, block.getStatus().name());
            ps.executeUpdate();
            int id = DaoUtil.readGeneratedId(ps);
            block.setId(id);
            return id;
        }
    }

    public boolean update(PlanBlock block) throws SQLException {
        String sql = "UPDATE plan_blocks SET task_id = ?, plan_date = ?, planned_minutes = ?, status = ? "
                + "WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, block.getTaskId());
            ps.setString(2, DateUtil.formatDate(block.getPlanDate()));
            ps.setInt(3, block.getPlannedMinutes());
            ps.setString(4, block.getStatus().name());
            ps.setInt(5, block.getId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM plan_blocks WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public PlanBlock findById(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM plan_blocks WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Ordered by plan date, then id. */
    public List<PlanBlock> findAllByUser(int userId) throws SQLException {
        List<PlanBlock> list = new ArrayList<PlanBlock>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT * FROM plan_blocks WHERE user_id = ? ORDER BY plan_date, id")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    /** Blocks with plan_date from 'from' to 'to' (both included), by date then id. */
    public List<PlanBlock> findByUserBetween(int userId, LocalDate from, LocalDate to) throws SQLException {
        List<PlanBlock> list = new ArrayList<PlanBlock>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM plan_blocks WHERE user_id = ? "
                        + "AND plan_date >= ? AND plan_date <= ? ORDER BY plan_date, id")) {
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

    /** Deletes PLANNED blocks from 'from' on (DONE and SKIPPED stay). */
    public int deletePlannedFrom(Connection conn, int userId, LocalDate from) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM plan_blocks WHERE user_id = ? AND status = 'PLANNED' AND plan_date >= ?")) {
            ps.setInt(1, userId);
            ps.setString(2, DateUtil.formatDate(from));
            return ps.executeUpdate();
        }
    }

    public boolean updateStatus(Connection conn, int id, PlanStatus status) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE plan_blocks SET status = ? WHERE id = ?")) {
            ps.setString(1, status.name());
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    /** task_id -> minutes of its DONE blocks. */
    public Map<Integer, Integer> sumDoneMinutesByTask(int userId) throws SQLException {
        Map<Integer, Integer> sums = new HashMap<Integer, Integer>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT task_id, SUM(planned_minutes) "
                        + "FROM plan_blocks WHERE user_id = ? AND status = 'DONE' GROUP BY task_id")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sums.put(rs.getInt(1), rs.getInt(2));
                }
            }
        }
        return sums;
    }

    /** plan_date -> minutes of DONE blocks, for dates from 'from' on. */
    public Map<LocalDate, Integer> sumDoneMinutesByDateFrom(int userId, LocalDate from) throws SQLException {
        Map<LocalDate, Integer> sums = new HashMap<LocalDate, Integer>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT plan_date, SUM(planned_minutes) "
                        + "FROM plan_blocks WHERE user_id = ? AND status = 'DONE' AND plan_date >= ? "
                        + "GROUP BY plan_date")) {
            ps.setInt(1, userId);
            ps.setString(2, DateUtil.formatDate(from));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sums.put(DateUtil.parseDate(rs.getString(1)), rs.getInt(2));
                }
            }
        }
        return sums;
    }

    private PlanBlock map(ResultSet rs) throws SQLException {
        PlanBlock block = new PlanBlock();
        block.setId(rs.getInt("id"));
        block.setUserId(rs.getInt("user_id"));
        block.setTaskId(rs.getInt("task_id"));
        block.setPlanDate(DateUtil.parseDate(rs.getString("plan_date")));
        block.setPlannedMinutes(rs.getInt("planned_minutes"));
        block.setStatus(PlanStatus.valueOf(rs.getString("status")));
        return block;
    }
}
