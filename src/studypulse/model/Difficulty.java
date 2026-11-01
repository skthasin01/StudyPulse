package studypulse.model;

/** How hard a task is (tasks.difficulty). */
public enum Difficulty {

    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard");

    private final String label;

    Difficulty(String label) {
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

    /** Enum for a display label ("Hard" -> HARD); MEDIUM if unknown. */
    public static Difficulty fromLabel(Object label) {
        for (Difficulty d : values()) {
            if (d.label.equals(label)) {
                return d;
            }
        }
        return MEDIUM;
    }
}
