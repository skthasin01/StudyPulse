package studypulse.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import studypulse.model.TaskStatus;

/** Numbers for the four Analytics charts and the insight text (result of AnalyticsService). */
public class AnalyticsData {

    private final int days;
    private final LocalDate from;
    private final LocalDate to;
    /** Every day of the range, 0 when nothing was studied. */
    private final Map<LocalDate, Double> hoursByDay = new LinkedHashMap<LocalDate, Double>();
    /** Only days with a risk snapshot / check-in. */
    private final Map<LocalDate, Integer> loadByDay = new LinkedHashMap<LocalDate, Integer>();
    private final Map<LocalDate, Integer> riskByDay = new LinkedHashMap<LocalDate, Integer>();
    private final Map<LocalDate, Double> sleepByDay = new LinkedHashMap<LocalDate, Double>();
    private final Map<LocalDate, Integer> stressByDay = new LinkedHashMap<LocalDate, Integer>();
    private final Map<TaskStatus, Integer> statusCounts = new EnumMap<TaskStatus, Integer>(TaskStatus.class);
    private final List<String> insights = new ArrayList<String>();
    private double totalHours;
    private double previousHours;

    public AnalyticsData(int days, LocalDate from, LocalDate to) {
        this.days = days;
        this.from = from;
        this.to = to;
    }

    public int getDays() {
        return days;
    }

    public LocalDate getFrom() {
        return from;
    }

    public LocalDate getTo() {
        return to;
    }

    public Map<LocalDate, Double> getHoursByDay() {
        return hoursByDay;
    }

    public Map<LocalDate, Integer> getLoadByDay() {
        return loadByDay;
    }

    public Map<LocalDate, Integer> getRiskByDay() {
        return riskByDay;
    }

    public Map<LocalDate, Double> getSleepByDay() {
        return sleepByDay;
    }

    public Map<LocalDate, Integer> getStressByDay() {
        return stressByDay;
    }

    /** Tasks with a deadline in or after the range start, by status. */
    public Map<TaskStatus, Integer> getStatusCounts() {
        return statusCounts;
    }

    /** 1-2 short sentences for lblInsight. */
    public List<String> getInsights() {
        return insights;
    }

    public double getTotalHours() {
        return totalHours;
    }

    void setTotalHours(double totalHours) {
        this.totalHours = totalHours;
    }

    /** Study hours in the same number of days before the range. */
    public double getPreviousHours() {
        return previousHours;
    }

    void setPreviousHours(double previousHours) {
        this.previousHours = previousHours;
    }

    public boolean hasStudyHours() {
        return totalHours > 0;
    }

    /** A trend needs at least 2 days with a risk snapshot. */
    public boolean hasRiskData() {
        return riskByDay.size() >= 2;
    }

    public boolean hasTasks() {
        for (int count : statusCounts.values()) {
            if (count > 0) {
                return true;
            }
        }
        return false;
    }

    public boolean hasCheckins() {
        return !sleepByDay.isEmpty();
    }
}
