package studypulse.util;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import studypulse.dao.AvailabilityDAO;
import studypulse.dao.CheckinDAO;
import studypulse.dao.NotificationDAO;
import studypulse.dao.PlanBlockDAO;
import studypulse.dao.RiskSnapshotDAO;
import studypulse.dao.StudyLogDAO;
import studypulse.dao.TaskDAO;
import studypulse.dao.UserDAO;
import studypulse.dao.UserSettingsDAO;
import studypulse.engine.BurnoutRiskCalculator;
import studypulse.engine.RiskAssessment;
import studypulse.engine.RiskReason;
import studypulse.engine.WorkloadCalculator;
import studypulse.model.Availability;
import studypulse.model.Checkin;
import studypulse.model.Difficulty;
import studypulse.model.Notification;
import studypulse.model.NotificationType;
import studypulse.model.PlanBlock;
import studypulse.model.PlanStatus;
import studypulse.model.Priority;
import studypulse.model.RiskSnapshot;
import studypulse.model.StudyLog;
import studypulse.model.Task;
import studypulse.model.TaskStatus;
import studypulse.model.TaskType;
import studypulse.model.User;
import studypulse.model.UserSettings;
import studypulse.service.NotificationService;
import studypulse.service.RiskService;
import studypulse.service.ServiceException;
import studypulse.service.StudyPlanService;

/**
 * Creates the demo account demo@studypulse.com / demo123 with three weeks of realistic data for a
 * 2nd-year CSE student: a balanced first week, stress rising in week 2 and sleep below 6 h in
 * week 3. Every row goes through the DAOs (ids follow the ID rule, in date order); the risk
 * snapshots of the past 20 days are calculated by the real WorkloadCalculator and
 * BurnoutRiskCalculator from the data as it was on that day.
 */
public final class DemoDataSeeder {

    public static final String EMAIL = "demo@studypulse.com";
    public static final String PASSWORD = "demo123";
    public static final String FULL_NAME = "Demo Student";
    public static final int DAYS = 21;
    private static final int FREE_MINUTES = 180;
    private static final int REST_DAY = 5;   // Friday

    /** title, type, difficulty, priority, course, estimated, deadline (days from today), status, completed (days), actual. */
    private static final Object[][] TASKS = {
        {"OOP Quiz Prep", TaskType.PREPARATION, Difficulty.EASY, Priority.LOW, "CSE 2101 OOP", 90, -13, TaskStatus.DONE, -14, 100},
        {"Math Problem Set 4", TaskType.ASSIGNMENT, Difficulty.MEDIUM, Priority.MEDIUM, "MAT 2103 Math", 180, -11, TaskStatus.DONE, -12, 210},
        {"DBMS Lab Report", TaskType.ASSIGNMENT, Difficulty.MEDIUM, Priority.HIGH, "CSE 2104 DBMS Lab", 240, -8, TaskStatus.DONE, -9, 300},
        {"Discrete Math Quiz Prep", TaskType.PREPARATION, Difficulty.EASY, Priority.LOW, "CSE 2105 Discrete Math", 60, -6, TaskStatus.DONE, -7, 75},
        {"Data Structures Assignment 2", TaskType.ASSIGNMENT, Difficulty.HARD, Priority.HIGH, "CSE 2102 Data Structures", 300, -5, TaskStatus.DONE, -5, 390},
        {"Technical Writing Essay", TaskType.ASSIGNMENT, Difficulty.MEDIUM, Priority.LOW, "HUM 2107 Writing", 150, -4, TaskStatus.MISSED, null, 0},
        {"Digital Logic Lab 4", TaskType.ASSIGNMENT, Difficulty.EASY, Priority.MEDIUM, "EEE 2106 Digital Logic Lab", 90, -2, TaskStatus.MISSED, null, 0},
        {"Data Structures Assignment 3", TaskType.ASSIGNMENT, Difficulty.HARD, Priority.HIGH, "CSE 2102 Data Structures", 360, 2, TaskStatus.PENDING, null, 0},
        {"Computer Networks Mid-term", TaskType.EXAM, Difficulty.HARD, Priority.HIGH, "CSE 2203 Computer Networks", 480, 4, TaskStatus.PENDING, null, 0},
        {"Math Problem Set 5", TaskType.ASSIGNMENT, Difficulty.MEDIUM, Priority.MEDIUM, "MAT 2103 Math", 180, 6, TaskStatus.PENDING, null, 0},
        {"OOP Lab Exam Prep", TaskType.PREPARATION, Difficulty.MEDIUM, Priority.MEDIUM, "CSE 2101 OOP Lab", 240, 11, TaskStatus.PENDING, null, 0},
        {"Software Engineering Project", TaskType.PROJECT, Difficulty.HARD, Priority.HIGH, "CSE 2205 Software Engineering", 900, 19, TaskStatus.PENDING, null, 0},
    };

