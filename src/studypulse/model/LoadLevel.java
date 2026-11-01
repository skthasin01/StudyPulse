package studypulse.model;

/** Band of the Study Load Index: 0-49 Light, 50-79 Balanced, 80-100 Heavy, above 100 Overloaded. */
public enum LoadLevel {

    LIGHT("Light"),
    BALANCED("Balanced"),
    HEAVY("Heavy"),
    OVERLOADED("Overloaded");

    private final String label;

    LoadLevel(String label) {
        this.label = label;
    }

    /** Text shown to the user. */
    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
