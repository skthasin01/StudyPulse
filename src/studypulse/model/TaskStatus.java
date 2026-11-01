package studypulse.model;

/** Task progress (tasks.status). */
public enum TaskStatus {

    PENDING("Pending"),
    IN_PROGRESS("In progress"),
    DONE("Done"),
    MISSED("Missed");

    private final String label;

    TaskStatus(String label) {
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
