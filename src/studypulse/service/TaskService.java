package studypulse.service;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import studypulse.dao.TaskDAO;
import studypulse.model.DeadlineState;
import studypulse.model.Difficulty;
import studypulse.model.Task;
import studypulse.model.TaskStatus;
import studypulse.model.TaskType;
import studypulse.util.DateUtil;
import studypulse.util.Session;

/** Task rules: validation, MISSED detection, filtering, estimate hints. */
public class TaskService {

    public static final int MAX_TITLE_LENGTH = 100;
    public static final int DUE_SOON_HOURS = 48;
    /** DONE tasks (with real minutes) needed before an estimate hint is shown. */
    public static final int MIN_TASKS_FOR_HINT = 3;

    private final TaskDAO taskDAO = new TaskDAO();
    private final RiskService riskService = new RiskService();

    /** All tasks of the logged-in user, ordered by deadline. */
    public List<Task> getTasks() throws ServiceException {
        try {
            return taskDAO.findAllByUser(Session.getUserId());
        } catch (SQLException ex) {
            throw new ServiceException("Could not load tasks.", ex);
        }
    }

    /** One task of the logged-in user. */
    public Task getTask(int id) throws ServiceException {
        try {
            Task task = taskDAO.findById(id);
            if (task == null || task.getUserId() != Session.getUserId()) {
                throw new ServiceException("Task not found.");
            }
            return task;
        } catch (SQLException ex) {
            throw new ServiceException("Could not load the task.", ex);
        }
    }

    /** Pending / in-progress tasks whose deadline has passed become MISSED. */
    public int markOverdueAsMissed() throws ServiceException {
        try {
            return taskDAO.markOverdueAsMissed(Session.getUserId(), DateUtil.now());
        } catch (SQLException ex) {
            throw new ServiceException("Could not update missed tasks.", ex);
        }
    }

    public int addTask(Task task) throws ServiceException {
        validate(task, true);
        task.setUserId(Session.getUserId());
        task.setStatus(TaskStatus.PENDING);
        task.setActualMinutes(0);
        task.setCreatedAt(DateUtil.now());
        task.setCompletedAt(null);
        try {
            int id = taskDAO.insert(task);
            riskService.refreshQuietly();
            return id;
        } catch (SQLException ex) {
            throw new ServiceException("Could not save the task.", ex);
        }
    }

    public void updateTask(Task task) throws ServiceException {
        validate(task, false);
        getTask(task.getId());   // ownership check
        // a missed task with a new future deadline is open again
        if (task.getStatus() == TaskStatus.MISSED && task.getDeadline().isAfter(DateUtil.now())) {
            task.setStatus(TaskStatus.PENDING);
        }
        try {
            taskDAO.update(task);
            riskService.refreshQuietly();
        } catch (SQLException ex) {
            throw new ServiceException("Could not update the task.", ex);
        }
    }

    /** Deletes the task; its plan_blocks and study_logs go with it (ON DELETE CASCADE). */
    public void deleteTask(int id) throws ServiceException {
        getTask(id);
        try {
            taskDAO.delete(id);
            riskService.refreshQuietly();
        } catch (SQLException ex) {
            throw new ServiceException("Could not delete the task.", ex);
        }
    }

    /** Status DONE and completed_at = now. */
    public Task markDone(int id) throws ServiceException {
        Task task = getTask(id);
        if (task.getStatus() == TaskStatus.DONE) {
            throw new ServiceException("This task is already done.");
        }
        task.setStatus(TaskStatus.DONE);
        task.setCompletedAt(DateUtil.now());
        try {
            taskDAO.update(task);
            riskService.refreshQuietly();
            return task;
        } catch (SQLException ex) {
            throw new ServiceException("Could not update the task.", ex);
        }
    }

    public void saveActualMinutes(int id, int minutes) throws ServiceException {
        if (minutes <= 0) {
            throw new ServiceException("Please enter a number of minutes above 0.");
        }
        Task task = getTask(id);
        task.setActualMinutes(minutes);
        try {
            taskDAO.update(task);
            riskService.refreshQuietly();
        } catch (SQLException ex) {
            throw new ServiceException("Could not update the task.", ex);
        }
    }

    /** Search on title and course (case-insensitive); null status / difficulty = all. */
    public List<Task> filter(List<Task> tasks, String search, TaskStatus status, Difficulty difficulty) {
        String text = search == null ? "" : search.trim().toLowerCase();
        List<Task> result = new ArrayList<Task>();
        for (Task task : tasks) {
            if (status != null && task.getStatus() != status) {
                continue;
            }
            if (difficulty != null && task.getDifficulty() != difficulty) {
                continue;
            }
            if (!text.isEmpty() && !contains(task.getTitle(), text) && !contains(task.getCourseName(), text)) {
                continue;
            }
            result.add(task);
        }
        return result;
    }

