package studypulse.engine;

import java.util.ArrayList;
import java.util.List;
import studypulse.model.PlanBlock;

/** Output of PlanGenerator: new blocks (not saved yet) and tasks that did not fit. */
public class PlanResult {

    private final List<PlanBlock> blocks = new ArrayList<PlanBlock>();
    private final List<OverloadedTask> overloaded = new ArrayList<OverloadedTask>();
    private int plannedTaskCount;

    public List<PlanBlock> getBlocks() {
        return blocks;
    }

    public List<OverloadedTask> getOverloaded() {
        return overloaded;
    }

    /** Number of tasks that got at least one block. */
    public int getPlannedTaskCount() {
        return plannedTaskCount;
    }

    void setPlannedTaskCount(int plannedTaskCount) {
        this.plannedTaskCount = plannedTaskCount;
    }
}
