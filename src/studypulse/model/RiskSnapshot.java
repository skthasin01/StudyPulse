package studypulse.model;

import java.time.LocalDate;

/** Workload index and burnout score saved for one day (table risk_snapshots). */
public class RiskSnapshot {

    private int id;
    private int userId;
    private LocalDate snapshotDate;
    private double workloadIndex;
    private double burnoutScore;
    private RiskLevel riskLevel = RiskLevel.LOW;
    private String reasons;

    public RiskSnapshot() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public void setSnapshotDate(LocalDate snapshotDate) {
        this.snapshotDate = snapshotDate;
    }

    public double getWorkloadIndex() {
        return workloadIndex;
    }

    public void setWorkloadIndex(double workloadIndex) {
        this.workloadIndex = workloadIndex;
    }

    public double getBurnoutScore() {
        return burnoutScore;
    }

    public void setBurnoutScore(double burnoutScore) {
        this.burnoutScore = burnoutScore;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getReasons() {
        return reasons;
    }

    public void setReasons(String reasons) {
        this.reasons = reasons;
    }
}
