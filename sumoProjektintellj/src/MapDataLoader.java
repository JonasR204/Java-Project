//DIESE KLASSE LIESST .net.xml daten und wandelt sie in Straßen Java objekte (laneshapes)
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;		//xml lesen
import java.io.File;				//öffnet files
import java.util.ArrayList;
import java.util.List;
//(HILFSKLASSE)
public class MapDataLoader {

    public static List<LaneShape> loadLanes(String netFilePath) {
        List<LaneShape> lanes = new ArrayList<>();			//liste aller gefunden spuren aus der map

        try {
            File file = new File(netFilePath);			//FIle-Objekt für sumo netzdatei
            if (!file.exists()) {
                System.out.println("Net-Datei nicht gefunden: " + netFilePath);
                return lanes;
            }
            													// XML Initialisieren und .net.xml in den speicher
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file);
            doc.getDocumentElement().normalize();

            NodeList laneNodes = doc.getElementsByTagName("lane"); //ALLE <lane> holen

            for (int i = 0; i < laneNodes.getLength(); i++) {
                Node laneNode = laneNodes.item(i);
                if (laneNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element laneElem = (Element) laneNode;
                    String shapeStr = laneElem.getAttribute("shape");
                    if (shapeStr == null || shapeStr.isEmpty()) continue;

                    LaneShape laneShape = new LaneShape();

                    
                    String[] pointStrs = shapeStr.split(" ");
                    for (String p : pointStrs) {
                        String[] xy = p.split(",");
                        if (xy.length != 2) continue;
                        double x = Double.parseDouble(xy[0]);
                        double y = Double.parseDouble(xy[1]);
                        laneShape.addPoint(x, y);
                    }

                    lanes.add(laneShape);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return lanes;
    }
}
