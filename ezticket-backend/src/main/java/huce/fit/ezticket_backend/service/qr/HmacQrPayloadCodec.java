package huce.fit.ezticket_backend.service.qr;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * Ký và kiểm tra QR vé bằng HMAC-SHA256 (BR-09, C-04 theo DD).
 * Format: payload = ticketCode.eventId.epochSec,
 * signature = base64url(HMAC-SHA256(payload, secret)), qrContent = payload.signature.
 * Secret chỉ nằm phía server (${TICKET_QR_SECRET}); không log secret hay qrContent đầy đủ.
 */
@Slf4j
@Component
public class HmacQrPayloadCodec implements QrPayloadCodec {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final String DEV_FALLBACK_SECRET = "ezticket-dev-secret-change-me";

    private final String secret;

    public HmacQrPayloadCodec(
            @Value("${ticket.qr.secret:" + DEV_FALLBACK_SECRET + "}") String secret) {
        this.secret = (secret == null || secret.isBlank()) ? DEV_FALLBACK_SECRET : secret;
    }

    @PostConstruct
    void warnOnDevSecret() {
        if (DEV_FALLBACK_SECRET.equals(secret)) {
            log.warn("ticket.qr.secret dang dung gia tri dev mac dinh; production phai dat env TICKET_QR_SECRET");
        }
    }

    @Override
    public SignedQr sign(String ticketCode, Long eventId) {
        if (ticketCode == null || ticketCode.isBlank()) {
            throw new IllegalArgumentException("ticketCode is required");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId is required");
        }
        long epochSec = Instant.now().getEpochSecond();
        String payload = ticketCode + "." + eventId + "." + epochSec;
        String signature = hmacBase64Url(payload);
        return new SignedQr(payload, signature, payload + "." + signature);
    }

    @Override
    public QrVerifyResult verify(String qrContent) {
        try {
            if (qrContent == null || qrContent.isBlank()) {
                return invalid();
            }
            // ticketCode UUID v4 khong chua dau cham; signature base64url khong padding nen khong chua dau cham.
            String[] parts = qrContent.split("\\.");
            if (parts.length != 4) {
                return invalid();
            }
            String ticketCode = parts[0];
            Long eventId = Long.parseLong(parts[1]);
            Long issuedAt = Long.parseLong(parts[2]);
            String payload = parts[0] + "." + parts[1] + "." + parts[2];
            String expected = hmacBase64Url(payload);
            boolean ok = MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    parts[3].getBytes(StandardCharsets.UTF_8));
            return new QrVerifyResult(ok, ticketCode, eventId, issuedAt);
        } catch (Exception e) {
            // verify() khong nem loi voi dau vao rac.
            log.debug("QR verify that bai voi dau vao khong hop le");
            return invalid();
        }
    }

    private String hmacBase64Url(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Khong the ky HMAC-SHA256 cho QR", e);
        }
    }

    private static QrVerifyResult invalid() {
        return new QrVerifyResult(false, null, null, null);
    }
}
