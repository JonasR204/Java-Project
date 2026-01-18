import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class LaneShape {

    private final String id;
    private final List<Point2D.Double> points = new ArrayList<>();

    public LaneShape(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void addPoint(double x, double y) {
        points.add(new Point2D.Double(x, y));
    }

    public List<Point2D.Double> getPoints() {
        return points;
    }
}