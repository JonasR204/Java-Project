public class DataValidator {

    public boolean isValid(DataPoint p) {
        if (p == null || p.timestamp() == null) return false;

        if (p instanceof VehicleTelemetry v) {
            return v.getVehicleId() != null && v.getSpeed() >= 0;
        }

        if (p instanceof TrafficLightTelemetry t) {
            return t.getTlsId() != null;
        }

        return true;
    }
}
