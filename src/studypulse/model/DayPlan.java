package studypulse.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** One day of the study plan week grid. Not a table. */
public class DayPlan {

    private final LocalDate date;
    private final int freeMinutes;
    private final boolean restDay;
    private final List<PlanBlockItem> items = new ArrayList<PlanBlockItem>();

    public DayPlan(LocalDate date, int freeMinutes, boolean restDay) {
        this.date = date;
        this.freeMinutes = freeMinutes;
        this.restDay = restDay;
    }

    public LocalDate getDate() {
        return date;
    }

    /** Free minutes of this weekday (0 on the rest day). */
    public int getFreeMinutes() {
        return freeMinutes;
    }

    public boolean isRestDay() {
        return restDay;
    }

    public List<PlanBlockItem> getItems() {
        return items;
    }

    /** Minutes of PLANNED and DONE blocks (skipped blocks do not count). */
    public int getPlannedMinutes() {
        int sum = 0;
        for (PlanBlockItem item : items) {
            if (item.getBlock().getStatus() != PlanStatus.SKIPPED) {
                sum += item.getBlock().getPlannedMinutes();
            }
        }
        return sum;
    }
}
