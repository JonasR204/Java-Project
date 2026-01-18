/**
 * Repräsentiert eine Ampelphase - ohne Swing UI (JavaFX kompatibel)
 */
public class PhaseEntry {
    public final int index;
    public String state;
    public double duration;

    public PhaseEntry(int index, String state, double duration) {
        this.index = index;
        this.state = state;
        this.duration = duration;
    }

    public int getIndex() { return index; }
    public String getState() { return state; }
    public double getDuration() { return duration; }
    
    public void setDuration(double duration) { this.duration = duration; }
    public void setState(String state) { this.state = state; }
    
    @Override
    public String toString() {
        return "Phase " + index + ": " + state + " (" + duration + "s)";
    }
}
