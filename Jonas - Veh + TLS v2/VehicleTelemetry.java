import java.time.Instant;

public class VehicleTelemetry implements DataPoint {

    private final Instant timestamp;
    private final String vehicleId;
    private final double x;
    private final double y;
    private final double speed;

    public VehicleTelemetry(Instant timestamp, String vehicleId,
                            double x, double y, double speed) {
        this.timestamp = timestamp;
        this.vehicleId = vehicleId;
        this.x = x;
        this.y = y;
        this.speed = speed;
    }

    @Override
    public Instant timestamp() {
        return timestamp;
    }

    public String getVehicleId() { return vehicleId; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getSpeed() { return speed; }
}
