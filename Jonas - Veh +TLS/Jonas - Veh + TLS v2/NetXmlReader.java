import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.File;
import java.util.*;

public class NetXmlReader {

    public final Map<String, List<Double>> tlsDurations = new HashMap<>();
    public final Map<String, List<String>> tlsStates = new HashMap<>();

    public void parse(String netXmlPath) throws Exception {
        File file = new File(netXmlPath);
        if (!file.exists()) {
            throw new IllegalArgumentException("File not found: " + netXmlPath);
        }

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(file);
        doc.getDocumentElement().normalize();

        NodeList tlLogics = doc.getElementsByTagName("tlLogic");

        for (int i = 0; i < tlLogics.getLength(); i++) {
            Node tlNode = tlLogics.item(i);
            if (tlNode.getNodeType() != Node.ELEMENT_NODE) continue;

            Element tlElement = (Element) tlNode;
            String tlsId = tlElement.getAttribute("id");

            NodeList phaseNodes = tlElement.getElementsByTagName("phase");

            List<Double> durations = new ArrayList<>();
            List<String> states = new ArrayList<>();

            for (int j = 0; j < phaseNodes.getLength(); j++) {
                Element phase = (Element) phaseNodes.item(j);

                durations.add(
                        Double.parseDouble(phase.getAttribute("duration"))
                );

                states.add(
                        phase.getAttribute("state")
                );
            }

            tlsDurations.put(tlsId, durations);
            tlsStates.put(tlsId, states);
        }
    }

    public List<Double> getDurations(String tlsId) {
        return tlsDurations.getOrDefault(tlsId, Collections.emptyList());
    }

    public List<String> getStates(String tlsId) {
        return tlsStates.getOrDefault(tlsId, Collections.emptyList());
    }
}