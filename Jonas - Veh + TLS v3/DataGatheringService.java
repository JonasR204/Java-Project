import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class DataGatheringService {

    private final BlockingQueue<DataPoint> queue = new LinkedBlockingQueue<>();
    private final InMemoryTelemetryStore store = new InMemoryTelemetryStore();
    private final DataValidator validator = new DataValidator();
    private boolean running = true;

    public DataGatheringService() {
        Thread worker = new Thread(this::run);
        worker.setDaemon(true);
        worker.start();
    }

    private void run() {
        while (running) {
            try {
                DataPoint p = queue.take();
                if (validator.isValid(p)) {
                    store.add(p);
                }
            } catch (InterruptedException ignored) {}
        }
    }

    public void submit(DataPoint p) {
        if (running) queue.offer(p);
    }

    public InMemoryTelemetryStore getStore() {
        return store;
    }

    public Instant now() {
        return Instant.now();
    }

    public void stop() {
        running = false;
    }
}
