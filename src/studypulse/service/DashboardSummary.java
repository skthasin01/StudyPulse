package studypulse.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import studypulse.model.PlanBlockItem;
import studypulse.model.Task;

/** Everything the home dashboard shows (result of AnalyticsService.getDashboard). */
public class DashboardSummary {

    private RiskReport risk;
    private int dueThisWeek;
    private Task nextTask;
    private double hoursThisWeek;
    private double hoursLastWeek;
    private int unreadNotifications;
    private final List<PlanBlockItem> todayBlocks = new ArrayList<PlanBlockItem>();
    private final List<Task> upcoming = new ArrayList<Task>();
    private final Map<LocalDate, Integer> riskLast7Days = new LinkedHashMap<LocalDate, Integer>();

    /** Today's Study Load Index and burnout risk. */
    public RiskReport getRisk() {
        return risk;
    }

    void setRisk(RiskReport risk) {
        this.risk = risk;
    }

    /** Open tasks due in the next 7 days. */
    public int getDueThisWeek() {
        return dueThisWeek;
    }

    void setDueThisWeek(int dueThisWeek) {
        this.dueThisWeek = dueThisWeek;
    }

    /** The open task with the nearest deadline, or null. */
    public Task getNextTask() {
        return nextTask;
    }

    void setNextTask(Task nextTask) {
        this.nextTask = nextTask;
    }

    /** Study hours from Monday of this week until now. */
    public double getHoursThisWeek() {
        return hoursThisWeek;
    }

    void setHoursThisWeek(double hoursThisWeek) {
        this.hoursThisWeek = hoursThisWeek;
    }

    /** Study hours in the same part of last week (Monday until the same time 7 days ago). */
    public double getHoursLastWeek() {
        return hoursLastWeek;
    }

    void setHoursLastWeek(double hoursLastWeek) {
        this.hoursLastWeek = hoursLastWeek;
    }

    public int getUnreadNotifications() {
        return unreadNotifications;
    }

    void setUnreadNotifications(int unreadNotifications) {
        this.unreadNotifications = unreadNotifications;
    }

    /** Today's plan blocks with task titles. */
    public List<PlanBlockItem> getTodayBlocks() {
        return todayBlocks;
    }

    /** Next 5 open tasks by deadline. */
    public List<Task> getUpcoming() {
        return upcoming;
    }

    /** Burnout risk score per day (oldest first), only days with a snapshot. */
    public Map<LocalDate, Integer> getRiskLast7Days() {
        return riskLast7Days;
    }
}
