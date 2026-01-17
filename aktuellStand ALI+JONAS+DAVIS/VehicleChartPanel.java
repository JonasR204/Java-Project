import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;



public class VehicleChartPanel extends JPanel {

    private List<Integer> values = List.of();

    public void setValues(List<Integer> values) {
        this.values = values;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (values == null || values.size() < 2) return;

        int w = getWidth();
        int h = getHeight();

        int padding = 30;
        int x0 = padding;
        int y0 = h - padding;
        int x1 = w - padding;
        int y1 = padding;

        int min = values.stream().min(Integer::compareTo).orElse(0);
        int max = values.stream().max(Integer::compareTo).orElse(1);
        if (max == min) max = min + 1;

        // Achsen
        g.drawLine(x0, y0, x1, y0);
        g.drawLine(x0, y0, x0, y1);

        int n = values.size();
        int prevX = x0;
        int prevY = map(values.get(0), min, max, y0, y1);

        for (int i = 1; i < n; i++) {
            int x = x0 + (int)((i / (double)(n - 1)) * (x1 - x0));
            int y = map(values.get(i), min, max, y0, y1);
            g.drawLine(prevX, prevY, x, y);
            prevX = x;
            prevY = y;
        }

        g.drawString("Vehicles now: " + values.get(n - 1), x0, y1 - 10);
    }

    private int map(int v, int min, int max, int outMin, int outMax) {
        double t = (v - min) / (double)(max - min);
        return (int)(outMin + t * (outMax - outMin));
    }


    public BufferedImage createImage() {
        int w = Math.max(getWidth(), 700);
        int h = Math.max(getHeight(), 500);

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();

        // wichtig, falls das Fenster noch nie geöffnet war
        this.setSize(w, h);
        this.doLayout();
        this.paint(g2);

        g2.dispose();
        return img;
    }


}

