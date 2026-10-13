package huce.fit.ezticket_backend.service.qr;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HmacQrPayloadCodecTest {

    private final HmacQrPayloadCodec codec = new HmacQrPayloadCodec("test-secret-123");

    @Test
    void signRoiVerifyDung() {
        String ticketCode = UUID.randomUUID().toString();
        QrPayloadCodec.SignedQr signed = codec.sign(ticketCode, 101L);

        assertNotNull(signed.payload());
        assertNotNull(signed.signature());
        assertEquals(signed.payload() + "." + signed.signature(), signed.qrContent());

        QrPayloadCodec.QrVerifyResult result = codec.verify(signed.qrContent());
        assertTrue(result.signatureValid());
        assertEquals(ticketCode, result.ticketCode());
        assertEquals(101L, result.eventId());
        assertNotNull(result.issuedAtEpochSec());
    }

    @Test
    void suaMotKyTuPayloadThiChuKySai() {
        QrPayloadCodec.SignedQr signed = codec.sign(UUID.randomUUID().toString(), 101L);
        String tampered = "x" + signed.qrContent().substring(1);

        QrPayloadCodec.QrVerifyResult result = codec.verify(tampered);
        assertFalse(result.signatureValid());
    }

    @Test
    void saiSecretThiChuKySai() {
        QrPayloadCodec.SignedQr signed = codec.sign(UUID.randomUUID().toString(), 101L);
        HmacQrPayloadCodec otherCodec = new HmacQrPayloadCodec("secret-khac");

        QrPayloadCodec.QrVerifyResult result = otherCodec.verify(signed.qrContent());
        assertFalse(result.signatureValid());
    }

    @Test
    void dauVaoRacKhongNemLoi() {
        String[] garbage = {null, "", "   ", "abc", "a.b", "a.b.c", "a.b.c.d.e", "..", "x.notanumber.123.sig"};
        for (String input : garbage) {
            QrPayloadCodec.QrVerifyResult result = codec.verify(input);
            assertNotNull(result);
            assertFalse(result.signatureValid(), "Dau vao rac phai tra signatureValid=false: " + input);
        }
    }
}