    public Map<TaskStatus, Integer> countByStatus(List<Task> tasks) {
        Map<TaskStatus, Integer> counts = new EnumMap<TaskStatus, Integer>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            counts.put(status, 0);
        }
        for (Task task : tasks) {
            counts.put(task.getStatus(), counts.get(task.getStatus()) + 1);
        }
        return counts;
    }

    public DeadlineState getDeadlineState(Task task) {
        if (task.getStatus() == TaskStatus.DONE) {
            return DeadlineState.DONE;
        }
        LocalDateTime now = DateUtil.now();
        if (task.getDeadline().isBefore(now)) {
            return DeadlineState.OVERDUE;
        }
        if (task.getDeadline().isBefore(now.plusHours(DUE_SOON_HOURS))) {
            return DeadlineState.DUE_SOON;
        }
        return DeadlineState.NORMAL;
    }

    /** "Done", "Overdue 2d", "3d 4h", "5h 20m". */
    public String formatTimeLeft(Task task) {
        if (task.getStatus() == TaskStatus.DONE) {
            return "Done";
        }
        long minutes = Duration.between(DateUtil.now(), task.getDeadline()).toMinutes();
        if (minutes < 0) {
            long late = -minutes;
            return "Overdue " + (late >= 1440 ? late / 1440 + "d" : late >= 60 ? late / 60 + "h" : late + "m");
        }
        if (minutes >= 1440) {
            return minutes / 1440 + "d " + (minutes % 1440) / 60 + "h";
        }
        return minutes / 60 + "h " + minutes % 60 + "m";
    }

    /**
     * Hint from the user's history: compares real and estimated minutes of DONE tasks
     * of the same type and difficulty (or only the same type if there are not enough).
     * Returns null when there are fewer than 3 such tasks.
     */
    public String suggestEstimate(Difficulty difficulty, TaskType type) {
        List<Task> all;
        try {
            all = taskDAO.findAllByUser(Session.getUserId());
        } catch (SQLException ex) {
            return null;
        }
        int[] same = sumMinutes(all, type, difficulty);
        if (same[0] >= MIN_TASKS_FOR_HINT) {
            return hint(difficulty.getLabel() + " " + plural(type), same);
        }
        int[] sameType = sumMinutes(all, type, null);
        if (sameType[0] >= MIN_TASKS_FOR_HINT) {
            return hint(plural(type), sameType);
        }
        return null;
    }

    /** {count, estimated sum, actual sum} of DONE tasks with real minutes. */
    private int[] sumMinutes(List<Task> tasks, TaskType type, Difficulty difficulty) {
        int[] sums = new int[3];
        for (Task task : tasks) {
            if (task.getStatus() == TaskStatus.DONE && task.getActualMinutes() > 0
                    && task.getTaskType() == type
                    && (difficulty == null || task.getDifficulty() == difficulty)) {
                sums[0]++;
                sums[1] += task.getEstimatedMinutes();
                sums[2] += task.getActualMinutes();
            }
        }
        return sums;
    }

    private String hint(String what, int[] sums) {
        long percent = Math.round((sums[2] - sums[1]) * 100.0 / sums[1]);
        if (percent >= 5) {
            return "Your " + what + " usually take " + percent + "% longer than estimated.";
        }
        if (percent <= -5) {
            return "Your " + what + " usually take " + (-percent) + "% less time than estimated.";
        }
        return "Your " + what + " usually take about as long as estimated.";
    }

    private static String plural(TaskType type) {
        switch (type) {
            case ASSIGNMENT:
                return "assignments";
            case EXAM:
                return "exams";
            case PROJECT:
                return "projects";
            default:
                return "preparation tasks";
        }
    }

    private void validate(Task task, boolean isNew) throws ServiceException {
        String title = task.getTitle() == null ? "" : task.getTitle().trim();
        if (title.isEmpty()) {
            throw new ServiceException("Please enter a title.");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new ServiceException("The title can have at most " + MAX_TITLE_LENGTH + " characters.");
        }
        task.setTitle(title);
        task.setCourseName(task.getCourseName() == null ? null : task.getCourseName().trim());
        if (task.getEstimatedMinutes() <= 0) {
            throw new ServiceException("The estimated time must be more than 0 minutes.");
        }
        if (task.getDeadline() == null) {
            throw new ServiceException("Please choose a deadline.");
        }
        if (isNew && task.getDeadline().isBefore(DateUtil.now())) {
            throw new ServiceException("The deadline cannot be in the past.");
        }
    }

    private static boolean contains(String value, String lowerText) {
        return value != null && value.toLowerCase().contains(lowerText);
    }
}
