/**
 * Repräsentiert eine Phase einer Ampel (Traffic Light)
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
    
    public int getIndex() {
        return index;
    }
    
    public String getState() {
        return state;
    }
    
    public double getDuration() {
        return duration;
    }
    
    public void setDuration(double duration) {
        this.duration = duration;
    }
    
    public void setState(String state) {
        this.state = state;
    }
    
    /**
     * Gibt die Farbe basierend auf dem State-String zurück
     * z.B. "GGrrGGrr" -> erstes Zeichen bestimmt Hauptfarbe
     */
    public char getPrimarySignal() {
        if (state == null || state.isEmpty()) return 'r';
        return state.charAt(0);
    }
    
    @Override
    public String toString() {
        return "Phase " + index + ": " + state + " (" + duration + "s)";
    }
}
