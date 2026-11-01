package studypulse.model;

/** How close a task is to its deadline (used for row colours). Not stored in the database. */
public enum DeadlineState {

    NORMAL,
    DUE_SOON,     // deadline within 2 days, not done
    OVERDUE,      // deadline passed, not done
    DONE
}
