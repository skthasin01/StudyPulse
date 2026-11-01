package studypulse.model;

/** Look of the app (user_settings.theme). */
public enum AppTheme {

    LIGHT("Light"),
    DARK("Dark");

    private final String label;

    AppTheme(String label) {
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
