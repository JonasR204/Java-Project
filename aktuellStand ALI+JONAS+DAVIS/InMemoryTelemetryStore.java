import java.util.ArrayList;
import java.util.List;

public class InMemoryTelemetryStore {

    private final List<DataPoint> data = new ArrayList<>();

    public synchronized void add(DataPoint p) {
        data.add(p);
    }

    public synchronized List<DataPoint> snapshot() {
        return new ArrayList<>(data);
    }
}
