import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * Exportiert DataPoints in eine CSV-Datei
 */
public class CsvExporter {

    /**
     * Exportiert eine Liste von DataPoints in eine CSV-Datei
     */
    public void export(String filePath, List<DataPoint> dataPoints) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            // Header schreiben
            writer.println("timestamp,type,id,x,y,speed,phase,state");

            for (DataPoint dp : dataPoints) {
                if (dp instanceof VehicleTelemetry vt) {
                    writer.println(String.format("%s,vehicle,%s,%.4f,%.4f,%.4f,,",
                            vt.timestamp().toString(),
                            vt.getVehicleId(),
                            vt.getX(),
                            vt.getY(),
                            vt.getSpeed()
                    ));
                } else if (dp instanceof TrafficLightTelemetry tlt) {
                    writer.println(String.format("%s,trafficlight,%s,,,,%d,%s",
                            tlt.timestamp().toString(),
                            tlt.getTlsId(),
                            tlt.getPhase(),
                            tlt.getState()
                    ));
                } else {
                    // Generischer DataPoint
                    writer.println(String.format("%s,unknown,,,,,",
                            dp.timestamp().toString()
                    ));
                }
            }
        }
    }

    /**
     * Exportiert nur Fahrzeug-Telemetrie
     */
    public void exportVehicles(String filePath, List<DataPoint> dataPoints) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("timestamp,vehicleId,x,y,speed");

            for (DataPoint dp : dataPoints) {
                if (dp instanceof VehicleTelemetry vt) {
                    writer.println(String.format("%s,%s,%.4f,%.4f,%.4f",
                            vt.timestamp().toString(),
                            vt.getVehicleId(),
                            vt.getX(),
                            vt.getY(),
                            vt.getSpeed()
                    ));
                }
            }
        }
    }

    /**
     * Exportiert nur Ampel-Telemetrie
     */
    public void exportTrafficLights(String filePath, List<DataPoint> dataPoints) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("timestamp,tlsId,phase,state");

            for (DataPoint dp : dataPoints) {
                if (dp instanceof TrafficLightTelemetry tlt) {
                    writer.println(String.format("%s,%s,%d,%s",
                            tlt.timestamp().toString(),
                            tlt.getTlsId(),
                            tlt.getPhase(),
                            tlt.getState()
                    ));
                }
            }
        }
    }
}
