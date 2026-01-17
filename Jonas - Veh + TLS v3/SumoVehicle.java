import java.awt.Color;
import java.awt.geom.Point2D;

public class SumoVehicle {

    private String id;
    private double x;
    private double y;
    private Color color = Color.BLUE;

    private final SimulationController controller;

    public SumoVehicle(String id, SimulationController controller) {
        this.id = id;
        this.controller = controller;
    }

    public String getId() {
        return id;
    }

    public void updatePosition(Point2D.Double p) {
        if (p == null) return;
        x = p.x;
        y = p.y;
    }

    public void refresh() throws Exception {
        updatePosition(controller.getVehiclePosition(id));
        color = controller.getVehicleColor(id); // cached for rendering & filter
    }

    public double getX() { return x; }
    public double getY() { return y; }

    public Color getRenderColor() {
        return color;
    }

    public double getMaxAllowedSpeed() throws Exception {
        String edge = getCurrentEdge();
        if (edge == null || edge.isEmpty())
            return Double.POSITIVE_INFINITY;

        String laneId = edge + "_0";
        return controller.getLaneMaxSpeed(laneId);
    }

    public double getSpeed() throws Exception {
        return controller.getVehicleSpeed(id);
    }

    public String getCurrentEdge() throws Exception {
        return controller.getVehicleEdge(id);
    }

    public Color getColor() {
        return color;
    }
}