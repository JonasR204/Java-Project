//DATENOBJEKT,das die Geometrie einer straße speichert


import java.awt.geom.Point2D; 	//x,y koordinate
import java.util.ArrayList;	//liste von punkten
import java.util.List;

public class LaneShape {			//Klasse für einzelne spur (Lane)
    private final List<Point2D.Double> points = new ArrayList<>();

    public void addPoint(double x, double y) {
        points.add(new Point2D.Double(x, y));
    }

    public List<Point2D.Double> getPoints() { //LISTE VON PUNKTEN DIE STRAßE ergebn
        return points;
    }
}
