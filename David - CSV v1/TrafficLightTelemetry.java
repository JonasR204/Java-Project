import java.time.Instant;

public class TrafficLightTelemetry implements DataPoint {

    private final Instant timestamp;
    private final String tlsId;
    private final int phase;
    private final String state;

    public TrafficLightTelemetry(Instant timestamp,
                                 String tlsId,
                                 int phase,
                                 String state) {
        this.timestamp = timestamp;
        this.tlsId = tlsId;
        this.phase = phase;
        this.state = state;
    }

    @Override
    public Instant timestamp() {
        return timestamp;
    }

    public String getTlsId() { return tlsId; }
    public int getPhase() { return phase; }
    public String getState() { return state; }
}
