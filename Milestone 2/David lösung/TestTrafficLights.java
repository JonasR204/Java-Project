package trafficlight;

public class TestTrafficLights {

    public static void main(String[] args) {

        TrafficLightService service = new TrafficLightService();

        // Zwei Ampeln "simulieren"
        service.registerTrafficLight("A1", TrafficLightState.RED);
        service.registerTrafficLight("B2", TrafficLightState.GREEN);

        // Status abfragen
        System.out.println("A1 = " + service.getStatus("A1"));
        System.out.println("B2 = " + service.getStatus("B2"));

        // Status ändern
        service.updateState("A1", TrafficLightState.GREEN);

        System.out.println("A1 neu = " + service.getStatus("A1"));

        // Alle Ampeln anzeigen
        for (TrafficLight tl : service.getAllTrafficLights()) {
            System.out.println(tl.getId() + " -> " + tl.getState());
        }
    }
}
