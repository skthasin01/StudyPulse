package studypulse.model;

import java.time.LocalDateTime;

/** One recorded study session (table study_logs). */
public class StudyLog {

    private int id;
    private int userId;
    private Integer taskId;           // null = general study, not linked to a task
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int durationMinutes;
    private int breaksTaken;

    public StudyLog() {
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

    public Integer getTaskId() {
        return taskId;
    }

    public void setTaskId(Integer taskId) {
        this.taskId = taskId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public int getBreaksTaken() {
        return breaksTaken;
    }

    public void setBreaksTaken(int breaksTaken) {
        this.breaksTaken = breaksTaken;
    }
}
