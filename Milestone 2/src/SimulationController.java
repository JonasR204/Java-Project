//Needed to talk to SUMO
import it.polito.appeal.traci.SumoTraciConnection;
//Allows the manipulation/access to Vehicle, Routes and the Simulation itself
import de.tudresden.sumo.cmd.Vehicle;
import de.tudresden.sumo.cmd.Route;

//IOException is needed when handling error messages when communicating with SUMO
import java.io.IOException;
import java.util.List;
import java.util.Random;


//Responsible for speaking with Sumo and manipulating the Simulation and accessing its data
public class SimulationController {
	
    private final SumoTraciConnection conn; //Stores the connection to Sumo
    private boolean running = false; //Determines if the Simulation is running
    private final Random rand = new Random(); //rand allows to generate Random numbers later on
    
    
    //Constructor Responsible for creating the Sumo connection and modify the simulation settings
    public SimulationController(String sumoGuiExe, String sumoCfg) throws IOException {
        conn = new SumoTraciConnection(sumoGuiExe, sumoCfg); //Creates the Sumo connection with the provided arguments

        conn.addOption("quit-on-end", "false"); //Sumo shouldn't close once the simulation is over
        conn.addOption("end", "999999999"); //Sets the simulations end time far in the future so it runs as long as the user wants
        conn.addOption("start", "true"); //Tells to start Sumo immediately  
        conn.addOption("delay", "50"); //Slow down Simulation speed
    }
    
    
    //Launches the Sumo-GUI and connects to it
    public void start() throws IOException {
        if (running) return; //If Sumo-GUI was already launched then return
        conn.runServer(); //Launches Sumo-GUI and connects to it
        running = true;
        System.out.println("SUMO-GUI started.");
    }
    
    
    //Stepping through the simulation
    public void step() {
        ensureRunning(); //Makes to only step if the Sumo connection is still active
        try {
            conn.do_timestep(); //Advance a step in the simulation
        } 
        //Error handling if you try to advance a step even though the simulation has ended or another error occured
        catch (Exception e) {
            System.out.println("SUMO connection closed internally.");
            running = false;
        }
    }

    
    //Spawn Vehicles at the start of a random route
    public SumoVehicle spawnRandomVehicle(List<String> routeIds) throws Exception {
        ensureRunning(); //Makes sure the simulation is still running
        
        //Only create new vehicles if there are routes to be driven
        if (routeIds.isEmpty()) {
            System.err.println("No routes available!");
            return null;
        }

        String route = routeIds.get(rand.nextInt(routeIds.size())); //Picks a random route for the new car object
        String id = "veh" + System.currentTimeMillis(); //Generates unique id with the similarity being "veh" at the start

        try {
        	//Tells Sumo to add a new car with a id, vehicle type, chosen route, speed and more
            conn.do_job_set(Vehicle.add(
                    id,
                    "DEFAULT_VEHTYPE",
                    route,
                    0,
                    0.0,
                    0.0,
                    (byte) 0
            ));
            return new SumoVehicle(id, this); //If the creation of the car was successful return the new car object

        } 
        //Error handling if the route was invalid or the creation of the vehicle led to and error
        catch (Exception e) {
            System.err.println("Error spawning vehicle on route " + route);
            return null;
        }
    }
    
    
    //Returns a List<String> of every Route in the Simulation
    @SuppressWarnings("unchecked")
    public List<String> getAllRoutes() throws Exception {
        ensureRunning();
        return (List<String>) conn.do_job_get(Route.getIDList());
    }

    
    //Returns a List<String> of every Vehicle in the Simulation
    @SuppressWarnings("unchecked")
    public List<String> getVehicleIds() throws Exception {
        ensureRunning();
        return (List<String>) conn.do_job_get(Vehicle.getIDList());
    }
    
    
    //Returns the speed of a vehicle based on its ID
    public double getVehicleSpeed(String id) throws Exception {
        ensureRunning();
        //do_job_get returns a Generic object which gets casted to a Number type object which allows it to be cast to a double
        return ((Number) conn.do_job_get(Vehicle.getSpeed(id))).doubleValue();
    }

    
    //Closes the Sumo connection and marks the simulation as nut running
    public void close() {
        try {
            conn.close();
        } catch (Exception ignored) {}
        running = false;
    }

    
    //Ensures that Sumo is running otherwise throw an error message
    private void ensureRunning() {
        if (!running)
            throw new IllegalStateException("SUMO not started.");
    }
    
    
    //Returns true if Sumo is running
    public boolean isRunning() {
        return running;
    }
}