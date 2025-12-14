import de.tudresden.sumo.cmd.Trafficlight;
import de.tudresden.sumo.util.SumoCommand;
import it.polito.appeal.traci.SumoTraciConnection;


//Class to create objects of Sumo traffic lights
public class SumoTrafficLight {
	
    private final String id; //Unique ID
    private final SumoTraciConnection conn; //Reference to the Traas connection, allows to use TraCi Commands
    
    
    //Constructor setting the ID and passing the Traas connection reference 
    public SumoTrafficLight(SumoTraciConnection conn, String id) {
        this.conn = conn;
        this.id = id;
    }
    
    
    //Returns the ID
    public String getId() {
        return id;
    }

    
    //Return a string corresponding to the current State, like "r" which stands for red light
    public String getRawState() throws Exception {
        SumoCommand cmd = Trafficlight.getRedYellowGreenState(id);
        Object result = conn.do_job_get(cmd);
        return (result != null) ? result.toString() : "";
    }

    
    //Returns Integer based on the current Phase, like 0 means the first phase of the traffic light program is active
    public int getPhaseIndex() throws Exception {
        SumoCommand cmd = Trafficlight.getPhase(id);
        Object result = conn.do_job_get(cmd);
        return (result instanceof Number) ? ((Number) result).intValue() : -1;
    }
}
