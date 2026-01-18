import java.awt.image.BufferedImage;
import java.io.FileWriter;
import java.io.PrintWriter;

/**
 * PDF Export - Vereinfachte Version ohne Apache PDFBox
 * Exportiert stattdessen als Text-Datei
 */
public class PdfReportExporter {

    public static void exportVehicleChart(String filePath,
                                          BufferedImage chartImage,
                                          int vehiclesEver) throws Exception {

        // Da PDFBox nicht verfügbar ist, exportieren wir als Text
        String textFile = filePath.replace(".pdf", "_report.txt");
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(textFile))) {
            writer.println("═══════════════════════════════════════════");
            writer.println("         SUMO VEHICLE REPORT");
            writer.println("═══════════════════════════════════════════");
            writer.println();
            writer.println("Generated: " + java.time.LocalDateTime.now());
            writer.println("Vehicles (total): " + vehiclesEver);
            writer.println();
            writer.println("───────────────────────────────────────────");
            writer.println("Note: PDF export requires Apache PDFBox library.");
            writer.println("This is a text-based alternative report.");
            writer.println("───────────────────────────────────────────");
            
            if (chartImage != null) {
                writer.println();
                writer.println("Chart dimensions: " + chartImage.getWidth() + "x" + chartImage.getHeight());
            }
        }
        
        System.out.println("[EXPORT] Text report saved: " + textFile);
    }
}
