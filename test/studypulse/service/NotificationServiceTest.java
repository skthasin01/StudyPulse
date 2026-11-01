package studypulse.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import studypulse.model.RiskSnapshot;

/** Notification texts and the "risk is increasing" rule. */
public class NotificationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

    private static RiskSnapshot snap(int daysAgo, double score) {
        RiskSnapshot s = new RiskSnapshot();
        s.setSnapshotDate(TODAY.minusDays(daysAgo));
        s.setBurnoutScore(score);
        return s;
    }

    @Test
    public void deadlineMessageUsesSingularAndPlural() {
        assertEquals("You have 1 deadline in the next 48 hours.", NotificationService.deadlineMessage(1));
        assertEquals("You have 3 deadlines in the next 48 hours.", NotificationService.deadlineMessage(3));
        assertEquals("SE Project was missed.", NotificationService.missedMessage("SE Project"));
    }

    @Test
    public void riskRisingThreeDaysInARow() {
        List<RiskSnapshot> rising = Arrays.asList(snap(0, 62), snap(1, 50), snap(2, 41), snap(3, 30));
        assertTrue(NotificationService.risingNewestFirst(rising));
    }

    @Test
    public void flatOrFallingOrMissingDayIsNotRising() {
        assertFalse(NotificationService.risingNewestFirst(
                Arrays.asList(snap(0, 62), snap(1, 62), snap(2, 41), snap(3, 30))));
        assertFalse(NotificationService.risingNewestFirst(
                Arrays.asList(snap(0, 40), snap(1, 50), snap(2, 41), snap(3, 30))));
        // a gap between days does not count as "in a row"
        assertFalse(NotificationService.risingNewestFirst(
                Arrays.asList(snap(0, 62), snap(1, 50), snap(3, 41), snap(4, 30))));
    }
}