    /** Check-ins for day 0..20 (20 = today): sleep, stress, energy, mood. */
    private static final double[] SLEEP = {7.5, 8, 7.5, 7, 8, 7.5, 8, 7, 6.5, 6.5, 6.5, 6, 6.5, 6,
        5.5, 5.5, 5, 5, 4.5, 5, 4.5};
    private static final int[] STRESS = {2, 2, 1, 2, 2, 2, 2, 3, 3, 4, 4, 4, 5, 4, 4, 5, 4, 5, 5, 4, 5};
    private static final int[] ENERGY = {4, 4, 5, 4, 4, 4, 5, 3, 3, 3, 3, 3, 3, 3, 2, 2, 2, 1, 2, 1, 2};
    private static final int[] MOOD = {4, 4, 5, 4, 4, 5, 4, 3, 3, 3, 3, 3, 3, 3, 2, 2, 2, 2, 1, 2, 2};
    private static final String[] NOTES = new String[DAYS];

    static {
        NOTES[0] = "Fresh start of the term, feeling good.";
        NOTES[9] = "DBMS lab report took longer than planned.";
        NOTES[12] = "Two deadlines this week, a bit stressed.";
        NOTES[16] = "Stayed up late for the Data Structures assignment.";
        NOTES[18] = "Missed the essay deadline, very tired.";
        NOTES[20] = "Mid-term coming, slept badly.";
    }

    private DemoDataSeeder() {
    }

    /**
     * Creates the demo account and its data. Returns false (and changes nothing) if
     * demo@studypulse.com already exists. Logs out of the demo account afterwards.
     */
    public static boolean seed() throws ServiceException {
        UserDAO userDAO = new UserDAO();
        User previous = Session.getUser();
        try {
            if (userDAO.findByEmail(EMAIL) != null) {
                return false;
            }
            LocalDate today = DateUtil.today();
            LocalDate start = today.minusDays(DAYS - 1);
            User user = createUser(userDAO, start.minusDays(1).atTime(20, 0));
            Session.setUser(user);
            int userId = user.getId();

            List<Task> tasks = createTasks(userId, today);
            List<PlanBlock> doneBlocks = createStudyHistory(userId, tasks, start, today);
            createCheckins(userId, start);
            createPastSnapshots(userId, tasks, doneBlocks, start, today);
            createNotifications(userId, today);

            // today: the real services (plan from today on, risk report, current warnings)
            new StudyPlanService().generatePlan();
            new RiskService().calculateToday(userId);
            new NotificationService().runChecks(userId);
            return true;
        } catch (SQLException ex) {
            throw new ServiceException("Could not load the demo data.", ex);
        } finally {
            Session.logout();
            if (previous != null) {
                Session.setUser(previous);
            }
        }
    }

    /** Same rows as AuthService.register: user, 7 availability rows, settings. */
    private static User createUser(UserDAO userDAO, LocalDateTime createdAt) throws SQLException {
        String salt = PasswordUtil.generateSalt();
        User user = new User(FULL_NAME, EMAIL, PasswordUtil.hash(PASSWORD.toCharArray(), salt), salt);
        user.setCreatedAt(createdAt);
        user.setRestDay(REST_DAY);
        user.setWellbeingEnabled(true);
        userDAO.insert(user);
        AvailabilityDAO availabilityDAO = new AvailabilityDAO();
        for (int day = 1; day <= 7; day++) {
            availabilityDAO.insert(new Availability(user.getId(), day, day == REST_DAY ? 0 : FREE_MINUTES));
        }
        new UserSettingsDAO().insert(new UserSettings(user.getId()));
        return user;
    }

    private static List<Task> createTasks(int userId, LocalDate today) throws SQLException {
        TaskDAO taskDAO = new TaskDAO();
        List<Task> tasks = new ArrayList<Task>();
        for (Object[] row : TASKS) {
            Task t = new Task();
            t.setUserId(userId);
            t.setTitle((String) row[0]);
            t.setTaskType((TaskType) row[1]);
            t.setDifficulty((Difficulty) row[2]);
            t.setPriority((Priority) row[3]);
            t.setCourseName((String) row[4]);
            t.setEstimatedMinutes((Integer) row[5]);
            LocalDateTime deadline = today.plusDays((Integer) row[6]).atTime(23, 0);
            t.setDeadline(deadline);
            t.setStatus((TaskStatus) row[7]);
            if (row[8] != null) {
                t.setCompletedAt(today.plusDays((Integer) row[8]).atTime(21, 30));
            }
            t.setActualMinutes((Integer) row[9]);
            LocalDate created = deadline.toLocalDate().minusDays(10);
            LocalDate earliest = today.minusDays(DAYS);
            t.setCreatedAt((created.isBefore(earliest) ? earliest : created).atTime(10, 0));
            t.setDescription("Course work for " + t.getCourseName() + ".");
            taskDAO.insert(t);
            tasks.add(t);
        }
        return tasks;
    }

