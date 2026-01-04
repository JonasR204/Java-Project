import java.awt.*;

public class TrafficLightBar{
    public final String tlsId;
    public final String laneId;
    public final int indexInState;

    public final double x1, y1;
    public final double x2, y2;

    private Color color = Color.GRAY;

    public TrafficLightBar(String tlsId, String laneId, int indexInState,
                           double x1, double y1, double x2, double y2) {
        this.tlsId = tlsId;
        this.laneId = laneId;
        this.indexInState = indexInState;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }

    public Color getColor() {
        return color;
    }

    public void setStateChar(char c) {
        switch (c) {
            case 'r':
            case 'R':
                color = Color.RED;
                break;
            case 'y':
            case 'Y':
                color = Color.YELLOW;
                break;
            case 'g':
            case 'G':
                color = Color.GREEN;
                break;
            default:
                color = Color.DARK_GRAY;
        }
    }
}