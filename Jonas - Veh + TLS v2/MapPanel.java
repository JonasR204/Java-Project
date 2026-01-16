import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class MapPanel extends JPanel {

    private final List<LaneShape> lanes;
    private List<SumoVehicle> vehicles = new ArrayList<>();
    private List<TrafficLightBar> trafficLights = new ArrayList<>();
    private List<String> highlightedTlsLanes = new ArrayList<>();

    private String selectedVehicleId;

    private double minX = Double.POSITIVE_INFINITY;
    private double maxX = Double.NEGATIVE_INFINITY;
    private double minY = Double.POSITIVE_INFINITY;
    private double maxY = Double.NEGATIVE_INFINITY;

    public MapPanel(List<LaneShape> lanes) {
        this.lanes = lanes;
        computeBounds();
    }

    public void setVehicles(List<SumoVehicle> vehicles) {
        this.vehicles = vehicles != null ? vehicles : new ArrayList<>();
        repaint();
    }

    public void setTrafficLights(List<TrafficLightBar> lights) {
        this.trafficLights = lights != null ? lights : new ArrayList<>();
        repaint();
    }

    public void setSelectedVehicle(String id) {
        this.selectedVehicleId = id;
        repaint();
    }

    public void setHighlightedTlsLanes(List<String> laneIds) {
        this.highlightedTlsLanes = laneIds != null ? laneIds : new ArrayList<>();
        repaint();
    }

    private void computeBounds() {
        for (LaneShape lane : lanes) {
            for (Point2D.Double p : lane.getPoints()) {
                minX = Math.min(minX, p.x);
                maxX = Math.max(maxX, p.x);
                minY = Math.min(minY, p.y);
                maxY = Math.max(maxY, p.y);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int margin = 20;

        double scale = Math.min(
                (w - 2.0 * margin) / (maxX - minX),
                (h - 2.0 * margin) / (maxY - minY)
        );

        // Background
        g2.setColor(new Color(240,240,240));
        g2.fillRect(0,0,w,h);

        // Lanes
        for (LaneShape lane : lanes) {
            boolean highlight = highlightedTlsLanes.contains(lane.getId());
            g2.setStroke(new BasicStroke(highlight ? 6 : 3));
            g2.setColor(highlight ? Color.ORANGE : Color.DARK_GRAY);

            List<Point2D.Double> pts = lane.getPoints();
            for (int i = 0; i < pts.size() - 1; i++) {
                Point2D.Double p1 = pts.get(i);
                Point2D.Double p2 = pts.get(i+1);

                int x1 = (int)((p1.x - minX) * scale) + margin;
                int y1 = h - (int)((p1.y - minY) * scale) - margin;
                int x2 = (int)((p2.x - minX) * scale) + margin;
                int y2 = h - (int)((p2.y - minY) * scale) - margin;

                g2.drawLine(x1,y1,x2,y2);
            }
        }

        // Vehicles
        int r = 6;
        for (SumoVehicle v : vehicles) {
            int x = (int)((v.getX() - minX) * scale) + margin;
            int y = h - (int)((v.getY() - minY) * scale) - margin;

            if (v.getId().equals(selectedVehicleId)) {
                g2.setColor(Color.BLACK);
                g2.setStroke(new BasicStroke(2));
                g2.drawOval(x - r - 3, y - r - 3, (r + 3) * 2, (r + 3) * 2);
            }

            g2.setColor(v.getRenderColor());
            g2.fillOval(x - r, y - r, r * 2, r * 2);
        }

        // Traffic light bars
        g2.setStroke(new BasicStroke(6));
        for (TrafficLightBar tl : trafficLights) {
            g2.setColor(tl.getColor());
            int x1 = (int)((tl.x1 - minX) * scale) + margin;
            int y1 = h - (int)((tl.y1 - minY) * scale) - margin;
            int x2 = (int)((tl.x2 - minX) * scale) + margin;
            int y2 = h - (int)((tl.y2 - minY) * scale) - margin;
            g2.drawLine(x1,y1,x2,y2);
        }
    }
}