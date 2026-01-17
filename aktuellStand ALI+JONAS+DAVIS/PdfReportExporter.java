import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PdfReportExporter {

    public static void exportVehicleChart(String filePath,
                                          BufferedImage chartImage,
                                          int vehiclesEver) throws Exception {

        try (PDDocument document = new PDDocument()) {

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            float margin = 50f;
            float pageW = page.getMediaBox().getWidth();
            float pageH = page.getMediaBox().getHeight();

            // Fonts (PDFBox 3.x)
            PDType1Font fontTitle = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fontText  = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {

                float y = pageH - margin;

                // Title
                cs.setFont(fontTitle, 18);
                cs.beginText();
                cs.newLineAtOffset(margin, y);
                cs.showText("SUMO Vehicle Report");
                cs.endText();

                y -= 28;

                // Timestamp
                cs.setFont(fontText, 11);
                String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                cs.beginText();
                cs.newLineAtOffset(margin, y);
                cs.showText("Generated: " + ts);
                cs.endText();

                y -= 16;

                // Vehicles ever
                cs.beginText();
                cs.newLineAtOffset(margin, y);
                cs.showText("Vehicles (ever): " + vehiclesEver);
                cs.endText();

                y -= 22;

                // Chart image
                PDImageXObject img = LosslessFactory.createFromImage(document, chartImage);

                float maxW = pageW - 2 * margin;
                float scale = maxW / chartImage.getWidth();
                float imgW = chartImage.getWidth() * scale;
                float imgH = chartImage.getHeight() * scale;

                // If too tall, scale down
                float maxH = y - margin;
                if (imgH > maxH) {
                    float s2 = maxH / imgH;
                    imgW *= s2;
                    imgH *= s2;
                }

                cs.drawImage(img, margin, y - imgH, imgW, imgH);
            }

            document.save(filePath);
        }
    }
}


