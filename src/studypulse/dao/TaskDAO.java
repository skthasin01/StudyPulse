package studypulse.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import studypulse.model.Difficulty;
import studypulse.model.Priority;
import studypulse.model.Task;
import studypulse.model.TaskStatus;
import studypulse.model.TaskType;
import studypulse.util.DBConnection;
import studypulse.util.DateUtil;

/** Data access for table tasks. */
public class TaskDAO {

    public int insert(Task task) throws SQLException {
        String sql = "INSERT INTO tasks (user_id, title, description, task_type, course_name, difficulty, "
                + "estimated_minutes, actual_minutes, deadline, priority, status, created_at, completed_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        if (task.getCreatedAt() == null) {
            task.setCreatedAt(DateUtil.now());
        }
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, task.getUserId());
            ps.setString(2, task.getTitle());
            ps.setString(3, task.getDescription());
            ps.setString(4, task.getTaskType().name());
            ps.setString(5, task.getCourseName());
            ps.setString(6, task.getDifficulty().name());
            ps.setInt(7, task.getEstimatedMinutes());
            ps.setInt(8, task.getActualMinutes());
            ps.setString(9, DateUtil.formatDateTime(task.getDeadline()));
            ps.setString(10, task.getPriority().name());
            ps.setString(11, task.getStatus().name());
            ps.setString(12, DateUtil.formatDateTime(task.getCreatedAt()));
            ps.setString(13, DateUtil.formatDateTime(task.getCompletedAt()));
            ps.executeUpdate();
            int id = DaoUtil.readGeneratedId(ps);
            task.setId(id);
            return id;
        }
    }

    public boolean update(Task task) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return update(conn, task);
        }
    }

    /** Update using the caller's connection (for transactions). */
    public boolean update(Connection conn, Task task) throws SQLException {
        String sql = "UPDATE tasks SET title = ?, description = ?, task_type = ?, course_name = ?, "
                + "difficulty = ?, estimated_minutes = ?, actual_minutes = ?, deadline = ?, priority = ?, "
                + "status = ?, completed_at = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, task.getTitle());
            ps.setString(2, task.getDescription());
            ps.setString(3, task.getTaskType().name());
            ps.setString(4, task.getCourseName());
            ps.setString(5, task.getDifficulty().name());
            ps.setInt(6, task.getEstimatedMinutes());
            ps.setInt(7, task.getActualMinutes());
            ps.setString(8, DateUtil.formatDateTime(task.getDeadline()));
            ps.setString(9, task.getPriority().name());
            ps.setString(10, task.getStatus().name());
            ps.setString(11, DateUtil.formatDateTime(task.getCompletedAt()));
            ps.setInt(12, task.getId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM tasks WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public Task findById(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM tasks WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Ordered by deadline, then id. */
    public List<Task> findAllByUser(int userId) throws SQLException {
        List<Task> list = new ArrayList<Task>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT * FROM tasks WHERE user_id = ? ORDER BY deadline, id")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    /** MISSED tasks whose deadline lies between 'from' and 'to'. */
    public int countMissedBetween(int userId, LocalDateTime from, LocalDateTime to) throws SQLException {
        String sql = "SELECT COUNT(*) FROM tasks WHERE user_id = ? AND status = 'MISSED' "
                + "AND deadline >= ? AND deadline <= ?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, DateUtil.formatDateTime(from));
            ps.setString(3, DateUtil.formatDateTime(to));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** PENDING / IN_PROGRESS tasks whose deadline is before 'now' become MISSED. Returns the count. */
    public int markOverdueAsMissed(int userId, LocalDateTime now) throws SQLException {
        String sql = "UPDATE tasks SET status = 'MISSED' WHERE user_id = ? "
                + "AND status IN ('PENDING', 'IN_PROGRESS') AND deadline < ?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, DateUtil.formatDateTime(now));
            return ps.executeUpdate();
        }
    }

    private Task map(ResultSet rs) throws SQLException {
        Task task = new Task();
        task.setId(rs.getInt("id"));
        task.setUserId(rs.getInt("user_id"));
        task.setTitle(rs.getString("title"));
        task.setDescription(rs.getString("description"));
        task.setTaskType(TaskType.valueOf(rs.getString("task_type")));
        task.setCourseName(rs.getString("course_name"));
        task.setDifficulty(Difficulty.valueOf(rs.getString("difficulty")));
        task.setEstimatedMinutes(rs.getInt("estimated_minutes"));
        task.setActualMinutes(rs.getInt("actual_minutes"));
        task.setDeadline(DateUtil.parseDateTime(rs.getString("deadline")));
        task.setPriority(Priority.valueOf(rs.getString("priority")));
        task.setStatus(TaskStatus.valueOf(rs.getString("status")));
        task.setCreatedAt(DateUtil.parseDateTime(rs.getString("created_at")));
        task.setCompletedAt(DateUtil.parseDateTime(rs.getString("completed_at")));
        return task;
    }
}
