package trafficlight;

import it.polito.appeal.traci.TraCIException;
import it.polito.appeal.traci.TrafficLight;

public class TrafficLightModel {

    private final String id;
    private String currentState;

    public TrafficLightModel(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getState() throws TraCIException {
        currentState = TrafficLight.getRedYellowGreenState(id);
        return currentState;
    }

    public void setState(String state) throws TraCIException {
        TrafficLight.setRedYellowGreenState(id, state);
        this.currentState = state;
    }

    public int getPhase() throws TraCIException {
        return TrafficLight.getPhase(id);
    }

    public void setPhase(int phase) throws TraCIException {
        TrafficLight.setPhase(id, phase);
    }

    public int getPhaseDuration() throws TraCIException {
        return TrafficLight.getPhaseDuration(id);
    }

    public void setPhaseDuration(int seconds) throws TraCIException {
        TrafficLight.setPhaseDuration(id, seconds);
    }
}
