import it.polito.appeal.traci.SumoTraciConnection;
import de.tudresden.sumo.cmd.Vehicle;
import de.tudresden.sumo.cmd.Route;
import de.tudresden.sumo.cmd.Trafficlight;
import de.tudresden.sumo.cmd.Lane;
import de.tudresden.sumo.objects.SumoPosition2D;
import de.tudresden.sumo.objects.SumoGeometry;
import de.tudresden.sumo.objects.SumoColor;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.*;

public class SimulationController {

    private final SumoTraciConnection conn;
    private boolean running = false;
    private final DataGatheringService dg = new DataGatheringService();

    public SimulationController(String sumoGuiExe, String sumoCfg) throws IOException {
        conn = new SumoTraciConnection(sumoGuiExe, sumoCfg);
        conn.addOption("quit-on-end", "false");
        conn.addOption("start", "true");
        conn.addOption("delay", "50");
    }

    public void start() throws IOException {
        if (running) return;
        conn.runServer();
        running = true;
    }

    public void step() {
        ensureRunning();
        try {
            conn.do_timestep();
            // ---- DATA GATHERING (Fahrzeuge) ----
            for (String id : getVehicleIds()) {
                Point2D.Double pos = getVehiclePosition(id);
                double speed = getVehicleSpeed(id);

                dg.submit(new VehicleTelemetry(dg.now(), id, pos.x, pos.y, speed));
            }

// ---- DATA GATHERING (Ampeln) ----
            for (String tlsId : getTrafficLightIds()) {
                int phase = getTlsPhase(tlsId);
                String state = getRedYellowGreenState(tlsId);

                dg.submit(new TrafficLightTelemetry(dg.now(), tlsId, phase, state));
            }
        } catch (Exception e) {
            running = false;
        }
    }

    public boolean isRunning() {
        return running;
    }

    /*
    public void close() {
        try { conn.close(); } catch (Exception ignored) {}
        running = false;
    }
    */

    public void close() {
        try {
            String file = "export.csv";

            var snapshot = dg.getStore().snapshot();
            System.out.println("[EXPORT] datapoints = " + snapshot.size());
            System.out.println("[EXPORT] path = " + new java.io.File(file).getAbsolutePath());

            new CsvExporter().export(file, snapshot);

            System.out.println("[EXPORT] CSV export successful.");
        } catch (Exception e) {
            System.out.println("[EXPORT] CSV export failed!");
            e.printStackTrace(); // <-- WICHTIG: damit du den echten Fehler siehst
        }

        try { conn.close(); } catch (Exception ignored) {}
        running = false;
    }

    private void ensureRunning() {
        if (!running)
            throw new IllegalStateException("SUMO not started");
    }


    @SuppressWarnings("unchecked")
    public List<String> getVehicleIds() throws Exception {
        ensureRunning();
        return (List<String>) conn.do_job_get(Vehicle.getIDList());
    }

    public double getTlsPhaseDurationSafe(String tlsId, int phaseIndex) {
        try {
            ensureRunning();
            // SUMO returns list of durations
            Object durationsObj = conn.do_job_get(Trafficlight.getPhaseDuration(tlsId));
            if (durationsObj instanceof Number n && phaseIndex == 0) return n.doubleValue();
            if (durationsObj instanceof List<?> list && phaseIndex < list.size()) {
                Object val = list.get(phaseIndex);
                if (val instanceof Number num) return num.doubleValue();
            }
        } catch (Exception ignored) { }
        return -1;
    }

    public void injectVehicle(String id, String routeId, double speed, Color color)
            throws Exception {

        ensureRunning();

        if (vehicleExists(id))
            throw new IllegalArgumentException("Vehicle ID already exists: " + id);

        conn.do_job_set(Vehicle.add(
                id,
                "DEFAULT_VEHTYPE",
                routeId,
                0,
                speed,
                speed,
                (byte) 0
        ));

        setVehicleColor(id, color);
    }

    @SuppressWarnings("unchecked")
    public List<String> getAllEdges() throws Exception {
        ensureRunning();
        return (List<String>) conn.do_job_get(de.tudresden.sumo.cmd.Edge.getIDList());
    }

    public double getVehicleSpeed(String id) throws Exception {
        ensureRunning();
        return ((Number) conn.do_job_get(Vehicle.getSpeed(id))).doubleValue();
    }

    public void setVehicleSpeed(String id, double speed) throws Exception {
        ensureRunning();
        conn.do_job_set(Vehicle.setSpeed(id, speed));
    }

