import java.time.Instant;

public class VehicleTelemetry implements DataPoint {

    private final Instant timestamp;
    private final String vehicleId;
    private final double x;
    private final double y;
    private final double speed;


    // NEU ALI ALI ALI ALI ALI
    private final String edgeId; // z.B. "E12"
    private final String color;  // z.B. "red"


    public VehicleTelemetry(Instant timestamp, String vehicleId,
                            double x, double y, double speed,
                            String edgeId, String color) {

        this.timestamp = timestamp;
        this.vehicleId = vehicleId;
        this.x = x;
        this.y = y;
        this.speed = speed;
        this.edgeId = edgeId; //ALI
        this.color = color;   //ALI
    }

    @Override
    public Instant timestamp() {
        return timestamp;
    }

    public String getVehicleId() { return vehicleId; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getSpeed() { return speed; }

    // NEU ALI ALI ALI
    public String getEdgeId() { return edgeId; }
    public String getColor() { return color; }
}
