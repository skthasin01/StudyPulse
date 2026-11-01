package studypulse.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import studypulse.dao.PlanBlockDAO;
import studypulse.dao.StudyLogDAO;
import studypulse.dao.TaskDAO;
import studypulse.model.PlanBlock;
import studypulse.model.StudyLog;
import studypulse.model.Task;
import studypulse.model.TaskStatus;
import studypulse.util.DateUtil;
import studypulse.util.Session;

/** Data for the Pomodoro timer: today's tasks and saving finished focus rounds as study logs. */
public class PomodoroService {

    /** More focus minutes than this without a real break -> "Consider taking a short break." */
    public static final int MAX_MINUTES_WITHOUT_BREAK = 120;

    private final PlanBlockDAO planBlockDAO = new PlanBlockDAO();
    private final TaskDAO taskDAO = new TaskDAO();
    private final StudyLogDAO studyLogDAO = new StudyLogDAO();
    private final RiskService riskService = new RiskService();

    /** Tasks with a plan block today that are not done yet, in plan order. */
    public List<Task> getTodayTasks() throws ServiceException {
        List<Task> list = new ArrayList<Task>();
        Set<Integer> seen = new HashSet<Integer>();
        LocalDate today = DateUtil.today();
        try {
            for (PlanBlock b : planBlockDAO.findByUserBetween(Session.getUserId(), today, today)) {
                if (seen.add(b.getTaskId())) {
                    Task t = taskDAO.findById(b.getTaskId());
                    if (t != null && t.getStatus() != TaskStatus.DONE) {
                        list.add(t);
                    }
                }
            }
            return list;
        } catch (SQLException ex) {
            throw new ServiceException("Could not load today's tasks.", ex);
        }
    }

    /** One task of the logged-in user, or null. */
    public Task findTask(int taskId) {
        try {
            Task t = taskDAO.findById(taskId);
            return t != null && t.getUserId() == Session.getUserId() ? t : null;
        } catch (SQLException ex) {
            return null;
        }
    }

    /**
     * Saves a finished (or stopped) focus round as a study_logs row and sets its task
     * from PENDING to IN_PROGRESS. Nothing is saved below 1 minute.
     *
     * @param taskId the task, or null for "No task"
     * @return the new study_logs id, or -1 when nothing was saved
     */
    public int saveFocusRound(Integer taskId, LocalDateTime start, LocalDateTime end, int minutes, int breaksTaken)
            throws ServiceException {
        if (minutes < 1) {
            return -1;
        }
        StudyLog log = new StudyLog();
        log.setUserId(Session.getUserId());
        log.setTaskId(taskId);
        log.setStartTime(start.withNano(0));
        log.setEndTime(end.withNano(0));
        log.setDurationMinutes(minutes);
        log.setBreaksTaken(breaksTaken);
        try {
            int id = studyLogDAO.insert(log);
            if (taskId != null) {
                Task task = taskDAO.findById(taskId);
                if (task != null && task.getStatus() == TaskStatus.PENDING) {
                    task.setStatus(TaskStatus.IN_PROGRESS);
                    taskDAO.update(task);
                }
            }
            riskService.refreshQuietly();
            return id;
        } catch (SQLException ex) {
            throw new ServiceException("Could not save the study session.", ex);
        }
    }
}
