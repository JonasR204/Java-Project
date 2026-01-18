import java.util.*;

public class AnalyticsService {

    // ===== Ergebnisobjekt =====
    public static class SnapshotResult {
        public final int vehiclesEver;                 // 👈 Historie: alle Autos
        public final double globalAvgSpeed;
        public final Map<String, EdgeStats> perEdge;
        public final List<String> congestedEdges;

        public SnapshotResult(int vehiclesEver,
                              double globalAvgSpeed,
                              Map<String, EdgeStats> perEdge,
                              List<String> congestedEdges) {
            this.vehiclesEver = vehiclesEver;
            this.globalAvgSpeed = globalAvgSpeed;
            this.perEdge = perEdge;
            this.congestedEdges = congestedEdges;
        }
    }

    // ===== Stats pro Edge =====
    public static class EdgeStats {
        public int vehicleCount = 0;
        public double speedSum = 0;

        public double avgSpeed() {
            return vehicleCount == 0 ? 0 : speedSum / vehicleCount;
        }

        // 👈 einfache, HUET-sichere Stau-Definition
        public boolean isCongested() {
            return avgSpeed() < 5.0 && vehicleCount > 10;
        }
    }

    // ===== DIE WICHTIGE METHODE =====
    public SnapshotResult compute(List<DataPoint> snapshot) {

        // 👇 speichert JE Fahrzeug nur EINMAL (neuester Datensatz)
        Map<String, VehicleTelemetry> latestByVehicle = new HashMap<>();

        for (DataPoint p : snapshot) {
            if (!(p instanceof VehicleTelemetry v)) continue;

            String id = v.getVehicleId();
            VehicleTelemetry prev = latestByVehicle.get(id);

            if (prev == null || v.timestamp().isAfter(prev.timestamp())) {
                latestByVehicle.put(id, v);
            }
        }

        // 👈 DAS ist deine HISTORIE-ZAHL
        int vehiclesEver = latestByVehicle.size();

        // ===== Analytics =====
        Map<String, EdgeStats> perEdge = new HashMap<>();
        double globalSpeedSum = 0.0;

        for (VehicleTelemetry v : latestByVehicle.values()) {
            String edgeId = v.getEdgeId();
            double speed = v.getSpeed();

            if (edgeId == null || edgeId.isBlank()) continue;

            globalSpeedSum += speed;

            EdgeStats stats = perEdge.computeIfAbsent(edgeId, e -> new EdgeStats());
            stats.vehicleCount++;
            stats.speedSum += speed;
        }

        double globalAvgSpeed =
                vehiclesEver == 0 ? 0.0 : globalSpeedSum / vehiclesEver;

        // ===== Congestion =====
        List<String> congested = new ArrayList<>();
        for (Map.Entry<String, EdgeStats> e : perEdge.entrySet()) {
            if (e.getValue().isCongested()) {
                congested.add(e.getKey());
            }
        }
        Collections.sort(congested);

        return new SnapshotResult(
                vehiclesEver,
                globalAvgSpeed,
                perEdge,
                congested
        );
    }
}