    /**
     * One evening study session per day (none on the Friday rest day) on the open task with the
     * nearest deadline: 90 min with 2 breaks in week 1, 130 min with 1 break in week 2 and
     * 160 min without a break in week 3. Each session is a study_logs row plus a DONE plan block;
     * two blocks of the tasks that were missed later are SKIPPED.
     */
    private static List<PlanBlock> createStudyHistory(int userId, List<Task> tasks, LocalDate start, LocalDate today)
            throws SQLException {
        StudyLogDAO logDAO = new StudyLogDAO();
        PlanBlockDAO blockDAO = new PlanBlockDAO();
        TaskDAO taskDAO = new TaskDAO();
        List<PlanBlock> done = new ArrayList<PlanBlock>();
        for (int i = 0; i < DAYS - 1; i++) {
            LocalDate day = start.plusDays(i);
            if (day.getDayOfWeek().getValue() == REST_DAY) {
                continue;
            }
            Task task = nearestOpenTask(tasks, day);
            if (task == null) {
                continue;
            }
            int week = i / 7;
            int minutes = week == 0 ? 90 : week == 1 ? 130 : 160;
            int breaks = week == 0 ? 2 : week == 1 ? 1 : 0;
            if (task.getStatus() == TaskStatus.MISSED) {
                // planned, but the student skipped it (that task is missed later)
                PlanBlock skipped = new PlanBlock(userId, task.getId(), day, 60);
                skipped.setStatus(PlanStatus.SKIPPED);
                blockDAO.insert(skipped);
                continue;
            }
            LocalDateTime startTime = day.atTime(19, 0);
            StudyLog log = new StudyLog();
            log.setUserId(userId);
            log.setTaskId(task.getId());
            log.setStartTime(startTime);
            log.setEndTime(startTime.plusMinutes(minutes + breaks * 5));
            log.setDurationMinutes(minutes);
            log.setBreaksTaken(breaks);
            logDAO.insert(log);
            PlanBlock block = new PlanBlock(userId, task.getId(), day, Math.min(minutes, 120));
            block.setStatus(PlanStatus.DONE);
            blockDAO.insert(block);
            done.add(block);
            if (task.getStatus() == TaskStatus.PENDING) {
                task.setStatus(TaskStatus.IN_PROGRESS);
                taskDAO.update(task);
            }
        }
        return done;
    }

    private static void createCheckins(int userId, LocalDate start) throws SQLException {
        CheckinDAO checkinDAO = new CheckinDAO();
        for (int i = 0; i < DAYS; i++) {
            Checkin c = new Checkin();
            c.setUserId(userId);
            c.setCheckinDate(start.plusDays(i));
            c.setSleepHours(SLEEP[i]);
            c.setStressLevel(STRESS[i]);
            c.setEnergyLevel(ENERGY[i]);
            c.setMoodLevel(MOOD[i]);
            c.setNote(NOTES[i]);
            checkinDAO.insert(c);
        }
    }

