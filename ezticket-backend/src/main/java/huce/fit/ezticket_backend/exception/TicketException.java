package huce.fit.ezticket_backend.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Business exception for ticket flows (SCRUM-29).
 * Carries a machine-readable errorCode per API Spec 9.4/9.5:
 * INVALID_SIGNATURE / WRONG_EVENT (400), ALREADY_USED / EXPIRED (409).
 */
@Getter
public class TicketException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public TicketException(String errorCode, HttpStatus httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public static TicketException invalidSignature() {
        return new TicketException("INVALID_SIGNATURE", HttpStatus.BAD_REQUEST, "VÉ KHÔNG HỢP LỆ");
    }

    public static TicketException wrongEvent() {
        return new TicketException("WRONG_EVENT", HttpStatus.BAD_REQUEST, "VÉ KHÔNG HỢP LỆ");
    }

    public static TicketException alreadyUsed(String checkedInAtText) {
        return new TicketException("ALREADY_USED", HttpStatus.CONFLICT,
                "CẢNH BÁO: VÉ ĐÃ SỬ DỤNG LÚC [" + checkedInAtText + "]");
    }

    public static TicketException expired() {
        // BR-24: ticket past events.end_time is EXPIRED.
        // [CHƯA CÓ TRONG TÀI LIỆU] exact UI string for expired scan; reuse reason code.
        return new TicketException("EXPIRED", HttpStatus.CONFLICT, "VÉ ĐÃ HẾT HẠN");
    }

    public static TicketException cancelled() {
        // [CHƯA CÓ TRONG TÀI LIỆU] CANCELLED scan has no code in API Spec table 34;
        // default: reject with 409 under its own code.
        return new TicketException("TICKET_CANCELLED", HttpStatus.CONFLICT, "VÉ KHÔNG HỢP LỆ");
    }

    public static TicketException notFound() {
        return new TicketException("TICKET_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy vé");
    }

    public static TicketException forbidden() {
        return new TicketException("FORBIDDEN", HttpStatus.FORBIDDEN,
                "Bạn không có quyền truy cập vé này");
    }
}
