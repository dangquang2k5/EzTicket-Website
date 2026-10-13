package huce.fit.ezticket_backend.dto.ticket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** GET /api/v1/tickets/{code} detail (US-KH-06 AC-02). No QR secret material is logged. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketDetailResponse {

    private String code;
    private String eventTitle;
    private LocalDateTime eventTime;
    private String venue;
    private String ticketType;
    private String holderName;
    private String buyerEmail;
    private String status;
    private String qrContent;
    private String qrSignature;
    private LocalDateTime checkedInAt;
}
