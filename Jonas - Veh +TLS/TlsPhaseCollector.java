import java.util.*;

public class TlsPhaseCollector {

    private final SimulationController controller;
    private final String tlsId;

    private final List<PhaseEntry> phases = new ArrayList<>();

    public TlsPhaseCollector(SimulationController controller, String tlsId) {
        this.controller = controller;
        this.tlsId = tlsId;
    }

    public void initialize(List<Double> durations, List<String> states) {
        phases.clear();

        int count = Math.min(durations.size(), states.size());

        for (int i = 0; i < count; i++) {
            phases.add(
                    new PhaseEntry(i, states.get(i), durations.get(i))
            );
        }
    }

    public List<PhaseEntry> getPhases() {
        return phases;
    }

    public void observe() throws Exception {
        int current = controller.getTlsPhase(tlsId);

        for (PhaseEntry p : phases) {
            if (p.index == current) {
                p.state = controller.getRedYellowGreenState(tlsId);
                break;
            }
        }
    }

    public void applyCurrentPhaseDuration() {
        try {
            int current = controller.getTlsPhase(tlsId);

            PhaseEntry p = phases.get(current);
            double seconds = Double.parseDouble(p.durationField.getText());

            if (seconds <= 0) seconds = 0.1;

            p.duration = seconds;
            controller.setTlsPhaseDuration(tlsId, seconds);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}