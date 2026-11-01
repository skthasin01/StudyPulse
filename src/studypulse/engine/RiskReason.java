package studypulse.engine;

/** One rule that added points to the burnout risk score, as a plain sentence. */
public class RiskReason {

    /** Which rule fired (used by RecommendationEngine). */
    public enum Kind {
        SLEEP, STRESS, ENERGY, MOOD, LONG_SESSION, MISSED_TASKS, HIGH_LOAD, RISING_LOAD
    }

    private final Kind kind;
    private final String text;
    private final int points;

    public RiskReason(Kind kind, String text, int points) {
        this.kind = kind;
        this.text = text;
        this.points = points;
    }

    public Kind getKind() {
        return kind;
    }

    public String getText() {
        return text;
    }

    public int getPoints() {
        return points;
    }

    /** "Average sleep in last 3 days is 5.7 h (below 6 h)  +15". */
    @Override
    public String toString() {
        return text + "  +" + points;
    }
}
