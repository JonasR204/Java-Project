import java.util.List;


//Responsible for utilizing the created classes and deciding when each event occurs in the simulation
public class Main {
	//tells all other classes if Sumo is open or closed
    private static volatile boolean stop = false;

    public static void main(String[] args) throws Exception {
    	
    	//File path to the Sumo-Gui executable and Sumo configuration 
        String sumoGuiExe = "\"C:\\Program Files (x86)\\Eclipse\\New Sumo\\bin\\sumo-gui.exe\"";
        String sumoCfg = "\"C:\\Users\\jsreu\\OneDrive\\Dokumente\\SUMO Test\\Demo2\\Demo2.sumocfg\"";

        //Allows communication with Sumo, then with start launching the passed through Sumo Configuration
        SimulationController sim = new SimulationController(sumoGuiExe, sumoCfg);
        sim.start();
        
        //Store every Route available from the .rou file then printing them in the terminal
        List<String> routes = sim.getAllRoutes();
        System.out.println("Loaded routes: " + routes);
        
        //Instructions on how to close the Sumo connection, finishing the simulation
        System.out.println("SUMO-GUI running. Press ENTER to stop.");
        
        //Stores time since start of Simulation in Milliseconds
        long lastSpawn = 0;
        
        //Runs as long as the simulation is running and enter was not pressed
        while (!stop && sim.isRunning()) {

            //Checks if Enter was pressed
            if (System.in.available() > 0) {
                stop = true;                  
                break;
            }

            //Do a Simulation Step
            try {
                sim.step();
            }
            //To make sure no step executes even though the simulation is closed
            catch (Exception e) {
                System.out.println("SUMO connection closed.");
                break;
            }

            //Instantiate a new vehicle every 2 seconds at the start of one of the routes
            long now = System.currentTimeMillis();
            if (now - lastSpawn >= 2000) {
                sim.spawnRandomVehicle(routes);
                lastSpawn = now;
            }

            //Pauses the execution for 0.1seconds, otherwise having an infinite loop might be harsh on the CPU 
            Thread.sleep(100);
        }
        
        //Once the Simulation isn't running anymore or the enter has been pressed ends the simulation
        sim.close();
        System.out.println("SUMO-GUI closed.");
    }
}
