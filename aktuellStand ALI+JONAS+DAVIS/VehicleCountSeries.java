import java.util.*;

public class VehicleCountSeries {
    private final Deque<Integer> values = new ArrayDeque<>();
    private final Deque<Long> timesMs = new ArrayDeque<>();
    private final int maxPoints;

    public VehicleCountSeries(int maxPoints) {
        this.maxPoints = maxPoints;
    }

    public void add(long timeMs, int vehiclesNow) {
        timesMs.addLast(timeMs);
        values.addLast(vehiclesNow);
        while (values.size() > maxPoints) {
            values.removeFirst();
            timesMs.removeFirst();
        }
    }

    public List<Integer> values() { return new ArrayList<>(values); }
    public List<Long> timesMs() { return new ArrayList<>(timesMs); }
}

