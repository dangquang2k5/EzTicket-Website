package huce.fit.ezticket_backend.service.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * Render nội dung QR thành ảnh PNG sắc nét (BR-09, US-KH-06 AC-02).
 */
@Service
public class QrImageService {

    public static final int MIN_SIZE = 300;
    public static final int DEFAULT_SIZE = 350;

    /**
     * @param qrContent nội dung QR đã ký (qrContent từ {@link QrPayloadCodec})
     * @return bytes ảnh PNG vuông, cạnh >= 300px
     */
    public byte[] generateQrPng(String qrContent) {
        return generateQrPng(qrContent, DEFAULT_SIZE);
    }

    public byte[] generateQrPng(String qrContent, int size) {
        if (qrContent == null || qrContent.isBlank()) {
            throw new IllegalArgumentException("qrContent is required");
        }
        int edge = Math.max(size, MIN_SIZE);
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(qrContent, BarcodeFormat.QR_CODE, edge, edge,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN, 2));
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                MatrixToImageWriter.writeToStream(matrix, "PNG", out);
                return out.toByteArray();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Khong the sinh anh QR", e);
        }
    }
}
