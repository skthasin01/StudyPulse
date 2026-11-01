package studypulse.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import studypulse.dao.PlanBlockDAO;
import studypulse.dao.StudyLogDAO;
import studypulse.dao.TaskDAO;
import studypulse.engine.OverloadedTask;
import studypulse.engine.PlanGenerator;
import studypulse.engine.PlanResult;
import studypulse.model.DayPlan;
import studypulse.model.PlanBlock;
import studypulse.model.PlanBlockItem;
import studypulse.model.PlanStatus;
import studypulse.model.StudyLog;
import studypulse.model.Task;
import studypulse.model.TaskStatus;
import studypulse.util.DBConnection;
import studypulse.util.DateUtil;
import studypulse.util.Session;

/** Week view of the study plan, plan generation and block status changes. */
public class StudyPlanService {

    private final PlanBlockDAO planBlockDAO = new PlanBlockDAO();
    private final RiskService riskService = new RiskService();
    private final NotificationService notificationService = new NotificationService();
    private final TaskDAO taskDAO = new TaskDAO();
    private final StudyLogDAO studyLogDAO = new StudyLogDAO();
    private final TaskService taskService = new TaskService();
    private final AvailabilityService availabilityService = new AvailabilityService();

    /** 7 days starting at 'monday', each with its blocks and free minutes. */
    public List<DayPlan> getWeek(LocalDate monday) throws ServiceException {
        int userId = Session.getUserId();
        Map<Integer, Integer> free = availabilityService.getFreeMinutesByDay();
        int restDay = availabilityService.getRestDay();
        try {
            Map<Integer, Task> tasks = tasksById();
            List<DayPlan> week = new ArrayList<DayPlan>();
            for (int i = 0; i < 7; i++) {
                LocalDate date = monday.plusDays(i);
                int dow = date.getDayOfWeek().getValue();
                boolean rest = dow == restDay;
                Integer minutes = free.get(dow);
                week.add(new DayPlan(date, rest || minutes == null ? 0 : minutes, rest));
            }
            for (PlanBlock block : planBlockDAO.findByUserBetween(userId, monday, monday.plusDays(6))) {
                Task task = tasks.get(block.getTaskId());
                int index = (int) (block.getPlanDate().toEpochDay() - monday.toEpochDay());
                week.get(index).getItems().add(new PlanBlockItem(block,
                        task == null ? "(deleted task)" : task.getTitle(),
                        task == null ? null : task.getDifficulty()));
            }
            return week;
        } catch (SQLException ex) {
            throw new ServiceException("Could not load the study plan.", ex);
        }
    }

