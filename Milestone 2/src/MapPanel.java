import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class MapPanel extends JPanel {

    private final List<LaneShape> lanes;
    private List<VehicleState> vehicles = new ArrayList<>();
    																//GRENZEN KOORDNIATENSYSTEM
    private double minX = Double.POSITIVE_INFINITY;
    private double maxX = Double.NEGATIVE_INFINITY;
    private double minY = Double.POSITIVE_INFINITY;
    private double maxY = Double.NEGATIVE_INFINITY;
    														//MapPAnel BEKOMMT STRAßen
    public MapPanel(List<LaneShape> lanes) {
        this.lanes = lanes;
        computeBounds();				//BERECHNET DIE AUSDEHNUNG DER GESAMTEN KARTE
    }
    							//GUI RUFT AUF WENN NEUE FAHRZEUGPOSITIONEN DA SIND
    public void setVehicles(List<VehicleState> vehicles) {
        this.vehicles = (vehicles != null) ? new ArrayList<>(vehicles) : new ArrayList<>();
        repaint();		//PANEL Neuzeichnen
    }
    											//MINIMALE GRENZEN KOORDINATENSYSTEM
    private void computeBounds() {
        for (LaneShape lane : lanes) {
            for (Point2D.Double p : lane.getPoints()) {
                minX = Math.min(minX, p.x);
                maxX = Math.max(maxX, p.x);
                minY = Math.min(minY, p.y);
                maxY = Math.max(maxY, p.y);
            }
        }
        					//FALLS KEINE LASNES VORHANDEN SIND-> fallback
        if (lanes.isEmpty()) {
            minX = minY = 0;
            maxX = maxY = 1;
        }
    }
//METHODE ZEICHNET KOMPLETTE PANEL NEU
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (lanes.isEmpty()) {
            g.drawString("Keine Lanes geladen.", 10, 20);
            return;
        }
//ERWEITERTE ZEICHENFUNKTION
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
//PANEL GRÖßE
        int w = getWidth();
        int h = getHeight();
        int margin = 20; //RAND DAMIT NICHT FENSTER KLEBT
//GRÖßE DER WELT (SUMO)
        double worldWidth = Math.max(1, maxX - minX);
        double worldHeight = Math.max(1, maxY - minY);
//DAMIT ALLES INS FENSTER PASST
        double scale = Math.min(
                (w - 2.0 * margin) / worldWidth,
                (h - 2.0 * margin) / worldHeight
        );

    // Hintergrund
        g2.setColor(new Color(240, 240, 240));
        g2.fillRect(0, 0, w, h);

     // Straßen zeichnen
        g2.setColor(Color.DARK_GRAY);
        g2.setStroke(new BasicStroke(3));

        for (LaneShape lane : lanes) {
            List<Point2D.Double> pts = lane.getPoints();
            for (int i = 0; i < pts.size() - 1; i++) {
                Point2D.Double p1 = pts.get(i);
                Point2D.Double p2 = pts.get(i + 1);

                int x1 = (int) ((p1.x - minX) * scale) + margin;
                int y1 = h - (int) ((p1.y - minY) * scale) - margin;
                int x2 = (int) ((p2.x - minX) * scale) + margin;
                int y2 = h - (int) ((p2.y - minY) * scale) - margin;

                g2.drawLine(x1, y1, x2, y2);
            }
        }

        // Fahrzeuge (blaue Punkte)
        g2.setColor(Color.BLUE);
        int r = 6;

        for (VehicleState v : vehicles) {
            int x = (int) ((v.x - minX) * scale) + margin;
            int y = h - (int) ((v.y - minY) * scale) - margin;
            g2.fillOval(x - r, y - r, r * 2, r * 2);
        }
    }
}





