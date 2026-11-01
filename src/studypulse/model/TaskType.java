package studypulse.model;

/** Kind of study task (tasks.task_type). */
public enum TaskType {

    ASSIGNMENT("Assignment"),
    EXAM("Exam"),
    PROJECT("Project"),
    PREPARATION("Preparation");

    private final String label;

    TaskType(String label) {
        this.label = label;
    }

    /** Text shown to the user. Use name() when saving to the database. */
    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
