
import javax.swing.*;

public class PhaseEntry {
    public final int index;
    public String state;
    public double duration;
    public final JTextField durationField;

    public PhaseEntry(int index, String state, double duration) {
        this.index = index;
        this.state = state;
        this.duration = duration;
        this.durationField = new JTextField(String.valueOf((int)duration), 5);
    }
}
