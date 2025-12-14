// Needed to talk to SUMO
import it.polito.appeal.traci.SumoTraciConnection;
import de.tudresden.sumo.objects.SumoPosition2D;



// Allows manipulation/access to Vehicles and Routes
import de.tudresden.sumo.cmd.Vehicle;
import de.tudresden.sumo.cmd.Route;
import de.tudresden.sumo.cmd.Trafficlight;

// Java
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.awt.geom.Point2D;

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
    
    // Get all Traffic Light IDs
    @SuppressWarnings("unchecked")
    public List<String> getTrafficLightIds() throws Exception {
        ensureRunning();
        return (List<String>) conn.do_job_get(Trafficlight.getIDList());
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

