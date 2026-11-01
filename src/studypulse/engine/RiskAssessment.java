package studypulse.engine;

import java.util.ArrayList;
import java.util.List;
import studypulse.model.RiskLevel;

/** Output of BurnoutRiskCalculator: score 0-100, level and the reasons behind every point. */
public class RiskAssessment {

    private final List<RiskReason> reasons = new ArrayList<RiskReason>();
    private final boolean wellbeingEnabled;
    private int score;
    private RiskLevel level = RiskLevel.LOW;

    public RiskAssessment(boolean wellbeingEnabled) {
        this.wellbeingEnabled = wellbeingEnabled;
    }

    public List<RiskReason> getReasons() {
        return reasons;
    }

    /** Sum of the reason points before capping / scaling. */
    public int getRawPoints() {
        int sum = 0;
        for (RiskReason r : reasons) {
            sum += r.getPoints();
        }
        return sum;
    }

    public int getScore() {
        return score;
    }

    void setScore(int score) {
        this.score = score;
    }

    public RiskLevel getLevel() {
        return level;
    }

    void setLevel(RiskLevel level) {
        this.level = level;
    }

    /** False: only workload rules were used and the points were scaled (× 100 ÷ 40). */
    public boolean isWellbeingEnabled() {
        return wellbeingEnabled;
    }

    public boolean hasReason(RiskReason.Kind kind) {
        for (RiskReason r : reasons) {
            if (r.getKind() == kind) {
                return true;
            }
        }
        return false;
    }

    /** "Total: 60 / 100", with the scaling or the cap explained when used. */
    public String getTotalLine() {
        int raw = getRawPoints();
        if (!wellbeingEnabled) {
            return "Total: " + raw + " of " + BurnoutRiskCalculator.WORKLOAD_ONLY_MAX
                    + " points × 100 ÷ " + BurnoutRiskCalculator.WORKLOAD_ONLY_MAX + " = " + score + " / 100";
        }
        if (raw > score) {
            return "Total: " + raw + " points, capped at " + score + " / 100";
        }
        return "Total: " + score + " / 100";
    }
}
