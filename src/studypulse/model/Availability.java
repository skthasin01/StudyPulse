package studypulse.model;

/** Free study minutes of a user on one weekday (table availability). */
public class Availability {

    private int id;
    private int userId;
    private int dayOfWeek;            // 1 = Monday ... 7 = Sunday
    private int freeMinutes;

    public Availability() {
    }

    public Availability(int userId, int dayOfWeek, int freeMinutes) {
        this.userId = userId;
        this.dayOfWeek = dayOfWeek;
        this.freeMinutes = freeMinutes;
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

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(int dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public int getFreeMinutes() {
        return freeMinutes;
    }

    public void setFreeMinutes(int freeMinutes) {
        this.freeMinutes = freeMinutes;
    }
}
