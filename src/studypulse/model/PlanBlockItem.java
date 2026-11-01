package studypulse.model;

/** A plan block together with the task information shown in the week grid. Not a table. */
public class PlanBlockItem {

    private final PlanBlock block;
    private final String taskTitle;
    private final Difficulty difficulty;

    public PlanBlockItem(PlanBlock block, String taskTitle, Difficulty difficulty) {
        this.block = block;
        this.taskTitle = taskTitle;
        this.difficulty = difficulty;
    }

    public PlanBlock getBlock() {
        return block;
    }

    public String getTaskTitle() {
        return taskTitle;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }
}
