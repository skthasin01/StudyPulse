package studypulse.model;

/** Status of a planned study block (plan_blocks.status). */
public enum PlanStatus {

    PLANNED("Planned"),
    DONE("Done"),
    SKIPPED("Skipped");

    private final String label;

    PlanStatus(String label) {
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
