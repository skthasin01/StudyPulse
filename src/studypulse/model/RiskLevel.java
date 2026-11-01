package studypulse.model;

/** Burnout risk band (risk_snapshots.risk_level). */
public enum RiskLevel {

    LOW("Low"),
    MODERATE("Moderate"),
    HIGH("High"),
    SEVERE("Severe");

    private final String label;

    RiskLevel(String label) {
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

    /** Enum for a display label ("High" -> HIGH); LOW if unknown. */
    public static RiskLevel fromLabel(Object label) {
        for (RiskLevel r : values()) {
            if (r.label.equals(label)) {
                return r;
            }
        }
        return LOW;
    }
}