    /** Snapshots for the 20 days before today, from the data as it was on each day. */
    private static void createPastSnapshots(int userId, List<Task> tasks, List<PlanBlock> doneBlocks,
            LocalDate start, LocalDate today) throws SQLException {
        Map<Integer, Integer> free = new HashMap<Integer, Integer>();
        for (int day = 1; day <= 7; day++) {
            free.put(day, day == REST_DAY ? 0 : FREE_MINUTES);
        }
        WorkloadCalculator workload = new WorkloadCalculator(free, REST_DAY);
        BurnoutRiskCalculator burnout = new BurnoutRiskCalculator();
        List<Checkin> checkins = new CheckinDAO().findAllByUser(userId);      // newest first
        List<StudyLog> logs = new StudyLogDAO().findAllByUser(userId);
        RiskSnapshotDAO snapshotDAO = new RiskSnapshotDAO();
        Map<LocalDate, Integer> loads = new HashMap<LocalDate, Integer>();

        for (LocalDate day = start; day.isBefore(today); day = day.plusDays(1)) {
            List<Task> asOf = new ArrayList<Task>();
            int missed = 0;
            for (Task t : tasks) {
                Task copy = copyAsOf(t, day);
                asOf.add(copy);
                if (copy.getStatus() == TaskStatus.MISSED
                        && !copy.getDeadline().toLocalDate().isBefore(day.minusDays(7))) {
                    missed++;
                }
            }
            Map<Integer, Integer> doneMinutes = new HashMap<Integer, Integer>();
            for (PlanBlock b : doneBlocks) {
                if (b.getPlanDate().isBefore(day)) {
                    Integer old = doneMinutes.get(b.getTaskId());
                    doneMinutes.put(b.getTaskId(), (old == null ? 0 : old) + b.getPlannedMinutes());
                }
            }
            int load = workload.studyLoadIndex(asOf, doneMinutes, day);
            loads.put(day, load);

            List<Checkin> last3 = new ArrayList<Checkin>();
            for (Checkin c : checkins) {
                if (!c.getCheckinDate().isAfter(day) && !c.getCheckinDate().isBefore(day.minusDays(6))
                        && last3.size() < 3) {
                    last3.add(c);
                }
            }
            List<StudyLog> recentLogs = new ArrayList<StudyLog>();
            for (StudyLog l : logs) {
                LocalDate d = l.getStartTime().toLocalDate();
                if (!d.isAfter(day) && !d.isBefore(day.minusDays(2))) {
                    recentLogs.add(l);
                }
            }
            List<Integer> previous = new ArrayList<Integer>();
            for (int back = 3; back >= 1; back--) {
                Integer l = loads.get(day.minusDays(back));
                if (l != null) {
                    previous.add(l);
                }
            }
            RiskAssessment risk = burnout.calculate(true, last3, recentLogs, missed, load, previous);

            RiskSnapshot s = new RiskSnapshot();
            s.setUserId(userId);
            s.setSnapshotDate(day);
            s.setWorkloadIndex(load);
            s.setBurnoutScore(risk.getScore());
            s.setRiskLevel(risk.getLevel());
            StringBuilder reasons = new StringBuilder();
            for (RiskReason r : risk.getReasons()) {
                if (reasons.length() > 0) {
                    reasons.append('\n');
                }
                reasons.append('+').append(r.getPoints()).append(' ').append(r.getText());
            }
            s.setReasons(reasons.toString());
            snapshotDAO.saveForDay(s);
        }
    }

    /** The task as it was on 'day': not yet done, not yet missed. */
    private static Task copyAsOf(Task t, LocalDate day) {
        Task c = new Task();
        c.setId(t.getId());
        c.setUserId(t.getUserId());
        c.setTitle(t.getTitle());
        c.setTaskType(t.getTaskType());
        c.setDifficulty(t.getDifficulty());
        c.setPriority(t.getPriority());
        c.setEstimatedMinutes(t.getEstimatedMinutes());
        c.setDeadline(t.getDeadline());
        if (t.getCompletedAt() != null && t.getCompletedAt().toLocalDate().isBefore(day)) {
            c.setStatus(TaskStatus.DONE);
        } else if (t.getStatus() == TaskStatus.MISSED && t.getDeadline().toLocalDate().isBefore(day)) {
            c.setStatus(TaskStatus.MISSED);
        } else {
            c.setStatus(TaskStatus.PENDING);
        }
        return c;
    }

    private static Task nearestOpenTask(List<Task> tasks, LocalDate day) {
        Task best = null;
        for (Task t : tasks) {
            boolean doneBefore = t.getCompletedAt() != null && t.getCompletedAt().toLocalDate().isBefore(day);
            boolean due = !t.getDeadline().toLocalDate().isBefore(day);
            if (!doneBefore && due && !t.getCreatedAt().toLocalDate().isAfter(day)
                    && (best == null || t.getDeadline().isBefore(best.getDeadline()))) {
                best = t;
            }
        }
        return best;
    }

    /** Older notifications (read) and a few current ones (unread). */
    private static void createNotifications(int userId, LocalDate today) throws SQLException {
        NotificationDAO dao = new NotificationDAO();
        Object[][] rows = {
            {-20, NotificationType.INFO, "Welcome to StudyPulse! Add your tasks and generate a study plan."},
            {-9, NotificationType.WORKLOAD, "High workload detected for tomorrow."},
            {-4, NotificationType.DEADLINE, NotificationService.missedMessage("Technical Writing Essay")},
            {-2, NotificationType.DEADLINE, NotificationService.missedMessage("Digital Logic Lab 4")},
            {-1, NotificationType.BREAK, NotificationService.TAKE_A_BREAK},
        };
        for (Object[] row : rows) {
            Notification n = new Notification(userId, (NotificationType) row[1], (String) row[2]);
            n.setCreatedAt(today.plusDays((Integer) row[0]).atTime(18, 30));
            n.setRead(true);
            dao.insert(n);
        }
    }
}