    /**
     * Deletes PLANNED blocks from today on and plans all open tasks again.
     * DONE and SKIPPED blocks stay; skipped minutes are planned again.
     */
    public PlanResult generatePlan() throws ServiceException {
        int userId = Session.getUserId();
        LocalDate today = DateUtil.today();
        taskService.markOverdueAsMissed();
        PlanGenerator generator = new PlanGenerator(availabilityService.getFreeMinutesByDay(),
                availabilityService.getRestDay());
        try {
            List<Task> tasks = taskDAO.findAllByUser(userId);
            PlanResult result = generator.generate(tasks, planBlockDAO.sumDoneMinutesByTask(userId),
                    planBlockDAO.sumDoneMinutesByDateFrom(userId, today), today);
            try (Connection conn = DBConnection.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    planBlockDAO.deletePlannedFrom(conn, userId, today);
                    for (PlanBlock block : result.getBlocks()) {
                        planBlockDAO.insert(conn, block);
                    }
                    conn.commit();
                } catch (SQLException ex) {
                    conn.rollback();
                    throw ex;
                }
            }
            riskService.refreshQuietly();
            if (!result.getBlocks().isEmpty()) {
                notificationService.info(userId, "Study plan generated: " + result.getBlocks().size()
                        + (result.getBlocks().size() == 1 ? " block" : " blocks") + " for "
                        + result.getPlannedTaskCount() + (result.getPlannedTaskCount() == 1 ? " task." : " tasks."));
            }
            return result;
        } catch (SQLException ex) {
            throw new ServiceException("Could not generate the plan.", ex);
        }
    }

    /** Text for the JOptionPane after generating. */
    public String summarize(PlanResult result) {
        if (result.getBlocks().isEmpty() && result.getOverloaded().isEmpty()) {
            return "There are no pending tasks to plan.\nAdd tasks on the Tasks screen first.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Planned ").append(result.getBlocks().size()).append(result.getBlocks().size() == 1
                ? " block" : " blocks").append(" for ").append(result.getPlannedTaskCount())
                .append(result.getPlannedTaskCount() == 1 ? " task." : " tasks.");
        List<OverloadedTask> over = result.getOverloaded();
        if (over.isEmpty()) {
            sb.append("\nEverything fits before its deadline.");
        } else {
            sb.append("\n\n").append(over.size()).append(over.size() == 1 ? " task does" : " tasks do")
                    .append(" not fit before the deadline:");
            for (OverloadedTask o : over) {
                sb.append("\n  • ").append(o.getTask().getTitle()).append(" (needs ")
                        .append(DateUtil.formatMinutes(o.getMissingMinutes())).append(" more)");
            }
            sb.append("\n\nAdd free time or move the deadline.");
        }
        return sb.toString();
    }

    /**
     * Block DONE + a study_logs row (duration = planned minutes) + task IN_PROGRESS,
     * in one transaction. Returns the task's remaining minutes (0 = all planned time done).
     */
    public int markBlockDone(int blockId) throws ServiceException {
        PlanBlock block = getBlock(blockId);
        if (block.getStatus() == PlanStatus.DONE) {
            throw new ServiceException("This block is already done.");
        }
        try {
            Task task = taskDAO.findById(block.getTaskId());
            LocalDateTime end = DateUtil.now();
            StudyLog log = new StudyLog();
            log.setUserId(block.getUserId());
            log.setTaskId(block.getTaskId());
            log.setStartTime(end.minusMinutes(block.getPlannedMinutes()));
            log.setEndTime(end);
            log.setDurationMinutes(block.getPlannedMinutes());
            log.setBreaksTaken(0);
            try (Connection conn = DBConnection.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    planBlockDAO.updateStatus(conn, blockId, PlanStatus.DONE);
                    studyLogDAO.insert(conn, log);
                    if (task.getStatus() == TaskStatus.PENDING) {
                        task.setStatus(TaskStatus.IN_PROGRESS);
                        taskDAO.update(conn, task);
                    }
                    conn.commit();
                } catch (SQLException ex) {
                    conn.rollback();
                    throw ex;
                }
            }
            riskService.refreshQuietly();
            return remainingMinutes(task);
        } catch (SQLException ex) {
            throw new ServiceException("Could not mark the block as done.", ex);
        }
    }

    public void skipBlock(int blockId) throws ServiceException {
        PlanBlock block = getBlock(blockId);
        if (block.getStatus() != PlanStatus.PLANNED) {
            throw new ServiceException("Only planned blocks can be skipped.");
        }
        try (Connection conn = DBConnection.getConnection()) {
            planBlockDAO.updateStatus(conn, blockId, PlanStatus.SKIPPED);
        } catch (SQLException ex) {
            throw new ServiceException("Could not skip the block.", ex);
        }
    }

    /** Task DONE after all its planned time was studied; actual minutes = studied block minutes. */
    public void completeTask(int taskId) throws ServiceException {
        Task task = taskService.getTask(taskId);
        task.setStatus(TaskStatus.DONE);
        task.setCompletedAt(DateUtil.now());
        try {
            if (task.getActualMinutes() <= 0) {
                Integer done = planBlockDAO.sumDoneMinutesByTask(task.getUserId()).get(taskId);
                task.setActualMinutes(done == null ? 0 : done);
            }
            taskDAO.update(task);
            riskService.refreshQuietly();
        } catch (SQLException ex) {
            throw new ServiceException("Could not update the task.", ex);
        }
    }

    public Task getTaskOfBlock(int blockId) throws ServiceException {
        return taskService.getTask(getBlock(blockId).getTaskId());
    }

    private int remainingMinutes(Task task) throws SQLException {
        return PlanGenerator.remainingMinutes(task, planBlockDAO.sumDoneMinutesByTask(task.getUserId()));
    }

    private PlanBlock getBlock(int blockId) throws ServiceException {
        try {
            PlanBlock block = planBlockDAO.findById(blockId);
            if (block == null || block.getUserId() != Session.getUserId()) {
                throw new ServiceException("Plan block not found.");
            }
            return block;
        } catch (SQLException ex) {
            throw new ServiceException("Could not load the plan block.", ex);
        }
    }

    private Map<Integer, Task> tasksById() throws SQLException {
        Map<Integer, Task> map = new HashMap<Integer, Task>();
        for (Task t : taskDAO.findAllByUser(Session.getUserId())) {
            map.put(t.getId(), t);
        }
        return map;
    }
}
