package scheduling.scheduling.export;

import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class PdfExportFonts {

    private static final String REGULAR_RESOURCE = "/fonts/DejaVuSans.ttf";
    private static final String BOLD_RESOURCE = "/fonts/DejaVuSans-Bold.ttf";
    private static final Map<String, BaseFont> BASE_FONTS = new ConcurrentHashMap<>();

    private PdfExportFonts() {
    }

    static Font regular(float size) {
        return new Font(baseFont(REGULAR_RESOURCE), size, Font.NORMAL);
    }

    static Font bold(float size) {
        return new Font(baseFont(BOLD_RESOURCE), size, Font.BOLD);
    }

    private static BaseFont baseFont(String resource) {
        return BASE_FONTS.computeIfAbsent(resource, PdfExportFonts::load);
    }

    private static BaseFont load(String resource) {
        try {
            Path fontFile = Files.createTempFile("scheduling-export-font-", ".ttf");
            try {
                fontFile.toFile().deleteOnExit();
                Files.write(fontFile, readResource(resource));
                return BaseFont.createFont(fontFile.toAbsolutePath().toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (DocumentException | IOException exception) {
                throw new IllegalStateException(
                        "Failed to load embedded font resource " + resource + " required for the PDF export", exception);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to stage embedded font resource " + resource + " required for the PDF export", exception);
        }
    }

    private static byte[] readResource(String resource) {
        try (InputStream stream = PdfExportFonts.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException(
                        "Missing embedded font resource " + resource + " required for the PDF export");
            }
            return stream.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to read embedded font resource " + resource + " required for the PDF export", exception);
        }
    }
}