    public Point2D.Double getVehiclePosition(String id) throws Exception {
        ensureRunning();
        SumoPosition2D p =
                (SumoPosition2D) conn.do_job_get(Vehicle.getPosition(id));
        return (p != null)
                ? new Point2D.Double(p.x, p.y)
                : new Point2D.Double();
    }

    /** REQUIRED by SumoVehicle + VehicleFilter */
    public String getVehicleEdge(String id) throws Exception {
        ensureRunning();
        return (String) conn.do_job_get(Vehicle.getRoadID(id));
    }

    public boolean vehicleExists(String vehId) {
        if (vehId == null)
            return false;

        try {
            ensureRunning();
            return ((Number) conn.do_job_get(
                    de.tudresden.sumo.cmd.Vehicle.getIDCount()
            )).intValue() > 0
                    && getVehicleIds().contains(vehId);
        } catch (Exception e) {
            return false;
        }
    }

    public Color getVehicleColor(String id) throws Exception {
        ensureRunning();

        Object obj = conn.do_job_get(Vehicle.getColor(id));

        if (obj instanceof SumoColor sc) {
            return new Color(
                    Byte.toUnsignedInt(sc.r),
                    Byte.toUnsignedInt(sc.g),
                    Byte.toUnsignedInt(sc.b)
            );
        }

        return Color.BLUE; // fallback
    }

    @SuppressWarnings("unchecked")
    public List<String> getRouteEdges(String routeId) throws Exception {
        ensureRunning();
        return (List<String>) conn.do_job_get(
                de.tudresden.sumo.cmd.Route.getEdges(routeId)
        );
    }

    public double getLaneMaxSpeed(String laneId) throws Exception {
        ensureRunning();
        return ((Number) conn.do_job_get(
                Lane.getMaxSpeed(laneId)
        )).doubleValue();
    }

    public void setVehicleColor(String id, Color color) throws Exception {
        ensureRunning();

        // create a SumoColor with RGBA (alpha 255 = fully opaque)
        SumoColor sc = new SumoColor(
                (byte) color.getRed(),
                (byte) color.getGreen(),
                (byte) color.getBlue(),
                (byte) 255
        );

        conn.do_job_set(Vehicle.setColor(id, sc));
    }

    @SuppressWarnings("unchecked")
    public List<String> getAllRoutes() throws Exception {
        ensureRunning();
        return (List<String>) conn.do_job_get(Route.getIDList());
    }

    // ================= TRAFFIC LIGHTS =================

    @SuppressWarnings("unchecked")
    public List<String> getTrafficLightIds() throws Exception {
        ensureRunning();
        return (List<String>) conn.do_job_get(Trafficlight.getIDList());
    }

    public int getTlsPhase(String tlsId) throws Exception {
        ensureRunning();
        return ((Number) conn.do_job_get(Trafficlight.getPhase(tlsId))).intValue();
    }

    public void setTlsPhase(String tlsId, int phase) throws Exception {
        ensureRunning();
        conn.do_job_set(Trafficlight.setPhase(tlsId, phase));
    }

    public void setTlsPhaseDuration(String tlsId, double seconds) throws Exception {
        ensureRunning();
        conn.do_job_set(Trafficlight.setPhaseDuration(tlsId, seconds));
    }

    public String getRedYellowGreenState(String tlsId) throws Exception {
        ensureRunning();
        return conn.do_job_get(
                Trafficlight.getRedYellowGreenState(tlsId)
        ).toString();
    }

    public String getVehicleRoute(String vehId) throws Exception {
        ensureRunning();
        return (String) conn.do_job_get(
                de.tudresden.sumo.cmd.Vehicle.getRouteID(vehId)
        );
    }

    public void setVehicleRoute(String vehId, String routeId) throws Exception {
        ensureRunning();
        conn.do_job_set(
                de.tudresden.sumo.cmd.Vehicle.setRouteID(vehId, routeId)
        );
    }

    @SuppressWarnings("unchecked")
    public List<String> getControlledLanes(String tlsId) throws Exception {
        ensureRunning();
        return (List<String>) conn.do_job_get(
                Trafficlight.getControlledLanes(tlsId)
        );
    }

    public List<Point2D.Double> getLaneShapePoints(String laneId)
            throws Exception {

        ensureRunning();
        Object obj = conn.do_job_get(Lane.getShape(laneId));

        if (obj instanceof SumoGeometry geo) {
            List<Point2D.Double> pts = new ArrayList<>();
            for (Object o : geo.coords) {
                SumoPosition2D p = (SumoPosition2D) o;
                pts.add(new Point2D.Double(p.x, p.y));
            }
            return pts;
        }
        return new ArrayList<>();
    }
}
