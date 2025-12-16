// Needed to talk to SUMO
import it.polito.appeal.traci.SumoTraciConnection;
import de.tudresden.sumo.objects.SumoPosition2D;




// Allows manipulation/access to Vehicles and Routes
import de.tudresden.sumo.cmd.Vehicle;
import de.tudresden.sumo.cmd.Route;

// Java
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.awt.geom.Point2D;

//Trafficlights
import de.tudresden.sumo.cmd.Trafficlight;
import de.tudresden.sumo.cmd.Lane;
import de.tudresden.sumo.objects.SumoGeometry;

import java.util.ArrayList;
import java.lang.reflect.Field;


// Responsible for speaking with SUMO and manipulating the simulation (vehicles only)
public class SimulationController {

    private final SumoTraciConnection conn;
    private boolean running = false;
    private final Random rand = new Random();

    // Constructor
    public SimulationController(String sumoGuiExe, String sumoCfg) throws IOException {
        conn = new SumoTraciConnection(sumoGuiExe, sumoCfg);

        conn.addOption("quit-on-end", "false");
        conn.addOption("end", "999999999");
        conn.addOption("start", "true");
        conn.addOption("delay", "50");
    }

    // Start SUMO
    public void start() throws IOException {
        if (running) return;
        conn.runServer();
        running = true;
        System.out.println("SUMO-GUI started.");
    }

    // Advance simulation by one step
    public void step() {
        ensureRunning();
        try {
            conn.do_timestep();
        } catch (Exception e) {
            System.out.println("SUMO connection closed internally.");
            running = false;
        }
    }

    // Spawn a vehicle on a random route
    public SumoVehicle spawnRandomVehicle(List<String> routeIds) throws Exception {
        ensureRunning();

        if (routeIds == null || routeIds.isEmpty()) {
            System.err.println("No routes available!");
            return null;
        }

        String route = routeIds.get(rand.nextInt(routeIds.size()));
        String id = "veh" + System.nanoTime();

        try {
            conn.do_job_set(Vehicle.add(
                    id,
                    "DEFAULT_VEHTYPE",
                    route,
                    0,
                    0.0,
                    0.0,
                    (byte) 0
            ));
            return new SumoVehicle(id, this);
        } catch (Exception e) {
            System.err.println("Error spawning vehicle on route " + route);
            return null;
        }
    }

    // Get all route IDs
    @SuppressWarnings("unchecked")
    public List<String> getAllRoutes() throws Exception {
        ensureRunning();
        return (List<String>) conn.do_job_get(Route.getIDList());
    }

    // Get all vehicle IDs
    @SuppressWarnings("unchecked")
    public List<String> getVehicleIds() throws Exception {
        ensureRunning();
        return (List<String>) conn.do_job_get(Vehicle.getIDList());
    }

    // Get vehicle speed
    public double getVehicleSpeed(String id) throws Exception {
        ensureRunning();
        return ((Number) conn.do_job_get(Vehicle.getSpeed(id))).doubleValue();
    }

    // Get vehicle position (correct TraaS way)
    public Point2D.Double getVehiclePosition(String id) throws Exception {
        ensureRunning();

        SumoPosition2D p =
                (SumoPosition2D) conn.do_job_get(Vehicle.getPosition(id));

        if (p == null) return new Point2D.Double(0, 0);

        return new Point2D.Double(p.x, p.y);
    }

//AMPEL PHASEN

    public int getTlsPhase(String tlsId) throws Exception {
        ensureRunning();
        Object res = conn.do_job_get(Trafficlight.getPhase(tlsId));
        return (res instanceof Number) ? ((Number) res).intValue() : Integer.parseInt(res.toString());
    }

    public void setTlsPhase(String tlsId, int phase) throws Exception {
        ensureRunning();
        conn.do_job_set(Trafficlight.setPhase(tlsId, phase));
    }
    public void setTlsPhaseDuration(String tlsId, double seconds) throws Exception {
        ensureRunning();
        conn.do_job_set(Trafficlight.setPhaseDuration(tlsId, seconds));
    }
    public String getTlsState(String tlsId) throws Exception {
        ensureRunning();
        Object res = conn.do_job_get(Trafficlight.getRedYellowGreenState(tlsId));
        return (res != null) ? res.toString() : "";
    }
    // Optional (Demo-Button “All-Red” etc.)
    public void setTlsState(String tlsId, String state) throws Exception {
        ensureRunning();
        conn.do_job_set(Trafficlight.setRedYellowGreenState(tlsId, state));
    }













    //TRAFFICLIGHTS
    // ------------------------------
// Traffic Lights (TraaS)
// ------------------------------

    /** All traffic light system IDs (TLS IDs) known to SUMO. */
    @SuppressWarnings("unchecked")
    public List<String> getTrafficLightIds() throws Exception {
        ensureRunning();
        return (List<String>) conn.do_job_get(Trafficlight.getIDList());
    }

    /** Lanes controlled by this traffic light system (same order as the RYG state string). */
    @SuppressWarnings("unchecked")
    public List<String> getControlledLanes(String tlsId) throws Exception {
        ensureRunning();
        return (List<String>) conn.do_job_get(Trafficlight.getControlledLanes(tlsId));
    }

    /** Returns the red-yellow-green state string (e.g., "rrGGyy"). */
    public String getRedYellowGreenState(String tlsId) throws Exception {
        ensureRunning();
        Object res = conn.do_job_get(Trafficlight.getRedYellowGreenState(tlsId));
        return (res != null) ? res.toString() : "";
    }

    /**
     * Fetch the lane polyline from SUMO (world coords).
     * TraaS returns a SumoGeometry whose field "coords" contains a list of SumoPosition2D.
     */
    public List<Point2D.Double> getLaneShapePoints(String laneId) throws Exception {
        ensureRunning();

        Object obj = conn.do_job_get(Lane.getShape(laneId));
        if (obj == null) return new ArrayList<>();

        if (obj instanceof SumoGeometry geo) {
            return extractPointsFromCoords(geo.coords);
        }

        // Fallback: try reflection (version differences)
        try {
            Field f = obj.getClass().getField("coords");
            Object coordsObj = f.get(obj);
            return extractPointsFromCoords(coordsObj);
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private List<Point2D.Double> extractPointsFromCoords(Object coordsObj) {
        List<Point2D.Double> pts = new ArrayList<>();
        if (coordsObj == null) return pts;

        if (coordsObj instanceof Iterable<?> it) {
            for (Object p : it) {
                if (p instanceof SumoPosition2D sp) {
                    pts.add(new Point2D.Double(sp.x, sp.y));
                } else {
                    try {
                        Field fx = p.getClass().getField("x");
                        Field fy = p.getClass().getField("y");
                        double x = ((Number) fx.get(p)).doubleValue();
                        double y = ((Number) fy.get(p)).doubleValue();
                        pts.add(new Point2D.Double(x, y));
                    } catch (Exception ignored) {}
                }
            }
        }
        return pts;
    }














    // Close SUMO connection
    public void close() {
        try {
            conn.close();
        } catch (Exception ignored) {}
        running = false;
    }

    private void ensureRunning() {
        if (!running)
            throw new IllegalStateException("SUMO not started.");
    }

    public boolean isRunning() {
        return running;
    }
}

