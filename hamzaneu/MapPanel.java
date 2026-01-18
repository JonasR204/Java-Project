import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        if (lanes == null) return;
        for (LaneShape lane : lanes) {
            if (lane == null || lane.getPoints() == null) continue;
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

        // Background
        g2.setColor(new Color(240,240,240));
        g2.fillRect(0,0,w,h);

        // Safety: falls keine Daten da sind
        if (lanes == null || lanes.isEmpty() || maxX == minX || maxY == minY) {
            return;
        }

        double scale = Math.min(
                (w - 2.0 * margin) / (maxX - minX),
                (h - 2.0 * margin) / (maxY - minY)
        );

        /// --- SETUP ---


        // WICHTIG: Wir zeichnen pro Lane -> deshalb Lane-Breite, nicht 7m pro Lane.
        float laneWidthInMeters = 3.2f; // simple realistische Annahme
        float pixelWidth = (float) (laneWidthInMeters * scale);
        if (pixelWidth < 4.0f) pixelWidth = 4.0f;

        Stroke asphaltStroke = new BasicStroke(pixelWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

        float markWidth = Math.max(1.0f, pixelWidth / 12.0f);
        float[] dashPattern = {markWidth * 12, markWidth * 12};


        Stroke dashedStroke = new BasicStroke(markWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dashPattern, 3.0f);

        //Lane
        g2.setStroke(asphaltStroke);
        for (LaneShape lane : lanes) {
            List<Point2D.Double> pts = lane.getPoints();
            g2.setColor(new Color(60, 60, 60)); // Standard Asphalt

            // Zeichnen der grauen Basis
            for (int i = 0; i < pts.size() - 1; i++) {
                int x1 = (int)((pts.get(i).x - minX) * scale) + margin;
                int y1 = h - (int)((pts.get(i).y - minY) * scale) - margin;
                int x2 = (int)((pts.get(i+1).x - minX) * scale) + margin;
                int y2 = h - (int)((pts.get(i+1).y - minY) * scale) - margin;
                g2.drawLine(x1, y1, x2, y2);
            }
        }

        //  Die Ampel-Hervorhebung (Über den Asphalt)
        g2.setStroke(asphaltStroke);
        for (LaneShape lane : lanes) {
            if (highlightedTlsLanes.contains(lane.getId())) {
                g2.setColor(new Color(255, 150, 0)); // Sattes Orange für Ampeln
                List<Point2D.Double> pts = lane.getPoints();
                for (int i = 0; i < pts.size() - 1; i++) {
                    int x1 = (int)((pts.get(i).x - minX) * scale) + margin;
                    int y1 = h - (int)((pts.get(i).y - minY) * scale) - margin;
                    int x2 = (int)((pts.get(i+1).x - minX) * scale) + margin;
                    int y2 = h - (int)((pts.get(i+1).y - minY) * scale) - margin;
                    g2.drawLine(x1, y1, x2, y2);
                }
            }
        }

        //  Die Markierungen (Trennlinie ZWISCHEN den Spuren)
        g2.setStroke(dashedStroke);
        g2.setColor(new Color(230, 230, 230)); // Fast Reinweiß für maximalen Kontrast

        // Damit wir pro Straße/Edge nur EINE Mittellinie zeichnen (nicht doppelt)
        Set<String> alreadyDrawnCenter = new HashSet<>();

        for (LaneShape lane : lanes) {
            String laneId = lane.getId();
            if (laneId == null) continue;

            // Wir nehmen Lane _0 als Basis (rechte Spur) und schieben die Linie nach links
            // -> ergibt die Mittellinie zwischen _0 und _1.
            if (laneId.endsWith("_0") && !highlightedTlsLanes.contains(laneId)) {

                // "Edge-Id" ohne _0 / _1 am Ende
                String edgeBase = laneId.substring(0, laneId.length() - 2);
                if (alreadyDrawnCenter.contains(edgeBase)) continue;

                // Prüfen ob es überhaupt eine _1 gibt (sonst ergibt Mittellinie keinen Sinn)
                boolean hasLane1 = false;
                for (LaneShape other : lanes) {
                    if ((edgeBase + "_1").equals(other.getId())) {
                        hasLane1 = true;
                        break;
                    }
                }
                if (!hasLane1) continue;

                alreadyDrawnCenter.add(edgeBase);

                List<Point2D.Double> pts = lane.getPoints();
                for (int i = 0; i < pts.size() - 1; i++) {
                    int x1 = (int)((pts.get(i).x - minX) * scale) + margin;
                    int y1 = h - (int)((pts.get(i).y - minY) * scale) - margin;
                    int x2 = (int)((pts.get(i+1).x - minX) * scale) + margin;
                    int y2 = h - (int)((pts.get(i+1).y - minY) * scale) - margin;

                    double dx = x2 - x1;
                    double dy = y2 - y1;
                    double len = Math.sqrt(dx * dx + dy * dy);

                    if (len > 0) {
                        // halbe Lane-Breite, damit die Linie exakt zwischen _0 und _1 liegt
                        double shift = pixelWidth * 0.5;

                        // Normalenvektor nach links: (dy, -dx)
                        double offX = (dy / len) * shift;
                        double offY = (-dx / len) * shift;

                        g2.drawLine((int)(x1 + offX), (int)(y1 + offY),
                                (int)(x2 + offX), (int)(y2 + offY));
                    }
                }
            }
        }

        // Vehicles
        for (SumoVehicle v : vehicles) {
            int x = (int)((v.getX() - minX) * scale) + margin;
            int y = h - (int)((v.getY() - minY) * scale) - margin;

            // 1. Kopie für die Rotation erstellen
            Graphics2D gAuto = (Graphics2D) g2.create();
            gAuto.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            gAuto.translate(x, y);

            // 2. Winkel bestimmen
            double rotation = 0;
            try {
                // Wir greifen auf das angle-Feld zu. Falls getAngle() nicht geht,
                // wird der catch-Block ausgeführt und das Auto fährt geradeaus.
                rotation = Math.toRadians(v.getAngle() - 90);
            } catch (Exception e) {
                rotation = 0;
            }
            gAuto.rotate(rotation);

            // Auswahl-Effekt
            if (v.getId().equals(selectedVehicleId)) {
                gAuto.setColor(Color.YELLOW);
                gAuto.setStroke(new BasicStroke(2));
                gAuto.drawRect(-13, -8, 26, 16);
            }

            //  Das Auto-Design
            gAuto.setColor(v.getRenderColor());
            gAuto.fillRect(-10, -5, 20, 10); // Korpus

            // Windschutzscheibe (Vorne ist rechts im Koordinatensystem)
            gAuto.setColor(new Color(255, 255, 255, 200));
            gAuto.fillRect(4, -4, 3, 8);

            gAuto.dispose();

            //  Namen kürzen
            String shortId = v.getId().length() > 5 ? "..." + v.getId().substring(v.getId().length()-4) : v.getId();
            g2.setColor(Color.DARK_GRAY);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
            g2.drawString(shortId, x - 10, y - 15);
        }

        // (Haltelinien-Optik wie in SUMO) H
        for (TrafficLightBar tl : trafficLights) {
            g2.setColor(tl.getColor());
            g2.setStroke(new BasicStroke(4));

            // 1. Pixel-Koordinaten berechnen
            int px1 = (int)((tl.x1 - minX) * scale) + margin;
            int py1 = h - (int)((tl.y1 - minY) * scale) - margin;
            int px2 = (int)((tl.x2 - minX) * scale) + margin;
            int py2 = h - (int)((tl.y2 - minY) * scale) - margin;

            // 2. Richtungsvektor bestimmen
            double dx = px2 - px1;
            double dy = py2 - py1;
            double laenge = Math.sqrt(dx * dx + dy * dy);

            if (laenge > 0) {
                // 3. Einheitsvektor (Richtung der Straße)
                double ex = dx / laenge;
                double ey = dy / laenge;

                // 4. Normalenvektor (90 Grad zur Straße für Querstrich)
                double nx = -ey;
                double ny = ex;

                // 5. Haltelinie zeichnen (xA/yA bis xE/yE)
                int xA = (int) (px2 + nx * 8);
                int yA = (int) (py2 + ny * 8);
                int xE = (int) (px2 - nx * 8);
                int yE = (int) (py2 - ny * 8);
                g2.drawLine(xA, yA, xE, yE);

                // 6. Pfeilspitze zeichnen (Dreieck in Fahrtrichtung)
                double pfSize = 6.0;
                // Die hinteren Punkte des Pfeils werden berechnet
                int xP1 = (int) (px2 - ex * 10 + nx * pfSize);
                int yP1 = (int) (py2 - ey * 10 + ny * pfSize);
                int xP2 = (int) (px2 - ex * 10 - nx * pfSize);
                int yP2 = (int) (py2 - ey * 10 - ny * pfSize);

                int[] xPoints = {px2, xP1, xP2};
                int[] yPoints = {py2, yP1, yP2};
                g2.fillPolygon(xPoints, yPoints, 3);
            }
        }
    }
}
