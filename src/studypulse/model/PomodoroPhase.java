package studypulse.model;

/** Phase of the Pomodoro timer. Not stored in the database. */
public enum PomodoroPhase {

    FOCUS("Focus"),
    SHORT_BREAK("Short break"),
    LONG_BREAK("Long break");

    private final String label;

    PomodoroPhase(String label) {
        this.label = label;
    }

    /** Text shown to the user. */
    public String getLabel() {
        return label;
    }

    public boolean isBreak() {
        return this != FOCUS;
    }

    @Override
    public String toString() {
        return label;
    }
}
