import java.io.FileWriter;
import java.util.List;

public class CsvExporter {

    public void export(String file, List<DataPoint> data) throws Exception {
        try (FileWriter w = new FileWriter(file)) {
            // NEU: edge,color erweitert ALI ALI
            w.write("type,timestamp,id,x,y,speed,edge,color,phase,state\n");

            for (DataPoint p : data) {
                if (p instanceof VehicleTelemetry v) {
                    w.write("vehicle," + v.timestamp() + "," +
                            v.getVehicleId() + ",," +
                            v.getX() + "," + v.getY() + "," +
                            v.getSpeed() + ",,\n");
                }
                if (p instanceof TrafficLightTelemetry t) {
                    w.write("trafficlight," + t.timestamp() + "," +
                            t.getTlsId() + ",,,," + ",,"+
                            t.getPhase() + "," +
                            t.getState() + "\n");
                }
            }
        }
    }
}
