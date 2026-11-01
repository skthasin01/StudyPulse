package studypulse.model;

import java.time.LocalDate;

/** Minutes of one task planned on one day (table plan_blocks). */
public class PlanBlock {

    private int id;
    private int userId;
    private int taskId;
    private LocalDate planDate;
    private int plannedMinutes;
    private PlanStatus status = PlanStatus.PLANNED;

    public PlanBlock() {
    }

    public PlanBlock(int userId, int taskId, LocalDate planDate, int plannedMinutes) {
        this.userId = userId;
        this.taskId = taskId;
        this.planDate = planDate;
        this.plannedMinutes = plannedMinutes;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getTaskId() {
        return taskId;
    }

    public void setTaskId(int taskId) {
        this.taskId = taskId;
    }

    public LocalDate getPlanDate() {
        return planDate;
    }

    public void setPlanDate(LocalDate planDate) {
        this.planDate = planDate;
    }

    public int getPlannedMinutes() {
        return plannedMinutes;
    }

    public void setPlannedMinutes(int plannedMinutes) {
        this.plannedMinutes = plannedMinutes;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public void setStatus(PlanStatus status) {
        this.status = status;
    }
}
