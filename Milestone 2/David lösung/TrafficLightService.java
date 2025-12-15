package trafficlight;

import it.polito.appeal.traci.TraCIException;
import it.polito.appeal.traci.TrafficLight;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrafficLightService {

    private final Map<String, TrafficLightModel> trafficLights = new HashMap<>();

    // Einmal nach dem Start der Simulation aufrufen
    public void loadTrafficLights() throws TraCIException {
        List<String> ids = TrafficLight.getIDList();
        for (String id : ids) {
            trafficLights.put(id, new TrafficLightModel(id));
        }
    }

    public TrafficLightModel getTrafficLight(String id) {
        return trafficLights.get(id);
    }

    public Collection<TrafficLightModel> getAllTrafficLights() {
        return trafficLights.values();
    }
}
