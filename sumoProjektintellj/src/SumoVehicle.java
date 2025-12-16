//Used as a blueprint of vehicles in the simulation 
public class SumoVehicle {

    private final String id; //unique id starting with "veh"
    private final SimulationController controller; //Reference to the SimulationController because its connected to Sumo

    
    //Constructor with arguments setting the ID and the SimulationController reference
    public SumoVehicle(String id, SimulationController controller) {
        this.id = id;
        this.controller = controller;
    }
    
    
    //Returns the Vehicles ID as a string
    public String getId() {
        return id;
    }

    
    //Returns the Vehicles speed as a double
    public double getSpeed() throws Exception {
        return controller.getVehicleSpeed(id);
    }
}