package huce.fit.ezticket_backend.service.qr;

/**
 * Codec for ticket QR content (C-04 default: DD format).
 * Fixed format: payload = ticketCode + "." + eventId + "." + epochSec;
 * signature = base64url(HMAC-SHA256(payload, secret));
 * qrContent = payload + "." + signature.
 */
public interface QrPayloadCodec {

    SignedQr sign(String ticketCode, Long eventId);

    QrVerifyResult verify(String qrContent);

    record SignedQr(String payload, String signature, String qrContent) {
    }

    record QrVerifyResult(boolean signatureValid, String ticketCode, Long eventId, Long issuedAtEpochSec) {
    }
}
