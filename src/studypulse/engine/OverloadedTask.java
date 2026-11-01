package studypulse.engine;

import studypulse.model.Task;

/** A task whose remaining minutes did not all fit before its deadline. */
public class OverloadedTask {

    private final Task task;
    private final int missingMinutes;

    public OverloadedTask(Task task, int missingMinutes) {
        this.task = task;
        this.missingMinutes = missingMinutes;
    }

    public Task getTask() {
        return task;
    }

    /** Minutes that could not be planned. */
    public int getMissingMinutes() {
        return missingMinutes;
    }
}
