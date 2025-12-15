package simulation;

import it.polito.appeal.traci.SumoTraciConnection;
import trafficlight.TrafficLightService;

public class SimulationController {

    private SumoTraciConnection connection;
    private TrafficLightService trafficLightService;

    public void start(String sumoCmd, String sumoConfigPath) throws Exception {
        connection = new SumoTraciConnection(sumoCmd, sumoConfigPath);
        connection.runServer();

        trafficLightService = new TrafficLightService();
        trafficLightService.loadTrafficLights();
    }

    public void step() throws Exception {
        connection.do_timestep();
    }

    public TrafficLightService getTrafficLightService() {
        return trafficLightService;
    }

    public void close() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }
}
