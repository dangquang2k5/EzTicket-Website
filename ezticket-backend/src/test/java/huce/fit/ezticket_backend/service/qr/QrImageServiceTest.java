package huce.fit.ezticket_backend.service.qr;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QrImageServiceTest {

    private final QrImageService qrImageService = new QrImageService();

    @Test
    void qrPngSacNetToiThieu300x300() throws Exception {
        byte[] png = qrImageService.generateQrPng("ticket-code.101.1729000000.signature");

        // Magic bytes PNG: 89 50 4E 47
        assertTrue(png.length > 100);
        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 0x50, png[1]);
        assertEquals((byte) 0x4E, png[2]);
        assertEquals((byte) 0x47, png[3]);

        BufferedImage image;
        try (ByteArrayInputStream in = new ByteArrayInputStream(png)) {
            image = ImageIO.read(in);
        }
        assertNotNull(image);
        assertTrue(image.getWidth() >= 300, "Rong QR: " + image.getWidth());
        assertTrue(image.getHeight() >= 300, "Cao QR: " + image.getHeight());
    }

    @Test
    void qrContentRongThiBaoLoi() {
        assertThrows(IllegalArgumentException.class, () -> qrImageService.generateQrPng("  "));
        assertThrows(IllegalArgumentException.class, () -> qrImageService.generateQrPng(null));
    }
}
