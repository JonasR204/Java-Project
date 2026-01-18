import java.util.*;

/**
 * Sammelt und verwaltet Ampelphasen - ohne Swing UI (JavaFX kompatibel)
 */
public class TlsPhaseCollector {

    private Integer lastAppliedPhase = null;
    private final SimulationController controller;
    private final String tlsId;

    private final List<PhaseEntry> phases = new ArrayList<>();

    public TlsPhaseCollector(SimulationController controller, String tlsId) {
        this.controller = controller;
        this.tlsId = tlsId;
    }

    public String getTlsId() {
        return tlsId;
    }

    public void initialize(List<Double> durations, List<String> states) {
        phases.clear();

        int count = Math.min(durations.size(), states.size());

        for (int i = 0; i < count; i++) {
            phases.add(new PhaseEntry(i, states.get(i), durations.get(i)));
        }
    }

    public List<PhaseEntry> getPhases() {
        return phases;
    }

    public int getPhaseCount() {
        return phases.size();
    }

    public PhaseEntry getPhase(int index) {
        if (index >= 0 && index < phases.size()) {
            return phases.get(index);
        }
        return null;
    }

    public int getCurrentPhaseIndex() {
        try {
            return controller.getTlsPhase(tlsId);
        } catch (Exception e) {
            return -1;
        }
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

    /**
     * Setzt die Dauer für eine bestimmte Phase
     */
    public void setPhaseDuration(int phaseIndex, double seconds) {
        try {
            if (phaseIndex >= 0 && phaseIndex < phases.size()) {
                PhaseEntry p = phases.get(phaseIndex);
                p.duration = seconds;
                
                if (controller.getTlsPhase(tlsId) == phaseIndex) {
                    controller.setTlsPhaseDuration(tlsId, seconds);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Wendet die gespeicherte Dauer an, wenn sich die Phase geändert hat
     */
    public void applyDurationIfPhaseChanged() {
        try {
            int currentPhase = controller.getTlsPhase(tlsId);

            if (lastAppliedPhase != null && lastAppliedPhase == currentPhase) {
                return;
            }

            if (currentPhase >= 0 && currentPhase < phases.size()) {
                PhaseEntry p = phases.get(currentPhase);
                double seconds = p.duration;

                if (seconds <= 0) seconds = 1;

                controller.setTlsPhaseDuration(tlsId, seconds);
                lastAppliedPhase = currentPhase;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Wendet die Dauer der aktuellen Phase an
     */
    public void applyCurrentPhaseDuration() {
        try {
            int current = controller.getTlsPhase(tlsId);

            if (current >= 0 && current < phases.size()) {
                PhaseEntry p = phases.get(current);
                double seconds = p.duration;

                if (seconds <= 0) seconds = 0.1;

                controller.setTlsPhaseDuration(tlsId, seconds);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Setzt eine spezifische Phase
     */
    public void setPhase(int phaseIndex) {
        try {
            if (phaseIndex >= 0 && phaseIndex < phases.size()) {
                controller.setTlsPhase(tlsId, phaseIndex);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
