package studypulse.engine;

import studypulse.model.PomodoroPhase;

/**
 * Order of Pomodoro phases: Focus → Short break → Focus → ... and a Long break after
 * every 4th focus round. Pure logic (the frame does the timing), unit-tested.
 */
public class PomodoroCycle {

    public static final int ROUNDS_BEFORE_LONG_BREAK = 4;

    private PomodoroPhase phase = PomodoroPhase.FOCUS;
    private int completedFocusRounds;

    public PomodoroPhase getPhase() {
        return phase;
    }

    /** Focus rounds finished (or skipped) since the start. */
    public int getCompletedFocusRounds() {
        return completedFocusRounds;
    }

    /** Round 1-4 of the current set: the running focus round, or the one the break follows. */
    public int getRound() {
        int done = completedFocusRounds % ROUNDS_BEFORE_LONG_BREAK;
        if (phase == PomodoroPhase.FOCUS) {
            return done + 1;
        }
        return done == 0 ? ROUNDS_BEFORE_LONG_BREAK : done;
    }

    /** Ends a focus round; returns the break that follows. */
    public PomodoroPhase completeFocus() {
        completedFocusRounds++;
        phase = completedFocusRounds % ROUNDS_BEFORE_LONG_BREAK == 0
                ? PomodoroPhase.LONG_BREAK : PomodoroPhase.SHORT_BREAK;
        return phase;
    }

    /** Ends a break (finished or skipped); the next phase is always Focus. */
    public PomodoroPhase completeBreak() {
        phase = PomodoroPhase.FOCUS;
        return phase;
    }

    /** Back to round 1, Focus. */
    public void reset() {
        phase = PomodoroPhase.FOCUS;
        completedFocusRounds = 0;
    }
}
