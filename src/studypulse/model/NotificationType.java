package studypulse.model;

/** Kind of notification (notifications.type). */
public enum NotificationType {

    WORKLOAD("Workload"),
    BURNOUT("Burnout"),
    BREAK("Break"),
    DEADLINE("Deadline"),
    INFO("Info");

    private final String label;

    NotificationType(String label) {
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
