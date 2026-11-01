package studypulse.engine;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import studypulse.model.PomodoroPhase;

/** Order of Pomodoro phases and round numbers. */
public class PomodoroCycleTest {

    @Test
    public void startsWithFocusRoundOne() {
        PomodoroCycle cycle = new PomodoroCycle();
        assertEquals(PomodoroPhase.FOCUS, cycle.getPhase());
        assertEquals(1, cycle.getRound());
    }

    @Test
    public void longBreakAfterEveryFourthFocusRound() {
        PomodoroCycle cycle = new PomodoroCycle();
        for (int round = 1; round <= 3; round++) {
            assertEquals(round, cycle.getRound());
            assertEquals(PomodoroPhase.SHORT_BREAK, cycle.completeFocus());
            assertEquals("break keeps the round number", round, cycle.getRound());
            assertEquals(PomodoroPhase.FOCUS, cycle.completeBreak());
        }
        assertEquals(4, cycle.getRound());
        assertEquals(PomodoroPhase.LONG_BREAK, cycle.completeFocus());
        assertEquals(4, cycle.getRound());
        cycle.completeBreak();
        assertEquals("a new set starts", 1, cycle.getRound());
        assertEquals(PomodoroPhase.SHORT_BREAK, cycle.completeFocus());
        assertEquals(5, cycle.getCompletedFocusRounds());
    }

    @Test
    public void resetGoesBackToRoundOne() {
        PomodoroCycle cycle = new PomodoroCycle();
        cycle.completeFocus();
        cycle.reset();
        assertEquals(PomodoroPhase.FOCUS, cycle.getPhase());
        assertEquals(1, cycle.getRound());
        assertEquals(0, cycle.getCompletedFocusRounds());
    }
}
