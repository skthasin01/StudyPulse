package studypulse.service;

import java.time.LocalDate;
import java.util.List;
import studypulse.engine.RiskAssessment;
import studypulse.model.LoadLevel;

/** Today's Study Load Index, burnout risk and recommendations (result of RiskService). */
public class RiskReport {

    private final LocalDate date;
    private final int loadIndex;
    private final LoadLevel loadLevel;
    private final RiskAssessment risk;
    private final List<String> recommendations;
    private final int snapshotId;

    public RiskReport(LocalDate date, int loadIndex, LoadLevel loadLevel, RiskAssessment risk,
            List<String> recommendations, int snapshotId) {
        this.date = date;
        this.loadIndex = loadIndex;
        this.loadLevel = loadLevel;
        this.risk = risk;
        this.recommendations = recommendations;
        this.snapshotId = snapshotId;
    }

    public LocalDate getDate() {
        return date;
    }

    public int getLoadIndex() {
        return loadIndex;
    }

    public LoadLevel getLoadLevel() {
        return loadLevel;
    }

    public RiskAssessment getRisk() {
        return risk;
    }

    /** The last item is always the disclaimer. */
    public List<String> getRecommendations() {
        return recommendations;
    }

    /** risk_snapshots.id of today's row. */
    public int getSnapshotId() {
        return snapshotId;
    }
}
