package studypulse.model;

/** Task priority (tasks.priority). */
public enum Priority {

    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High");

    private final String label;

    Priority(String label) {
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
